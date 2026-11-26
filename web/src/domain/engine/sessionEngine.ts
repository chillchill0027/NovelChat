import type { AppSettings, CreativityLevel, StoryRecord } from '../../types/models';
import { nowIsoString } from '../../types/models';
import type { StateDelta } from '../../types/stateDelta';
import {
  createCheckpointSnapshot,
  getStory,
  mutateStory,
  rollbackToBeforeRound,
} from '../../storage/storyRepository';
import { assembleNarratorContext } from './contextAssembler';
import { parseLooseJson } from './jsonExtract';
import { completeChatJson, streamChatCompletion } from './llmClient';
import { cleanNarrativeText } from './narrativeCleaner';
import { evaluateNarrativeGuard } from './narrativeGuard';
import {
  buildChapterSummarySystemPrompt,
  buildChapterSummaryUserPrompt,
  buildOverviewSummarySystemPrompt,
  buildOverviewSummaryUserPrompt,
  buildStateKeeperSystemPrompt,
  buildStateKeeperUserPrompt,
} from './prompts';
import { applyStateDelta } from './stateDeltaApplier';

const CHAPTER_STRIDE = 10;
const OVERVIEW_THRESHOLD = 6;

export function creativityTemperature(level: CreativityLevel): number {
  if (level === 'steady') return 0.65;
  if (level === 'vivid') return 1.0;
  return 0.85;
}

export interface SessionCallbacks {
  onRecordChange: (record: StoryRecord) => void;
  onStreamUpdate: (streamingText: string, statusText: string) => void;
  onSyncingChange: (syncingRounds: number[]) => void;
  onError: (message: string) => void;
}

/**
 * Dual-track Engine V2 Session Coordinator for browser runtime:
 * - Foreground track: streams prose directly, runs guard, commits provisional checkpoint immediately.
 * - Background track: serial SyncQueue executes StateKeeper + 10/60-round MemoryManager without blocking player input.
 */
export class StorySessionEngine {
  private readonly storyId: string;
  private settings: AppSettings;
  private callbacks: SessionCallbacks;
  private activeAbort: AbortController | null = null;
  private syncQueueRunning = false;
  private syncingRounds = new Set<number>();
  private destroyed = false;

  constructor(storyId: string, settings: AppSettings, callbacks: SessionCallbacks) {
    this.storyId = storyId;
    this.settings = settings;
    this.callbacks = callbacks;
  }

  updateSettings(settings: AppSettings): void {
    this.settings = settings;
  }

  cancelStream(): void {
    this.activeAbort?.abort();
    this.activeAbort = null;
  }

  destroy(): void {
    this.destroyed = true;
    this.cancelStream();
  }

  async sendAction(rawInput: string): Promise<void> {
    const cleanInput = rawInput.trim();
    if (!cleanInput) return;

    const current = await getStory(this.storyId);
    if (!current) throw new Error('故事存档不存在');

    const nextRound =
      Math.max(
        current.story.current_round,
        current.narratives.reduce((m, n) => Math.max(m, n.turn), 0),
      ) + 1;

    await this.runForegroundRound(current, nextRound, cleanInput);
  }

  async regenerateRound(targetRound: number, revisedInput: string): Promise<void> {
    const cleanInput = revisedInput.trim();
    if (!cleanInput) return;
    this.cancelStream();
    const rolledBack = await rollbackToBeforeRound(this.storyId, targetRound);
    this.callbacks.onRecordChange(rolledBack);
    await this.runForegroundRound(rolledBack, targetRound, cleanInput);
  }

  async retryBackgroundSync(): Promise<void> {
    const rec = await mutateStory(this.storyId, (cur) => {
      const merged = Array.from(
        new Set([...cur.sync_status.pending_rounds, ...cur.sync_status.failed_rounds]),
      ).sort((a, b) => a - b);
      cur.sync_status.pending_rounds = merged;
      cur.sync_status.failed_rounds = [];
      cur.sync_status.last_error = undefined;
      return cur;
    });
    this.callbacks.onRecordChange(rec);
    void this.drainSyncQueue();
  }

  async resumePendingSyncOnVisibility(): Promise<void> {
    if (this.destroyed || this.syncQueueRunning) return;
    const rec = await getStory(this.storyId);
    if (!rec) return;
    if (rec.sync_status.pending_rounds.length > 0) {
      void this.drainSyncQueue();
    }
  }

  private async runForegroundRound(
    snapshot: StoryRecord,
    targetRound: number,
    cleanInput: string,
  ): Promise<void> {
    const controller = new AbortController();
    this.activeAbort = controller;

    const assembled = assembleNarratorContext(
      snapshot,
      cleanInput,
      this.settings.default_length,
    );
    const messages = [
      { role: 'system' as const, content: assembled.systemPrompt },
      ...assembled.history,
      { role: 'user' as const, content: assembled.userTurn },
    ];
    const previousNarrative =
      snapshot.narratives[snapshot.narratives.length - 1]?.narrative ?? '';
    const temp = creativityTemperature(this.settings.creativity);

    try {
      let finalNarrative = '';
      for (let attempt = 0; attempt < 2; attempt++) {
        this.callbacks.onStreamUpdate(
          '',
          attempt === 0 ? '正在构思并续写剧情...' : '检测到输出异常，正在轻度重试...',
        );
        const attemptMessages =
          attempt === 0
            ? messages
            : [
                ...messages,
                {
                  role: 'user' as const,
                  content: '请直接续写本轮故事正文，不要重复上一轮内容，也不要输出说明文字。',
                },
              ];

        const res = await streamChatCompletion({
          settings: this.settings,
          stage: 'narrative',
          messages: attemptMessages,
          temperature: attempt === 0 ? temp : Math.min(1.0, temp + 0.1),
          maxTokens: assembled.lengthHint.maxTokens,
          signal: controller.signal,
          onToken: (_delta, accumulated) => {
            this.callbacks.onStreamUpdate(accumulated, '正在流式续写剧情...');
          },
        });

        const cleaned = cleanNarrativeText(res.content, res.finishReason);
        const verdict = evaluateNarrativeGuard(cleaned, previousNarrative, attempt);
        if (verdict.kind === 'pass') {
          finalNarrative = verdict.cleanedText;
          break;
        }
        if (verdict.kind === 'fail') {
          if (cleaned) {
            finalNarrative = cleaned;
            break;
          }
          throw new Error(`正文生成质量校验未通过（${verdict.reason}），请重试`);
        }
      }

      if (!finalNarrative) {
        throw new Error('模型未返回有效正文，请重试');
      }

      // Commit foreground narrative + provisional pre-round checkpoint immediately
      const committed = await mutateStory(this.storyId, (cur) => {
        const now = nowIsoString();
        const isProvisional = cur.sync_status.pending_rounds.length > 0;
        if (!cur.checkpoints[targetRound]) {
          cur.checkpoints[targetRound] = createCheckpointSnapshot(cur, targetRound, isProvisional);
        }
        cur.narratives = [
          ...cur.narratives.filter((n) => n.turn !== targetRound),
          {
            turn: targetRound,
            user_input: cleanInput,
            narrative: finalNarrative,
            created_at: now,
          },
        ].sort((a, b) => a.turn - b.turn);
        cur.story.current_round = Math.max(cur.story.current_round, targetRound);
        cur.preview = finalNarrative.slice(0, 140);
        cur.sync_status.pending_rounds = Array.from(
          new Set([...cur.sync_status.pending_rounds, targetRound]),
        ).sort((a, b) => a - b);
        cur.sync_status.failed_rounds = cur.sync_status.failed_rounds.filter(
          (r) => r !== targetRound,
        );
        return cur;
      });

      this.activeAbort = null;
      this.callbacks.onStreamUpdate('', '');
      this.callbacks.onRecordChange(committed);

      // Kick off background serial StateKeeper + MemoryManager (non-blocking)
      void this.drainSyncQueue();
    } catch (err) {
      this.activeAbort = null;
      this.callbacks.onStreamUpdate('', '');
      const msg = err instanceof Error ? err.message : String(err);
      if (msg !== '已停止生成') {
        this.callbacks.onError(msg);
      }
    }
  }

  private async drainSyncQueue(): Promise<void> {
    if (this.syncQueueRunning || this.destroyed) return;
    this.syncQueueRunning = true;

    try {
      while (!this.destroyed) {
        const current = await getStory(this.storyId);
        if (!current) break;
        const pending = [...current.sync_status.pending_rounds].sort((a, b) => a - b);
        if (pending.length === 0) break;

        // Batch any queued pending rounds together so background sync catches up swiftly
        const batch = pending.slice(0, 3);
        batch.forEach((r) => this.syncingRounds.add(r));
        this.callbacks.onSyncingChange(Array.from(this.syncingRounds).sort((a, b) => a - b));

        try {
          const sysPrompt = buildStateKeeperSystemPrompt(current.world);
          const userPrompt = buildStateKeeperUserPrompt(current, batch);
          const rawJson = await completeChatJson({
            settings: this.settings,
            stage: 'state_keeper',
            messages: [
              { role: 'system', content: sysPrompt },
              { role: 'user', content: userPrompt },
            ],
            temperature: 0.15,
            maxTokens: 2000,
            jsonMode: true,
          });
          const delta = parseLooseJson<StateDelta>(rawJson);
          if (!delta) {
            throw new Error('后台状态同步返回的 JSON 无法解析');
          }

          const updated = await mutateStory(this.storyId, (latest) => {
            const now = nowIsoString();
            const applied = applyStateDelta(latest, delta, batch, now);
            const next = applied.record;
            const lastSynced = batch[batch.length - 1];
            next.sync_status.synced_round = Math.max(next.sync_status.synced_round, lastSynced);
            next.sync_status.pending_rounds = next.sync_status.pending_rounds.filter(
              (r) => !batch.includes(r),
            );
            next.sync_status.failed_rounds = next.sync_status.failed_rounds.filter(
              (r) => !batch.includes(r),
            );
            next.sync_status.last_error = undefined;
            if (applied.suggestions.length > 0) {
              next.sync_status.last_suggestions = applied.suggestions;
            }
            // Patch any provisional checkpoint created for a later round while this batch was syncing
            for (const key of Object.keys(next.checkpoints)) {
              const cpRound = Number(key);
              const cp = next.checkpoints[cpRound];
              if (cp && cp.provisional && cpRound > lastSynced) {
                next.checkpoints[cpRound] = {
                  ...cp,
                  story_state: structuredClone(next.story),
                  player_state: structuredClone(next.player),
                  character_cards: structuredClone(next.characters),
                  worldbook_entries: structuredClone(next.worldbook),
                  provisional: next.sync_status.pending_rounds.some((r) => r < cpRound),
                };
              }
            }
            return next;
          });

          if (!this.destroyed) {
            this.callbacks.onRecordChange(updated);
          }

          // Check if 10-round chapter summary or 60-round overview compaction is due
          await this.maybeCompactMemory(updated);
        } catch (err) {
          const errMsg = err instanceof Error ? err.message : String(err);
          const failedRec = await mutateStory(this.storyId, (latest) => {
            latest.sync_status.pending_rounds = latest.sync_status.pending_rounds.filter(
              (r) => !batch.includes(r),
            );
            latest.sync_status.failed_rounds = Array.from(
              new Set([...latest.sync_status.failed_rounds, ...batch]),
            ).sort((a, b) => a - b);
            latest.sync_status.last_error = errMsg;
            return latest;
          });
          if (!this.destroyed) {
            this.callbacks.onRecordChange(failedRec);
          }
          break;
        } finally {
          batch.forEach((r) => this.syncingRounds.delete(r));
          this.callbacks.onSyncingChange(Array.from(this.syncingRounds).sort((a, b) => a - b));
        }
      }
    } finally {
      this.syncQueueRunning = false;
      this.syncingRounds.clear();
      this.callbacks.onSyncingChange([]);
    }
  }

  private async maybeCompactMemory(record: StoryRecord): Promise<void> {
    if (this.destroyed) return;
    const regularChapters = record.chapters.filter((c) => !c.is_overview);
    const highestCovered = record.chapters.reduce((m, c) => Math.max(m, c.to_turn), 0);
    const nextFrom = highestCovered + 1;
    const nextTo = highestCovered + CHAPTER_STRIDE;

    if (record.sync_status.synced_round >= nextTo) {
      const roundSummaries = record.summaries
        .filter((s) => s.turn >= nextFrom && s.turn <= nextTo)
        .sort((a, b) => a.turn - b.turn)
        .map((s) => ({ turn: s.turn, summary: s.summary }));

      if (roundSummaries.length >= 6) {
        try {
          const prevChapter = regularChapters[regularChapters.length - 1]?.text ?? '';
          const lastNarr = record.narratives.find((n) => n.turn === nextTo)?.narrative.slice(-400) ?? '';
          const chapterText = await completeChatJson({
            settings: this.settings,
            stage: 'summarizer',
            messages: [
              { role: 'system', content: buildChapterSummarySystemPrompt() },
              {
                role: 'user',
                content: buildChapterSummaryUserPrompt(prevChapter, roundSummaries, lastNarr),
              },
            ],
            temperature: 0.25,
            maxTokens: 700,
            jsonMode: false,
          });
          if (chapterText.trim()) {
            const nextIdx = (regularChapters[regularChapters.length - 1]?.index ?? 0) + 1;
            const afterChapter = await mutateStory(this.storyId, (cur) => {
              cur.chapters.push({
                index: nextIdx,
                from_turn: nextFrom,
                to_turn: nextTo,
                text: chapterText.trim(),
                is_overview: false,
                created_at: nowIsoString(),
              });
              return cur;
            });
            this.callbacks.onRecordChange(afterChapter);

            // Overview compaction when > 6 regular chapters exist
            const overview = afterChapter.chapters.find((c) => c.is_overview);
            const uncoveredRegular = afterChapter.chapters
              .filter((c) => !c.is_overview && c.to_turn > (overview?.to_turn ?? 0))
              .sort((a, b) => a.index - b.index);
            if (uncoveredRegular.length >= OVERVIEW_THRESHOLD) {
              const toMerge = uncoveredRegular.slice(0, 3);
              const overviewText = await completeChatJson({
                settings: this.settings,
                stage: 'summarizer',
                messages: [
                  { role: 'system', content: buildOverviewSummarySystemPrompt() },
                  {
                    role: 'user',
                    content: buildOverviewSummaryUserPrompt(overview?.text ?? '', toMerge),
                  },
                ],
                temperature: 0.2,
                maxTokens: 1000,
                jsonMode: false,
              });
              if (overviewText.trim()) {
                const afterOverview = await mutateStory(this.storyId, (cur) => {
                  cur.chapters = cur.chapters.filter((c) => !c.is_overview);
                  cur.chapters.unshift({
                    index: 0,
                    from_turn: 1,
                    to_turn: toMerge[toMerge.length - 1].to_turn,
                    text: overviewText.trim(),
                    is_overview: true,
                    created_at: nowIsoString(),
                  });
                  return cur;
                });
                this.callbacks.onRecordChange(afterOverview);
              }
            }
          }
        } catch {
          // Chapter summarization is best-effort and never blocks gameplay
        }
      }
    }
  }
}

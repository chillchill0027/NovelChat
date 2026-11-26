import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  Activity,
  ArrowDown,
  ArrowLeft,
  BookOpen,
  ChevronDown,
  ChevronRight,
  ChevronUp,
  Copy,
  Edit2,
  Eye,
  EyeOff,
  Flag,
  Loader2,
  MapPin,
  MoreVertical,
  PanelLeft,
  RefreshCw,
  Send,
  Settings2,
  Sliders,
  Square,
  Users,
} from 'lucide-react';
import {
  groupedFieldsForNpc,
  groupedFieldsForPlayer,
  npcPrimaryFields,
  playerPrimaryFields,
} from '../../domain/characterFields';
import { getRecentMetrics } from '../../domain/engine/llmClient';
import { StorySessionEngine } from '../../domain/engine/sessionEngine';
import { favorColor, formatFavor, trustColor } from '../../domain/palette';
import { saveSettings } from '../../storage/settingsStore';
import { updateNarrationSettings } from '../../storage/storyRepository';
import type {
  AppSettings,
  CharacterCard,
  CreativityLevel,
  FieldGroup,
  LengthPreference,
  NarrationPov,
  NarrativeEntry,
  StoryRecord,
} from '../../types/models';
import { EditorialAvatar } from '../components/EditorialAvatar';
import { ModalSheet } from '../components/ModalSheet';
import { useViewport } from '../hooks/useViewport';
import { useStreamingLifecycle } from '../hooks/useWakeLock';

interface ChatScreenProps {
  record: StoryRecord;
  settings: AppSettings;
  onBack: () => void;
  onRecordChange: (next: StoryRecord) => void;
  onSettingsChange: (next: AppSettings) => void;
  onOpenWorldbook: () => void;
  onOpenCharacters: (selectedName?: string) => void;
  onOpenApiSetup: () => void;
  onToast: (msg: string) => void;
}

const GROUP_TITLES: Record<FieldGroup, string> = {
  status: '当前状态',
  relationship: '与主角的关系',
  profile: '人物画像',
  ability: '能力与成长',
  identity: '基础信息',
};

export function ChatScreen({
  record,
  settings,
  onBack,
  onRecordChange,
  onSettingsChange,
  onOpenWorldbook,
  onOpenCharacters,
  onOpenApiSetup,
  onToast,
}: ChatScreenProps) {
  const viewport = useViewport();
  const [input, setInput] = useState('');
  const [streamingText, setStreamingText] = useState('');
  const [statusText, setStatusText] = useState('');
  const [syncingRounds, setSyncingRounds] = useState<number[]>([]);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  const [showSidebar, setShowSidebar] = useState(true);
  const [capsuleExpanded, setCapsuleExpanded] = useState(false);
  const [menuOpen, setMenuOpen] = useState(false);

  const [playerSheetOpen, setPlayerSheetOpen] = useState(false);
  const [selectedNpcName, setSelectedNpcName] = useState<string | null>(null);
  const [narrationSheetOpen, setNarrationSheetOpen] = useState(false);
  const [perfSheetOpen, setPerfSheetOpen] = useState(false);

  const [pendingRegen, setPendingRegen] = useState<NarrativeEntry | null>(null);
  const [editingEntry, setEditingEntry] = useState<NarrativeEntry | null>(null);
  const [editedActionText, setEditedActionText] = useState('');

  // Narration settings form state
  const [draftPov, setDraftPov] = useState<NarrationPov>(record.narration.pov);
  const [draftLen, setDraftLen] = useState<LengthPreference | ''>(record.narration.length ?? '');
  const [draftCreativity, setDraftCreativity] = useState<CreativityLevel>(settings.creativity);
  const [draftStyleSample, setDraftStyleSample] = useState(record.narration.style_sample);

  // Scroll auto-follow
  const scrollRef = useRef<HTMLDivElement | null>(null);
  const [autoFollow, setAutoFollow] = useState(true);
  const [showJumpLatest, setShowJumpLatest] = useState(false);

  const isStreaming = Boolean(statusText || streamingText);
  const engineRef = useRef<StorySessionEngine | null>(null);
  const recordChangeRef = useRef(onRecordChange);
  recordChangeRef.current = onRecordChange;

  useEffect(() => {
    const engine = new StorySessionEngine(record.id, settings, {
      onRecordChange: (next) => recordChangeRef.current(next),
      onStreamUpdate: (text, status) => {
        setStreamingText(text);
        setStatusText(status);
      },
      onSyncingChange: (rounds) => setSyncingRounds(rounds),
      onError: (msg) => setErrorMsg(msg),
    });
    engineRef.current = engine;
    void engine.resumePendingSyncOnVisibility();
    return () => {
      engine.destroy();
      engineRef.current = null;
    };
  }, [record.id]);

  useEffect(() => {
    engineRef.current?.updateSettings(settings);
  }, [settings]);

  const handleTabVisible = useCallback(() => {
    void engineRef.current?.resumePendingSyncOnVisibility();
  }, []);

  useStreamingLifecycle(isStreaming, handleTabVisible);

  const scrollToBottom = useCallback((smooth = false) => {
    const el = scrollRef.current;
    if (!el) return;
    el.scrollTo({
      top: el.scrollHeight,
      behavior: smooth ? 'smooth' : 'auto',
    });
  }, []);

  useEffect(() => {
    if (autoFollow) {
      scrollToBottom(false);
    }
  }, [record.narratives.length, streamingText, autoFollow, scrollToBottom]);

  const handleScroll = () => {
    const el = scrollRef.current;
    if (!el) return;
    const distFromBottom = el.scrollHeight - el.scrollTop - el.clientHeight;
    const atBottom = distFromBottom < 80;
    setAutoFollow(atBottom);
    setShowJumpLatest(!atBottom && record.narratives.length > 0);
  };

  const playerName = record.player.name || '主角';
  const primaryPlayerFields = useMemo(
    () => playerPrimaryFields(record.player, record.world.character_fields),
    [record.player, record.world.character_fields],
  );

  const presentNpcs = useMemo(() => {
    const active = record.story.active_characters;
    const matched = active
      .map((name) => record.characters.find((c) => c.name === name || c.id === name))
      .filter((x): x is CharacterCard => Boolean(x));
    return matched;
  }, [record.characters, record.story.active_characters]);

  const currentGoal =
    record.player.story_progress.current_goal.trim() ||
    record.story.pending_events[0]?.trim() ||
    '';

  const suggestions = useMemo(() => {
    const saved = record.sync_status.last_suggestions.filter((s) => s.trim());
    if (saved.length > 0) return saved.slice(0, 4);
    const defaults: string[] = [];
    if (presentNpcs[0]) defaults.push(`和${presentNpcs[0].name}聊聊`);
    if (currentGoal) defaults.push('继续推进当前目标');
    defaults.push('查看待办和时间安排', '观察一下周围');
    return defaults.slice(0, 4);
  }, [record.sync_status.last_suggestions, presentNpcs, currentGoal]);

  const handleSend = async () => {
    const trimmed = input.trim();
    if (!trimmed || isStreaming) return;
    setErrorMsg(null);
    setInput('');
    setAutoFollow(true);
    await engineRef.current?.sendAction(trimmed);
  };

  const handleComposerKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.nativeEvent.isComposing) return;
    if (e.key === 'Enter' && (e.ctrlKey || e.metaKey || (!e.shiftKey && viewport.isExpanded))) {
      e.preventDefault();
      void handleSend();
    }
  };

  const handleCopy = async (text: string) => {
    try {
      await navigator.clipboard.writeText(text);
      onToast('已复制到剪贴板');
    } catch {
      onToast('复制失败，请手动长按选择');
    }
  };

  const handleConfirmRegen = async () => {
    if (!pendingRegen) return;
    const entry = pendingRegen;
    setPendingRegen(null);
    setErrorMsg(null);
    setAutoFollow(true);
    await engineRef.current?.regenerateRound(entry.turn, entry.user_input);
  };

  const handleConfirmEdit = async () => {
    if (!editingEntry || !editedActionText.trim()) return;
    const turn = editingEntry.turn;
    const revised = editedActionText.trim();
    setEditingEntry(null);
    setErrorMsg(null);
    setAutoFollow(true);
    await engineRef.current?.regenerateRound(turn, revised);
  };

  const openNarrationSheet = () => {
    setDraftPov(record.narration.pov);
    setDraftLen(record.narration.length ?? '');
    setDraftCreativity(settings.creativity);
    setDraftStyleSample(record.narration.style_sample);
    setNarrationSheetOpen(true);
  };

  const handleSaveNarration = async () => {
    const updatedRec = await updateNarrationSettings(record.id, {
      pov: draftPov,
      length: draftLen || null,
      style_sample: draftStyleSample,
    });
    onRecordChange(updatedRec);
    if (draftCreativity !== settings.creativity) {
      const updatedSettings = await saveSettings({ ...settings, creativity: draftCreativity });
      onSettingsChange(updatedSettings);
    }
    setNarrationSheetOpen(false);
    onToast('已更新叙事偏好');
  };

  const selectedNpcCard = selectedNpcName
    ? record.characters.find((c) => c.name === selectedNpcName) ?? null
    : null;

  const sceneTitle = record.story.current_location || '当前场景';
  const sceneMeta = [record.story.time_of_day, record.story.weather, record.story.mood]
    .filter((s) => s?.trim())
    .join(' · ');

  const failedRounds = record.sync_status.failed_rounds;
  const readingMaxWidth = viewport.isExpanded ? 'max-w-[780px]' : 'max-w-[720px]';

  return (
    <div className="safe-top flex h-full w-full flex-col overflow-hidden bg-[#0E1014]">
      {/* Top App Bar */}
      <header className="z-20 shrink-0 border-b border-[#292F3C]/60 bg-[#0E1014]">
        <div className="flex h-14 items-center justify-between px-3 sm:px-4">
          <div className="flex min-w-0 items-center gap-2">
            <button
              type="button"
              onClick={() => {
                if (isStreaming) {
                  engineRef.current?.cancelStream();
                }
                onBack();
              }}
              className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-[#A0A8B6] hover:bg-[#1B1F28] hover:text-[#ECEAE4]"
              aria-label="返回故事库"
            >
              <ArrowLeft className="h-5 w-5" />
            </button>
            <div className="min-w-0">
              <div className="truncate text-[15px] font-semibold text-[#ECEAE4]">{playerName}</div>
              <div className="truncate text-[11px] text-[#A0A8B6]">
                {record.player.class ? `${record.player.class} · ` : ''}第{record.story.current_round}轮
              </div>
            </div>
          </div>

          <div className="flex shrink-0 items-center gap-1">
            {viewport.isTwoPane && (
              <button
                type="button"
                onClick={() => setShowSidebar((s) => !s)}
                className={`flex h-9 w-9 items-center justify-center rounded-full transition-colors ${
                  showSidebar
                    ? 'bg-[#1B1F28] text-[#E7C278]'
                    : 'text-[#A0A8B6] hover:bg-[#1B1F28] hover:text-[#ECEAE4]'
                }`}
                title={showSidebar ? '隐藏左侧边栏' : '展开左侧边栏'}
                aria-label="切换侧边栏"
              >
                <PanelLeft className="h-4 w-4" />
              </button>
            )}
            <button
              type="button"
              disabled={isStreaming}
              onClick={onOpenWorldbook}
              className="flex h-9 w-9 items-center justify-center rounded-full text-[#A0A8B6] transition-colors hover:bg-[#1B1F28] hover:text-[#ECEAE4] disabled:opacity-40"
              title="世界书"
              aria-label="世界书"
            >
              <BookOpen className="h-4 w-4" />
            </button>
            <button
              type="button"
              disabled={isStreaming}
              onClick={() => onOpenCharacters()}
              className="flex h-9 w-9 items-center justify-center rounded-full text-[#A0A8B6] transition-colors hover:bg-[#1B1F28] hover:text-[#ECEAE4] disabled:opacity-40"
              title="全部角色"
              aria-label="全部角色"
            >
              <Users className="h-4 w-4" />
            </button>
            <div className="relative">
              <button
                type="button"
                onClick={() => setMenuOpen((o) => !o)}
                className="flex h-9 w-9 items-center justify-center rounded-full text-[#A0A8B6] hover:bg-[#1B1F28] hover:text-[#ECEAE4]"
                aria-label="更多菜单"
              >
                <MoreVertical className="h-4 w-4" />
              </button>
              {menuOpen && (
                <>
                  <div className="fixed inset-0 z-30" onClick={() => setMenuOpen(false)} />
                  <div className="absolute right-0 z-40 mt-1.5 w-48 overflow-hidden rounded-2xl border border-[#292F3C] bg-[#1B1F28] py-1.5 shadow-2xl">
                    <button
                      type="button"
                      onClick={() => {
                        setMenuOpen(false);
                        openNarrationSheet();
                      }}
                      className="flex w-full items-center gap-2.5 px-4 py-2.5 text-left text-xs text-[#ECEAE4] hover:bg-[#232834]"
                    >
                      <Sliders className="h-4 w-4 text-[#E7C278]" />
                      <span>叙事偏好与文风</span>
                    </button>
                    <button
                      type="button"
                      onClick={() => {
                        setMenuOpen(false);
                        setPerfSheetOpen(true);
                      }}
                      className="flex w-full items-center gap-2.5 px-4 py-2.5 text-left text-xs text-[#ECEAE4] hover:bg-[#232834]"
                    >
                      <Activity className="h-4 w-4 text-[#7AD0B8]" />
                      <span>引擎耗时与缓存监控</span>
                    </button>
                    <button
                      type="button"
                      onClick={() => {
                        setMenuOpen(false);
                        onOpenApiSetup();
                      }}
                      className="flex w-full items-center gap-2.5 px-4 py-2.5 text-left text-xs text-[#ECEAE4] hover:bg-[#232834]"
                    >
                      <Settings2 className="h-4 w-4 text-[#B5C1E3]" />
                      <span>API 与模型配置</span>
                    </button>
                    {!viewport.isTwoPane && (
                      <button
                        type="button"
                        onClick={() => {
                          setMenuOpen(false);
                          setCapsuleExpanded((v) => !v);
                        }}
                        className="flex w-full items-center gap-2.5 px-4 py-2.5 text-left text-xs text-[#ECEAE4] hover:bg-[#232834]"
                      >
                        {capsuleExpanded ? (
                          <EyeOff className="h-4 w-4 text-[#A0A8B6]" />
                        ) : (
                          <Eye className="h-4 w-4 text-[#A0A8B6]" />
                        )}
                        <span>{capsuleExpanded ? '收起场景状态' : '展开场景状态'}</span>
                      </button>
                    )}
                  </div>
                </>
              )}
            </div>
          </div>
        </div>
      </header>

      {/* Main Content Area: Two-Pane on Tablet Landscape / Desktop, Single-Pane on Phone / Portrait */}
      <div className="flex min-h-0 flex-1 overflow-hidden">
        {/* Redesigned Tablet/Desktop Persistent Left Story Sidebar (360px) */}
        {viewport.isTwoPane && showSidebar && (
          <aside className="flex w-[360px] shrink-0 flex-col border-r border-[#292F3C] bg-[#15181F]">
            {/* Scrollable Body */}
            <div className="flex-1 space-y-3 overflow-y-auto px-4 py-3.5">
              {/* Scene Card */}
              <div className="rounded-2xl border border-[#292F3C] bg-[#1B1F28] p-3">
                <div className="flex items-center gap-2.5">
                  <div className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-[#362B16] text-[#E7C278]">
                    <MapPin className="h-3.5 w-3.5" />
                  </div>
                  <div className="min-w-0 flex-1 text-sm font-semibold text-[#ECEAE4]">
                    {sceneTitle}
                  </div>
                </div>
                {sceneMeta && (
                  <p className="mt-1.5 text-xs leading-5 text-[#A0A8B6]">{sceneMeta}</p>
                )}
                {(syncingRounds.length > 0 || failedRounds.length > 0) && (
                  <div className="mt-2 flex flex-wrap gap-1.5">
                    {syncingRounds.length > 0 && (
                      <span className="inline-flex items-center gap-1.5 rounded-full bg-[#17332C] px-2.5 py-0.5 text-[11px] text-[#B6F0DF]">
                        <Loader2 className="h-3 w-3 animate-spin text-[#7AD0B8]" />
                        同步第{syncingRounds.join('/')}轮
                      </span>
                    )}
                    {failedRounds.length > 0 && (
                      <button
                        type="button"
                        onClick={() => void engineRef.current?.retryBackgroundSync()}
                        className="rounded-full bg-[#362B16] px-2.5 py-0.5 text-[11px] font-medium text-[#FBE2AC] hover:brightness-110"
                      >
                        补同步 ({failedRounds.length})
                      </button>
                    )}
                  </div>
                )}
              </div>

              {/* Protagonist Card */}
              <button
                type="button"
                onClick={() => setPlayerSheetOpen(true)}
                className="w-full rounded-2xl border border-[#292F3C] bg-[#1B1F28] p-3 text-left transition-colors hover:border-[#3A4254]"
              >
                <div className="flex items-center gap-2.5">
                  <EditorialAvatar name={playerName} size={36} />
                  <div className="min-w-0 flex-1">
                    <div className="truncate text-sm font-semibold text-[#ECEAE4]">{playerName}</div>
                    <div className="truncate text-[11px] text-[#6E7686]">
                      {record.player.class || '城市居民'}
                    </div>
                  </div>
                  <ChevronRight className="h-4 w-4 shrink-0 text-[#6E7686]" />
                </div>

                {primaryPlayerFields.length > 0 && (
                  <div className="mt-2.5 grid grid-cols-3 gap-1.5">
                    {primaryPlayerFields.map((f) => (
                      <div
                        key={f.definition.key}
                        className="rounded-[10px] bg-[#15181F] px-2 py-1.5 text-center"
                      >
                        <div className="truncate text-[10px] text-[#6E7686]">
                          {f.definition.label}
                        </div>
                        <div
                          className="tabular-nums mt-0.5 truncate text-xs font-semibold"
                          style={{ color: f.color }}
                        >
                          {f.formattedCompact}
                        </div>
                      </div>
                    ))}
                  </div>
                )}

                {currentGoal && (
                  <div className="mt-2.5 flex items-start gap-2 rounded-[10px] bg-[#15181F] px-2.5 py-2">
                    <Flag className="mt-0.5 h-3.5 w-3.5 shrink-0 text-[#E7C278]" />
                    <span className="text-xs leading-[17px] text-[#ECEAE4]">{currentGoal}</span>
                  </div>
                )}
              </button>

              {/* Unified Grouped Editorial NPC Roster (matches redesigned Android SidebarNpcRow) */}
              <div>
                <div className="mb-2 flex items-center justify-between px-0.5">
                  <span className="text-xs font-semibold text-[#A0A8B6]">在场人物</span>
                  <span
                    className={`text-[11px] font-medium ${
                      presentNpcs.length ? 'text-[#7AD0B8]' : 'text-[#6E7686]'
                    }`}
                  >
                    {presentNpcs.length} 人
                  </span>
                </div>

                <div className="overflow-hidden rounded-2xl border border-[#292F3C] bg-[#1B1F28]">
                  {presentNpcs.length === 0 ? (
                    <div className="px-3.5 py-3.5 text-xs text-[#6E7686]">
                      当前场景暂无其他在场人物
                    </div>
                  ) : (
                    presentNpcs.map((card, idx) => {
                      const rel = card.relationships[playerName] ?? {
                        favor: card.favor,
                        trust: 0,
                        stage: card.relationship_status || '初识',
                      };
                      const extras = npcPrimaryFields(
                        card,
                        playerName,
                        record.world.character_fields,
                      );
                      return (
                        <div key={card.id || card.name}>
                          <button
                            type="button"
                            onClick={() => setSelectedNpcName(card.name)}
                            className="flex w-full items-center gap-3 px-3 py-2.5 text-left transition-colors hover:bg-[#232834]/70"
                          >
                            <EditorialAvatar name={card.name} size={38} />
                            <div className="min-w-0 flex-1 space-y-0.5">
                              <div className="flex items-center gap-1.5">
                                <span className="truncate text-sm font-semibold text-[#ECEAE4]">
                                  {card.name}
                                </span>
                                <span className="shrink-0 rounded-md bg-[#232834] px-1.5 py-0.5 text-[10px] font-medium text-[#7AD0B8]">
                                  {rel.stage}
                                </span>
                              </div>
                              <div className="truncate text-[11px] text-[#6E7686]">
                                {card.occupation || '身份未知'}
                              </div>
                              <div className="flex items-center gap-2 text-[11px]">
                                <span className="tabular-nums">
                                  <span className="text-[#6E7686]">好感 </span>
                                  <span
                                    style={{ color: favorColor(rel.favor) }}
                                    className="font-semibold"
                                  >
                                    {formatFavor(rel.favor)}
                                  </span>
                                </span>
                                <span className="text-[#6E7686]/45">·</span>
                                <span className="tabular-nums">
                                  <span className="text-[#6E7686]">信任 </span>
                                  <span
                                    style={{ color: trustColor(rel.trust) }}
                                    className="font-semibold"
                                  >
                                    {rel.trust}/100
                                  </span>
                                </span>
                              </div>
                              {extras.length > 0 && (
                                <div className="flex flex-wrap gap-1 pt-0.5">
                                  {extras.map((ef) => (
                                    <span
                                      key={ef.definition.key}
                                      className="rounded-md bg-[#15181F] px-1.5 py-0.5 text-[10px]"
                                    >
                                      <span className="text-[#6E7686]">{ef.definition.label} </span>
                                      <span style={{ color: ef.color }} className="font-semibold">
                                        {ef.formattedCompact}
                                      </span>
                                    </span>
                                  ))}
                                </div>
                              )}
                            </div>
                            <ChevronRight className="h-4 w-4 shrink-0 text-[#6E7686]/70" />
                          </button>
                          {idx < presentNpcs.length - 1 && (
                            <div className="ml-[62px] mr-3 h-px bg-[#292F3C]" />
                          )}
                        </div>
                      );
                    })
                  )}
                </div>
              </div>
            </div>

            {/* Pinned Sidebar Footer */}
            <div className="safe-bottom shrink-0 border-t border-[#292F3C]/70 px-4 py-2.5">
              <div className="grid grid-cols-2 gap-2">
                <button
                  type="button"
                  disabled={isStreaming}
                  onClick={onOpenWorldbook}
                  className="inline-flex items-center justify-center gap-1.5 rounded-full border border-[#292F3C] bg-[#1B1F28] py-2 text-xs font-medium text-[#ECEAE4] transition-colors hover:bg-[#232834] disabled:opacity-40"
                >
                  <BookOpen className="h-3.5 w-3.5 text-[#E7C278]" />
                  <span>世界书</span>
                </button>
                <button
                  type="button"
                  disabled={isStreaming}
                  onClick={() => onOpenCharacters()}
                  className="inline-flex items-center justify-center gap-1.5 rounded-full border border-[#292F3C] bg-[#1B1F28] py-2 text-xs font-medium text-[#ECEAE4] transition-colors hover:bg-[#232834] disabled:opacity-40"
                >
                  <Users className="h-3.5 w-3.5 text-[#7AD0B8]" />
                  <span>全部角色</span>
                </button>
              </div>
            </div>
          </aside>
        )}

        {/* Right / Main Reading & Composer Pane */}
        <div className="relative flex min-w-0 flex-1 flex-col overflow-hidden">
          {/* Mobile / Tablet Portrait Collapsible Scene + Protagonist Capsule */}
          {!viewport.isTwoPane && (
            <div className={`mx-auto w-full ${readingMaxWidth} shrink-0 px-3 pt-2 pb-1`}>
              <div className="overflow-hidden rounded-[20px] border border-[#292F3C] bg-[#15181F]">
                <button
                  type="button"
                  onClick={() => setCapsuleExpanded((v) => !v)}
                  className="flex w-full items-center gap-2.5 px-3.5 py-2.5 text-left"
                >
                  <div className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-[#362B16] text-[#E7C278]">
                    <MapPin className="h-3.5 w-3.5" />
                  </div>
                  <div className="min-w-0 flex-1">
                    <div className="truncate text-[13px] font-semibold text-[#ECEAE4]">
                      {sceneTitle}
                    </div>
                    {sceneMeta && (
                      <div className="truncate text-[11px] text-[#6E7686]">{sceneMeta}</div>
                    )}
                  </div>
                  {syncingRounds.length > 0 && (
                    <span className="inline-flex items-center gap-1 rounded-full bg-[#17332C] px-2 py-0.5 text-[10px] text-[#B6F0DF]">
                      <Loader2 className="h-2.5 w-2.5 animate-spin text-[#7AD0B8]" />
                      同步
                    </span>
                  )}
                  {presentNpcs.length > 0 && (
                    <span className="rounded-full bg-[#1B1F28] px-2.5 py-0.5 text-[11px] text-[#7AD0B8]">
                      {presentNpcs.length} 人在场
                    </span>
                  )}
                  {capsuleExpanded ? (
                    <ChevronUp className="h-4 w-4 text-[#6E7686]" />
                  ) : (
                    <ChevronDown className="h-4 w-4 text-[#6E7686]" />
                  )}
                </button>

                <div className="mx-3.5 h-px bg-[#292F3C]" />
                <button
                  type="button"
                  onClick={() => setPlayerSheetOpen(true)}
                  className="flex w-full items-center gap-2.5 px-3.5 py-2 text-left"
                >
                  <EditorialAvatar name={playerName} size={30} />
                  <div className="min-w-0 flex-1">
                    <div className="truncate text-[13px] font-semibold text-[#ECEAE4]">
                      {playerName}
                    </div>
                    <div className="truncate text-[10px] text-[#6E7686]">
                      {record.player.class || '城市居民'}
                    </div>
                  </div>
                  <div className="flex shrink-0 gap-1.5">
                    {primaryPlayerFields.map((f) => (
                      <div
                        key={f.definition.key}
                        className="rounded-[10px] bg-[#1B1F28] px-2 py-1 text-center"
                      >
                        <div className="text-[9px] text-[#6E7686]">{f.definition.label}</div>
                        <div
                          className="tabular-nums text-[11px] font-semibold"
                          style={{ color: f.color }}
                        >
                          {f.formattedCompact}
                        </div>
                      </div>
                    ))}
                  </div>
                </button>

                {capsuleExpanded && (
                  <div className="space-y-2 border-t border-[#292F3C] px-3.5 py-2.5">
                    {currentGoal && (
                      <div className="flex items-center gap-2 rounded-xl bg-[#1B1F28] px-3 py-2 text-xs text-[#ECEAE4]">
                        <Flag className="h-3.5 w-3.5 shrink-0 text-[#E7C278]" />
                        <span className="truncate">{currentGoal}</span>
                      </div>
                    )}
                    {presentNpcs.length > 0 && (
                      <div className="no-scrollbar flex gap-2 overflow-x-auto pb-0.5">
                        {presentNpcs.map((card) => {
                          const rel = card.relationships[playerName] ?? {
                            favor: card.favor,
                            trust: 0,
                            stage: card.relationship_status || '初识',
                          };
                          return (
                            <button
                              key={card.id || card.name}
                              type="button"
                              onClick={() => setSelectedNpcName(card.name)}
                              className="flex w-44 shrink-0 items-center gap-2.5 rounded-2xl border border-[#292F3C] bg-[#1B1F28] p-2.5 text-left"
                            >
                              <EditorialAvatar name={card.name} size={32} />
                              <div className="min-w-0 flex-1">
                                <div className="truncate text-xs font-semibold text-[#ECEAE4]">
                                  {card.name}
                                </div>
                                <div className="truncate text-[10px] text-[#7AD0B8]">
                                  {rel.stage} · {formatFavor(rel.favor)}
                                </div>
                              </div>
                            </button>
                          );
                        })}
                      </div>
                    )}
                  </div>
                )}
              </div>
            </div>
          )}

          {/* Narrative Scroll Canvas */}
          <div className="relative min-h-0 flex-1">
            <div
              ref={scrollRef}
              onScroll={handleScroll}
              className="h-full overflow-y-auto px-4 pt-4 pb-16 sm:px-6"
            >
              <div className={`mx-auto w-full ${readingMaxWidth} space-y-6`}>
                {/* Opening Scene Banner if Round 0 */}
                {record.narratives.length === 0 && !isStreaming && (
                  <div className="rounded-[22px] border border-[#292F3C] bg-[#15181F] p-5">
                    <div className="text-xs font-semibold text-[#E7C278]">
                      序幕 · {sceneTitle}
                    </div>
                    <p className="mt-2.5 whitespace-pre-wrap text-[15px] leading-7 text-[#ECEAE4]/90">
                      {record.story.scene_description ||
                        record.world.description ||
                        '世界已经准备就绪。在下方描述你的第一步行动，开始故事。'}
                    </p>
                    {record.story.pending_events.length > 0 && (
                      <div className="mt-4 space-y-1.5 border-t border-[#292F3C] pt-3">
                        <div className="text-[11px] font-medium text-[#A0A8B6]">当前待办</div>
                        {record.story.pending_events.map((ev) => (
                          <div key={ev} className="flex items-start gap-2 text-xs text-[#ECEAE4]/85">
                            <span className="text-[#E7C278]">·</span>
                            <span>{ev}</span>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                )}

                {/* Story Rounds */}
                {record.narratives.map((entry) => {
                  const paragraphs = entry.narrative
                    .split(/\n+/)
                    .map((p) => p.trim())
                    .filter(Boolean);
                  const hasCheckpoint = Boolean(record.checkpoints[entry.turn]);
                  return (
                    <article key={entry.turn} className="space-y-4">
                      {/* Player Action Bubble */}
                      <div className="flex flex-col items-end">
                        <div className="mb-1 text-[11px] text-[#6E7686]">
                          {playerName} · 第{entry.turn}轮
                        </div>
                        <div className="max-w-[85%] rounded-[20px] rounded-tr-md border border-[#333F56] bg-[#222A3A] px-4 py-2.5 text-[14px] leading-6 text-[#ECEAE4]">
                          {entry.user_input}
                        </div>
                        <div className="mt-1.5 flex items-center gap-3 text-[11px] text-[#6E7686]">
                          <button
                            type="button"
                            onClick={() => void handleCopy(entry.user_input)}
                            className="inline-flex items-center gap-1 hover:text-[#ECEAE4]"
                          >
                            <Copy className="h-3 w-3" />
                            <span>复制</span>
                          </button>
                          {hasCheckpoint && !isStreaming && (
                            <button
                              type="button"
                              onClick={() => {
                                setEditingEntry(entry);
                                setEditedActionText(entry.user_input);
                              }}
                              className="inline-flex items-center gap-1 hover:text-[#E7C278]"
                            >
                              <Edit2 className="h-3 w-3" />
                              <span>修改</span>
                            </button>
                          )}
                        </div>
                      </div>

                      {/* Round Editorial Header + Narrative Prose */}
                      <div>
                        <div className="mb-3 flex items-center gap-3">
                          <span className="rounded-full bg-[#1B1F28] px-2.5 py-0.5 text-[11px] font-medium text-[#A0A8B6]">
                            第 {entry.turn} 轮
                          </span>
                          <div className="h-px flex-1 bg-[#292F3C]" />
                          <button
                            type="button"
                            onClick={() => void handleCopy(entry.narrative)}
                            className="inline-flex items-center gap-1 text-[11px] text-[#6E7686] hover:text-[#ECEAE4]"
                          >
                            <Copy className="h-3 w-3" />
                            <span>复制正文</span>
                          </button>
                          {hasCheckpoint && !isStreaming && (
                            <button
                              type="button"
                              onClick={() => setPendingRegen(entry)}
                              className="inline-flex items-center gap-1 text-[11px] text-[#6E7686] hover:text-[#E7C278]"
                            >
                              <RefreshCw className="h-3 w-3" />
                              <span>重新生成</span>
                            </button>
                          )}
                        </div>

                        <div
                          className={`space-y-4 text-[#ECEAE4] ${
                            viewport.isExpanded
                              ? 'text-[17px] leading-[29px]'
                              : 'text-[16px] leading-[27px]'
                          }`}
                        >
                          {paragraphs.map((para, pIdx) => (
                            <p
                              key={pIdx}
                              className={
                                pIdx === 0
                                  ? 'border-l-2 border-[#54472C] pl-3.5'
                                  : ''
                              }
                            >
                              {para}
                            </p>
                          ))}
                        </div>
                      </div>
                    </article>
                  );
                })}

                {/* Live Streaming Output */}
                {isStreaming && (
                  <div className="space-y-3">
                    <div className="flex items-center gap-2 text-xs text-[#A0A8B6]">
                      <Loader2 className="h-3.5 w-3.5 animate-spin text-[#E7C278]" />
                      <span>{statusText || '正在续写剧情...'}</span>
                    </div>
                    {streamingText && (
                      <div
                        className={`space-y-4 whitespace-pre-wrap text-[#ECEAE4] ${
                          viewport.isExpanded
                            ? 'text-[17px] leading-[29px]'
                            : 'text-[16px] leading-[27px]'
                        }`}
                      >
                        <p className="border-l-2 border-[#E7C278]/60 pl-3.5">
                          {streamingText}
                          <span className="streaming-cursor" />
                        </p>
                      </div>
                    )}
                  </div>
                )}
              </div>
            </div>

            {/* Bottom Gradient Fade Scrim + Floating "最新" Scroll Pill */}
            <div className="pointer-events-none absolute inset-x-0 bottom-0 h-12 bg-gradient-to-t from-[#0E1014] to-transparent" />
            {showJumpLatest && (
              <button
                type="button"
                onClick={() => {
                  setAutoFollow(true);
                  scrollToBottom(true);
                }}
                className="absolute bottom-3 left-1/2 z-10 inline-flex -translate-x-1/2 items-center gap-1 rounded-full border border-[#292F3C] bg-[#232834]/95 px-3.5 py-1.5 text-xs font-medium text-[#ECEAE4] shadow-lg backdrop-blur-sm hover:bg-[#2D3342]"
              >
                <ArrowDown className="h-3.5 w-3.5" />
                <span>最新</span>
              </button>
            )}
          </div>

          {/* Error & Sync Retry Banners */}
          {errorMsg && (
            <div className={`mx-auto w-full ${readingMaxWidth} px-3 pb-1.5`}>
              <div className="flex items-center justify-between gap-2 rounded-2xl bg-[#3B1B20] px-3.5 py-2 text-xs text-[#FFD9DC]">
                <span className="break-all">{errorMsg}</span>
                <button
                  type="button"
                  onClick={() => setErrorMsg(null)}
                  className="shrink-0 font-semibold text-[#E7C278]"
                >
                  关闭
                </button>
              </div>
            </div>
          )}

          {failedRounds.length > 0 && (
            <div className={`mx-auto w-full ${readingMaxWidth} px-3 pb-1.5`}>
              <div className="flex items-center justify-between gap-2 rounded-2xl border border-[#E7C278]/35 bg-[#362B16] px-3.5 py-2 text-xs text-[#FBE2AC]">
                <span>
                  第 {failedRounds.join('、')} 轮后台状态同步失败，可继续游玩或点击重试
                </span>
                <button
                  type="button"
                  onClick={() => void engineRef.current?.retryBackgroundSync()}
                  className="shrink-0 font-semibold text-[#E7C278] hover:underline"
                >
                  重试同步
                </button>
              </div>
            </div>
          )}

          {/* Quick Suggestion Chips + Gemini-Style Floating Pill Composer */}
          <div className="safe-bottom shrink-0 bg-[#0E1014] px-3 pt-1 pb-2.5 sm:px-5">
            <div className={`mx-auto w-full ${readingMaxWidth} space-y-2`}>
              {!isStreaming && suggestions.length > 0 && (
                <div className="no-scrollbar flex gap-2 overflow-x-auto pb-0.5">
                  {suggestions.map((chip) => (
                    <button
                      key={chip}
                      type="button"
                      onClick={() => setInput(chip)}
                      title={chip}
                      className="max-w-[260px] shrink-0 truncate rounded-full border border-[#292F3C] bg-[#15181F] px-3.5 py-1.5 text-xs text-[#ECEAE4]/90 transition-colors hover:border-[#E7C278]/50 hover:bg-[#1B1F28] sm:max-w-[340px]"
                    >
                      {chip}
                    </button>
                  ))}
                </div>
              )}

              <div className="flex items-end gap-2 rounded-[26px] border border-[#292F3C] bg-[#1B1F28] py-1.5 pr-2 pl-4 transition-colors focus-within:border-[#E7C278]/60">
                <textarea
                  rows={1}
                  value={input}
                  onChange={(e) => setInput(e.target.value)}
                  onKeyDown={handleComposerKeyDown}
                  placeholder={
                    viewport.isExpanded
                      ? '描述你的下一步行动...（Enter 发送 · Shift+Enter 换行）'
                      : '描述你的下一步行动...'
                  }
                  disabled={isStreaming}
                  className="max-h-36 min-h-[36px] flex-1 resize-none bg-transparent py-2 text-sm leading-6 text-[#ECEAE4] placeholder-[#6E7686] outline-none"
                />
                {isStreaming ? (
                  <button
                    type="button"
                    onClick={() => engineRef.current?.cancelStream()}
                    className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-[#3B1B20] text-[#F07B84] transition-colors hover:brightness-110"
                    title="停止生成"
                    aria-label="停止生成"
                  >
                    <Square className="h-4 w-4 fill-current" />
                  </button>
                ) : (
                  <button
                    type="button"
                    disabled={!input.trim()}
                    onClick={() => void handleSend()}
                    className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-[#E7C278] text-[#231904] transition-all hover:brightness-105 disabled:bg-[#232834] disabled:text-[#6E7686]"
                    title="发送行动"
                    aria-label="发送行动"
                  >
                    <Send className="h-4 w-4" />
                  </button>
                )}
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Player Detail Modal */}
      <ModalSheet
        open={playerSheetOpen}
        title={playerName}
        subtitle={record.player.class || '主角档案'}
        onClose={() => setPlayerSheetOpen(false)}
      >
        <div className="space-y-4">
          {currentGoal && (
            <div className="flex items-start gap-2 rounded-2xl border border-[#E7C278]/30 bg-[#362B16]/50 p-3.5 text-xs text-[#FBE2AC]">
              <Flag className="mt-0.5 h-4 w-4 shrink-0 text-[#E7C278]" />
              <div>
                <div className="font-semibold text-[#E7C278]">当前目标</div>
                <div className="mt-0.5 leading-5">{currentGoal}</div>
              </div>
            </div>
          )}

          {record.player.inventory.length > 0 && (
            <div className="rounded-2xl border border-[#292F3C] bg-[#1B1F28] p-4">
              <div className="mb-2 text-xs font-semibold text-[#E7C278]">随身物品</div>
              <div className="flex flex-wrap gap-1.5">
                {record.player.inventory.map((item) => (
                  <span
                    key={item.name}
                    className="rounded-full bg-[#15181F] px-3 py-1 text-xs text-[#ECEAE4]"
                  >
                    {item.name}
                    {item.quantity > 1 ? ` ×${item.quantity}` : ''}
                  </span>
                ))}
              </div>
            </div>
          )}

          {(() => {
            const grouped = groupedFieldsForPlayer(record.player, record.world.character_fields);
            return (['status', 'ability', 'profile', 'identity'] as FieldGroup[]).map((g) => {
              const fields = grouped[g];
              if (!fields.length) return null;
              return (
                <div key={g} className="rounded-2xl border border-[#292F3C] bg-[#1B1F28] p-4">
                  <div className="mb-2.5 text-xs font-semibold text-[#E7C278]">
                    {GROUP_TITLES[g]}
                  </div>
                  <div className="space-y-2.5">
                    {fields.map((f) => (
                      <div key={f.definition.key} className="flex justify-between gap-3 text-xs">
                        <span className="shrink-0 text-[#A0A8B6]">{f.definition.label}</span>
                        <span
                          className="tabular-nums text-right font-medium"
                          style={{ color: f.color }}
                        >
                          {f.formattedDetail}
                        </span>
                      </div>
                    ))}
                  </div>
                </div>
              );
            });
          })()}
        </div>
      </ModalSheet>

      {/* NPC Quick Detail Modal */}
      <ModalSheet
        open={Boolean(selectedNpcCard)}
        title={selectedNpcCard?.name}
        subtitle={selectedNpcCard?.occupation || '角色档案'}
        onClose={() => setSelectedNpcName(null)}
        footer={
          selectedNpcCard && (
            <div className="flex justify-end">
              <button
                type="button"
                onClick={() => {
                  const name = selectedNpcCard.name;
                  setSelectedNpcName(null);
                  onOpenCharacters(name);
                }}
                className="rounded-full bg-[#17332C] px-4 py-2 text-xs font-semibold text-[#7AD0B8] hover:brightness-110"
              >
                打开完整档案 / 编辑口吻
              </button>
            </div>
          )
        }
      >
        {selectedNpcCard && (
          <div className="space-y-4">
            {(() => {
              const rel = selectedNpcCard.relationships[playerName] ?? {
                favor: selectedNpcCard.favor,
                trust: 0,
                stage: selectedNpcCard.relationship_status || '初识',
              };
              const grouped = groupedFieldsForNpc(
                selectedNpcCard,
                playerName,
                record.world.character_fields,
              );
              return (
                <>
                  <div className="flex flex-wrap gap-2">
                    <span className="rounded-full bg-[#232834] px-3 py-1 text-xs font-medium text-[#7AD0B8]">
                      {rel.stage}
                    </span>
                    <span
                      className="tabular-nums rounded-full bg-[#232834] px-3 py-1 text-xs font-semibold"
                      style={{ color: favorColor(rel.favor) }}
                    >
                      好感 {formatFavor(rel.favor)}
                    </span>
                    <span
                      className="tabular-nums rounded-full bg-[#232834] px-3 py-1 text-xs font-semibold"
                      style={{ color: trustColor(rel.trust) }}
                    >
                      信任 {rel.trust}/100
                    </span>
                  </div>

                  {selectedNpcCard.lore.secrets.length > 0 && (
                    <div className="rounded-2xl border border-[#E7C278]/35 bg-[#362B16]/50 p-3.5 text-xs text-[#FBE2AC]">
                      <div className="font-semibold text-[#E7C278]">幕后设定（角色不会主动透露）</div>
                      <ul className="mt-1.5 space-y-1">
                        {selectedNpcCard.lore.secrets.map((s) => (
                          <li key={s}>· {s}</li>
                        ))}
                      </ul>
                    </div>
                  )}

                  {(['status', 'profile', 'identity'] as FieldGroup[]).map((g) => {
                    const fields = grouped[g];
                    if (!fields.length) return null;
                    return (
                      <div key={g} className="rounded-2xl border border-[#292F3C] bg-[#1B1F28] p-4">
                        <div className="mb-2.5 text-xs font-semibold text-[#E7C278]">
                          {GROUP_TITLES[g]}
                        </div>
                        <div className="space-y-2">
                          {fields.map((f) => (
                            <div
                              key={f.definition.key}
                              className="flex justify-between gap-3 text-xs"
                            >
                              <span className="shrink-0 text-[#A0A8B6]">{f.definition.label}</span>
                              <span className="text-right text-[#ECEAE4]">{f.formattedDetail}</span>
                            </div>
                          ))}
                        </div>
                      </div>
                    );
                  })}
                </>
              );
            })()}
          </div>
        )}
      </ModalSheet>

      {/* Narration Settings Sheet */}
      <ModalSheet
        open={narrationSheetOpen}
        title="本故事叙事偏好"
        onClose={() => setNarrationSheetOpen(false)}
        footer={
          <div className="flex justify-end gap-2">
            <button
              type="button"
              onClick={() => setNarrationSheetOpen(false)}
              className="rounded-full px-4 py-2 text-xs text-[#A0A8B6] hover:bg-[#232834]"
            >
              取消
            </button>
            <button
              type="button"
              onClick={handleSaveNarration}
              className="rounded-full bg-[#E7C278] px-5 py-2 text-xs font-semibold text-[#231904]"
            >
              保存设置
            </button>
          </div>
        }
      >
        <div className="space-y-4">
          <div>
            <label className="mb-1.5 block text-xs text-[#A0A8B6]">叙事视角</label>
            <div className="grid grid-cols-2 gap-1 rounded-full bg-[#0E1014] p-1">
              {(
                [
                  ['third_limited', '第三人称限知'],
                  ['second', '第二人称（“你”）'],
                ] as [NarrationPov, string][]
              ).map(([val, label]) => (
                <button
                  key={val}
                  type="button"
                  onClick={() => setDraftPov(val)}
                  className={`rounded-full py-2 text-xs font-medium ${
                    draftPov === val ? 'bg-[#E7C278] text-[#231904]' : 'text-[#A0A8B6]'
                  }`}
                >
                  {label}
                </button>
              ))}
            </div>
          </div>

          <div>
            <label className="mb-1.5 block text-xs text-[#A0A8B6]">本故事每轮篇幅</label>
            <div className="grid grid-cols-4 gap-1 rounded-full bg-[#0E1014] p-1">
              {(
                [
                  ['', '跟随全局'],
                  ['short', '简短'],
                  ['standard', '标准'],
                  ['detailed', '详细'],
                ] as [LengthPreference | '', string][]
              ).map(([val, label]) => (
                <button
                  key={val || 'default'}
                  type="button"
                  onClick={() => setDraftLen(val)}
                  className={`rounded-full py-1.5 text-xs font-medium ${
                    draftLen === val ? 'bg-[#E7C278] text-[#231904]' : 'text-[#A0A8B6]'
                  }`}
                >
                  {label}
                </button>
              ))}
            </div>
          </div>

          <div>
            <label className="mb-1.5 block text-xs text-[#A0A8B6]">文风创造力（全局）</label>
            <div className="grid grid-cols-3 gap-1 rounded-full bg-[#0E1014] p-1">
              {(
                [
                  ['steady', '稳定 0.65'],
                  ['balanced', '平衡 0.85'],
                  ['vivid', '奔放 1.0'],
                ] as [CreativityLevel, string][]
              ).map(([val, label]) => (
                <button
                  key={val}
                  type="button"
                  onClick={() => setDraftCreativity(val)}
                  className={`rounded-full py-1.5 text-xs font-medium ${
                    draftCreativity === val ? 'bg-[#E7C278] text-[#231904]' : 'text-[#A0A8B6]'
                  }`}
                >
                  {label}
                </button>
              ))}
            </div>
          </div>

          <div>
            <label className="mb-1.5 block text-xs text-[#A0A8B6]">
              文风参考片段（可选，最多 800 字，仅模仿语气与节奏）
            </label>
            <textarea
              rows={4}
              value={draftStyleSample}
              onChange={(e) => setDraftStyleSample(e.target.value.slice(0, 800))}
              placeholder="贴一段你喜欢的小说文风段落..."
              className="w-full rounded-xl border border-[#292F3C] bg-[#0E1014] px-3.5 py-2.5 text-xs leading-6 text-[#ECEAE4] outline-none focus:border-[#E7C278]"
            />
          </div>
        </div>
      </ModalSheet>

      {/* Perf Metrics Modal */}
      <ModalSheet
        open={perfSheetOpen}
        title="Engine V2 性能与缓存监控"
        subtitle={`当前同步至第 ${record.sync_status.synced_round} 轮 · 章节记忆 ${record.chapters.length} 段`}
        onClose={() => setPerfSheetOpen(false)}
      >
        <div className="space-y-2.5">
          {getRecentMetrics().length === 0 ? (
            <div className="py-8 text-center text-xs text-[#6E7686]">
              本次会话尚未产生模型调用记录。
            </div>
          ) : (
            getRecentMetrics().map((m, idx) => (
              <div
                key={`${m.timestamp}-${idx}`}
                className="rounded-2xl border border-[#292F3C] bg-[#1B1F28] p-3 text-xs"
              >
                <div className="flex items-center justify-between">
                  <span className="font-semibold text-[#ECEAE4]">
                    {m.stage} · {m.model}
                  </span>
                  <span className={m.ok ? 'text-[#7AD0B8]' : 'text-[#F07B84]'}>
                    {m.ok ? `TTFT ${m.ttft_ms}ms / 总 ${m.total_ms}ms` : '失败'}
                  </span>
                </div>
                <div className="tabular-nums mt-1 text-[11px] text-[#A0A8B6]">
                  Prompt {m.prompt_tokens} · Cached {m.cached_tokens} · Output {m.completion_tokens}
                </div>
                {m.error && <div className="mt-1 text-[11px] text-[#F07B84]">{m.error}</div>}
              </div>
            ))
          )}
        </div>
      </ModalSheet>

      {/* Confirm Regenerate Modal */}
      <ModalSheet
        open={Boolean(pendingRegen)}
        title={`重新生成第 ${pendingRegen?.turn ?? ''} 轮？`}
        onClose={() => setPendingRegen(null)}
        maxWidthClass="sm:max-w-md"
        footer={
          <div className="flex justify-end gap-2">
            <button
              type="button"
              onClick={() => setPendingRegen(null)}
              className="rounded-full px-4 py-2 text-xs text-[#A0A8B6] hover:bg-[#232834]"
            >
              取消
            </button>
            <button
              type="button"
              onClick={handleConfirmRegen}
              className="rounded-full bg-[#E7C278] px-5 py-2 text-xs font-semibold text-[#231904]"
            >
              重新生成
            </button>
          </div>
        }
      >
        <p className="text-xs leading-6 text-[#A0A8B6]">
          将回溯到第 {pendingRegen?.turn} 轮开始前的检查点，并替换该轮及之后的旧剧情、角色和世界状态。
        </p>
      </ModalSheet>

      {/* Edit Action Modal */}
      <ModalSheet
        open={Boolean(editingEntry)}
        title={`修改第 ${editingEntry?.turn ?? ''} 轮行动`}
        onClose={() => setEditingEntry(null)}
        maxWidthClass="sm:max-w-lg"
        footer={
          <div className="flex justify-end gap-2">
            <button
              type="button"
              onClick={() => setEditingEntry(null)}
              className="rounded-full px-4 py-2 text-xs text-[#A0A8B6] hover:bg-[#232834]"
            >
              取消
            </button>
            <button
              type="button"
              disabled={!editedActionText.trim()}
              onClick={handleConfirmEdit}
              className="rounded-full bg-[#E7C278] px-5 py-2 text-xs font-semibold text-[#231904] disabled:opacity-40"
            >
              保存并重新生成
            </button>
          </div>
        }
      >
        <div className="space-y-3">
          <textarea
            rows={5}
            value={editedActionText}
            onChange={(e) => setEditedActionText(e.target.value)}
            className="w-full rounded-xl border border-[#292F3C] bg-[#0E1014] px-3.5 py-2.5 text-sm leading-6 text-[#ECEAE4] outline-none focus:border-[#E7C278]"
          />
          <p className="text-xs leading-5 text-[#A0A8B6]">
            保存后会回溯到本轮开始前的检查点，并根据修改后的行动重新生成；本轮之后的旧分支将被替换。
          </p>
        </div>
      </ModalSheet>
    </div>
  );
}

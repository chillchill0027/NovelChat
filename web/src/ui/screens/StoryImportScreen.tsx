import { useRef, useState } from 'react';
import {
  ArrowLeft,
  CheckCircle2,
  FileJson,
  Loader2,
  Sparkles,
  Upload,
} from 'lucide-react';
import {
  BUILTIN_ADVANCED_TEMPLATES,
  DEFAULT_MODERN_STORY_TEXT,
} from '../../domain/defaultStories';
import {
  buildStoryRecordFromParsed,
  parseAdvancedStoryJson,
  parseNaturalLanguageStoryWithAi,
  type ParsedStoryBundle,
} from '../../domain/storyImport';
import { saveStory } from '../../storage/storyRepository';
import type { AppSettings } from '../../types/models';
import { EditorialAvatar } from '../components/EditorialAvatar';

interface StoryImportScreenProps {
  settings: AppSettings;
  onBack: () => void;
  onCreated: (storyId: string) => void;
}

export function StoryImportScreen({ settings, onBack, onCreated }: StoryImportScreenProps) {
  const [mode, setMode] = useState<'advanced' | 'ai'>('advanced');
  const [jsonDraft, setJsonDraft] = useState(BUILTIN_ADVANCED_TEMPLATES[0].json);
  const [textDraft, setTextDraft] = useState(DEFAULT_MODERN_STORY_TEXT);
  const [parsing, setParsing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [parsed, setParsed] = useState<ParsedStoryBundle | null>(null);
  const [creating, setCreating] = useState(false);
  const fileRef = useRef<HTMLInputElement | null>(null);

  const handleLoadFile = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;
    const text = await file.text();
    if (mode === 'advanced') {
      setJsonDraft(text);
    } else {
      setTextDraft(text);
    }
    setParsed(null);
    setError(null);
  };

  const handleValidateAdvanced = () => {
    setError(null);
    try {
      const res = parseAdvancedStoryJson(jsonDraft, true);
      setParsed(res);
    } catch (err) {
      setParsed(null);
      setError(err instanceof Error ? err.message : String(err));
    }
  };

  const handleAiParse = async () => {
    setError(null);
    setParsed(null);
    setParsing(true);
    try {
      const res = await parseNaturalLanguageStoryWithAi(textDraft, settings);
      setParsed(res);
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    } finally {
      setParsing(false);
    }
  };

  const handleCreate = async () => {
    if (!parsed || creating) return;
    setCreating(true);
    try {
      const record = buildStoryRecordFromParsed(parsed);
      await saveStory(record);
      onCreated(record.id);
    } finally {
      setCreating(false);
    }
  };

  return (
    <div className="safe-top safe-bottom flex min-h-full flex-col bg-[#0E1014]">
      <input
        ref={fileRef}
        type="file"
        accept=".json,.txt,.md,application/json,text/plain"
        className="hidden"
        onChange={handleLoadFile}
      />

      <header className="sticky top-0 z-20 border-b border-[#292F3C]/50 bg-[#0E1014]/90 backdrop-blur-md">
        <div className="mx-auto flex h-14 max-w-5xl items-center justify-between px-4 sm:px-6">
          <div className="flex items-center gap-2.5">
            <button
              type="button"
              onClick={onBack}
              className="flex h-9 w-9 items-center justify-center rounded-full text-[#A0A8B6] hover:bg-[#1B1F28] hover:text-[#ECEAE4]"
              aria-label="返回"
            >
              <ArrowLeft className="h-5 w-5" />
            </button>
            <h1 className="text-lg font-semibold text-[#ECEAE4]">导入故事</h1>
          </div>

          <button
            type="button"
            onClick={() => fileRef.current?.click()}
            className="inline-flex items-center gap-1.5 rounded-full border border-[#292F3C] bg-[#1B1F28] px-3.5 py-1.5 text-xs text-[#ECEAE4] hover:bg-[#232834]"
          >
            <Upload className="h-3.5 w-3.5 text-[#E7C278]" />
            <span>从文件载入</span>
          </button>
        </div>
      </header>

      <main className="mx-auto w-full max-w-5xl flex-1 px-4 py-6 sm:px-6">
        {/* Mode Switch */}
        <div className="mx-auto mb-6 grid max-w-md grid-cols-2 gap-1 rounded-full border border-[#292F3C] bg-[#15181F] p-1">
          <button
            type="button"
            onClick={() => {
              setMode('advanced');
              setError(null);
              setParsed(null);
            }}
            className={`inline-flex items-center justify-center gap-1.5 rounded-full py-2 text-xs font-semibold transition-colors ${
              mode === 'advanced'
                ? 'bg-[#E7C278] text-[#231904]'
                : 'text-[#A0A8B6] hover:text-[#ECEAE4]'
            }`}
          >
            <FileJson className="h-3.5 w-3.5" />
            <span>高级 JSON（v1 剧本）</span>
          </button>
          <button
            type="button"
            onClick={() => {
              setMode('ai');
              setError(null);
              setParsed(null);
            }}
            className={`inline-flex items-center justify-center gap-1.5 rounded-full py-2 text-xs font-semibold transition-colors ${
              mode === 'ai'
                ? 'bg-[#E7C278] text-[#231904]'
                : 'text-[#A0A8B6] hover:text-[#ECEAE4]'
            }`}
          >
            <Sparkles className="h-3.5 w-3.5" />
            <span>AI 自然语言解析</span>
          </button>
        </div>

        <div className="grid grid-cols-1 gap-6 lg:grid-cols-12">
          {/* Left Editor */}
          <div className="flex flex-col gap-4 lg:col-span-7">
            {mode === 'advanced' ? (
              <>
                <div className="grid grid-cols-1 gap-2.5 sm:grid-cols-2">
                  {BUILTIN_ADVANCED_TEMPLATES.map((tpl) => (
                    <button
                      key={tpl.id}
                      type="button"
                      onClick={() => {
                        setJsonDraft(tpl.json);
                        setError(null);
                        setParsed(null);
                      }}
                      className="rounded-2xl border border-[#292F3C] bg-[#1B1F28] p-3.5 text-left transition-colors hover:border-[#E7C278]/50"
                    >
                      <div className="flex items-center justify-between gap-2">
                        <span className="text-xs font-semibold text-[#ECEAE4]">{tpl.name}</span>
                        <span className="rounded-full bg-[#362B16] px-2 py-0.5 text-[10px] text-[#E7C278]">
                          一键载入
                        </span>
                      </div>
                      <p className="mt-1 line-clamp-2 text-[11px] leading-5 text-[#A0A8B6]">
                        {tpl.description}
                      </p>
                    </button>
                  ))}
                </div>

                <textarea
                  value={jsonDraft}
                  onChange={(e) => {
                    setJsonDraft(e.target.value);
                    setParsed(null);
                  }}
                  rows={16}
                  spellCheck={false}
                  placeholder="粘贴符合 advanced_story_import_v1.schema.json 的 JSON..."
                  className="w-full rounded-[20px] border border-[#292F3C] bg-[#15181F] p-4 font-mono text-xs leading-6 text-[#ECEAE4] placeholder-[#6E7686] outline-none focus:border-[#E7C278]"
                />

                <button
                  type="button"
                  onClick={handleValidateAdvanced}
                  className="inline-flex items-center justify-center gap-2 rounded-full bg-[#E7C278] px-6 py-3 text-sm font-semibold text-[#231904] hover:brightness-105"
                >
                  <CheckCircle2 className="h-4 w-4" />
                  <span>校验 JSON 并预览故事结构</span>
                </button>
              </>
            ) : (
              <>
                <textarea
                  value={textDraft}
                  onChange={(e) => {
                    setTextDraft(e.target.value);
                    setParsed(null);
                  }}
                  rows={16}
                  placeholder="粘贴一段世界观、人物设定、故事大纲，AI 会自动拆解为结构化角色卡与世界书..."
                  className="w-full rounded-[20px] border border-[#292F3C] bg-[#15181F] p-4 text-sm leading-7 text-[#ECEAE4] placeholder-[#6E7686] outline-none focus:border-[#E7C278]"
                />

                <button
                  type="button"
                  disabled={parsing}
                  onClick={handleAiParse}
                  className="inline-flex items-center justify-center gap-2 rounded-full bg-[#E7C278] px-6 py-3 text-sm font-semibold text-[#231904] hover:brightness-105 disabled:opacity-50"
                >
                  {parsing ? (
                    <Loader2 className="h-4 w-4 animate-spin" />
                  ) : (
                    <Sparkles className="h-4 w-4" />
                  )}
                  <span>{parsing ? 'AI 正在解析世界观与角色...' : '调用 AI 解析故事设定'}</span>
                </button>
              </>
            )}

            {error && (
              <div className="rounded-2xl border border-[#F07B84]/35 bg-[#3B1B20] px-4 py-3 text-xs leading-6 text-[#FFD9DC]">
                {error}
              </div>
            )}
          </div>

          {/* Right Preview & Confirm */}
          <div className="lg:col-span-5">
            {!parsed ? (
              <div className="flex h-full min-h-[260px] flex-col items-center justify-center rounded-[22px] border border-dashed border-[#292F3C] bg-[#15181F]/50 p-6 text-center">
                <FileJson className="h-9 w-9 text-[#6E7686]" />
                <div className="mt-3 text-sm font-semibold text-[#ECEAE4]">校验与预览区</div>
                <p className="mt-1.5 text-xs leading-6 text-[#A0A8B6]">
                  点击左侧「校验 JSON」或「AI 解析」后，这里会展示完整的世界观、主角、在场人物与世界书清单，确认无误即可创建存档开始游玩。
                </p>
              </div>
            ) : (
              <div className="flex flex-col gap-4 rounded-[22px] border border-[#7AD0B8]/35 bg-[#1B1F28] p-5">
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <div className="inline-flex items-center gap-1.5 rounded-full bg-[#17332C] px-2.5 py-0.5 text-[11px] font-medium text-[#7AD0B8]">
                      <CheckCircle2 className="h-3.5 w-3.5" />
                      <span>结构校验通过</span>
                    </div>
                    <h2 className="mt-2 text-lg font-bold text-[#ECEAE4]">{parsed.world.name}</h2>
                    <p className="mt-0.5 text-xs text-[#A0A8B6]">{parsed.summary}</p>
                  </div>
                </div>

                <p className="text-xs leading-6 text-[#ECEAE4]/85">{parsed.world.description}</p>

                {/* Protagonist summary */}
                <div className="rounded-2xl border border-[#292F3C] bg-[#15181F] p-3.5">
                  <div className="text-[11px] font-medium text-[#A0A8B6]">主角设定</div>
                  <div className="mt-2 flex items-center gap-3">
                    <EditorialAvatar name={parsed.player.name} size={38} />
                    <div className="min-w-0">
                      <div className="text-sm font-semibold text-[#ECEAE4]">
                        {parsed.player.name}
                        {parsed.player.class && (
                          <span className="ml-2 text-xs font-normal text-[#A0A8B6]">
                            {parsed.player.class}
                          </span>
                        )}
                      </div>
                      <div className="mt-0.5 line-clamp-2 text-[11px] text-[#A0A8B6]">
                        {parsed.player.background || parsed.player.personality}
                      </div>
                    </div>
                  </div>
                </div>

                {/* NPCs preview */}
                {parsed.characters.length > 0 && (
                  <div className="rounded-2xl border border-[#292F3C] bg-[#15181F] p-3.5">
                    <div className="text-[11px] font-medium text-[#A0A8B6]">
                      初始角色（{parsed.characters.length} 人）
                    </div>
                    <div className="mt-2 flex flex-wrap gap-1.5">
                      {parsed.characters.map((c) => (
                        <span
                          key={c.name}
                          className="rounded-full border border-[#292F3C] bg-[#1B1F28] px-2.5 py-1 text-xs text-[#ECEAE4]"
                        >
                          {c.name}
                          {c.occupation ? ` · ${c.occupation}` : ''}
                        </span>
                      ))}
                    </div>
                  </div>
                )}

                {/* Opening */}
                <div className="rounded-2xl border border-[#292F3C] bg-[#15181F] p-3.5">
                  <div className="text-[11px] font-medium text-[#A0A8B6]">
                    开局现场 · {parsed.opening.current_location}（{parsed.opening.time_of_day}）
                  </div>
                  {parsed.opening.scene_description && (
                    <p className="mt-1.5 text-xs leading-6 text-[#ECEAE4]/85">
                      {parsed.opening.scene_description}
                    </p>
                  )}
                </div>

                <button
                  type="button"
                  disabled={creating}
                  onClick={handleCreate}
                  className="mt-1 inline-flex w-full items-center justify-center gap-2 rounded-full bg-[#E7C278] px-6 py-3 text-sm font-semibold text-[#231904] hover:brightness-105 disabled:opacity-50"
                >
                  {creating && <Loader2 className="h-4 w-4 animate-spin" />}
                  <span>确认创建并进入故事</span>
                </button>
              </div>
            )}
          </div>
        </div>
      </main>
    </div>
  );
}

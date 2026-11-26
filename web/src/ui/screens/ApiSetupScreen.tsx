import { useState } from 'react';
import {
  ArrowLeft,
  CheckCircle2,
  Eye,
  EyeOff,
  Loader2,
  Plus,
  Server,
  ShieldAlert,
  Trash2,
  Wifi,
} from 'lucide-react';
import {
  normalizeApiUrlDraft,
  normalizeBaseUrl,
  validateApiConfig,
} from '../../domain/apiEndpoint';
import { testApiConnection } from '../../domain/engine/llmClient';
import { deleteProfile, saveSettings, upsertProfile } from '../../storage/settingsStore';
import type {
  AiCallStage,
  AppSettings,
  CreativityLevel,
  LengthPreference,
} from '../../types/models';
import { AI_STAGE_LABELS } from '../../types/models';

interface ApiSetupScreenProps {
  settings: AppSettings;
  canGoBack: boolean;
  onBack: () => void;
  onSaved: (next: AppSettings) => void;
}

const PROVIDER_PRESETS = [
  { label: 'DeepSeek 官方', url: 'https://api.deepseek.com', model: 'deepseek-chat' },
  { label: 'DeepSeek 推理', url: 'https://api.deepseek.com', model: 'deepseek-reasoner' },
  { label: 'OpenAI 兼容', url: 'https://api.openai.com', model: 'gpt-4o-mini' },
  { label: '本机 Ollama', url: 'http://127.0.0.1:11434', model: 'qwen2.5:14b' },
];

const V2_STAGES: AiCallStage[] = [
  'narrative',
  'state_keeper',
  'summarizer',
  'story_import',
  'character_setup',
];

export function ApiSetupScreen({ settings, canGoBack, onBack, onSaved }: ApiSetupScreenProps) {
  const [draft, setDraft] = useState<AppSettings>(settings);
  const [showKey, setShowKey] = useState(false);
  const [profileName, setProfileName] = useState(settings.current_profile_name || '');
  const [isTesting, setIsTesting] = useState(false);
  const [testBanner, setTestBanner] = useState<{ ok: boolean; text: string } | null>(null);
  const [isSaving, setIsSaving] = useState(false);

  const handleTest = async () => {
    const err = validateApiConfig(draft.api_base_url, draft.api_key, draft.model);
    if (err) {
      setTestBanner({ ok: false, text: err });
      return;
    }
    setIsTesting(true);
    setTestBanner(null);
    const res = await testApiConnection(draft.api_base_url, draft.api_key, draft.model);
    setTestBanner({ ok: res.ok, text: res.message });
    setIsTesting(false);
  };

  const handleSaveProfile = async () => {
    const err = validateApiConfig(draft.api_base_url, draft.api_key, draft.model);
    if (err) {
      setTestBanner({ ok: false, text: err });
      return;
    }
    const name = profileName.trim();
    if (!name) {
      setTestBanner({ ok: false, text: '请先给这套配置起个名称（如「DeepSeek 主力」）' });
      return;
    }
    const updated = upsertProfile(draft, {
      name,
      base_url: draft.api_base_url,
      api_key: draft.api_key,
      model: draft.model,
    });
    const persisted = await saveSettings(updated);
    setDraft(persisted);
    setTestBanner({ ok: true, text: `已保存配置方案「${name}」` });
  };

  const handleDeleteProfile = async (name: string) => {
    const updated = deleteProfile(draft, name);
    const persisted = await saveSettings(updated);
    setDraft(persisted);
  };

  const handleContinue = async () => {
    const err = validateApiConfig(draft.api_base_url, draft.api_key, draft.model);
    if (err) {
      setTestBanner({ ok: false, text: err });
      return;
    }
    setIsSaving(true);
    const persisted = await saveSettings({
      ...draft,
      api_base_url: normalizeBaseUrl(draft.api_base_url),
      setup_completed: true,
    });
    setIsSaving(false);
    onSaved(persisted);
  };

  return (
    <div className="safe-top safe-bottom flex min-h-full flex-col bg-[#0E1014]">
      {/* Top Bar */}
      <header className="sticky top-0 z-20 border-b border-[#292F3C]/60 bg-[#0E1014]/90 backdrop-blur-md">
        <div className="mx-auto flex h-14 max-w-5xl items-center justify-between px-4 sm:px-6">
          <div className="flex items-center gap-2.5">
            {canGoBack && (
              <button
                type="button"
                onClick={onBack}
                className="flex h-9 w-9 items-center justify-center rounded-full text-[#A0A8B6] transition-colors hover:bg-[#1B1F28] hover:text-[#ECEAE4]"
                aria-label="返回"
              >
                <ArrowLeft className="h-5 w-5" />
              </button>
            )}
            <h1 className="text-lg font-semibold text-[#ECEAE4]">模型与 API 配置</h1>
          </div>
          <button
            type="button"
            onClick={handleContinue}
            disabled={isSaving}
            className="inline-flex items-center gap-1.5 rounded-full bg-[#E7C278] px-5 py-2 text-xs font-semibold text-[#231904] transition-all hover:brightness-105 disabled:opacity-50"
          >
            {isSaving && <Loader2 className="h-3.5 w-3.5 animate-spin" />}
            <span>保存并进入</span>
          </button>
        </div>
      </header>

      {/* Responsive Two-Column Content */}
      <main className="mx-auto w-full max-w-5xl flex-1 px-4 py-6 sm:px-6">
        <div className="grid grid-cols-1 gap-6 lg:grid-cols-12">
          {/* Left Column: Endpoint + Test + Preferences */}
          <div className="flex flex-col gap-5 lg:col-span-7">
            <section className="rounded-[22px] border border-[#292F3C] bg-[#1B1F28] p-5">
              <div className="flex items-center justify-between">
                <span className="text-xs font-semibold tracking-wider text-[#A0A8B6] uppercase">
                  主接口配置
                </span>
                <span className="rounded-full bg-[#17332C] px-2.5 py-0.5 text-[11px] font-medium text-[#7AD0B8]">
                  OpenAI 兼容协议
                </span>
              </div>

              {/* Quick Provider Pills */}
              <div className="mt-3 flex flex-wrap gap-2">
                {PROVIDER_PRESETS.map((p) => (
                  <button
                    key={p.label}
                    type="button"
                    onClick={() => {
                      setDraft((d) => ({ ...d, api_base_url: p.url, model: p.model }));
                      setTestBanner(null);
                    }}
                    className="rounded-full border border-[#292F3C] bg-[#15181F] px-3 py-1.5 text-xs text-[#ECEAE4] transition-colors hover:border-[#E7C278]/50 hover:text-[#FBE2AC]"
                  >
                    {p.label}
                  </button>
                ))}
              </div>

              <div className="mt-4 space-y-4">
                <div>
                  <label className="mb-1.5 block text-xs font-medium text-[#A0A8B6]">
                    API 地址（支持自动修正中文全角标点）
                  </label>
                  <input
                    type="text"
                    value={draft.api_base_url}
                    onChange={(e) =>
                      setDraft((d) => ({ ...d, api_base_url: normalizeApiUrlDraft(e.target.value) }))
                    }
                    placeholder="https://api.deepseek.com"
                    className="w-full rounded-2xl border border-[#292F3C] bg-[#0E1014] px-4 py-3 text-sm text-[#ECEAE4] placeholder-[#6E7686] outline-none transition-colors focus:border-[#E7C278]"
                  />
                </div>

                <div>
                  <label className="mb-1.5 block text-xs font-medium text-[#A0A8B6]">
                    API Key（仅存本机浏览器，云端接口必填）
                  </label>
                  <div className="relative">
                    <input
                      type={showKey ? 'text' : 'password'}
                      value={draft.api_key}
                      onChange={(e) => setDraft((d) => ({ ...d, api_key: e.target.value.trim() }))}
                      placeholder="sk-..."
                      autoComplete="off"
                      className="w-full rounded-2xl border border-[#292F3C] bg-[#0E1014] py-3 pr-11 pl-4 text-sm text-[#ECEAE4] placeholder-[#6E7686] outline-none transition-colors focus:border-[#E7C278]"
                    />
                    <button
                      type="button"
                      onClick={() => setShowKey((s) => !s)}
                      className="absolute top-1/2 right-3 -translate-y-1/2 rounded-full p-1.5 text-[#6E7686] hover:text-[#ECEAE4]"
                      aria-label={showKey ? '隐藏密钥' : '显示密钥'}
                    >
                      {showKey ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                    </button>
                  </div>
                </div>

                <div>
                  <label className="mb-1.5 block text-xs font-medium text-[#A0A8B6]">
                    模型名称
                  </label>
                  <input
                    type="text"
                    value={draft.model}
                    onChange={(e) => setDraft((d) => ({ ...d, model: e.target.value.trim() }))}
                    placeholder="deepseek-chat"
                    className="w-full rounded-2xl border border-[#292F3C] bg-[#0E1014] px-4 py-3 text-sm text-[#ECEAE4] placeholder-[#6E7686] outline-none transition-colors focus:border-[#E7C278]"
                  />
                </div>
              </div>

              {testBanner && (
                <div
                  className={`mt-4 flex items-start gap-2.5 rounded-2xl border px-3.5 py-3 text-xs leading-5 ${
                    testBanner.ok
                      ? 'border-[#7AD0B8]/30 bg-[#17332C] text-[#B6F0DF]'
                      : 'border-[#F07B84]/35 bg-[#3B1B20] text-[#FFD9DC]'
                  }`}
                >
                  {testBanner.ok ? (
                    <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-[#7AD0B8]" />
                  ) : (
                    <ShieldAlert className="mt-0.5 h-4 w-4 shrink-0 text-[#F07B84]" />
                  )}
                  <span className="break-all">{testBanner.text}</span>
                </div>
              )}

              <div className="mt-5 flex flex-wrap gap-2.5">
                <button
                  type="button"
                  onClick={handleTest}
                  disabled={isTesting}
                  className="inline-flex items-center gap-2 rounded-full border border-[#292F3C] bg-[#232834] px-4 py-2.5 text-xs font-medium text-[#ECEAE4] transition-colors hover:bg-[#2D3342] disabled:opacity-50"
                >
                  {isTesting ? (
                    <Loader2 className="h-3.5 w-3.5 animate-spin text-[#E7C278]" />
                  ) : (
                    <Wifi className="h-3.5 w-3.5 text-[#7AD0B8]" />
                  )}
                  <span>测试连接</span>
                </button>
                <button
                  type="button"
                  onClick={handleContinue}
                  disabled={isSaving}
                  className="inline-flex flex-1 items-center justify-center gap-2 rounded-full bg-[#E7C278] px-5 py-2.5 text-xs font-semibold text-[#231904] transition-all hover:brightness-105 disabled:opacity-50 sm:flex-none"
                >
                  <span>保存并使用此配置</span>
                </button>
              </div>
            </section>

            {/* Default Narration Preferences */}
            <section className="rounded-[22px] border border-[#292F3C] bg-[#1B1F28] p-5">
              <h2 className="text-xs font-semibold tracking-wider text-[#A0A8B6] uppercase">
                默认叙事风格
              </h2>
              <div className="mt-4 grid grid-cols-1 gap-4 sm:grid-cols-2">
                <div>
                  <label className="mb-2 block text-xs text-[#A0A8B6]">默认每轮篇幅</label>
                  <div className="grid grid-cols-3 gap-1 rounded-full bg-[#15181F] p-1">
                    {(
                      [
                        ['short', '简短'],
                        ['standard', '标准'],
                        ['detailed', '详细'],
                      ] as [LengthPreference, string][]
                    ).map(([val, label]) => (
                      <button
                        key={val}
                        type="button"
                        onClick={() => setDraft((d) => ({ ...d, default_length: val }))}
                        className={`rounded-full py-1.5 text-xs font-medium transition-colors ${
                          draft.default_length === val
                            ? 'bg-[#E7C278] text-[#231904]'
                            : 'text-[#A0A8B6] hover:text-[#ECEAE4]'
                        }`}
                      >
                        {label}
                      </button>
                    ))}
                  </div>
                </div>

                <div>
                  <label className="mb-2 block text-xs text-[#A0A8B6]">文风创造力（Temperature）</label>
                  <div className="grid grid-cols-3 gap-1 rounded-full bg-[#15181F] p-1">
                    {(
                      [
                        ['steady', '稳定'],
                        ['balanced', '平衡'],
                        ['vivid', '奔放'],
                      ] as [CreativityLevel, string][]
                    ).map(([val, label]) => (
                      <button
                        key={val}
                        type="button"
                        onClick={() => setDraft((d) => ({ ...d, creativity: val }))}
                        className={`rounded-full py-1.5 text-xs font-medium transition-colors ${
                          draft.creativity === val
                            ? 'bg-[#E7C278] text-[#231904]'
                            : 'text-[#A0A8B6] hover:text-[#ECEAE4]'
                        }`}
                      >
                        {label}
                      </button>
                    ))}
                  </div>
                </div>
              </div>
            </section>
          </div>

          {/* Right Column: Profiles + V2 Stage Routing + Pure Static CORS Notice */}
          <div className="flex flex-col gap-5 lg:col-span-5">
            <section className="rounded-[22px] border border-[#292F3C] bg-[#1B1F28] p-5">
              <div className="flex items-center gap-2">
                <Server className="h-4 w-4 text-[#E7C278]" />
                <h2 className="text-sm font-semibold text-[#ECEAE4]">多方案与 V2 阶段路由</h2>
              </div>
              <p className="mt-1 text-xs leading-5 text-[#A0A8B6]">
                可把当前接口另存为方案，再把「剧情正文」与「后台状态同步」分别指向不同模型。
              </p>

              <div className="mt-3.5 flex gap-2">
                <input
                  type="text"
                  value={profileName}
                  onChange={(e) => setProfileName(e.target.value)}
                  placeholder="方案名（如：DeepSeek 正文）"
                  className="min-w-0 flex-1 rounded-xl border border-[#292F3C] bg-[#0E1014] px-3 py-2 text-xs text-[#ECEAE4] placeholder-[#6E7686] outline-none focus:border-[#E7C278]"
                />
                <button
                  type="button"
                  onClick={handleSaveProfile}
                  className="inline-flex shrink-0 items-center gap-1 rounded-xl bg-[#232834] px-3.5 py-2 text-xs font-medium text-[#E7C278] hover:bg-[#2D3342]"
                >
                  <Plus className="h-3.5 w-3.5" />
                  <span>存为方案</span>
                </button>
              </div>

              {draft.profiles.length > 0 && (
                <div className="mt-3 space-y-2">
                  {draft.profiles.map((p) => (
                    <div
                      key={p.name}
                      className="flex items-center justify-between gap-2 rounded-xl border border-[#292F3C] bg-[#15181F] px-3 py-2.5"
                    >
                      <button
                        type="button"
                        onClick={() => {
                          setProfileName(p.name);
                          setDraft((d) => ({
                            ...d,
                            current_profile_name: p.name,
                            api_base_url: p.base_url,
                            api_key: p.api_key,
                            model: p.model,
                          }));
                        }}
                        className="min-w-0 flex-1 text-left"
                      >
                        <div className="truncate text-xs font-semibold text-[#ECEAE4]">{p.name}</div>
                        <div className="truncate text-[11px] text-[#6E7686]">
                          {p.model} · {p.base_url}
                        </div>
                      </button>
                      <button
                        type="button"
                        onClick={() => handleDeleteProfile(p.name)}
                        className="rounded-lg p-1.5 text-[#6E7686] hover:bg-[#3B1B20] hover:text-[#F07B84]"
                        aria-label={`删除 ${p.name}`}
                      >
                        <Trash2 className="h-3.5 w-3.5" />
                      </button>
                    </div>
                  ))}

                  <div className="mt-4 space-y-2 border-t border-[#292F3C] pt-3">
                    <div className="text-[11px] font-medium text-[#A0A8B6]">按引擎阶段分配方案</div>
                    {V2_STAGES.map((stage) => (
                      <div key={stage} className="flex items-center justify-between gap-2">
                        <span className="text-xs text-[#ECEAE4]">{AI_STAGE_LABELS[stage].label}</span>
                        <select
                          value={draft.stage_routes[stage] ?? ''}
                          onChange={(e) => {
                            const val = e.target.value;
                            setDraft((d) => ({
                              ...d,
                              stage_routes: {
                                ...d.stage_routes,
                                [stage]: val || undefined,
                              },
                            }));
                          }}
                          className="rounded-lg border border-[#292F3C] bg-[#0E1014] px-2.5 py-1.5 text-xs text-[#ECEAE4] outline-none focus:border-[#E7C278]"
                        >
                          <option value="">跟随主配置</option>
                          {draft.profiles.map((p) => (
                            <option key={p.name} value={p.name}>
                              {p.name}
                            </option>
                          ))}
                        </select>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </section>

            {/* Pure static CORS & Privacy notice */}
            <section className="rounded-[20px] border border-[#E7C278]/25 bg-[#362B16]/40 p-4 text-xs leading-6 text-[#FBE2AC]/90">
              <div className="font-semibold text-[#E7C278]">纯静态网页运行说明</div>
              <ul className="mt-1.5 list-disc space-y-1 pl-4 text-[12px] text-[#ECEAE4]/80">
                <li>DeepSeek 官方 API 默认支持浏览器跨域直连，密钥仅保存在当前设备。</li>
                <li>若使用第三方中转站提示跨域（CORS）拦截，请确认中转站已开启 CORS 或更换支持跨域的兼容地址。</li>
                <li>换设备（如手机 ↔ 平板）时，可在故事库一键导出 JSON 备份再导入继续游玩。</li>
              </ul>
            </section>
          </div>
        </div>
      </main>
    </div>
  );
}

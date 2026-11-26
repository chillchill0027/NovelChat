import { useState } from 'react';
import { ArrowLeft, ChevronRight, Edit3, Sparkles } from 'lucide-react';
import { CHARACTER_PRESETS, createStoryFromPreset } from '../../domain/defaultStories';
import { rankColor } from '../../domain/palette';
import { saveStory } from '../../storage/storyRepository';
import type { CharacterPreset } from '../../types/models';
import { EditorialAvatar } from '../components/EditorialAvatar';
import { ModalSheet } from '../components/ModalSheet';

interface PresetSelectScreenProps {
  onBack: () => void;
  onCreated: (storyId: string) => void;
}

export function PresetSelectScreen({ onBack, onCreated }: PresetSelectScreenProps) {
  const [creating, setCreating] = useState(false);
  const [customOpen, setCustomOpen] = useState(false);
  const [customName, setCustomName] = useState('');
  const [customAge, setCustomAge] = useState('25');
  const [customGender, setCustomGender] = useState('男');
  const [customRole, setCustomRole] = useState('');
  const [customBg, setCustomBg] = useState('');
  const [customErr, setCustomErr] = useState('');

  const handleSelectPreset = async (preset: CharacterPreset) => {
    if (creating) return;
    setCreating(true);
    try {
      const record = createStoryFromPreset(preset);
      await saveStory(record);
      onCreated(record.id);
    } finally {
      setCreating(false);
    }
  };

  const handleCreateCustom = async () => {
    const name = customName.trim();
    if (!name) {
      setCustomErr('请填写主角真实姓名');
      return;
    }
    setCreating(true);
    try {
      const ageNum = Math.max(16, Math.min(80, Number.parseInt(customAge, 10) || 25));
      const record = createStoryFromPreset(CHARACTER_PRESETS[0], {
        name,
        age: ageNum,
        gender: customGender,
        role: customRole.trim() || '城市居民',
        background: customBg.trim() || '在海临市独自打拼，迎接周一早晨的新选择。',
      });
      await saveStory(record);
      setCustomOpen(false);
      onCreated(record.id);
    } finally {
      setCreating(false);
    }
  };

  return (
    <div className="safe-top safe-bottom flex min-h-full flex-col bg-[#0E1014]">
      <header className="sticky top-0 z-20 border-b border-[#292F3C]/50 bg-[#0E1014]/90 backdrop-blur-md">
        <div className="mx-auto flex h-14 max-w-5xl items-center gap-2.5 px-4 sm:px-6">
          <button
            type="button"
            onClick={onBack}
            disabled={creating}
            className="flex h-9 w-9 items-center justify-center rounded-full text-[#A0A8B6] transition-colors hover:bg-[#1B1F28] hover:text-[#ECEAE4]"
            aria-label="返回"
          >
            <ArrowLeft className="h-5 w-5" />
          </button>
          <h1 className="text-lg font-semibold text-[#ECEAE4]">选择开局身份 · 海临市</h1>
        </div>
      </header>

      <main className="mx-auto w-full max-w-5xl flex-1 px-4 py-6 sm:px-6">
        {/* Custom Identity Banner */}
        <button
          type="button"
          onClick={() => {
            setCustomErr('');
            setCustomOpen(true);
          }}
          disabled={creating}
          className="flex w-full items-center justify-between gap-4 rounded-[20px] border border-[#E7C278]/40 bg-[#362B16]/70 p-4 text-left transition-all hover:border-[#E7C278]/70 hover:bg-[#362B16]"
        >
          <div className="flex items-center gap-3.5">
            <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl bg-[#E7C278] text-[#231904]">
              <Edit3 className="h-5 w-5" />
            </div>
            <div>
              <div className="text-[15px] font-semibold text-[#FBE2AC]">自定义主角身份</div>
              <div className="mt-0.5 text-xs text-[#ECEAE4]/75">
                自由设定姓名、年龄、职业与海临市开局背景
              </div>
            </div>
          </div>
          <ChevronRight className="h-5 w-5 shrink-0 text-[#E7C278]" />
        </button>

        {/* 2-Column Preset Grid */}
        <div className="mt-6">
          <div className="mb-3 flex items-center gap-1.5 text-xs font-semibold tracking-wider text-[#A0A8B6]">
            <Sparkles className="h-3.5 w-3.5 text-[#7AD0B8]" />
            <span>预设开局身份</span>
          </div>
          <div className="grid grid-cols-1 gap-3.5 md:grid-cols-2">
            {CHARACTER_PRESETS.map((preset) => (
              <button
                key={preset.key}
                type="button"
                disabled={creating}
                onClick={() => void handleSelectPreset(preset)}
                className="flex flex-col justify-between rounded-[22px] border border-[#292F3C] bg-[#1B1F28] p-4 text-left transition-all hover:border-[#E7C278]/50 hover:bg-[#202530] disabled:opacity-50"
              >
                <div>
                  <div className="flex items-center justify-between gap-3">
                    <div className="flex items-center gap-3">
                      <EditorialAvatar name={preset.player_name} size={42} />
                      <div>
                        <div className="flex items-center gap-2">
                          <span className="text-[16px] font-semibold text-[#ECEAE4]">
                            {preset.player_name}
                          </span>
                          <span className="rounded-md bg-[#232834] px-2 py-0.5 text-[11px] text-[#A0A8B6]">
                            {preset.name}
                          </span>
                        </div>
                      </div>
                    </div>
                    <span
                      className="rounded-full bg-[#15181F] px-2.5 py-0.5 text-[11px] font-semibold"
                      style={{ color: rankColor(preset.social_rank) }}
                    >
                      阶层 {preset.social_rank}
                    </span>
                  </div>

                  <p className="mt-3 text-[13px] leading-6 text-[#A0A8B6]">{preset.description}</p>
                  <p className="mt-2 rounded-xl bg-[#15181F] px-3 py-2 text-[12px] leading-5 text-[#ECEAE4]/80">
                    {preset.system_eval}
                  </p>
                </div>

                <div className="mt-3.5 grid grid-cols-4 gap-2 border-t border-[#292F3C]/70 pt-3 text-center">
                  {[
                    ['魅力', preset.charm, '#B5C1E3'],
                    ['行动', preset.hunting_skill, '#7AD0B8'],
                    ['意志', preset.alpha, '#E7C278'],
                    ['风险', preset.cv, preset.cv >= 15 ? '#EE9F62' : '#6ED49B'],
                  ].map(([lbl, val, col]) => (
                    <div key={String(lbl)} className="rounded-lg bg-[#15181F] py-1.5">
                      <div className="text-[10px] text-[#6E7686]">{lbl}</div>
                      <div
                        className="tabular-nums text-xs font-semibold"
                        style={{ color: String(col) }}
                      >
                        {val}
                      </div>
                    </div>
                  ))}
                </div>
              </button>
            ))}
          </div>
        </div>
      </main>

      <ModalSheet
        open={customOpen}
        title="自定义开局角色"
        subtitle="以海临市「周一三条消息」为起点"
        onClose={() => setCustomOpen(false)}
        footer={
          <div className="flex justify-end gap-2.5">
            <button
              type="button"
              onClick={() => setCustomOpen(false)}
              className="rounded-full px-4 py-2 text-xs text-[#A0A8B6] hover:bg-[#232834]"
            >
              取消
            </button>
            <button
              type="button"
              disabled={creating}
              onClick={handleCreateCustom}
              className="rounded-full bg-[#E7C278] px-5 py-2 text-xs font-semibold text-[#231904] hover:brightness-105 disabled:opacity-50"
            >
              创建并进入故事
            </button>
          </div>
        }
      >
        <div className="space-y-3.5">
          {customErr && (
            <div className="rounded-xl bg-[#3B1B20] px-3.5 py-2.5 text-xs text-[#FFD9DC]">
              {customErr}
            </div>
          )}
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <div>
              <label className="mb-1 block text-xs text-[#A0A8B6]">主角姓名 *</label>
              <input
                type="text"
                value={customName}
                onChange={(e) => {
                  setCustomName(e.target.value);
                  setCustomErr('');
                }}
                placeholder="例如：陈默"
                className="w-full rounded-xl border border-[#292F3C] bg-[#0E1014] px-3.5 py-2.5 text-sm text-[#ECEAE4] outline-none focus:border-[#E7C278]"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs text-[#A0A8B6]">身份 / 职业</label>
              <input
                type="text"
                value={customRole}
                onChange={(e) => setCustomRole(e.target.value)}
                placeholder="例如：平面设计师"
                className="w-full rounded-xl border border-[#292F3C] bg-[#0E1014] px-3.5 py-2.5 text-sm text-[#ECEAE4] outline-none focus:border-[#E7C278]"
              />
            </div>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="mb-1 block text-xs text-[#A0A8B6]">年龄</label>
              <input
                type="number"
                value={customAge}
                onChange={(e) => setCustomAge(e.target.value)}
                min={16}
                max={80}
                className="w-full rounded-xl border border-[#292F3C] bg-[#0E1014] px-3.5 py-2.5 text-sm text-[#ECEAE4] outline-none focus:border-[#E7C278]"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs text-[#A0A8B6]">性别</label>
              <div className="grid grid-cols-3 gap-1 rounded-xl bg-[#0E1014] p-1">
                {['男', '女', '未明确'].map((g) => (
                  <button
                    key={g}
                    type="button"
                    onClick={() => setCustomGender(g)}
                    className={`rounded-lg py-1.5 text-xs font-medium ${
                      customGender === g
                        ? 'bg-[#E7C278] text-[#231904]'
                        : 'text-[#A0A8B6] hover:text-[#ECEAE4]'
                    }`}
                  >
                    {g}
                  </button>
                ))}
              </div>
            </div>
          </div>
          <div>
            <label className="mb-1 block text-xs text-[#A0A8B6]">背景简介（可选）</label>
            <textarea
              rows={3}
              value={customBg}
              onChange={(e) => setCustomBg(e.target.value)}
              placeholder="简短写一下主角的来历或当下处境..."
              className="w-full rounded-xl border border-[#292F3C] bg-[#0E1014] px-3.5 py-2.5 text-sm text-[#ECEAE4] outline-none focus:border-[#E7C278]"
            />
          </div>
        </div>
      </ModalSheet>
    </div>
  );
}

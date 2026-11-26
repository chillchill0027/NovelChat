import { useState } from 'react';
import { ArrowLeft, ChevronRight, Edit3, Lock } from 'lucide-react';
import { groupedFieldsForNpc } from '../../domain/characterFields';
import { favorColor, formatFavor, trustColor } from '../../domain/palette';
import { updateCharacterSpeechProfile } from '../../storage/storyRepository';
import type { CharacterCard, FieldGroup, StoryRecord } from '../../types/models';
import { EditorialAvatar } from '../components/EditorialAvatar';
import { ModalSheet } from '../components/ModalSheet';

interface CharacterListScreenProps {
  record: StoryRecord;
  initialSelectedName?: string | null;
  onBack: () => void;
  onRecordChange: (next: StoryRecord) => void;
}

const GROUP_LABELS: Record<FieldGroup, string> = {
  status: '当前状态',
  relationship: '与主角的关系',
  ability: '能力与成长',
  identity: '基础信息',
  profile: '人物画像',
};

export function CharacterListScreen({
  record,
  initialSelectedName = null,
  onBack,
  onRecordChange,
}: CharacterListScreenProps) {
  const [selectedName, setSelectedName] = useState<string | null>(initialSelectedName);
  const [speechModalOpen, setSpeechModalOpen] = useState(false);
  const [speechStyle, setSpeechStyle] = useState('');
  const [sampleLines, setSampleLines] = useState('');

  const playerName = record.player.name || '主角';
  const selectedCard = selectedName
    ? record.characters.find((c) => c.name === selectedName || c.id === selectedName) ?? null
    : null;

  const openSpeechEditor = (card: CharacterCard) => {
    setSpeechStyle(card.lore.speech_style);
    setSampleLines(card.lore.sample_dialogues.join('\n'));
    setSpeechModalOpen(true);
  };

  const handleSaveSpeech = async () => {
    if (!selectedCard) return;
    const samples = sampleLines
      .split('\n')
      .map((s) => s.trim())
      .filter(Boolean);
    const updated = await updateCharacterSpeechProfile(
      record.id,
      selectedCard.id || selectedCard.name,
      speechStyle,
      samples,
    );
    onRecordChange(updated);
    setSpeechModalOpen(false);
  };

  if (selectedCard) {
    const rel = selectedCard.relationships[playerName] ?? {
      favor: selectedCard.favor,
      trust: 0,
      stage: selectedCard.relationship_status || '初识',
    };
    const grouped = groupedFieldsForNpc(selectedCard, playerName, record.world.character_fields);
    const secrets = [
      ...selectedCard.lore.secrets.filter((s) => s.trim()),
      ...(selectedCard.kink.trim() ? [selectedCard.kink.trim()] : []),
    ];

    return (
      <div className="safe-top safe-bottom flex min-h-full flex-col bg-[#0E1014]">
        <header className="sticky top-0 z-20 border-b border-[#292F3C]/50 bg-[#0E1014]/90 backdrop-blur-md">
          <div className="mx-auto flex h-14 max-w-6xl items-center justify-between px-4 sm:px-6">
            <div className="flex items-center gap-2.5">
              <button
                type="button"
                onClick={() => setSelectedName(null)}
                className="flex h-9 w-9 items-center justify-center rounded-full text-[#A0A8B6] hover:bg-[#1B1F28] hover:text-[#ECEAE4]"
                aria-label="返回角色列表"
              >
                <ArrowLeft className="h-5 w-5" />
              </button>
              <h1 className="text-lg font-semibold text-[#ECEAE4]">{selectedCard.name}</h1>
            </div>
            <button
              type="button"
              onClick={() => openSpeechEditor(selectedCard)}
              className="inline-flex items-center gap-1.5 rounded-full bg-[#17332C] px-3.5 py-1.5 text-xs font-medium text-[#7AD0B8] hover:brightness-110"
            >
              <Edit3 className="h-3.5 w-3.5" />
              <span>编辑口吻</span>
            </button>
          </div>
        </header>

        {/* Two-column Dossier on Tablet/Desktop Landscape */}
        <main className="mx-auto w-full max-w-6xl flex-1 px-4 py-6 sm:px-6">
          <div className="grid grid-cols-1 gap-5 lg:grid-cols-12">
            {/* Left sticky column: Hero + Secrets + Speech */}
            <div className="flex flex-col gap-4 lg:col-span-4">
              <div className="rounded-[20px] border border-[#292F3C] bg-[#1B1F28] p-5">
                <div className="flex items-center gap-3.5">
                  <EditorialAvatar name={selectedCard.name} size={52} />
                  <div className="min-w-0">
                    <div className="flex items-center gap-2">
                      <h2 className="truncate text-lg font-bold text-[#ECEAE4]">
                        {selectedCard.name}
                      </h2>
                      <span className="rounded-full bg-[#17332C] px-2 py-0.5 text-[10px] font-medium text-[#7AD0B8]">
                        {selectedCard.status === 'pool' ? '暂离' : '活跃'}
                      </span>
                    </div>
                    <p className="mt-0.5 truncate text-xs text-[#A0A8B6]">
                      {[
                        selectedCard.occupation,
                        selectedCard.age > 0 ? `${selectedCard.age}岁` : '',
                        selectedCard.gender,
                      ]
                        .filter(Boolean)
                        .join(' · ') || '身份未知'}
                    </p>
                  </div>
                </div>

                <div className="mt-4 flex flex-wrap gap-2">
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
              </div>

              {secrets.length > 0 && (
                <div className="rounded-[20px] border border-[#E7C278]/35 bg-[#362B16]/55 p-4">
                  <div className="flex items-center gap-1.5 text-xs font-semibold text-[#E7C278]">
                    <Lock className="h-3.5 w-3.5" />
                    <span>幕后设定（角色不会主动透露）</span>
                  </div>
                  <ul className="mt-2 space-y-1.5 text-xs leading-6 text-[#FBE2AC]/90">
                    {secrets.map((s) => (
                      <li key={s}>· {s}</li>
                    ))}
                  </ul>
                </div>
              )}

              {(selectedCard.lore.speech_style || selectedCard.lore.sample_dialogues.length > 0) && (
                <div className="rounded-[20px] border border-[#292F3C] bg-[#1B1F28] p-4">
                  <div className="text-xs font-semibold text-[#E7C278]">说话口吻与台词示范</div>
                  {selectedCard.lore.speech_style && (
                    <p className="mt-2 text-xs leading-6 text-[#ECEAE4]/85">
                      {selectedCard.lore.speech_style}
                    </p>
                  )}
                  {selectedCard.lore.sample_dialogues.length > 0 && (
                    <div className="mt-2.5 space-y-1.5">
                      {selectedCard.lore.sample_dialogues.map((line) => (
                        <div
                          key={line}
                          className="rounded-xl bg-[#15181F] px-3 py-2 text-xs leading-5 text-[#A0A8B6]"
                        >
                          「{line}」
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              )}
            </div>

            {/* Right detail cards */}
            <div className="flex flex-col gap-4 lg:col-span-8">
              {(['status', 'relationship', 'profile', 'ability', 'identity'] as FieldGroup[]).map(
                (groupKey) => {
                  const fields = grouped[groupKey];
                  if (!fields.length) return null;
                  return (
                    <section
                      key={groupKey}
                      className="rounded-[20px] border border-[#292F3C] bg-[#1B1F28] p-5"
                    >
                      <h3 className="mb-3 text-xs font-semibold text-[#E7C278]">
                        {GROUP_LABELS[groupKey]}
                      </h3>
                      <div className="space-y-3">
                        {fields.map((f) => {
                          const isLong =
                            f.formattedDetail.length > 18 ||
                            ['appearance', 'personality', 'background', 'skills'].includes(
                              f.definition.key,
                            );
                          if (isLong) {
                            return (
                              <div key={f.definition.key} className="space-y-1">
                                <div className="text-xs text-[#A0A8B6]">{f.definition.label}</div>
                                <div className="rounded-xl bg-[#15181F] px-3.5 py-2.5 text-xs leading-6 text-[#ECEAE4]">
                                  {f.formattedDetail}
                                </div>
                              </div>
                            );
                          }
                          return (
                            <div
                              key={f.definition.key}
                              className="flex items-center justify-between gap-4 text-sm"
                            >
                              <span className="text-xs text-[#A0A8B6]">{f.definition.label}</span>
                              <span
                                className="tabular-nums text-right text-xs font-semibold"
                                style={{ color: f.color }}
                              >
                                {f.formattedDetail}
                              </span>
                            </div>
                          );
                        })}
                      </div>
                    </section>
                  );
                },
              )}

              {selectedCard.memories.length > 0 && (
                <section className="rounded-[20px] border border-[#292F3C] bg-[#1B1F28] p-5">
                  <h3 className="mb-3 text-xs font-semibold text-[#E7C278]">
                    角色记忆（{selectedCard.memories.length} 条）
                  </h3>
                  <div className="space-y-2">
                    {[...selectedCard.memories].reverse().map((m, idx) => (
                      <div
                        key={`${m.turn}-${idx}`}
                        className="flex items-start justify-between gap-3 rounded-xl bg-[#15181F] px-3.5 py-2.5 text-xs"
                      >
                        <span className="leading-5 text-[#ECEAE4]">{m.text}</span>
                        <span className="shrink-0 text-[11px] text-[#6E7686]">第{m.turn}轮</span>
                      </div>
                    ))}
                  </div>
                </section>
              )}
            </div>
          </div>
        </main>

        <ModalSheet
          open={speechModalOpen}
          title={`编辑 ${selectedCard.name} 的说话口吻`}
          onClose={() => setSpeechModalOpen(false)}
          footer={
            <div className="flex justify-end gap-2">
              <button
                type="button"
                onClick={() => setSpeechModalOpen(false)}
                className="rounded-full px-4 py-2 text-xs text-[#A0A8B6] hover:bg-[#232834]"
              >
                取消
              </button>
              <button
                type="button"
                onClick={handleSaveSpeech}
                className="rounded-full bg-[#E7C278] px-5 py-2 text-xs font-semibold text-[#231904]"
              >
                保存口吻
              </button>
            </div>
          }
        >
          <div className="space-y-4">
            <div>
              <label className="mb-1.5 block text-xs text-[#A0A8B6]">说话风格描述</label>
              <textarea
                rows={3}
                value={speechStyle}
                onChange={(e) => setSpeechStyle(e.target.value)}
                placeholder="例如：语速偏慢，爱先反问再给答案，熟人面前常带半句玩笑..."
                className="w-full rounded-xl border border-[#292F3C] bg-[#0E1014] px-3.5 py-2.5 text-sm text-[#ECEAE4] outline-none focus:border-[#E7C278]"
              />
            </div>
            <div>
              <label className="mb-1.5 block text-xs text-[#A0A8B6]">
                示例台词（每行一句，最多 6 句）
              </label>
              <textarea
                rows={5}
                value={sampleLines}
                onChange={(e) => setSampleLines(e.target.value)}
                placeholder="每行写一句最能代表该角色语气的台词..."
                className="w-full rounded-xl border border-[#292F3C] bg-[#0E1014] px-3.5 py-2.5 text-sm leading-6 text-[#ECEAE4] outline-none focus:border-[#E7C278]"
              />
            </div>
          </div>
        </ModalSheet>
      </div>
    );
  }

  return (
    <div className="safe-top safe-bottom flex min-h-full flex-col bg-[#0E1014]">
      <header className="sticky top-0 z-20 border-b border-[#292F3C]/50 bg-[#0E1014]/90 backdrop-blur-md">
        <div className="mx-auto flex h-14 max-w-5xl items-center gap-2.5 px-4 sm:px-6">
          <button
            type="button"
            onClick={onBack}
            className="flex h-9 w-9 items-center justify-center rounded-full text-[#A0A8B6] hover:bg-[#1B1F28] hover:text-[#ECEAE4]"
            aria-label="返回"
          >
            <ArrowLeft className="h-5 w-5" />
          </button>
          <h1 className="text-lg font-semibold text-[#ECEAE4]">全部角色</h1>
          <span className="rounded-full bg-[#1B1F28] px-2.5 py-0.5 text-xs text-[#A0A8B6]">
            {record.characters.length} 人
          </span>
        </div>
      </header>

      <main className="mx-auto w-full max-w-5xl flex-1 px-4 py-6 sm:px-6">
        {record.characters.length === 0 ? (
          <div className="rounded-[22px] border border-dashed border-[#292F3C] bg-[#15181F]/50 px-6 py-16 text-center text-sm text-[#A0A8B6]">
            暂无已记录的 NPC，剧情中与新人物互动后将自动建档。
          </div>
        ) : (
          <div className="grid grid-cols-1 gap-3.5 md:grid-cols-2">
            {record.characters.map((card) => {
              const rel = card.relationships[playerName] ?? {
                favor: card.favor,
                trust: 0,
                stage: card.relationship_status || '初识',
              };
              const inScene = record.story.active_characters.includes(card.name);
              return (
                <button
                  key={card.id || card.name}
                  type="button"
                  onClick={() => setSelectedName(card.name)}
                  className="flex items-center gap-3.5 rounded-[20px] border border-[#292F3C] bg-[#1B1F28] p-4 text-left transition-all hover:border-[#E7C278]/45 hover:bg-[#202530]"
                >
                  <EditorialAvatar name={card.name} size={44} />
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2">
                      <span className="truncate text-[15px] font-semibold text-[#ECEAE4]">
                        {card.name}
                      </span>
                      <span className="shrink-0 rounded-md bg-[#232834] px-2 py-0.5 text-[10px] font-medium text-[#7AD0B8]">
                        {rel.stage}
                      </span>
                      {inScene && (
                        <span className="shrink-0 rounded-full bg-[#17332C] px-2 py-0.5 text-[10px] text-[#7AD0B8]">
                          在场
                        </span>
                      )}
                    </div>
                    <div className="mt-0.5 truncate text-xs text-[#A0A8B6]">
                      {card.occupation || '身份未知'}
                    </div>
                    <div className="mt-1.5 flex items-center gap-2.5 text-xs">
                      <span className="tabular-nums">
                        <span className="text-[#6E7686]">好感 </span>
                        <span style={{ color: favorColor(rel.favor) }} className="font-semibold">
                          {formatFavor(rel.favor)}
                        </span>
                      </span>
                      <span className="text-[#6E7686]/50">·</span>
                      <span className="tabular-nums">
                        <span className="text-[#6E7686]">信任 </span>
                        <span style={{ color: trustColor(rel.trust) }} className="font-semibold">
                          {rel.trust}/100
                        </span>
                      </span>
                    </div>
                  </div>
                  <ChevronRight className="h-4 w-4 shrink-0 text-[#6E7686]" />
                </button>
              );
            })}
          </div>
        )}
      </main>
    </div>
  );
}

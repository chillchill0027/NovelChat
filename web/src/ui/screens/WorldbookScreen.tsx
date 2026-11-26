import { useMemo, useState } from 'react';
import {
  ArrowLeft,
  Calendar,
  MapPin,
  Package,
  Plus,
  Trash2,
  User,
  Users,
} from 'lucide-react';
import { deleteWorldbookEntry, upsertWorldbookEntry } from '../../storage/storyRepository';
import type { StoryRecord, WorldbookEntry } from '../../types/models';
import { ModalSheet } from '../components/ModalSheet';

interface WorldbookScreenProps {
  record: StoryRecord;
  onBack: () => void;
  onRecordChange: (next: StoryRecord) => void;
}

const CATEGORIES: { key: string; label: string }[] = [
  { key: 'all', label: '全部' },
  { key: 'location', label: '地点' },
  { key: 'item', label: '物品' },
  { key: 'organization', label: '组织' },
  { key: 'event', label: '事件' },
  { key: 'character', label: '人物资料' },
];

function categoryMeta(type: string) {
  switch (type) {
    case 'location':
      return { label: '地点', icon: MapPin, color: '#E7C278', bg: '#362B16' };
    case 'item':
      return { label: '物品', icon: Package, color: '#ECEAE4', bg: '#252B3D' };
    case 'organization':
      return { label: '组织', icon: Users, color: '#B5C1E3', bg: '#252B3D' };
    case 'event':
      return { label: '事件', icon: Calendar, color: '#7AD0B8', bg: '#17332C' };
    default:
      return { label: '人物资料', icon: User, color: '#CBB0F5', bg: '#2D253C' };
  }
}

export function WorldbookScreen({ record, onBack, onRecordChange }: WorldbookScreenProps) {
  const [filter, setFilter] = useState('all');
  const [editing, setEditing] = useState<WorldbookEntry | null>(null);
  const [isNew, setIsNew] = useState(false);

  // Editor form state
  const [fName, setFName] = useState('');
  const [fType, setFType] = useState('location');
  const [fDesc, setFDesc] = useState('');
  const [fKeywords, setFKeywords] = useState('');
  const [fStatus, setFStatus] = useState('正常');
  const [fSecret, setFSecret] = useState(false);

  const counts = useMemo(() => {
    const map: Record<string, number> = { all: record.worldbook.length };
    for (const e of record.worldbook) {
      map[e.type] = (map[e.type] ?? 0) + 1;
    }
    return map;
  }, [record.worldbook]);

  const grouped = useMemo(() => {
    const list =
      filter === 'all'
        ? record.worldbook
        : record.worldbook.filter((w) => w.type === filter);
    const order = ['location', 'item', 'organization', 'event', 'character'];
    const map = new Map<string, WorldbookEntry[]>();
    for (const item of list) {
      const key = order.includes(item.type) ? item.type : 'other';
      const arr = map.get(key) ?? [];
      arr.push(item);
      map.set(key, arr);
    }
    return Array.from(map.entries());
  }, [record.worldbook, filter]);

  const openEditor = (entry: WorldbookEntry | null) => {
    if (entry) {
      setEditing(entry);
      setIsNew(false);
      setFName(entry.name);
      setFType(entry.type || 'location');
      setFDesc(entry.description);
      setFKeywords(entry.keywords.join('、'));
      setFStatus(entry.status || '正常');
      setFSecret(entry.is_secret);
    } else {
      setEditing({
        id: '',
        type: filter === 'all' ? 'location' : filter,
        name: '',
        description: '',
        keywords: [],
        related_characters: [],
        related_events: [],
        related_locations: [],
        is_secret: false,
        known_by_player: true,
        is_active: true,
        activated_by: '',
        deactivated_by: '',
        status: '正常',
        status_history: [],
        created_by: 'user',
        created_at: '',
        last_modified: '',
      });
      setIsNew(true);
      setFName('');
      setFType(filter === 'all' ? 'location' : filter);
      setFDesc('');
      setFKeywords('');
      setFStatus('正常');
      setFSecret(false);
    }
  };

  const handleSave = async () => {
    if (!editing || !fName.trim()) return;
    const keywords = fKeywords
      .split(/[,，、;\s]+/)
      .map((s) => s.trim())
      .filter(Boolean);
    const updated = await upsertWorldbookEntry(record.id, {
      ...editing,
      name: fName.trim(),
      type: fType,
      description: fDesc.trim(),
      keywords,
      status: fStatus.trim() || '正常',
      is_secret: fSecret,
      known_by_player: !fSecret,
    });
    onRecordChange(updated);
    setEditing(null);
  };

  const handleDelete = async () => {
    if (!editing || isNew) return;
    const updated = await deleteWorldbookEntry(record.id, editing.id);
    onRecordChange(updated);
    setEditing(null);
  };

  return (
    <div className="safe-top safe-bottom flex min-h-full flex-col bg-[#0E1014]">
      <header className="sticky top-0 z-20 border-b border-[#292F3C]/50 bg-[#0E1014]/90 backdrop-blur-md">
        <div className="mx-auto flex h-14 max-w-6xl items-center justify-between px-4 sm:px-6">
          <div className="flex items-center gap-2.5">
            <button
              type="button"
              onClick={onBack}
              className="flex h-9 w-9 items-center justify-center rounded-full text-[#A0A8B6] hover:bg-[#1B1F28] hover:text-[#ECEAE4]"
              aria-label="返回"
            >
              <ArrowLeft className="h-5 w-5" />
            </button>
            <h1 className="text-lg font-semibold text-[#ECEAE4]">世界书</h1>
            <span className="rounded-full bg-[#1B1F28] px-2.5 py-0.5 text-xs text-[#A0A8B6]">
              {record.worldbook.length} 条
            </span>
          </div>
          <button
            type="button"
            onClick={() => openEditor(null)}
            className="inline-flex items-center gap-1.5 rounded-full bg-[#E7C278] px-4 py-2 text-xs font-semibold text-[#231904] hover:brightness-105"
          >
            <Plus className="h-4 w-4" />
            <span>新增条目</span>
          </button>
        </div>
      </header>

      <main className="mx-auto w-full max-w-6xl flex-1 px-4 pt-4 pb-24 sm:px-6">
        {/* Category Filter Pills */}
        <div className="no-scrollbar mb-5 flex gap-2 overflow-x-auto pb-1">
          {CATEGORIES.filter((c) => c.key === 'all' || (counts[c.key] ?? 0) > 0).map((cat) => {
            const active = filter === cat.key;
            return (
              <button
                key={cat.key}
                type="button"
                onClick={() => setFilter(cat.key)}
                className={`shrink-0 rounded-full border px-3.5 py-1.5 text-xs font-medium transition-colors ${
                  active
                    ? 'border-[#E7C278] bg-[#362B16] text-[#FBE2AC]'
                    : 'border-[#292F3C] bg-[#15181F] text-[#A0A8B6] hover:text-[#ECEAE4]'
                }`}
              >
                {cat.label} ({counts[cat.key] ?? 0})
              </button>
            );
          })}
        </div>

        {grouped.length === 0 ? (
          <div className="rounded-[22px] border border-dashed border-[#292F3C] bg-[#15181F]/50 px-6 py-16 text-center text-sm text-[#A0A8B6]">
            暂无世界书条目，点击右上角「新增条目」或继续推进剧情由引擎自动记录。
          </div>
        ) : (
          <div className="space-y-6">
            {grouped.map(([typeKey, entries]) => {
              const meta = categoryMeta(typeKey);
              const Icon = meta.icon;
              return (
                <section key={typeKey}>
                  <h2 className="mb-3 text-xs font-semibold tracking-wider text-[#A0A8B6]">
                    {meta.label}
                  </h2>
                  <div className="grid grid-cols-1 gap-3.5 sm:grid-cols-2 lg:grid-cols-3">
                    {entries.map((entry) => (
                      <button
                        key={entry.id}
                        type="button"
                        onClick={() => openEditor(entry)}
                        className="flex flex-col justify-between rounded-[20px] border border-[#292F3C] bg-[#1B1F28] p-4 text-left transition-all hover:border-[#E7C278]/45 hover:bg-[#202530]"
                      >
                        <div>
                          <div className="flex items-start justify-between gap-2">
                            <div className="flex min-w-0 items-center gap-2.5">
                              <div
                                className="flex h-8 w-8 shrink-0 items-center justify-center rounded-xl"
                                style={{ backgroundColor: meta.bg, color: meta.color }}
                              >
                                <Icon className="h-4 w-4" />
                              </div>
                              <span className="truncate text-[14px] font-semibold text-[#ECEAE4]">
                                {entry.name}
                              </span>
                            </div>
                            <span className="shrink-0 rounded-full bg-[#232834] px-2.5 py-0.5 text-[11px] text-[#A0A8B6]">
                              {entry.is_secret ? '幕后' : entry.status || '正常'}
                            </span>
                          </div>
                          {entry.description && (
                            <p className="mt-2.5 line-clamp-3 text-xs leading-6 text-[#A0A8B6]">
                              {entry.description}
                            </p>
                          )}
                        </div>

                        {entry.keywords.length > 0 && (
                          <div className="mt-3 flex flex-wrap gap-1.5">
                            {entry.keywords.slice(0, 5).map((kw) => (
                              <span
                                key={kw}
                                className="rounded-full bg-[#15181F] px-2.5 py-0.5 text-[10px] text-[#6E7686]"
                              >
                                {kw}
                              </span>
                            ))}
                          </div>
                        )}
                      </button>
                    ))}
                  </div>
                </section>
              );
            })}
          </div>
        )}
      </main>

      <ModalSheet
        open={Boolean(editing)}
        title={isNew ? '新增世界书条目' : `编辑 · ${editing?.name}`}
        onClose={() => setEditing(null)}
        footer={
          <div className="flex items-center justify-between gap-2">
            {!isNew ? (
              <button
                type="button"
                onClick={handleDelete}
                className="inline-flex items-center gap-1 rounded-full px-3.5 py-2 text-xs text-[#F07B84] hover:bg-[#3B1B20]"
              >
                <Trash2 className="h-3.5 w-3.5" />
                <span>删除</span>
              </button>
            ) : (
              <span />
            )}
            <div className="flex gap-2">
              <button
                type="button"
                onClick={() => setEditing(null)}
                className="rounded-full px-4 py-2 text-xs text-[#A0A8B6] hover:bg-[#232834]"
              >
                取消
              </button>
              <button
                type="button"
                disabled={!fName.trim()}
                onClick={handleSave}
                className="rounded-full bg-[#E7C278] px-5 py-2 text-xs font-semibold text-[#231904] disabled:opacity-40"
              >
                保存条目
              </button>
            </div>
          </div>
        }
      >
        <div className="space-y-3.5">
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <div>
              <label className="mb-1 block text-xs text-[#A0A8B6]">条目名称 *</label>
              <input
                type="text"
                value={fName}
                onChange={(e) => setFName(e.target.value)}
                placeholder="例如：听雨茶馆"
                className="w-full rounded-xl border border-[#292F3C] bg-[#0E1014] px-3.5 py-2.5 text-sm text-[#ECEAE4] outline-none focus:border-[#E7C278]"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs text-[#A0A8B6]">类别</label>
              <select
                value={fType}
                onChange={(e) => setFType(e.target.value)}
                className="w-full rounded-xl border border-[#292F3C] bg-[#0E1014] px-3.5 py-2.5 text-sm text-[#ECEAE4] outline-none focus:border-[#E7C278]"
              >
                <option value="location">地点</option>
                <option value="item">物品</option>
                <option value="organization">组织</option>
                <option value="event">事件</option>
                <option value="character">人物资料</option>
              </select>
            </div>
          </div>

          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <div>
              <label className="mb-1 block text-xs text-[#A0A8B6]">当前状态</label>
              <input
                type="text"
                value={fStatus}
                onChange={(e) => setFStatus(e.target.value)}
                placeholder="正常 / 进行中 / 已封闭"
                className="w-full rounded-xl border border-[#292F3C] bg-[#0E1014] px-3.5 py-2.5 text-sm text-[#ECEAE4] outline-none focus:border-[#E7C278]"
              />
            </div>
            <div className="flex items-end pb-1">
              <label className="inline-flex cursor-pointer items-center gap-2 text-xs text-[#ECEAE4]">
                <input
                  type="checkbox"
                  checked={fSecret}
                  onChange={(e) => setFSecret(e.target.checked)}
                  className="h-4 w-4 accent-[#E7C278]"
                />
                <span>设为幕后设定（角色不会主动说破）</span>
              </label>
            </div>
          </div>

          <div>
            <label className="mb-1 block text-xs text-[#A0A8B6]">触发关键词（用顿号或逗号分隔）</label>
            <input
              type="text"
              value={fKeywords}
              onChange={(e) => setFKeywords(e.target.value)}
              placeholder="茶馆、听雨茶馆、二楼、乌龙茶"
              className="w-full rounded-xl border border-[#292F3C] bg-[#0E1014] px-3.5 py-2.5 text-sm text-[#ECEAE4] outline-none focus:border-[#E7C278]"
            />
          </div>

          <div>
            <label className="mb-1 block text-xs text-[#A0A8B6]">条目内容</label>
            <textarea
              rows={5}
              value={fDesc}
              onChange={(e) => setFDesc(e.target.value)}
              placeholder="描述该地点/物品/组织的事实细节，当剧情触发关键词时自动注入上下文..."
              className="w-full rounded-xl border border-[#292F3C] bg-[#0E1014] px-3.5 py-2.5 text-sm leading-6 text-[#ECEAE4] outline-none focus:border-[#E7C278]"
            />
          </div>
        </div>
      </ModalSheet>
    </div>
  );
}

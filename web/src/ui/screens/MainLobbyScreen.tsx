import { useRef, useState } from 'react';
import {
  BookOpen,
  Download,
  FileJson,
  FolderUp,
  Settings,
  Sparkles,
  Trash2,
} from 'lucide-react';
import {
  deleteStory,
  exportAllStories,
  exportSingleStory,
  importBackupBundle,
  triggerJsonDownload,
} from '../../storage/storyRepository';
import type { StoryMeta } from '../../types/models';
import { EditorialAvatar } from '../components/EditorialAvatar';
import { ModalSheet } from '../components/ModalSheet';

interface MainLobbyScreenProps {
  stories: StoryMeta[];
  modelLabel: string;
  onRefresh: () => Promise<void>;
  onOpenStory: (storyId: string) => void;
  onModernStart: () => void;
  onImportStory: () => void;
  onOpenSettings: () => void;
  onToast: (msg: string) => void;
}

function formatUpdatedAt(iso: string): string {
  try {
    const d = new Date(iso);
    const now = new Date();
    const sameDay =
      d.getFullYear() === now.getFullYear() &&
      d.getMonth() === now.getMonth() &&
      d.getDate() === now.getDate();
    const hh = String(d.getHours()).padStart(2, '0');
    const mm = String(d.getMinutes()).padStart(2, '0');
    if (sameDay) return `今天 ${hh}:${mm}`;
    return `${d.getMonth() + 1}/${d.getDate()} ${hh}:${mm}`;
  } catch {
    return '';
  }
}

export function MainLobbyScreen({
  stories,
  modelLabel,
  onRefresh,
  onOpenStory,
  onModernStart,
  onImportStory,
  onOpenSettings,
  onToast,
}: MainLobbyScreenProps) {
  const [confirmDelete, setConfirmDelete] = useState<StoryMeta | null>(null);
  const fileInputRef = useRef<HTMLInputElement | null>(null);

  const handleExportAll = async () => {
    try {
      const bundle = await exportAllStories();
      if (!bundle.stories.length) {
        onToast('当前故事库还没有可导出的存档');
        return;
      }
      const dateStr = new Date().toISOString().slice(0, 10);
      triggerJsonDownload(`NovelChat-backup-${dateStr}.json`, bundle);
      onToast(`已导出全部 ${bundle.stories.length} 部故事备份`);
    } catch (err) {
      onToast(err instanceof Error ? err.message : '导出失败');
    }
  };

  const handleExportSingle = async (story: StoryMeta) => {
    try {
      const bundle = await exportSingleStory(story.id);
      const safeTitle = story.title.replace(/[^\w\u4e00-\u9fa5-]+/g, '_');
      triggerJsonDownload(`NovelChat-${safeTitle}-R${story.round}.json`, bundle);
      onToast(`已导出「${story.title}」存档`);
    } catch (err) {
      onToast(err instanceof Error ? err.message : '导出失败');
    }
  };

  const handleImportBackupFile = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;
    try {
      const text = await file.text();
      const imported = await importBackupBundle(text);
      await onRefresh();
      onToast(`成功恢复 ${imported.length} 部故事存档`);
    } catch (err) {
      onToast(err instanceof Error ? err.message : '存档导入失败');
    }
  };

  const handleConfirmDelete = async () => {
    if (!confirmDelete) return;
    const title = confirmDelete.title;
    await deleteStory(confirmDelete.id);
    setConfirmDelete(null);
    await onRefresh();
    onToast(`已删除「${title}」`);
  };

  return (
    <div className="safe-top safe-bottom flex min-h-full flex-col bg-[#0E1014]">
      <input
        ref={fileInputRef}
        type="file"
        accept=".json,application/json"
        className="hidden"
        onChange={handleImportBackupFile}
      />

      {/* Top Header */}
      <header className="sticky top-0 z-20 border-b border-[#292F3C]/50 bg-[#0E1014]/90 backdrop-blur-md">
        <div className="mx-auto flex h-15 max-w-5xl items-center justify-between px-4 sm:px-6">
          <div className="flex items-center gap-3">
            <h1 className="text-xl font-bold tracking-tight text-[#ECEAE4]">故事库</h1>
            <span className="hidden rounded-full border border-[#292F3C] bg-[#15181F] px-2.5 py-0.5 text-[11px] text-[#A0A8B6] sm:inline-flex">
              {modelLabel}
            </span>
          </div>
          <div className="flex items-center gap-1.5">
            <button
              type="button"
              onClick={() => fileInputRef.current?.click()}
              className="inline-flex items-center gap-1.5 rounded-full border border-[#292F3C] bg-[#15181F] px-3 py-1.5 text-xs text-[#ECEAE4] transition-colors hover:bg-[#232834]"
              title="从备份 JSON 恢复存档"
            >
              <FolderUp className="h-3.5 w-3.5 text-[#7AD0B8]" />
              <span className="hidden sm:inline">恢复备份</span>
            </button>
            <button
              type="button"
              onClick={handleExportAll}
              className="inline-flex items-center gap-1.5 rounded-full border border-[#292F3C] bg-[#15181F] px-3 py-1.5 text-xs text-[#ECEAE4] transition-colors hover:bg-[#232834]"
              title="导出全部故事备份（跨设备同步）"
            >
              <Download className="h-3.5 w-3.5 text-[#E7C278]" />
              <span className="hidden sm:inline">导出备份</span>
            </button>
            <button
              type="button"
              onClick={onOpenSettings}
              className="flex h-9 w-9 items-center justify-center rounded-full text-[#A0A8B6] transition-colors hover:bg-[#1B1F28] hover:text-[#ECEAE4]"
              aria-label="API 与模型设置"
              title="API 与模型设置"
            >
              <Settings className="h-5 w-5" />
            </button>
          </div>
        </div>
      </header>

      <main className="mx-auto w-full max-w-5xl flex-1 px-4 py-6 sm:px-6">
        {/* Start New Chapter Actions */}
        <div>
          <div className="mb-3 text-xs font-semibold tracking-wider text-[#A0A8B6]">开启新篇章</div>
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <button
              type="button"
              onClick={onImportStory}
              className="group flex items-center gap-4 rounded-[20px] border border-[#E7C278]/35 bg-[#362B16]/65 p-4 text-left transition-all hover:border-[#E7C278]/65 hover:bg-[#362B16]/90"
            >
              <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl bg-[#E7C278] text-[#231904]">
                <FileJson className="h-6 w-6" />
              </div>
              <div className="min-w-0">
                <div className="text-[15px] font-semibold text-[#FBE2AC]">导入故事 / 剧本</div>
                <div className="mt-0.5 text-xs text-[#ECEAE4]/75">
                  AI 解析世界观、高级 JSON 模板（含雾港老城 &amp; 魔法学院）
                </div>
              </div>
            </button>

            <button
              type="button"
              onClick={onModernStart}
              className="group flex items-center gap-4 rounded-[20px] border border-[#292F3C] bg-[#1B1F28] p-4 text-left transition-all hover:border-[#7AD0B8]/50 hover:bg-[#232834]"
            >
              <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl bg-[#17332C] text-[#7AD0B8]">
                <Sparkles className="h-6 w-6" />
              </div>
              <div className="min-w-0">
                <div className="text-[15px] font-semibold text-[#ECEAE4]">现代开局</div>
                <div className="mt-0.5 text-xs text-[#A0A8B6]">
                  海临市真实人生模拟 · 5 位预设身份或自定义开局
                </div>
              </div>
            </button>
          </div>
        </div>

        {/* Continue Reading Grid */}
        <div className="mt-8">
          <div className="mb-3 flex items-center justify-between">
            <span className="text-xs font-semibold tracking-wider text-[#A0A8B6]">继续阅读</span>
            <span className="rounded-full bg-[#1B1F28] px-2.5 py-0.5 text-[11px] font-medium text-[#A0A8B6]">
              {stories.length} 部作品
            </span>
          </div>

          {stories.length === 0 ? (
            <div className="flex flex-col items-center justify-center rounded-[24px] border border-dashed border-[#292F3C] bg-[#15181F]/60 px-6 py-14 text-center">
              <div className="flex h-14 w-14 items-center justify-center rounded-2xl bg-[#1B1F28] text-[#E7C278]">
                <BookOpen className="h-7 w-7" />
              </div>
              <h3 className="mt-4 text-base font-semibold text-[#ECEAE4]">书架还是空的</h3>
              <p className="mt-1.5 max-w-sm text-xs leading-6 text-[#A0A8B6]">
                点击上方「导入故事」试试内置的《雾港老城》或《白塔魔法学院》，或者用「现代开局」直接开启海临市故事。
              </p>
            </div>
          ) : (
            <div className="grid grid-cols-1 gap-3.5 md:grid-cols-2">
              {stories.map((story) => (
                <div
                  key={story.id}
                  onClick={() => onOpenStory(story.id)}
                  role="button"
                  tabIndex={0}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter' || e.key === ' ') onOpenStory(story.id);
                  }}
                  className="group flex cursor-pointer flex-col justify-between rounded-[22px] border border-[#292F3C] bg-[#1B1F28] p-4 transition-all hover:border-[#E7C278]/45 hover:bg-[#202530]"
                >
                  <div>
                    <div className="flex items-start justify-between gap-3">
                      <div className="flex min-w-0 items-center gap-3">
                        <EditorialAvatar name={story.title} size={44} />
                        <div className="min-w-0">
                          <h3 className="truncate text-[16px] font-semibold text-[#ECEAE4]">
                            {story.title}
                          </h3>
                          <p className="truncate text-xs text-[#A0A8B6]">{story.subtitle}</p>
                        </div>
                      </div>
                      <div className="flex shrink-0 items-center gap-1">
                        <button
                          type="button"
                          onClick={(e) => {
                            e.stopPropagation();
                            void handleExportSingle(story);
                          }}
                          className="rounded-lg p-1.5 text-[#6E7686] transition-colors hover:bg-[#2D3342] hover:text-[#E7C278]"
                          title="导出此故事 JSON 存档"
                        >
                          <Download className="h-4 w-4" />
                        </button>
                        <button
                          type="button"
                          onClick={(e) => {
                            e.stopPropagation();
                            setConfirmDelete(story);
                          }}
                          className="rounded-lg p-1.5 text-[#6E7686] transition-colors hover:bg-[#3B1B20] hover:text-[#F07B84]"
                          title="删除此故事"
                        >
                          <Trash2 className="h-4 w-4" />
                        </button>
                      </div>
                    </div>

                    <p className="mt-3 line-clamp-3 text-[13px] leading-6 text-[#A0A8B6]">
                      {story.preview}
                    </p>
                  </div>

                  <div className="mt-3.5 flex items-center justify-between border-t border-[#292F3C]/70 pt-2.5 text-[11px] text-[#6E7686]">
                    <span className="tabular-nums font-medium text-[#7AD0B8]">
                      第 {story.round} 轮
                    </span>
                    <span>{formatUpdatedAt(story.updated_at)}</span>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </main>

      <ModalSheet
        open={Boolean(confirmDelete)}
        title="删除故事存档？"
        onClose={() => setConfirmDelete(null)}
        maxWidthClass="sm:max-w-md"
        footer={
          <div className="flex justify-end gap-2.5">
            <button
              type="button"
              onClick={() => setConfirmDelete(null)}
              className="rounded-full px-4 py-2 text-xs font-medium text-[#A0A8B6] hover:bg-[#232834]"
            >
              取消
            </button>
            <button
              type="button"
              onClick={handleConfirmDelete}
              className="rounded-full bg-[#F07B84] px-5 py-2 text-xs font-semibold text-[#231904] hover:brightness-105"
            >
              确认删除
            </button>
          </div>
        }
      >
        <p className="text-sm leading-6 text-[#A0A8B6]">
          确定删除「<span className="font-semibold text-[#ECEAE4]">{confirmDelete?.title}</span>
          」及其所有剧情、角色卡与世界书记录吗？删除前建议先点击右上角导出备份。
        </p>
      </ModalSheet>
    </div>
  );
}

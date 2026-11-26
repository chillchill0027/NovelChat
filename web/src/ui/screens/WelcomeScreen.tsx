import { ArrowRight, BookOpen, Cpu, ShieldCheck, Sparkles } from 'lucide-react';

interface WelcomeScreenProps {
  onStart: () => void;
  onExploreLobby?: () => void;
  hasExistingStories?: boolean;
}

const FEATURES = [
  {
    icon: Sparkles,
    color: '#E7C278',
    bg: '#362B16',
    title: '二代异步双轨引擎',
    desc: '前台纯正文流式直出，后台自动同步角色关系、世界书增量与 10/60 轮分层章节记忆。',
  },
  {
    icon: BookOpen,
    color: '#7AD0B8',
    bg: '#17332C',
    title: '完整兼容高级 JSON 世界书',
    desc: '支持 AI 自然语言解析与高级 v1 剧本导入，并可一键导出/导入存档跨设备接力。',
  },
  {
    icon: ShieldCheck,
    color: '#B5C1E3',
    bg: '#252B3D',
    title: '纯静态零服务器 · 隐私本地留存',
    desc: '所有故事与 API 密钥仅存在当前浏览器 IndexedDB，手机与平板可添加到主屏幕全屏游玩。',
  },
];

export function WelcomeScreen({
  onStart,
  onExploreLobby,
  hasExistingStories = false,
}: WelcomeScreenProps) {
  return (
    <div className="safe-top safe-bottom flex min-h-full w-full items-center justify-center bg-[#0E1014] px-5 py-10 sm:px-8">
      <div className="grid w-full max-w-5xl grid-cols-1 items-center gap-10 lg:grid-cols-12 lg:gap-12">
        {/* Left Hero Column */}
        <div className="flex flex-col items-start lg:col-span-6">
          <div className="inline-flex items-center gap-2 rounded-full border border-[#E7C278]/30 bg-[#362B16]/70 px-3.5 py-1.5 text-xs font-medium text-[#FBE2AC]">
            <Cpu className="h-3.5 w-3.5 text-[#E7C278]" />
            <span>NovelChat 2.0 · 纯静态 Web / PWA</span>
          </div>

          <h1 className="mt-5 text-4xl font-bold tracking-tight text-[#ECEAE4] sm:text-5xl">
            翻开你的
            <span className="text-[#E7C278]"> 互动故事</span>
          </h1>

          <p className="mt-4 max-w-lg text-[15px] leading-7 text-[#A0A8B6]">
            为长篇中文沉浸阅读打造的 AI 互动叙事引擎。无需后端服务器，在手机、平板与电脑浏览器中都能获得一致的暗色纸感阅读与角色成长体验。
          </p>

          <div className="mt-8 flex w-full flex-col gap-3 sm:w-auto sm:flex-row">
            <button
              type="button"
              onClick={onStart}
              className="inline-flex items-center justify-center gap-2 rounded-full bg-[#E7C278] px-7 py-3.5 text-sm font-semibold text-[#231904] shadow-lg shadow-[#E7C278]/10 transition-all hover:brightness-105 active:scale-[0.99]"
            >
              <span>配置模型并开始</span>
              <ArrowRight className="h-4 w-4" />
            </button>

            {onExploreLobby && (
              <button
                type="button"
                onClick={onExploreLobby}
                className="inline-flex items-center justify-center gap-2 rounded-full border border-[#292F3C] bg-[#1B1F28] px-6 py-3.5 text-sm font-medium text-[#ECEAE4] transition-colors hover:bg-[#232834]"
              >
                <span>{hasExistingStories ? '直接进入故事库' : '先逛逛故事库'}</span>
              </button>
            )}
          </div>
        </div>

        {/* Right Feature Cards Column */}
        <div className="flex flex-col gap-3.5 lg:col-span-6">
          {FEATURES.map((item) => {
            const Icon = item.icon;
            return (
              <div
                key={item.title}
                className="flex items-start gap-4 rounded-[20px] border border-[#292F3C] bg-[#1B1F28] p-5 transition-colors hover:border-[#3A4254]"
              >
                <div
                  className="flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl"
                  style={{ backgroundColor: item.bg, color: item.color }}
                >
                  <Icon className="h-5 w-5" />
                </div>
                <div className="min-w-0">
                  <h3 className="text-[15px] font-semibold text-[#ECEAE4]">{item.title}</h3>
                  <p className="mt-1 text-[13px] leading-6 text-[#A0A8B6]">{item.desc}</p>
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}

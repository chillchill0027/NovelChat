import { useEffect, type ReactNode } from 'react';
import { X } from 'lucide-react';

interface ModalSheetProps {
  open: boolean;
  title?: string;
  subtitle?: string;
  onClose: () => void;
  children: ReactNode;
  footer?: ReactNode;
  maxWidthClass?: string;
}

export function ModalSheet({
  open,
  title,
  subtitle,
  onClose,
  children,
  footer,
  maxWidthClass = 'sm:max-w-xl',
}: ModalSheetProps) {
  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open, onClose]);

  if (!open) return null;

  return (
    <div
      className="fixed inset-0 z-50 flex items-end justify-center sm:items-center sm:p-4"
      role="dialog"
      aria-modal="true"
    >
      <div
        className="fixed inset-0 bg-black/65 backdrop-blur-[2px] transition-opacity"
        onClick={onClose}
      />
      <div
        className={`relative z-10 flex max-h-[88vh] w-full flex-col overflow-hidden rounded-t-[28px] border border-[#292F3C] bg-[#15181F] shadow-2xl sm:rounded-[28px] ${maxWidthClass}`}
      >
        <div className="flex items-center justify-center pt-2.5 sm:hidden">
          <div className="h-1 w-9 rounded-full bg-[#3A4254]" />
        </div>
        {(title || subtitle) && (
          <div className="flex items-start justify-between gap-3 border-b border-[#292F3C]/70 px-5 py-4">
            <div className="min-w-0">
              {title && (
                <h2 className="truncate text-[17px] font-semibold text-[#ECEAE4]">{title}</h2>
              )}
              {subtitle && (
                <p className="mt-0.5 truncate text-xs text-[#A0A8B6]">{subtitle}</p>
              )}
            </div>
            <button
              type="button"
              onClick={onClose}
              className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-[#A0A8B6] transition-colors hover:bg-[#232834] hover:text-[#ECEAE4]"
              aria-label="关闭"
            >
              <X className="h-4 w-4" />
            </button>
          </div>
        )}
        <div className="flex-1 overflow-y-auto px-5 py-4">{children}</div>
        {footer && (
          <div className="safe-bottom border-t border-[#292F3C]/70 bg-[#15181F] px-5 py-3.5">
            {footer}
          </div>
        )}
      </div>
    </div>
  );
}

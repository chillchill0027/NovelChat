import { avatarPaletteFor } from '../../domain/palette';

interface EditorialAvatarProps {
  name: string;
  size?: number;
  className?: string;
}

export function EditorialAvatar({ name, size = 38, className = '' }: EditorialAvatarProps) {
  const palette = avatarPaletteFor(name);
  const initial = (name.trim()[0] ?? '故').toUpperCase();
  const radius = Math.round(size * 0.3);
  const fontSize = Math.max(12, Math.round(size * 0.42));

  return (
    <div
      className={`inline-flex shrink-0 items-center justify-center font-semibold select-none ${className}`}
      style={{
        width: size,
        height: size,
        borderRadius: radius,
        backgroundColor: palette.bg,
        color: palette.fg,
        fontSize,
        border: `1px solid ${palette.fg}22`,
      }}
      aria-hidden="true"
    >
      {initial}
    </div>
  );
}

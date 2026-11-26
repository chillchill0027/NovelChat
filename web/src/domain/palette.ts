export interface AvatarPalette {
  bg: string;
  fg: string;
}

export const AVATAR_PALETTES: AvatarPalette[] = [
  { bg: '#352B19', fg: '#F0CE86' }, // Warm Amber
  { bg: '#19322D', fg: '#84D9C0' }, // Soft Jade
  { bg: '#232A40', fg: '#AEBEF0' }, // Dusk Periwinkle
  { bg: '#34222D', fg: '#E8A4C4' }, // Dusty Rose
  { bg: '#2D253C', fg: '#CBB0F5' }, // Muted Lavender
  { bg: '#222F3A', fg: '#8CC5EA' }, // Mist Slate Blue
];

export function avatarPaletteFor(seed: string): AvatarPalette {
  const s = seed.trim();
  if (!s) return AVATAR_PALETTES[0];
  let hash = 0;
  for (let i = 0; i < s.length; i++) {
    hash = ((hash << 5) - hash + s.charCodeAt(i)) | 0;
  }
  const idx = (hash & 0x7fffffff) % AVATAR_PALETTES.length;
  return AVATAR_PALETTES[idx];
}

export function favorColor(favor: number): string {
  if (favor >= 40) return '#6ED49B';
  if (favor >= 10) return '#7AD0B8';
  if (favor <= -20) return '#F07B84';
  if (favor < 0) return '#EE9F62';
  return '#A0A8B6';
}

export function trustColor(trust: number): string {
  if (trust >= 60) return '#6ED49B';
  if (trust >= 25) return '#E7C278';
  return '#A0A8B6';
}

export function formatFavor(favor: number): string {
  return favor >= 0 ? `+${favor}` : `${favor}`;
}

export function rankColor(rank: string): string {
  switch (rank.trim().toUpperCase()) {
    case 'A':
    case 'S':
    case 'SS':
    case 'SSS':
      return '#EBC86E';
    case 'B':
      return '#C29EF8';
    case 'C':
      return '#7CB4F8';
    case 'D':
      return '#7AD0B8';
    default:
      return '#A3B0BE';
  }
}

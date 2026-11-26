import type { LengthPreference } from '../../types/models';

export interface LengthHint {
  minChars: number;
  maxChars: number;
  maxTokens: number;
}

export type InputLengthKind = 'continuation' | 'normal' | 'compound';

const CONTINUATION_RE = /^(继续|继续吧|往下|往下看|接着|然后呢|看下去|下一步|开始|开始吧|开局)$/;

export function classifyInputLength(rawInput: string, isOpening = false): InputLengthKind {
  const trimmed = rawInput.trim();
  if (isOpening) return 'normal';
  if (CONTINUATION_RE.test(trimmed) || trimmed.length <= 6) return 'continuation';
  const clauses = trimmed.split(/[，,。；;、\n]|然后|接着|顺便|再/).filter((s) => s.trim().length >= 3);
  if (clauses.length >= 3) return 'compound';
  return 'normal';
}

export function computeLengthHint(
  rawInput: string,
  pref: LengthPreference,
  isOpening = false,
): LengthHint {
  const kind = classifyInputLength(rawInput, isOpening);
  const table: Record<InputLengthKind, Record<LengthPreference, [number, number]>> = {
    continuation: {
      short: [200, 400],
      standard: [400, 700],
      detailed: [700, 1100],
    },
    normal: {
      short: [300, 600],
      standard: [600, 1000],
      detailed: [900, 1400],
    },
    compound: {
      short: [500, 800],
      standard: [800, 1300],
      detailed: [1100, 1600],
    },
  };
  const [minChars, maxChars] = table[kind][pref];
  return {
    minChars,
    maxChars,
    maxTokens: maxChars * 2 + 300,
  };
}

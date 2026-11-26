export type GuardReason = 'EMPTY' | 'TOO_SHORT' | 'REPETITION' | 'META_LEAK';

export type GuardVerdict =
  | { kind: 'pass'; cleanedText: string; hadMetaLeakStripped: boolean }
  | { kind: 'retry'; reason: GuardReason; cleanedText: string }
  | { kind: 'fail'; reason: GuardReason };

const META_LEAK_SENTENCE =
  /[^。！？!?\n]*(?:作为(?:一个)?\s*AI|语言模型|JSON\s*(?:格式|对象|字段)?|提示词|玩家输入|第\s*\d+\s*轮)[^。！？!?\n]*[。！？!?]?/gi;
const EXCESSIVE_NEWLINES = /\n(?:[ \t]*\n){2,}/g;
const PUNCT_OR_SPACE = /[\s，,。\.！!？\?；;：:“”"'（）()\[\]【】—\-…]/g;

export function stripMetaLeakSentences(text: string): string {
  return text
    .replace(META_LEAK_SENTENCE, '')
    .split('\n')
    .map((l) => l.trim())
    .join('\n')
    .replace(EXCESSIVE_NEWLINES, '\n\n')
    .trim();
}

function normalizeForComparison(text: string): string {
  return text.replace(PUNCT_OR_SPACE, '').toLowerCase();
}

export function isNarrativeRepetitive(current: string, previous: string): boolean {
  const a = normalizeForComparison(current);
  const b = normalizeForComparison(previous);
  if (a.length < 40 || b.length < 40) return false;

  // 8-gram shingle overlap > 40%
  const prevShingles = new Set<string>();
  for (let i = 0; i <= b.length - 8; i++) {
    prevShingles.add(b.slice(i, i + 8));
  }
  let overlap = 0;
  const total = a.length - 7;
  for (let i = 0; i <= a.length - 8; i++) {
    if (prevShingles.has(a.slice(i, i + 8))) overlap++;
  }
  return total > 0 && overlap / total > 0.4;
}

export function evaluateNarrativeGuard(
  cleanedText: string,
  previousNarrative: string,
  attempt: number,
): GuardVerdict {
  const stripped = stripMetaLeakSentences(cleanedText);
  const hadMeta = stripped !== cleanedText.trim();

  if (!stripped) {
    return attempt === 0
      ? { kind: 'retry', reason: 'EMPTY', cleanedText: '' }
      : { kind: 'fail', reason: 'EMPTY' };
  }

  const nonSpaceLen = stripped.replace(/\s/g, '').length;
  if (nonSpaceLen < 80) {
    if (attempt === 0) return { kind: 'retry', reason: 'TOO_SHORT', cleanedText: stripped };
    if (nonSpaceLen < 30) return { kind: 'fail', reason: 'TOO_SHORT' };
    return { kind: 'pass', cleanedText: stripped, hadMetaLeakStripped: hadMeta };
  }

  if (previousNarrative.trim() && isNarrativeRepetitive(stripped, previousNarrative) && attempt === 0) {
    return { kind: 'retry', reason: 'REPETITION', cleanedText: stripped };
  }

  return { kind: 'pass', cleanedText: stripped, hadMetaLeakStripped: hadMeta };
}

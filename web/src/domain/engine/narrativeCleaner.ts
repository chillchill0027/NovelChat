import { extractJsonObject } from './jsonExtract';

const CODE_FENCE_LINE = /^\s*```[a-zA-Z0-9_-]*\s*$/;
const MARKDOWN_HEADING = /^#{1,6}\s+/;
const BULLET_PREFIX = /^(?:[-*•])\s+/;
const LEADING_CHATTER =
  /^(?:(?:好的|明白|当然)[，,。！!\s]*)+(?:(?:以下是|下面为您|为您续写)[^：\n]{0,30}[：:])?$|^(?:以下是|下面为您|为您续写)[^：\n]{0,30}[：:]$/;
const TRAILING_CHATTER =
  /^(?:---+|===+|（本轮完）|\(本轮完\)|（待续）|\(待续\)|【本轮结束】)$/;
const EXCESSIVE_NEWLINES = /\n(?:[ \t]*\n){2,}/g;
const SENTENCE_ENDINGS = ['。', '！', '？', '…', '」', '”', '.', '!', '?'];

function extractNarrativeFromJsonIfWrapped(text: string): string | null {
  if (!text.startsWith('{') && !text.includes('"narrative"')) return null;
  const json = extractJsonObject(text);
  if (!json) return null;
  try {
    const obj = JSON.parse(json) as { narrative?: unknown };
    if (typeof obj.narrative === 'string' && obj.narrative.trim()) {
      return obj.narrative.trim();
    }
  } catch {
    // ignore
  }
  return null;
}

function trimToLastCompleteSentence(text: string): string {
  if (text.length < 40) return text;
  let lastEnd = -1;
  for (const ch of SENTENCE_ENDINGS) {
    const idx = text.lastIndexOf(ch);
    if (idx > lastEnd) lastEnd = idx;
  }
  if (lastEnd < 20) return text;
  return text.slice(0, lastEnd + 1).trimEnd();
}

export function cleanNarrativeText(raw: string, finishReason?: string | null): string {
  let text = raw.replace(/\r\n/g, '\n').trim();
  if (!text) return '';

  const initialLines = text.split('\n');
  while (initialLines.length > 0) {
    const head = initialLines[0].trim();
    if (!head || LEADING_CHATTER.test(head) || CODE_FENCE_LINE.test(head)) {
      initialLines.shift();
    } else {
      break;
    }
  }
  while (initialLines.length > 0) {
    const tail = initialLines[initialLines.length - 1].trim();
    if (!tail || TRAILING_CHATTER.test(tail) || CODE_FENCE_LINE.test(tail)) {
      initialLines.pop();
    } else {
      break;
    }
  }
  text = initialLines.join('\n').trim();
  if (!text) return '';

  const unwrapped = extractNarrativeFromJsonIfWrapped(text);
  if (unwrapped) text = unwrapped;

  const lines = text
    .split('\n')
    .map((line) => line.trimEnd().replace(MARKDOWN_HEADING, '').replace(BULLET_PREFIX, ''));

  while (lines.length > 0 && (!lines[0].trim() || LEADING_CHATTER.test(lines[0].trim()))) {
    lines.shift();
  }
  while (
    lines.length > 0 &&
    (!lines[lines.length - 1].trim() || TRAILING_CHATTER.test(lines[lines.length - 1].trim()))
  ) {
    lines.pop();
  }

  let normalized = lines.join('\n').replace(EXCESSIVE_NEWLINES, '\n\n').trim();
  if (finishReason === 'length') {
    normalized = trimToLastCompleteSentence(normalized);
  }
  return normalized;
}

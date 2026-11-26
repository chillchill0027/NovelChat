/**
 * Robustly extract a JSON object substring from noisy/fenced LLM output.
 */
export function extractJsonObject(raw: string): string | null {
  const text = raw.trim();
  if (!text) return null;

  // Prefer ```json ... ``` fenced block if present
  const fenceMatch = text.match(/```(?:json)?\s*([\s\S]*?)```/i);
  const candidate = fenceMatch ? fenceMatch[1].trim() : text;

  const firstBrace = candidate.indexOf('{');
  if (firstBrace < 0) return null;

  let depth = 0;
  let inString = false;
  let escaped = false;
  for (let i = firstBrace; i < candidate.length; i++) {
    const ch = candidate[i];
    if (inString) {
      if (escaped) {
        escaped = false;
      } else if (ch === '\\') {
        escaped = true;
      } else if (ch === '"') {
        inString = false;
      }
      continue;
    }
    if (ch === '"') {
      inString = true;
    } else if (ch === '{') {
      depth++;
    } else if (ch === '}') {
      depth--;
      if (depth === 0) {
        return candidate.slice(firstBrace, i + 1);
      }
    }
  }
  return null;
}

export function parseLooseJson<T>(raw: string): T | null {
  const extracted = extractJsonObject(raw);
  if (!extracted) return null;
  try {
    return JSON.parse(extracted) as T;
  } catch {
    // Try stripping trailing commas before } or ]
    const repaired = extracted.replace(/,\s*([}\]])/g, '$1');
    try {
      return JSON.parse(repaired) as T;
    } catch {
      return null;
    }
  }
}

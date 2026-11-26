export const DEFAULT_API_BASE_URL = 'https://api.deepseek.com';
export const DEFAULT_MODEL = 'deepseek-chat';

/**
 * Normalize Chinese IME full-width punctuation often typed in mobile URLs.
 */
export function normalizeApiUrlDraft(raw: string): string {
  return raw
    .replace(/：/g, ':')
    .replace(/／/g, '/')
    .replace(/[。．]/g, '.')
    .replace(/－/g, '-')
    .replace(/＿/g, '_')
    .replace(/\s+/g, '');
}

export function normalizeBaseUrl(raw: string): string {
  let clean = normalizeApiUrlDraft(raw).trim();
  if (!clean) return '';
  if (!/^https?:\/\//i.test(clean)) {
    const isLocal = /^(localhost|127\.0\.0\.1|10\.|192\.168\.|172\.(1[6-9]|2\d|3[01])\.)/i.test(clean);
    clean = `${isLocal ? 'http' : 'https'}://${clean}`;
  }
  return clean.replace(/\/+$/, '');
}

export function isLocalOrLanHost(hostname: string): boolean {
  const host = hostname.toLowerCase();
  if (host === 'localhost' || host === '127.0.0.1' || host === '::1') return true;
  if (host.endsWith('.local')) return true;
  if (/^10\.\d+\.\d+\.\d+$/.test(host)) return true;
  if (/^192\.168\.\d+\.\d+$/.test(host)) return true;
  if (/^172\.(1[6-9]|2\d|3[01])\.\d+\.\d+$/.test(host)) return true;
  return false;
}

export function chatCompletionsUrl(baseUrl: string): string {
  const clean = normalizeBaseUrl(baseUrl);
  if (clean.endsWith('/chat/completions')) return clean;
  if (clean.endsWith('/v1')) return `${clean}/chat/completions`;
  return `${clean}/v1/chat/completions`;
}

export function validateApiConfig(
  rawUrl: string,
  rawKey: string,
  rawModel: string,
): string | null {
  const normalizedUrl = normalizeBaseUrl(rawUrl);
  if (!normalizedUrl) return '请填写 API 地址';
  let parsed: URL;
  try {
    parsed = new URL(normalizedUrl);
  } catch {
    return 'API 地址格式不正确，请检查是否包含非法字符';
  }
  if (parsed.username || parsed.password) {
    return 'API 地址不能内嵌账号或密钥，请填写到下方密钥框';
  }
  if (parsed.search || parsed.hash) {
    return 'API 地址不能包含查询参数或锚点';
  }
  const local = isLocalOrLanHost(parsed.hostname);
  if (parsed.protocol === 'http:' && !local) {
    return '云端 API 必须使用 HTTPS 地址（仅本机/局域网模型允许 HTTP）';
  }
  if (parsed.protocol !== 'https:' && parsed.protocol !== 'http:') {
    return '仅支持 https:// 或本地 http:// 地址';
  }
  if (!local && !rawKey.trim()) {
    return '云端 API 必须填写 API Key';
  }
  if (!rawModel.trim()) {
    return '请填写模型名称（例如 deepseek-chat）';
  }
  return null;
}

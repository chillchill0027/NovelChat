import { chatCompletionsUrl } from '../apiEndpoint';
import type { AiCallStage, AppSettings, LlmCallMetric } from '../../types/models';
import { nowIsoString } from '../../types/models';
import { resolveEndpointForStage } from '../../storage/settingsStore';
import type { LlmMessage } from './contextAssembler';

export interface LlmStreamResult {
  content: string;
  finishReason: string | null;
  metric: LlmCallMetric;
}

const recentMetrics: LlmCallMetric[] = [];
const MAX_METRICS = 40;

export function getRecentMetrics(): LlmCallMetric[] {
  return [...recentMetrics];
}

function recordMetric(m: LlmCallMetric): void {
  recentMetrics.unshift(m);
  if (recentMetrics.length > MAX_METRICS) recentMetrics.pop();
}

function formatFetchError(err: unknown, url: string): string {
  if (err instanceof DOMException && err.name === 'AbortError') {
    return '已停止生成';
  }
  const msg = err instanceof Error ? err.message : String(err);
  if (/failed to fetch|networkerror|load failed/i.test(msg)) {
    return `网络请求失败（可能是目标接口未开放浏览器 CORS 跨域、地址不可达，或 HTTPS 页面访问了 HTTP 本地地址）：${url}`;
  }
  return msg.slice(0, 240);
}

async function readErrorBody(resp: Response): Promise<string> {
  try {
    const raw = await resp.text();
    try {
      const parsed = JSON.parse(raw) as { error?: { message?: string }; message?: string };
      const detail = parsed.error?.message || parsed.message || raw;
      return `HTTP ${resp.status}: ${String(detail).slice(0, 200)}`;
    } catch {
      return `HTTP ${resp.status}: ${raw.slice(0, 200)}`;
    }
  } catch {
    return `HTTP ${resp.status}`;
  }
}

export async function testApiConnection(
  baseUrl: string,
  apiKey: string,
  model: string,
): Promise<{ ok: boolean; latencyMs: number; message: string }> {
  const url = chatCompletionsUrl(baseUrl);
  const start = performance.now();
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 25_000);
  try {
    const headers: Record<string, string> = { 'Content-Type': 'application/json' };
    if (apiKey.trim()) headers.Authorization = `Bearer ${apiKey.trim()}`;
    const resp = await fetch(url, {
      method: 'POST',
      headers,
      signal: controller.signal,
      body: JSON.stringify({
        model: model.trim(),
        messages: [{ role: 'user', content: '回复OK' }],
        max_tokens: 12,
        temperature: 0,
      }),
    });
    const latencyMs = Math.round(performance.now() - start);
    if (!resp.ok) {
      return { ok: false, latencyMs, message: await readErrorBody(resp) };
    }
    const data = (await resp.json()) as {
      choices?: { message?: { content?: string } }[];
      model?: string;
    };
    const reply = data.choices?.[0]?.message?.content?.trim() || 'OK';
    return {
      ok: true,
      latencyMs,
      message: `连接成功（${latencyMs} ms · ${data.model || model} · 响应：${reply.slice(0, 20)}）`,
    };
  } catch (err) {
    return {
      ok: false,
      latencyMs: Math.round(performance.now() - start),
      message: formatFetchError(err, url),
    };
  } finally {
    clearTimeout(timer);
  }
}

export async function streamChatCompletion(params: {
  settings: AppSettings;
  stage: AiCallStage;
  messages: LlmMessage[];
  temperature: number;
  maxTokens: number;
  signal?: AbortSignal;
  onToken?: (delta: string, accumulated: string) => void;
}): Promise<LlmStreamResult> {
  const ep = resolveEndpointForStage(params.settings, params.stage);
  const url = chatCompletionsUrl(ep.baseUrl);
  const start = performance.now();
  let ttftMs = 0;
  let content = '';
  let finishReason: string | null = null;
  let promptTokens = 0;
  let completionTokens = 0;
  let cachedTokens = 0;

  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  if (ep.apiKey) headers.Authorization = `Bearer ${ep.apiKey}`;

  try {
    const resp = await fetch(url, {
      method: 'POST',
      headers,
      signal: params.signal,
      body: JSON.stringify({
        model: ep.model,
        messages: params.messages,
        temperature: params.temperature,
        max_tokens: params.maxTokens,
        stream: true,
        stream_options: { include_usage: true },
      }),
    });

    if (!resp.ok) {
      const errMsg = await readErrorBody(resp);
      throw new Error(errMsg);
    }

    const body = resp.body;
    if (!body) {
      // Fallback for non-streaming response
      const json = (await resp.json()) as {
        choices?: { message?: { content?: string }; finish_reason?: string }[];
        usage?: {
          prompt_tokens?: number;
          completion_tokens?: number;
          prompt_tokens_details?: { cached_tokens?: number };
          prompt_cache_hit_tokens?: number;
        };
      };
      content = json.choices?.[0]?.message?.content ?? '';
      finishReason = json.choices?.[0]?.finish_reason ?? null;
      promptTokens = json.usage?.prompt_tokens ?? 0;
      completionTokens = json.usage?.completion_tokens ?? 0;
      cachedTokens =
        json.usage?.prompt_tokens_details?.cached_tokens ??
        json.usage?.prompt_cache_hit_tokens ??
        0;
      if (content) params.onToken?.(content, content);
      ttftMs = Math.round(performance.now() - start);
    } else {
      const reader = body.getReader();
      const decoder = new TextDecoder('utf-8');
      let buffer = '';

      while (true) {
        const { value, done } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split('\n');
        buffer = lines.pop() ?? '';

        for (const rawLine of lines) {
          const line = rawLine.trim();
          if (!line || !line.startsWith('data:')) continue;
          const payload = line.slice(5).trim();
          if (payload === '[DONE]') continue;
          try {
            const chunk = JSON.parse(payload) as {
              choices?: { delta?: { content?: string }; finish_reason?: string | null }[];
              usage?: {
                prompt_tokens?: number;
                completion_tokens?: number;
                prompt_tokens_details?: { cached_tokens?: number };
                prompt_cache_hit_tokens?: number;
              };
            };
            const delta = chunk.choices?.[0]?.delta?.content;
            if (typeof delta === 'string' && delta.length > 0) {
              if (!ttftMs) ttftMs = Math.round(performance.now() - start);
              content += delta;
              params.onToken?.(delta, content);
            }
            if (chunk.choices?.[0]?.finish_reason) {
              finishReason = chunk.choices[0].finish_reason;
            }
            if (chunk.usage) {
              promptTokens = chunk.usage.prompt_tokens ?? promptTokens;
              completionTokens = chunk.usage.completion_tokens ?? completionTokens;
              cachedTokens =
                chunk.usage.prompt_tokens_details?.cached_tokens ??
                chunk.usage.prompt_cache_hit_tokens ??
                cachedTokens;
            }
          } catch {
            // Skip malformed SSE partial chunk
          }
        }
      }
    }

    const metric: LlmCallMetric = {
      stage: params.stage,
      model: ep.model,
      ttft_ms: ttftMs || Math.round(performance.now() - start),
      total_ms: Math.round(performance.now() - start),
      prompt_tokens: promptTokens,
      completion_tokens: completionTokens,
      cached_tokens: cachedTokens,
      timestamp: nowIsoString(),
      ok: true,
    };
    recordMetric(metric);
    return { content, finishReason, metric };
  } catch (err) {
    const errMsg = formatFetchError(err, url);
    recordMetric({
      stage: params.stage,
      model: ep.model,
      ttft_ms: ttftMs,
      total_ms: Math.round(performance.now() - start),
      prompt_tokens: promptTokens,
      completion_tokens: completionTokens,
      cached_tokens: cachedTokens,
      timestamp: nowIsoString(),
      ok: false,
      error: errMsg,
    });
    throw new Error(errMsg);
  }
}

export async function completeChatJson(params: {
  settings: AppSettings;
  stage: AiCallStage;
  messages: LlmMessage[];
  temperature: number;
  maxTokens: number;
  jsonMode?: boolean;
  signal?: AbortSignal;
}): Promise<string> {
  const ep = resolveEndpointForStage(params.settings, params.stage);
  const url = chatCompletionsUrl(ep.baseUrl);
  const start = performance.now();
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  if (ep.apiKey) headers.Authorization = `Bearer ${ep.apiKey}`;

  const bodyObj: Record<string, unknown> = {
    model: ep.model,
    messages: params.messages,
    temperature: params.temperature,
    max_tokens: params.maxTokens,
  };
  if (params.jsonMode) {
    bodyObj.response_format = { type: 'json_object' };
  }

  const doRequest = async (payload: Record<string, unknown>) => {
    const resp = await fetch(url, {
      method: 'POST',
      headers,
      signal: params.signal,
      body: JSON.stringify(payload),
    });
    return resp;
  };

  try {
    let resp = await doRequest(bodyObj);
    // Graceful fallback if provider rejects response_format=json_object
    if (!resp.ok && resp.status === 400 && params.jsonMode) {
      delete bodyObj.response_format;
      resp = await doRequest(bodyObj);
    }
    if (!resp.ok) {
      throw new Error(await readErrorBody(resp));
    }
    const data = (await resp.json()) as {
      choices?: { message?: { content?: string } }[];
      usage?: {
        prompt_tokens?: number;
        completion_tokens?: number;
        prompt_tokens_details?: { cached_tokens?: number };
        prompt_cache_hit_tokens?: number;
      };
    };
    const content = data.choices?.[0]?.message?.content ?? '';
    recordMetric({
      stage: params.stage,
      model: ep.model,
      ttft_ms: Math.round(performance.now() - start),
      total_ms: Math.round(performance.now() - start),
      prompt_tokens: data.usage?.prompt_tokens ?? 0,
      completion_tokens: data.usage?.completion_tokens ?? 0,
      cached_tokens:
        data.usage?.prompt_tokens_details?.cached_tokens ??
        data.usage?.prompt_cache_hit_tokens ??
        0,
      timestamp: nowIsoString(),
      ok: true,
    });
    return content;
  } catch (err) {
    const errMsg = formatFetchError(err, url);
    recordMetric({
      stage: params.stage,
      model: ep.model,
      ttft_ms: 0,
      total_ms: Math.round(performance.now() - start),
      prompt_tokens: 0,
      completion_tokens: 0,
      cached_tokens: 0,
      timestamp: nowIsoString(),
      ok: false,
      error: errMsg,
    });
    throw new Error(errMsg);
  }
}

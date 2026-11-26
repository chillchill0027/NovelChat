import { DEFAULT_API_BASE_URL, DEFAULT_MODEL, normalizeBaseUrl } from '../domain/apiEndpoint';
import type { AiCallStage, ApiProfile, AppSettings } from '../types/models';
import { idbGet, idbPut, STORE_KV } from './db';

const SETTINGS_KEY = 'app_settings_v2';

export function defaultSettings(): AppSettings {
  return {
    api_base_url: DEFAULT_API_BASE_URL,
    api_key: '',
    model: DEFAULT_MODEL,
    current_profile_name: '',
    profiles: [],
    stage_routes: {},
    default_length: 'standard',
    creativity: 'balanced',
    setup_completed: false,
  };
}

export async function loadSettings(): Promise<AppSettings> {
  const saved = await idbGet<Partial<AppSettings>>(STORE_KV, SETTINGS_KEY);
  const base = defaultSettings();
  if (!saved) return base;
  return {
    ...base,
    ...saved,
    profiles: Array.isArray(saved.profiles) ? saved.profiles : [],
    stage_routes: saved.stage_routes ?? {},
  };
}

export async function saveSettings(next: AppSettings): Promise<AppSettings> {
  const normalized: AppSettings = {
    ...next,
    api_base_url: normalizeBaseUrl(next.api_base_url) || DEFAULT_API_BASE_URL,
    api_key: next.api_key.trim(),
    model: next.model.trim() || DEFAULT_MODEL,
    current_profile_name: next.current_profile_name.trim(),
  };
  await idbPut(STORE_KV, normalized, SETTINGS_KEY);
  return normalized;
}

export function resolveEndpointForStage(
  settings: AppSettings,
  stage: AiCallStage,
): { baseUrl: string; apiKey: string; model: string } {
  const routedName = settings.stage_routes?.[stage]?.trim();
  if (routedName) {
    const match = settings.profiles.find((p) => p.name === routedName);
    if (match) {
      return {
        baseUrl: normalizeBaseUrl(match.base_url),
        apiKey: match.api_key.trim(),
        model: match.model.trim(),
      };
    }
  }
  return {
    baseUrl: normalizeBaseUrl(settings.api_base_url),
    apiKey: settings.api_key.trim(),
    model: settings.model.trim(),
  };
}

export function upsertProfile(settings: AppSettings, profile: ApiProfile): AppSettings {
  const clean: ApiProfile = {
    name: profile.name.trim(),
    base_url: normalizeBaseUrl(profile.base_url),
    api_key: profile.api_key.trim(),
    model: profile.model.trim(),
  };
  const rest = settings.profiles.filter((p) => p.name !== clean.name);
  return {
    ...settings,
    current_profile_name: clean.name,
    api_base_url: clean.base_url,
    api_key: clean.api_key,
    model: clean.model,
    profiles: [clean, ...rest],
  };
}

export function deleteProfile(settings: AppSettings, name: string): AppSettings {
  const nextRoutes = { ...settings.stage_routes };
  (Object.keys(nextRoutes) as AiCallStage[]).forEach((stage) => {
    if (nextRoutes[stage] === name) {
      delete nextRoutes[stage];
    }
  });
  return {
    ...settings,
    profiles: settings.profiles.filter((p) => p.name !== name),
    stage_routes: nextRoutes,
    current_profile_name: settings.current_profile_name === name ? '' : settings.current_profile_name,
  };
}

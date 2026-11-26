import { useCallback, useEffect, useState } from 'react';
import { createDefaultStory } from './domain/defaultStories';
import { requestPersistentStorage } from './storage/db';
import { defaultSettings, loadSettings } from './storage/settingsStore';
import { getStory, listStories, saveStory } from './storage/storyRepository';
import type { AppSettings, StoryMeta, StoryRecord } from './types/models';
import { ApiSetupScreen } from './ui/screens/ApiSetupScreen';
import { CharacterListScreen } from './ui/screens/CharacterListScreen';
import { ChatScreen } from './ui/screens/ChatScreen';
import { MainLobbyScreen } from './ui/screens/MainLobbyScreen';
import { StoryImportScreen } from './ui/screens/StoryImportScreen';
import { WelcomeScreen } from './ui/screens/WelcomeScreen';
import { WorldbookScreen } from './ui/screens/WorldbookScreen';

type Route =
  | { name: 'welcome' }
  | { name: 'setup'; returnTo: 'lobby' | 'chat'; storyId?: string }
  | { name: 'lobby' }
  | { name: 'import' }
  | { name: 'chat'; storyId: string }
  | { name: 'worldbook'; storyId: string }
  | { name: 'characters'; storyId: string; selectedName?: string | null };

function parseHashRoute(hash: string): Route | null {
  const clean = hash.replace(/^#\/?/, '').trim();
  if (!clean) return null;
  const parts = clean.split('/');
  switch (parts[0]) {
    case 'welcome':
      return { name: 'welcome' };
    case 'setup':
      return { name: 'setup', returnTo: 'lobby' };
    case 'lobby':
      return { name: 'lobby' };
    case 'import':
      return { name: 'import' };
    case 'chat':
      return parts[1] ? { name: 'chat', storyId: decodeURIComponent(parts[1]) } : { name: 'lobby' };
    case 'worldbook':
      return parts[1]
        ? { name: 'worldbook', storyId: decodeURIComponent(parts[1]) }
        : { name: 'lobby' };
    case 'characters':
      return parts[1]
        ? {
            name: 'characters',
            storyId: decodeURIComponent(parts[1]),
            selectedName: parts[2] ? decodeURIComponent(parts[2]) : null,
          }
        : { name: 'lobby' };
    default:
      return null;
  }
}

function toHash(route: Route): string {
  switch (route.name) {
    case 'welcome':
      return '#/welcome';
    case 'setup':
      return '#/setup';
    case 'lobby':
      return '#/lobby';
    case 'import':
      return '#/import';
    case 'chat':
      return `#/chat/${encodeURIComponent(route.storyId)}`;
    case 'worldbook':
      return `#/worldbook/${encodeURIComponent(route.storyId)}`;
    case 'characters':
      return route.selectedName
        ? `#/characters/${encodeURIComponent(route.storyId)}/${encodeURIComponent(route.selectedName)}`
        : `#/characters/${encodeURIComponent(route.storyId)}`;
  }
}

export function App() {
  const [booted, setBooted] = useState(false);
  const [settings, setSettings] = useState<AppSettings>(defaultSettings);
  const [stories, setStories] = useState<StoryMeta[]>([]);
  const [route, setRoute] = useState<Route>({ name: 'welcome' });
  const [activeRecord, setActiveRecord] = useState<StoryRecord | null>(null);
  const [toast, setToast] = useState<string | null>(null);

  const showToast = useCallback((msg: string) => {
    setToast(msg);
  }, []);

  useEffect(() => {
    if (!toast) return;
    const t = setTimeout(() => setToast(null), 2800);
    return () => clearTimeout(t);
  }, [toast]);

  const navigate = useCallback((next: Route) => {
    setRoute(next);
    const targetHash = toHash(next);
    if (window.location.hash !== targetHash) {
      window.location.hash = targetHash;
    }
  }, []);

  const refreshStories = useCallback(async () => {
    const list = await listStories();
    setStories(list);
  }, []);

  // Boot: request persistent IndexedDB storage, load settings & stories
  useEffect(() => {
    let alive = true;
    const init = async () => {
      void requestPersistentStorage();
      const [loadedSettings, loadedStories] = await Promise.all([loadSettings(), listStories()]);
      if (!alive) return;
      setSettings(loadedSettings);
      setStories(loadedStories);

      const fromHash = parseHashRoute(window.location.hash);
      if (fromHash) {
        setRoute(fromHash);
      } else if (loadedSettings.setup_completed || loadedStories.length > 0) {
        setRoute({ name: 'lobby' });
        window.location.hash = '#/lobby';
      } else {
        setRoute({ name: 'welcome' });
        window.location.hash = '#/welcome';
      }
      setBooted(true);
    };
    void init();
    return () => {
      alive = false;
    };
  }, []);

  // Browser back/forward hash sync
  useEffect(() => {
    const onHashChange = () => {
      const parsed = parseHashRoute(window.location.hash);
      if (parsed) setRoute(parsed);
    };
    window.addEventListener('hashchange', onHashChange);
    return () => window.removeEventListener('hashchange', onHashChange);
  }, []);

  // Load active StoryRecord whenever route enters chat/worldbook/characters
  useEffect(() => {
    if (route.name === 'chat' || route.name === 'worldbook' || route.name === 'characters') {
      const targetId = route.storyId;
      if (activeRecord?.id === targetId) return;
      let alive = true;
      void getStory(targetId).then((rec) => {
        if (!alive) return;
        if (rec) {
          setActiveRecord(rec);
        } else {
          navigate({ name: 'lobby' });
        }
      });
      return () => {
        alive = false;
      };
    }
  }, [route, activeRecord?.id, navigate]);

  const handleOpenStory = useCallback(
    async (storyId: string) => {
      const rec = await getStory(storyId);
      if (!rec) {
        showToast('故事存档不存在');
        await refreshStories();
        return;
      }
      setActiveRecord(rec);
      navigate({ name: 'chat', storyId });
    },
    [navigate, refreshStories, showToast],
  );

  const handleRecordChange = useCallback((next: StoryRecord) => {
    setActiveRecord(next);
  }, []);

  if (!booted) {
    return (
      <div className="flex h-full w-full items-center justify-center bg-[#0E1014] text-sm text-[#A0A8B6]">
        正在载入 NovelChat...
      </div>
    );
  }

  let screen = null;
  if (route.name === 'welcome') {
    screen = (
      <WelcomeScreen
        hasExistingStories={stories.length > 0}
        onStart={() => navigate({ name: 'setup', returnTo: 'lobby' })}
        onExploreLobby={() => navigate({ name: 'lobby' })}
      />
    );
  } else if (route.name === 'setup') {
    screen = (
      <ApiSetupScreen
        settings={settings}
        canGoBack={settings.setup_completed || stories.length > 0 || route.returnTo === 'chat'}
        onBack={() => {
          if (route.returnTo === 'chat' && route.storyId) {
            navigate({ name: 'chat', storyId: route.storyId });
          } else {
            navigate({ name: 'lobby' });
          }
        }}
        onSaved={(next) => {
          setSettings(next);
          showToast('API 配置已保存');
          if (route.returnTo === 'chat' && route.storyId) {
            navigate({ name: 'chat', storyId: route.storyId });
          } else {
            navigate({ name: 'lobby' });
          }
        }}
      />
    );
  } else if (route.name === 'lobby') {
    screen = (
      <MainLobbyScreen
        stories={stories}
        modelLabel={settings.model || '未配置模型'}
        onRefresh={refreshStories}
        onOpenStory={(id) => void handleOpenStory(id)}
        onDefaultStart={async () => {
          const rec = createDefaultStory();
          await saveStory(rec);
          await refreshStories();
          setActiveRecord(rec);
          navigate({ name: 'chat', storyId: rec.id });
        }}
        onImportStory={() => navigate({ name: 'import' })}
        onOpenSettings={() => navigate({ name: 'setup', returnTo: 'lobby' })}
        onToast={showToast}
      />
    );
  } else if (route.name === 'import') {
    screen = (
      <StoryImportScreen
        settings={settings}
        onBack={() => navigate({ name: 'lobby' })}
        onCreated={async (id) => {
          await refreshStories();
          await handleOpenStory(id);
        }}
      />
    );
  } else if (route.name === 'chat' && activeRecord) {
    screen = (
      <ChatScreen
        record={activeRecord}
        settings={settings}
        onBack={async () => {
          await refreshStories();
          navigate({ name: 'lobby' });
        }}
        onRecordChange={handleRecordChange}
        onSettingsChange={setSettings}
        onOpenWorldbook={() => navigate({ name: 'worldbook', storyId: activeRecord.id })}
        onOpenCharacters={(selectedName) =>
          navigate({ name: 'characters', storyId: activeRecord.id, selectedName })
        }
        onOpenApiSetup={() =>
          navigate({ name: 'setup', returnTo: 'chat', storyId: activeRecord.id })
        }
        onToast={showToast}
      />
    );
  } else if (route.name === 'worldbook' && activeRecord) {
    screen = (
      <WorldbookScreen
        record={activeRecord}
        onBack={() => navigate({ name: 'chat', storyId: activeRecord.id })}
        onRecordChange={handleRecordChange}
      />
    );
  } else if (route.name === 'characters' && activeRecord) {
    screen = (
      <CharacterListScreen
        record={activeRecord}
        initialSelectedName={route.selectedName ?? null}
        onBack={() => navigate({ name: 'chat', storyId: activeRecord.id })}
        onRecordChange={handleRecordChange}
      />
    );
  }

  return (
    <div className="h-full w-full overflow-y-auto bg-[#0E1014]">
      {screen}
      {toast && (
        <div className="pointer-events-none fixed inset-x-0 bottom-6 z-50 flex justify-center px-4">
          <div className="rounded-full border border-[#292F3C] bg-[#232834]/95 px-4 py-2 text-xs font-medium text-[#ECEAE4] shadow-xl backdrop-blur-sm">
            {toast}
          </div>
        </div>
      )}
    </div>
  );
}

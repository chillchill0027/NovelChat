// =============================================================================
// Browser IndexedDB Storage + Per-Story Async Mutex Lock + Persistence Request
// Zero external dependencies; clean fallback to in-memory store for tests/SSR.
// =============================================================================

const DB_NAME = 'novelchat_web_v2';
const DB_VERSION = 1;
export const STORE_STORIES = 'stories';
export const STORE_KV = 'kv';

let dbPromise: Promise<IDBDatabase> | null = null;
const memoryStores: Record<string, Map<string, unknown>> = {
  [STORE_STORIES]: new Map(),
  [STORE_KV]: new Map(),
};

function hasIndexedDb(): boolean {
  return typeof indexedDB !== 'undefined';
}

export async function requestPersistentStorage(): Promise<boolean> {
  try {
    if (typeof navigator !== 'undefined' && navigator.storage?.persist) {
      const already = await navigator.storage.persisted?.();
      if (already) return true;
      return await navigator.storage.persist();
    }
  } catch {
    // Ignore unsupported browsers
  }
  return false;
}

function openDb(): Promise<IDBDatabase> {
  if (dbPromise) return dbPromise;
  dbPromise = new Promise((resolve, reject) => {
    const req = indexedDB.open(DB_NAME, DB_VERSION);
    req.onupgradeneeded = () => {
      const db = req.result;
      if (!db.objectStoreNames.contains(STORE_STORIES)) {
        db.createObjectStore(STORE_STORIES, { keyPath: 'id' });
      }
      if (!db.objectStoreNames.contains(STORE_KV)) {
        db.createObjectStore(STORE_KV);
      }
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error ?? new Error('无法打开本地 IndexedDB 数据库'));
  });
  return dbPromise;
}

export async function idbGet<T>(storeName: string, key: string): Promise<T | undefined> {
  if (!hasIndexedDb()) {
    return memoryStores[storeName]?.get(key) as T | undefined;
  }
  const db = await openDb();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(storeName, 'readonly');
    const req = tx.objectStore(storeName).get(key);
    req.onsuccess = () => resolve(req.result as T | undefined);
    req.onerror = () => reject(req.error);
  });
}

export async function idbGetAll<T>(storeName: string): Promise<T[]> {
  if (!hasIndexedDb()) {
    return Array.from(memoryStores[storeName]?.values() ?? []) as T[];
  }
  const db = await openDb();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(storeName, 'readonly');
    const req = tx.objectStore(storeName).getAll();
    req.onsuccess = () => resolve((req.result as T[]) ?? []);
    req.onerror = () => reject(req.error);
  });
}

export async function idbPut<T>(storeName: string, value: T, key?: string): Promise<void> {
  if (!hasIndexedDb()) {
    const map = memoryStores[storeName];
    const k = key ?? (value as { id?: string }).id ?? '';
    map?.set(k, structuredClone(value));
    return;
  }
  const db = await openDb();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(storeName, 'readwrite');
    const store = tx.objectStore(storeName);
    if (key !== undefined) {
      store.put(value, key);
    } else {
      store.put(value);
    }
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
  });
}

export async function idbDelete(storeName: string, key: string): Promise<void> {
  if (!hasIndexedDb()) {
    memoryStores[storeName]?.delete(key);
    return;
  }
  const db = await openDb();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(storeName, 'readwrite');
    tx.objectStore(storeName).delete(key);
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
  });
}

/**
 * Per-story serial async mutex lock (mirrors Android StoryRepository lock).
 */
const storyLocks = new Map<string, Promise<unknown>>();

export async function withStoryLock<T>(storyId: string, fn: () => Promise<T>): Promise<T> {
  const prev = storyLocks.get(storyId) ?? Promise.resolve();
  let release!: () => void;
  const gate = new Promise<void>((res) => {
    release = res;
  });
  const chained = prev.then(() => gate);
  storyLocks.set(storyId, chained);
  await prev;
  try {
    return await fn();
  } finally {
    release();
    if (storyLocks.get(storyId) === chained) {
      storyLocks.delete(storyId);
    }
  }
}

export function clearMemoryDbForTests(): void {
  memoryStores[STORE_STORIES].clear();
  memoryStores[STORE_KV].clear();
}

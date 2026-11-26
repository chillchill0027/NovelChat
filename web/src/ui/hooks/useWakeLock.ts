import { useEffect } from 'react';

interface WakeLockSentinelLike {
  release(): Promise<void>;
}

/**
 * Holds Screen Wake Lock while streaming on supported mobile browsers
 * and fires onResume when the user returns to the foreground tab.
 */
export function useStreamingLifecycle(active: boolean, onTabVisible?: () => void): void {
  useEffect(() => {
    if (!active) return;
    let sentinel: WakeLockSentinelLike | null = null;
    let cancelled = false;

    const acquire = async () => {
      try {
        const nav = navigator as Navigator & {
          wakeLock?: { request(type: 'screen'): Promise<WakeLockSentinelLike> };
        };
        if (nav.wakeLock) {
          const lock = await nav.wakeLock.request('screen');
          if (cancelled) {
            await lock.release();
          } else {
            sentinel = lock;
          }
        }
      } catch {
        // Unsupported or blocked by battery saver — fail silently
      }
    };

    void acquire();
    return () => {
      cancelled = true;
      void sentinel?.release().catch(() => {});
    };
  }, [active]);

  useEffect(() => {
    if (!onTabVisible) return;
    const handler = () => {
      if (document.visibilityState === 'visible') {
        onTabVisible();
      }
    };
    document.addEventListener('visibilitychange', handler);
    return () => document.removeEventListener('visibilitychange', handler);
  }, [onTabVisible]);
}

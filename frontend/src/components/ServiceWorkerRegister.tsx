'use client';

import { useEffect } from 'react';

/** Registers the PWA service worker for offline shell + installability. */
export function ServiceWorkerRegister() {
  useEffect(() => {
    if ('serviceWorker' in navigator) {
      navigator.serviceWorker.register('/sw.js').catch(() => {
        // registration is best-effort
      });
    }
  }, []);
  return null;
}

'use client';

import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { createPortal } from 'react-dom';

type ToastTone = 'success' | 'error' | 'info';

interface ToastOptions {
  title: string;
  message?: string;
  tone?: ToastTone;
  durationMs?: number;
}

interface ConfirmOptions {
  title: string;
  message: string;
  confirmText?: string;
  cancelText?: string;
  tone?: 'danger' | 'default';
}

interface ToastItem {
  id: number;
  title: string;
  message?: string;
  tone: ToastTone;
}

interface ConfirmState extends ConfirmOptions {
  resolve: (value: boolean) => void;
}

interface FeedbackContextValue {
  toast: (options: ToastOptions) => void;
  confirm: (options: ConfirmOptions) => Promise<boolean>;
}

const FeedbackContext = createContext<FeedbackContextValue | null>(null);

export function FeedbackProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<ToastItem[]>([]);
  const [confirmState, setConfirmState] = useState<ConfirmState | null>(null);
  const [mounted, setMounted] = useState(false);
  const idRef = useRef(1);

  useEffect(() => {
    setMounted(true);
  }, []);

  const toast = useCallback((options: ToastOptions) => {
    const id = idRef.current++;
    const item: ToastItem = {
      id,
      title: options.title,
      message: options.message,
      tone: options.tone ?? 'info'
    };
    setToasts((curr) => [...curr, item]);

    const duration = options.durationMs ?? 3200;
    window.setTimeout(() => {
      setToasts((curr) => curr.filter((x) => x.id !== id));
    }, duration);
  }, []);

  const confirm = useCallback((options: ConfirmOptions) => {
    return new Promise<boolean>((resolve) => {
      setConfirmState({
        ...options,
        resolve
      });
    });
  }, []);

  const closeConfirm = useCallback((answer: boolean) => {
    setConfirmState((curr) => {
      if (curr) {
        curr.resolve(answer);
      }
      return null;
    });
  }, []);

  const value = useMemo<FeedbackContextValue>(() => ({ toast, confirm }), [toast, confirm]);

  const overlay = (
    <>
      <div className="pointer-events-none fixed right-4 top-4 z-[9998] flex w-[min(92vw,24rem)] flex-col gap-2">
        {toasts.map((item) => {
          const toneStyles =
            item.tone === 'success'
              ? 'border-emerald-200 bg-emerald-50 text-emerald-900'
              : item.tone === 'error'
              ? 'border-red-200 bg-red-50 text-red-900'
              : 'border-brand/20 bg-white text-wine';
          return (
            <div key={item.id} className={`pointer-events-auto rounded-xl border px-4 py-3 shadow-card ${toneStyles}`}>
              <p className="text-sm font-semibold">{item.title}</p>
              {item.message && <p className="mt-0.5 text-xs opacity-90">{item.message}</p>}
            </div>
          );
        })}
      </div>

      {confirmState && (
        <div className="fixed inset-0 z-[9999] flex items-center justify-center bg-wine/30 p-4">
          <div className="card w-full max-w-md p-5">
            <h3 className="text-xl font-semibold">{confirmState.title}</h3>
            <p className="mt-2 text-sm text-ink/70">{confirmState.message}</p>
            <div className="mt-5 flex justify-end gap-2">
              <button
                onClick={() => closeConfirm(false)}
                className="btn-outline px-4 py-2 text-sm"
                type="button"
              >
                {confirmState.cancelText ?? 'Cancel'}
              </button>
              <button
                onClick={() => closeConfirm(true)}
                className={`px-4 py-2 text-sm ${
                  confirmState.tone === 'danger'
                    ? 'btn rounded-full bg-red-600 text-white hover:bg-red-700'
                    : 'btn-primary'
                }`}
                type="button"
              >
                {confirmState.confirmText ?? 'Confirm'}
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );

  return (
    <FeedbackContext.Provider value={value}>
      {children}
      {mounted ? createPortal(overlay, document.body) : null}
    </FeedbackContext.Provider>
  );
}

export function useFeedback() {
  const ctx = useContext(FeedbackContext);
  if (!ctx) {
    throw new Error('useFeedback must be used inside FeedbackProvider');
  }
  return ctx;
}

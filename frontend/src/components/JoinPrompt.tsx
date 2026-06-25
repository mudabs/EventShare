'use client';

import { useState } from 'react';
import { joinEvent, ApiError } from '@/lib/api';
import { useGuestStore } from '@/store/guestStore';

export function JoinPrompt({ code, onJoined }: { code: string; onJoined: () => void }) {
  const setIdentity = useGuestStore((s) => s.setIdentity);
  const [name, setName] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!name.trim()) {
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const result = await joinEvent(code, name.trim());
      setIdentity(code, { membershipId: result.membershipId, displayName: result.displayName });
      onJoined();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not join this event');
    } finally {
      setBusy(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-3 rounded-lg border border-slate-200 bg-white p-5">
      <p className="text-sm text-slate-600">Add your name to start sharing photos and videos.</p>
      <div className="flex gap-2">
        <input
          value={name}
          maxLength={120}
          onChange={(e) => setName(e.target.value)}
          placeholder="Your name"
          className="flex-1 rounded-md border border-slate-300 px-3 py-2 focus:border-brand focus:outline-none"
        />
        <button
          type="submit"
          disabled={busy}
          className="rounded-md bg-brand px-4 py-2 font-medium text-white hover:bg-brand-dark disabled:opacity-60"
        >
          {busy ? 'Joining…' : 'Join'}
        </button>
      </div>
      {error && <p className="text-sm text-red-600">{error}</p>}
    </form>
  );
}

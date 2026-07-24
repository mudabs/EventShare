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
    <form onSubmit={handleSubmit} className="space-y-3">
      <p className="text-sm text-ink/70">Add your name to start sharing photos and videos.</p>
      <div className="flex gap-2">
        <input
          value={name}
          maxLength={120}
          onChange={(e) => setName(e.target.value)}
          placeholder="Your name"
          className="input flex-1"
        />
        <button type="submit" disabled={busy} className="btn-primary shrink-0">
          {busy ? 'Joining…' : 'Join'}
        </button>
      </div>
      {error && <p className="text-sm text-red-600">{error}</p>}
    </form>
  );
}

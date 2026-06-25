'use client';

import { useAuth, useUser } from '@clerk/nextjs';
import { useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { joinEventAuthenticated } from '@/lib/api';
import { useGuestStore } from '@/store/guestStore';

export function AuthedEventJoin({ code, onJoined }: { code: string; onJoined: () => void }) {
  const { getToken } = useAuth();
  const { user } = useUser();
  const setIdentity = useGuestStore((s) => s.setIdentity);
  const queryClient = useQueryClient();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function join() {
    setBusy(true);
    setError(null);
    try {
      const token = await getToken();
      if (!token) {
        throw new Error('Your session expired. Please sign in again.');
      }
      await joinEventAuthenticated(token, code);
      const name =
        user?.fullName ??
        user?.firstName ??
        user?.primaryEmailAddress?.emailAddress ??
        'Guest';
      setIdentity(code, { displayName: name });
      await queryClient.invalidateQueries({ queryKey: ['myEvents'] });
      onJoined();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not join this event');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="space-y-2 rounded-lg border border-slate-200 bg-white p-5">
      <p className="text-sm text-slate-600">
        Join to start sharing photos and videos. This event will be saved to your account.
      </p>
      <button
        onClick={join}
        disabled={busy}
        className="w-full rounded-md bg-brand px-4 py-3 font-medium text-white hover:bg-brand-dark disabled:opacity-60"
      >
        {busy ? 'Joining...' : 'Join and save to My Events'}
      </button>
      {error && <p className="text-sm text-red-600">{error}</p>}
    </div>
  );
}

'use client';

import { useAuth } from '@clerk/nextjs';
import { useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { redeemPromo } from '@/lib/api';

export function RedeemPromo() {
  const { getToken, isSignedIn } = useAuth();
  const queryClient = useQueryClient();
  const [code, setCode] = useState('');
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  if (!isSignedIn) {
    return null;
  }

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (!code.trim()) return;
    setBusy(true);
    setMessage(null);
    setError(null);
    try {
      const token = (await getToken()) ?? '';
      const result = await redeemPromo(token, code.trim());
      setMessage(result.message);
      setCode('');
      await queryClient.invalidateQueries({ queryKey: ['subscription'] });
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not redeem this code');
    } finally {
      setBusy(false);
    }
  }

  return (
    <form onSubmit={submit} className="mx-auto mt-10 max-w-sm text-center">
      <label className="label">Have a promo code?</label>
      <div className="mt-2 flex gap-2">
        <input
          value={code}
          onChange={(e) => setCode(e.target.value)}
          placeholder="Enter code"
          className="input flex-1 uppercase tracking-wide"
        />
        <button type="submit" disabled={busy} className="shrink-0 rounded-xl bg-wine px-4 py-2.5 font-medium text-white transition-colors hover:bg-wine/90 disabled:opacity-60">
          {busy ? '...' : 'Apply'}
        </button>
      </div>
      {message && <p className="mt-2 text-sm text-green-600">{message}</p>}
      {error && <p className="mt-2 text-sm text-red-600">{error}</p>}
    </form>
  );
}

'use client';

import { useAuth } from '@clerk/nextjs';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { adminAddWhitelist, adminRemoveWhitelist, adminWhitelist } from '@/lib/api';

export function AdminWhitelist() {
  const { getToken } = useAuth();
  const queryClient = useQueryClient();
  const { data } = useQuery({ queryKey: ['adminWhitelist'], queryFn: async () => adminWhitelist((await getToken()) ?? '') });
  const [email, setEmail] = useState('');
  const [note, setNote] = useState('');
  const [error, setError] = useState<string | null>(null);

  async function add(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    try {
      await adminAddWhitelist((await getToken()) ?? '', email.trim(), note.trim());
      setEmail(''); setNote('');
      await queryClient.invalidateQueries({ queryKey: ['adminWhitelist'] });
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not add');
    }
  }

  const entries = data ?? [];

  return (
    <div className="space-y-4">
      <form onSubmit={add} className="flex flex-wrap gap-2 rounded-lg border border-slate-200 bg-white p-4">
        <input value={email} onChange={(e) => setEmail(e.target.value)} type="email" required placeholder="email@example.com" className="flex-1 rounded border border-slate-300 px-2 py-1 text-sm" />
        <input value={note} onChange={(e) => setNote(e.target.value)} placeholder="Note (optional)" className="flex-1 rounded border border-slate-300 px-2 py-1 text-sm" />
        <button type="submit" className="rounded bg-brand px-3 py-1 text-sm font-medium text-white hover:bg-brand-dark">Whitelist</button>
      </form>
      {error && <p className="text-sm text-red-600">{error}</p>}
      <ul className="divide-y divide-slate-100 rounded-lg border border-slate-200 bg-white">
        {entries.map((w) => (
          <li key={w.id} className="flex items-center justify-between px-4 py-2 text-sm">
            <div>
              <span className="font-medium">{w.email}</span>
              {w.note && <span className="ml-2 text-slate-500">{w.note}</span>}
            </div>
            <button onClick={async () => { await adminRemoveWhitelist((await getToken()) ?? '', w.id); queryClient.invalidateQueries({ queryKey: ['adminWhitelist'] }); }} className="rounded bg-red-50 px-2 py-1 text-xs text-red-700 hover:bg-red-100">Remove</button>
          </li>
        ))}
        {entries.length === 0 && <li className="px-4 py-3 text-sm text-slate-500">No whitelisted emails.</li>}
      </ul>
    </div>
  );
}

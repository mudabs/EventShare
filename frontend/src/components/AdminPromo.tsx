'use client';

import { useAuth } from '@clerk/nextjs';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { adminCreatePromo, adminDisablePromo, adminPromoCodes } from '@/lib/api';

const TYPES = ['LIFETIME_PREMIUM', 'TEMP_PREMIUM', 'FREE_EVENT', 'PERCENT', 'FIXED'];

export function AdminPromo() {
  const { getToken } = useAuth();
  const queryClient = useQueryClient();
  const { data } = useQuery({ queryKey: ['adminPromo'], queryFn: async () => adminPromoCodes((await getToken()) ?? '') });

  const [code, setCode] = useState('');
  const [type, setType] = useState('TEMP_PREMIUM');
  const [grantsPlanCode, setGrantsPlanCode] = useState('WEDDING_PRO');
  const [durationDays, setDurationDays] = useState('30');
  const [maxRedemptions, setMaxRedemptions] = useState('');
  const [error, setError] = useState<string | null>(null);

  async function create(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    try {
      const token = (await getToken()) ?? '';
      await adminCreatePromo(token, {
        code,
        type,
        grantsPlanCode: grantsPlanCode || null,
        durationDays: durationDays ? Number(durationDays) : null,
        maxRedemptions: maxRedemptions ? Number(maxRedemptions) : null
      });
      setCode('');
      await queryClient.invalidateQueries({ queryKey: ['adminPromo'] });
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not create code');
    }
  }

  const codes = data ?? [];

  return (
    <div className="space-y-5">
      <form onSubmit={create} className="card grid grid-cols-2 gap-2 p-4 sm:grid-cols-3">
        <input value={code} onChange={(e) => setCode(e.target.value)} placeholder="CODE" required className="input text-sm uppercase" />
        <select value={type} onChange={(e) => setType(e.target.value)} className="input text-sm">
          {TYPES.map((t) => <option key={t} value={t}>{t}</option>)}
        </select>
        <input value={grantsPlanCode} onChange={(e) => setGrantsPlanCode(e.target.value)} placeholder="Grants plan" className="input text-sm" />
        <input value={durationDays} onChange={(e) => setDurationDays(e.target.value)} placeholder="Days" className="input text-sm" />
        <input value={maxRedemptions} onChange={(e) => setMaxRedemptions(e.target.value)} placeholder="Max uses" className="input text-sm" />
        <button type="submit" className="btn-primary px-4 py-1.5 text-sm">Create</button>
      </form>
      {error && <p className="text-sm text-red-600">{error}</p>}

      <div className="card overflow-x-auto p-2">
        <table className="w-full text-left text-sm">
          <thead className="text-xs uppercase text-ink/50"><tr><th className="py-2">Code</th><th>Type</th><th>Used</th><th>Active</th><th></th></tr></thead>
          <tbody>
            {codes.map((c) => (
              <tr key={c.id} className="border-t border-brand/10">
                <td className="py-2 font-mono">{c.code}</td>
                <td>{c.type}</td>
                <td>{c.redemptionsUsed}{c.maxRedemptions ? `/${c.maxRedemptions}` : ''}</td>
                <td>{c.active ? 'Yes' : 'No'}</td>
                <td className="py-2 text-right">
                  {c.active && <button onClick={async () => { await adminDisablePromo((await getToken()) ?? '', c.id); queryClient.invalidateQueries({ queryKey: ['adminPromo'] }); }} className="rounded-full bg-blush px-2.5 py-1 text-xs text-wine hover:bg-brand/10">Disable</button>}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

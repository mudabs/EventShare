'use client';

import { useAuth } from '@clerk/nextjs';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import {
  adminDeleteUser, adminDisableUser, adminEnableUser, adminSetUserPlan, adminUsers
} from '@/lib/api';

const PLANS = ['FREE', 'BASIC', 'WEDDING_PRO', 'LIFETIME'];

export function AdminUsers() {
  const { getToken } = useAuth();
  const queryClient = useQueryClient();
  const [q, setQ] = useState('');
  const { data, isLoading } = useQuery({
    queryKey: ['adminUsers', q],
    queryFn: async () => adminUsers((await getToken()) ?? '', q || undefined)
  });

  async function refresh() {
    await queryClient.invalidateQueries({ queryKey: ['adminUsers'] });
  }

  const users = data ?? [];

  return (
    <div className="space-y-3">
      <input
        value={q}
        onChange={(e) => setQ(e.target.value)}
        placeholder="Search by email or name"
        className="w-full max-w-sm rounded-md border border-slate-300 px-3 py-2 text-sm focus:border-brand focus:outline-none"
      />
      {isLoading ? (
        <p className="text-slate-500">Loading...</p>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="text-xs uppercase text-slate-500">
              <tr>
                <th className="py-2">User</th><th>Role</th><th>Plan</th><th>Status</th><th></th>
              </tr>
            </thead>
            <tbody>
              {users.map((u) => (
                <tr key={u.id} className="border-t border-slate-100">
                  <td className="py-2">
                    <div className="font-medium">{u.displayName || '(no name)'}</div>
                    <div className="text-xs text-slate-500">{u.email}</div>
                  </td>
                  <td>{u.role}</td>
                  <td>
                    <select
                      value={u.planCode}
                      onChange={async (e) => {
                        await adminSetUserPlan((await getToken()) ?? '', u.id, e.target.value);
                        refresh();
                      }}
                      className="rounded border border-slate-300 px-1 py-0.5 text-xs"
                    >
                      {PLANS.map((p) => <option key={p} value={p}>{p}</option>)}
                    </select>
                  </td>
                  <td>{u.disabled ? <span className="text-red-600">Disabled</span> : <span className="text-green-600">Active</span>}</td>
                  <td className="space-x-2 whitespace-nowrap py-2 text-right text-xs">
                    <button
                      onClick={async () => { const t = (await getToken()) ?? ''; await (u.disabled ? adminEnableUser(t, u.id) : adminDisableUser(t, u.id)); refresh(); }}
                      className="rounded bg-slate-100 px-2 py-1 hover:bg-slate-200"
                    >
                      {u.disabled ? 'Enable' : 'Disable'}
                    </button>
                    <button
                      onClick={async () => { if (!window.confirm('Delete this user?')) return; await adminDeleteUser((await getToken()) ?? '', u.id); refresh(); }}
                      className="rounded bg-red-50 px-2 py-1 text-red-700 hover:bg-red-100"
                    >
                      Delete
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

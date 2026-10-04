'use client';

import { useAuth } from '@clerk/nextjs';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useFeedback } from '@/components/feedback/AppFeedback';
import {
  adminDeleteUser, adminDisableUser, adminEnableUser, adminSetUserPlan, adminUsers
} from '@/lib/api';

const PLANS = ['FREE', 'BASIC', 'WEDDING_PRO', 'LIFETIME', 'UNLIMITED'];

export function AdminUsers() {
  const { getToken } = useAuth();
  const { confirm, toast } = useFeedback();
  const queryClient = useQueryClient();
  const [q, setQ] = useState('');
  const [actionError, setActionError] = useState<string | null>(null);
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
        className="input max-w-sm text-sm"
      />
      {actionError && <p className="text-sm text-red-600">{actionError}</p>}
      {isLoading ? (
        <p className="text-ink/50">Loading...</p>
      ) : (
        <div className="card overflow-x-auto p-2">
          <table className="w-full text-left text-sm">
            <thead className="text-xs uppercase text-ink/50">
              <tr>
                <th className="py-2">User</th><th>Role</th><th>Plan</th><th>Status</th><th></th>
              </tr>
            </thead>
            <tbody>
              {users.map((u) => (
                <tr key={u.id} className="border-t border-brand/10">
                  <td className="py-2">
                    <div className="font-medium text-wine">{u.displayName || '(no name)'}</div>
                    <div className="text-xs text-ink/50">{u.email}</div>
                  </td>
                  <td>{u.role}</td>
                  <td>
                    <select
                      value={u.planCode}
                      onChange={async (e) => {
                        setActionError(null);
                        try {
                          await adminSetUserPlan((await getToken()) ?? '', u.id, e.target.value);
                          await refresh();
                          toast({ title: 'Plan updated', message: `${u.email} is now on ${e.target.value}.`, tone: 'success' });
                        } catch (err) {
                          const message = err instanceof Error ? err.message : 'Could not change user plan';
                          setActionError(message);
                          toast({ title: 'Plan update failed', message, tone: 'error' });
                        }
                      }}
                      className="rounded-lg border border-brand/20 px-1 py-0.5 text-xs"
                    >
                      {PLANS.map((p) => <option key={p} value={p}>{p}</option>)}
                    </select>
                  </td>
                  <td>{u.disabled ? <span className="text-red-600">Disabled</span> : <span className="text-green-600">Active</span>}</td>
                  <td className="space-x-2 whitespace-nowrap py-2 text-right text-xs">
                    <button
                      onClick={async () => {
                        setActionError(null);
                        try {
                          const t = (await getToken()) ?? '';
                          await (u.disabled ? adminEnableUser(t, u.id) : adminDisableUser(t, u.id));
                          await refresh();
                          toast({
                            title: u.disabled ? 'User enabled' : 'User disabled',
                            message: u.email,
                            tone: 'success'
                          });
                        } catch (err) {
                          const message = err instanceof Error ? err.message : 'Could not update user status';
                          setActionError(message);
                          toast({ title: 'Status update failed', message, tone: 'error' });
                        }
                      }}
                      className="rounded-full bg-blush px-2.5 py-1 text-wine hover:bg-brand/10"
                    >
                      {u.disabled ? 'Enable' : 'Disable'}
                    </button>
                    <button
                      onClick={async () => {
                        const accepted = await confirm({
                          title: 'Delete this user?',
                          message: `${u.email} will lose access to this account.`,
                          confirmText: 'Delete user',
                          tone: 'danger'
                        });
                        if (!accepted) return;
                        setActionError(null);
                        try {
                          await adminDeleteUser((await getToken()) ?? '', u.id);
                          await refresh();
                          toast({ title: 'User deleted', message: u.email, tone: 'success' });
                        } catch (err) {
                          const message = err instanceof Error ? err.message : 'Could not delete user';
                          setActionError(message);
                          toast({ title: 'Delete failed', message, tone: 'error' });
                        }
                      }}
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

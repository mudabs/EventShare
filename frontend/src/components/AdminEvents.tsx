'use client';

import { useAuth } from '@clerk/nextjs';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useFeedback } from '@/components/feedback/AppFeedback';
import { adminArchiveEvent, adminEvents, adminRemoveEvent } from '@/lib/api';

export function AdminEvents() {
  const { getToken } = useAuth();
  const { confirm, toast } = useFeedback();
  const queryClient = useQueryClient();
  const [q, setQ] = useState('');
  const [actionError, setActionError] = useState<string | null>(null);
  const { data, isLoading } = useQuery({
    queryKey: ['adminEvents', q],
    queryFn: async () => adminEvents((await getToken()) ?? '', q || undefined)
  });

  async function refresh() {
    await queryClient.invalidateQueries({ queryKey: ['adminEvents'] });
  }

  const events = data ?? [];

  return (
    <div className="space-y-3">
      <input
        value={q}
        onChange={(e) => setQ(e.target.value)}
        placeholder="Search by event name"
        className="input max-w-sm text-sm"
      />
      {actionError && <p className="text-sm text-red-600">{actionError}</p>}
      {isLoading ? (
        <p className="text-ink/50">Loading...</p>
      ) : (
        <div className="card overflow-x-auto p-2">
          <table className="w-full text-left text-sm">
            <thead className="text-xs uppercase text-ink/50">
              <tr><th className="py-2">Event</th><th>Type</th><th>Status</th><th>Media</th><th></th></tr>
            </thead>
            <tbody>
              {events.map((ev) => (
                <tr key={ev.id} className="border-t border-brand/10">
                  <td className="py-2 font-medium text-wine">{ev.name}</td>
                  <td>{ev.eventType}</td>
                  <td>{ev.status}</td>
                  <td>{ev.mediaCount}</td>
                  <td className="space-x-2 whitespace-nowrap py-2 text-right text-xs">
                    <button
                      onClick={async () => {
                        setActionError(null);
                        try {
                          await adminArchiveEvent((await getToken()) ?? '', ev.id);
                          await refresh();
                          toast({ title: 'Event archived', tone: 'success' });
                        } catch (err) {
                          const message = err instanceof Error ? err.message : 'Could not archive event';
                          setActionError(message);
                          toast({ title: 'Archive failed', message, tone: 'error' });
                        }
                      }}
                      className="rounded-full bg-blush px-2.5 py-1 text-wine hover:bg-brand/10"
                    >
                      Archive
                    </button>
                    <button
                      onClick={async () => {
                        const accepted = await confirm({
                          title: 'Remove this event?',
                          message: 'This action removes the event from standard views.',
                          confirmText: 'Remove event',
                          tone: 'danger'
                        });
                        if (!accepted) return;
                        setActionError(null);
                        try {
                          await adminRemoveEvent((await getToken()) ?? '', ev.id);
                          await refresh();
                          toast({ title: 'Event removed', tone: 'success' });
                        } catch (err) {
                          const message = err instanceof Error ? err.message : 'Could not remove event';
                          setActionError(message);
                          toast({ title: 'Remove failed', message, tone: 'error' });
                        }
                      }}
                      className="rounded bg-red-50 px-2 py-1 text-red-700 hover:bg-red-100"
                    >
                      Remove
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

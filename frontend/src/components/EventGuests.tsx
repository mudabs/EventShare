'use client';

import { useAuth } from '@clerk/nextjs';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { fetchEventMembers, removeEventMember } from '@/lib/api';
import type { MemberView } from '@/lib/types';

function statusBadge(status: MemberView['status']) {
  switch (status) {
    case 'ACTIVE':
      return 'bg-emerald-100 text-emerald-700';
    case 'LEFT':
      return 'bg-blush text-ink/60';
    case 'REMOVED':
      return 'bg-red-50 text-red-700';
  }
}

function relative(iso: string | null) {
  if (!iso) return '—';
  return new Date(iso).toLocaleDateString();
}

export function EventGuests({ eventId }: { eventId: string }) {
  const { getToken } = useAuth();
  const queryClient = useQueryClient();

  const { data, isLoading, isError } = useQuery({
    queryKey: ['eventMembers', eventId],
    queryFn: async () => fetchEventMembers((await getToken()) ?? '', eventId)
  });

  const remove = useMutation({
    mutationFn: async (membershipId: string) =>
      removeEventMember((await getToken()) ?? '', eventId, membershipId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['eventMembers', eventId] })
  });

  if (isLoading) return <div className="h-40 animate-pulse rounded-2xl bg-blush" />;
  if (isError) return <p className="py-8 text-center text-red-600">Could not load guests.</p>;

  const members = data ?? [];
  const active = members.filter((m) => m.status === 'ACTIVE');

  return (
    <div className="space-y-3">
      <div className="flex items-baseline justify-between">
        <h2 className="text-xl font-semibold">Guests</h2>
        <span className="text-sm text-ink/50">{active.length} active</span>
      </div>

      {members.length === 0 ? (
        <div className="card p-8 text-center text-sm text-ink/60">
          No one has joined yet. Share your code or QR to invite guests.
        </div>
      ) : (
        <ul className="card divide-y divide-brand/10">
          {members.map((m) => {
            const isOwner = m.role === 'HOST';
            const removable = !isOwner && m.status === 'ACTIVE';
            return (
              <li key={m.membershipId} className="flex items-center justify-between gap-3 px-4 py-3">
                <div className="min-w-0">
                  <div className="flex items-center gap-2">
                    <span className="truncate font-medium text-wine">{m.displayName || 'Guest'}</span>
                    {isOwner && (
                      <span className="rounded-full bg-brand/10 px-2 py-0.5 text-[10px] font-semibold text-brand">
                        Host
                      </span>
                    )}
                    <span className={`rounded-full px-2 py-0.5 text-[10px] font-semibold ${statusBadge(m.status)}`}>
                      {m.status.charAt(0) + m.status.slice(1).toLowerCase()}
                    </span>
                  </div>
                  <div className="mt-0.5 text-xs text-ink/50">
                    Joined {relative(m.joinedAt)} · Last active {relative(m.lastActivityAt)}
                  </div>
                </div>
                {removable && (
                  <button
                    onClick={() => {
                      if (window.confirm(`Remove ${m.displayName || 'this guest'} from the event?`)) {
                        remove.mutate(m.membershipId);
                      }
                    }}
                    disabled={remove.isPending}
                    className="shrink-0 rounded-full bg-red-50 px-3 py-1 text-xs font-medium text-red-700 transition-colors hover:bg-red-100 disabled:opacity-60"
                  >
                    Remove
                  </button>
                )}
              </li>
            );
          })}
        </ul>
      )}
      <p className="text-xs text-ink/40">
        Removing a guest revokes their access. Photos they already uploaded remain in the gallery
        (manage those under the Gallery tab).
      </p>
    </div>
  );
}

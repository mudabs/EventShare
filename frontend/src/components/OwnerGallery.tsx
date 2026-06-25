'use client';

import { useAuth } from '@clerk/nextjs';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { fetchOwnerGallery, moderateMedia, permanentDeleteMedia, updateEventSettings } from '@/lib/api';
import type { MediaItem, ModerationState } from '@/lib/types';

const STATES: ModerationState[] = ['VISIBLE', 'HIDDEN', 'ARCHIVED', 'DELETED'];

function actionsFor(state: ModerationState): { label: string; action: string; danger?: boolean }[] {
  switch (state) {
    case 'VISIBLE':
      return [{ label: 'Hide', action: 'HIDE' }, { label: 'Archive', action: 'ARCHIVE' }, { label: 'Delete', action: 'DELETE', danger: true }];
    case 'HIDDEN':
      return [{ label: 'Unhide', action: 'UNHIDE' }, { label: 'Archive', action: 'ARCHIVE' }, { label: 'Delete', action: 'DELETE', danger: true }];
    case 'ARCHIVED':
      return [{ label: 'Restore', action: 'RESTORE' }, { label: 'Delete', action: 'DELETE', danger: true }];
    case 'DELETED':
      return [{ label: 'Restore', action: 'RESTORE' }, { label: 'Purge', action: 'PERMANENT', danger: true }];
  }
}

function Tile({
  item, state, onAction, onSetCover
}: {
  item: MediaItem;
  state: ModerationState;
  onAction: (id: string, action: string) => void;
  onSetCover: (id: string) => void;
}) {
  return (
    <div className="overflow-hidden rounded-md border border-slate-200 bg-white">
      <div className="aspect-square bg-slate-100">
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img src={item.thumbnailUrl ?? item.originalUrl} alt={item.originalFilename ?? 'media'} loading="lazy" className="h-full w-full object-cover" />
      </div>
      <div className="flex flex-wrap gap-1 p-2">
        {actionsFor(state).map((a) => (
          <button
            key={a.action}
            onClick={() => onAction(item.id, a.action)}
            className={`rounded px-2 py-1 text-[11px] font-medium ${a.danger ? 'bg-red-50 text-red-700 hover:bg-red-100' : 'bg-slate-100 text-slate-700 hover:bg-slate-200'}`}
          >
            {a.label}
          </button>
        ))}
        {state === 'VISIBLE' && (
          <button
            onClick={() => onSetCover(item.id)}
            className="rounded bg-indigo-50 px-2 py-1 text-[11px] font-medium text-indigo-700 hover:bg-indigo-100"
          >
            Set cover
          </button>
        )}
      </div>
    </div>
  );
}

export function OwnerGallery({ eventId }: { eventId: string }) {
  const { getToken } = useAuth();
  const queryClient = useQueryClient();
  const [state, setState] = useState<ModerationState>('VISIBLE');
  const [notice, setNotice] = useState<string | null>(null);

  const { data, isLoading, isError } = useQuery({
    queryKey: ['ownerGallery', eventId, state],
    queryFn: async () => fetchOwnerGallery((await getToken()) ?? '', eventId, state)
  });

  async function handleAction(mediaId: string, action: string) {
    if (action === 'PERMANENT' && !window.confirm('Permanently delete this item? This cannot be undone.')) {
      return;
    }
    const token = (await getToken()) ?? '';
    if (action === 'PERMANENT') {
      await permanentDeleteMedia(token, eventId, mediaId);
    } else {
      await moderateMedia(token, eventId, mediaId, action);
    }
    queryClient.invalidateQueries({ queryKey: ['ownerGallery', eventId] });
  }

  async function handleSetCover(mediaId: string) {
    const token = (await getToken()) ?? '';
    await updateEventSettings(token, eventId, { coverMediaId: mediaId });
    await queryClient.invalidateQueries({ queryKey: ['event', eventId] });
    setNotice('Cover image updated. Guests will see it on the join page.');
    setTimeout(() => setNotice(null), 2500);
  }

  const items = data?.items ?? [];

  return (
    <div className="space-y-3">
      <div className="flex gap-2">
        {STATES.map((s) => (
          <button
            key={s}
            onClick={() => setState(s)}
            className={`rounded-md px-3 py-1 text-xs font-medium ${state === s ? 'bg-brand text-white' : 'bg-slate-100 text-slate-600 hover:bg-slate-200'}`}
          >
            {s.charAt(0) + s.slice(1).toLowerCase()}
          </button>
        ))}
      </div>

      {notice && <p className="rounded bg-green-50 px-3 py-2 text-sm text-green-700">{notice}</p>}
      {isLoading && <p className="py-8 text-center text-slate-500">Loading...</p>}
      {isError && <p className="py-8 text-center text-red-600">Could not load media.</p>}
      {!isLoading && items.length === 0 && (
        <p className="py-8 text-center text-slate-500">Nothing in {state.toLowerCase()}.</p>
      )}

      <div className="grid grid-cols-2 gap-2 sm:grid-cols-3 md:grid-cols-4">
        {items.map((item) => (
          <Tile key={item.id} item={item} state={state} onAction={handleAction} onSetCover={handleSetCover} />
        ))}
      </div>
    </div>
  );
}

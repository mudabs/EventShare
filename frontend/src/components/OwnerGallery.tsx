'use client';

import { useAuth } from '@clerk/nextjs';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useFeedback } from '@/components/feedback/AppFeedback';
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
    <div className="card overflow-hidden">
      <div className="relative aspect-square bg-blush">
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img src={item.thumbnailUrl ?? item.originalUrl} alt={item.originalFilename ?? 'media'} loading="lazy" className="h-full w-full object-cover" />
        {/* Open visitor reports. Several reports (or a child-safety report) hide the photo
            automatically; restoring it marks the reports as reviewed. */}
        {item.reportCount ? (
          <span
            className="absolute left-2 top-2 rounded-full bg-red-600 px-2 py-0.5 text-[11px] font-medium text-white shadow"
            title="Guests reported this photo. Review it, then hide or restore."
          >
            Reported{item.reportCount > 1 ? ` ×${item.reportCount}` : ''}
          </span>
        ) : null}
      </div>
      <div className="flex flex-wrap gap-1 p-2">
        {actionsFor(state).map((a) => (
          <button
            key={a.action}
            onClick={() => onAction(item.id, a.action)}
            className={`rounded-full px-2.5 py-1 text-[11px] font-medium ${a.danger ? 'bg-red-50 text-red-700 hover:bg-red-100' : 'bg-blush text-wine hover:bg-brand/10'}`}
          >
            {a.label}
          </button>
        ))}
        {state === 'VISIBLE' && (
          <button
            onClick={() => onSetCover(item.id)}
            className="rounded-full bg-brand/10 px-2.5 py-1 text-[11px] font-medium text-brand hover:bg-brand/20"
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
  const { confirm, toast } = useFeedback();
  const queryClient = useQueryClient();
  const [state, setState] = useState<ModerationState>('VISIBLE');
  const [actionError, setActionError] = useState<string | null>(null);

  const { data, isLoading, isError } = useQuery({
    queryKey: ['ownerGallery', eventId, state],
    queryFn: async () => fetchOwnerGallery((await getToken()) ?? '', eventId, state)
  });

  async function handleAction(mediaId: string, action: string) {
    if (action === 'DELETE') {
      const accepted = await confirm({
        title: 'Delete image from gallery?',
        message: 'The image will move to Deleted and can be restored later.',
        confirmText: 'Delete',
        tone: 'danger'
      });
      if (!accepted) return;
    }

    if (action === 'PERMANENT') {
      const accepted = await confirm({
        title: 'Permanently delete this image?',
        message: 'This cannot be undone and will remove stored files.',
        confirmText: 'Permanently delete',
        tone: 'danger'
      });
      if (!accepted) return;
    }
    setActionError(null);
    try {
      const token = (await getToken()) ?? '';
      if (action === 'PERMANENT') {
        await permanentDeleteMedia(token, eventId, mediaId);
        toast({ title: 'Image permanently deleted', tone: 'success' });
      } else {
        await moderateMedia(token, eventId, mediaId, action);
        toast({
          title: action === 'DELETE' ? 'Image deleted' : 'Image updated',
          message: action === 'DELETE' ? 'You can restore it from the Deleted tab.' : undefined,
          tone: 'success'
        });
      }
      queryClient.invalidateQueries({ queryKey: ['ownerGallery', eventId] });
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Could not update media';
      setActionError(message);
      toast({ title: 'Media action failed', message, tone: 'error' });
    }
  }

  async function handleSetCover(mediaId: string) {
    setActionError(null);
    try {
      const token = (await getToken()) ?? '';
      await updateEventSettings(token, eventId, { coverMediaId: mediaId });
      await queryClient.invalidateQueries({ queryKey: ['event', eventId] });
      toast({ title: 'Cover image updated', message: 'Guests will see this on the join page.', tone: 'success' });
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Could not set cover image';
      setActionError(message);
      toast({ title: 'Could not set cover image', message, tone: 'error' });
    }
  }

  const items = data?.items ?? [];

  return (
    <div className="space-y-3">
      <div className="flex gap-2">
        {STATES.map((s) => (
          <button
            key={s}
            onClick={() => setState(s)}
            className={`rounded-full px-3 py-1 text-xs font-medium transition-colors ${state === s ? 'bg-brand text-white' : 'bg-blush text-ink/60 hover:bg-brand/10'}`}
          >
            {s.charAt(0) + s.slice(1).toLowerCase()}
          </button>
        ))}
      </div>

      {actionError && <p className="rounded-xl bg-red-50 px-3 py-2 text-sm text-red-700">{actionError}</p>}
      {isLoading && <p className="py-8 text-center text-ink/50">Loading...</p>}
      {isError && <p className="py-8 text-center text-red-600">Could not load media.</p>}
      {!isLoading && items.length === 0 && (
        <p className="py-8 text-center text-ink/50">Nothing in {state.toLowerCase()}.</p>
      )}

      <div className="grid grid-cols-2 gap-2 sm:grid-cols-3 md:grid-cols-4">
        {items.map((item) => (
          <Tile key={item.id} item={item} state={state} onAction={handleAction} onSetCover={handleSetCover} />
        ))}
      </div>
    </div>
  );
}

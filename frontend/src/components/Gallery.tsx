'use client';

import { useInfiniteQuery } from '@tanstack/react-query';
import { useEffect, useMemo, useRef, useState } from 'react';
import { deleteOwnMedia, fetchGallery } from '@/lib/api';
import type { MediaItem } from '@/lib/types';
import { queryKeys } from '@/lib/queryKeys';
import { useGuestStore } from '@/store/guestStore';
import { useFeedback } from './feedback/AppFeedback';
import { MediaTile } from './MediaTile';
import { EmptyGallery } from './illustrations';

function formatBytes(bytes: number | null) {
  if (!bytes || bytes <= 0) return 'Unknown size';
  const units = ['B', 'KB', 'MB', 'GB'];
  let value = bytes;
  let idx = 0;
  while (value >= 1024 && idx < units.length - 1) {
    value /= 1024;
    idx += 1;
  }
  return `${value.toFixed(idx === 0 ? 0 : 1)} ${units[idx]}`;
}

function isSameName(a?: string | null, b?: string | null) {
  if (!a || !b) return false;
  return a.trim().toLowerCase() === b.trim().toLowerCase();
}

export function Gallery({ code }: { code: string }) {
  const { confirm, toast } = useFeedback();
  const identity = useGuestStore((s) => s.identities[code]);
  const [selected, setSelected] = useState<MediaItem | null>(null);
  const [showMeta, setShowMeta] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [selectionMode, setSelectionMode] = useState(false);
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const [batchDownloading, setBatchDownloading] = useState(false);
  const {
    data, fetchNextPage, hasNextPage, isFetchingNextPage, isLoading, isError, refetch
  } = useInfiniteQuery({
    queryKey: queryKeys.gallery(code),
    queryFn: ({ pageParam }) => fetchGallery(code, pageParam),
    initialPageParam: null as string | null,
    getNextPageParam: (lastPage) => lastPage.nextCursor,
    // Lightweight near-real-time: re-poll the first page periodically.
    refetchInterval: 15_000
  });

  const sentinel = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const el = sentinel.current;
    if (!el) {
      return;
    }
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries[0].isIntersecting && hasNextPage && !isFetchingNextPage) {
          fetchNextPage();
        }
      },
      { rootMargin: '400px' }
    );
    observer.observe(el);
    return () => observer.disconnect();
  }, [hasNextPage, isFetchingNextPage, fetchNextPage]);

  const items = data?.pages.flatMap((p) => p.items) ?? [];
  const selectedItems = useMemo(
    () => items.filter((item) => selectedIds.has(item.id)),
    [items, selectedIds]
  );
  const selectedIndex = useMemo(
    () => (selected ? items.findIndex((item) => item.id === selected.id) : -1),
    [items, selected]
  );
  const hasPrev = selectedIndex > 0;
  const hasNext = selectedIndex >= 0 && selectedIndex < items.length - 1;

  const canDeleteSelected = useMemo(() => {
    if (!selected || !identity?.displayName) return false;
    return isSameName(identity.displayName, selected.uploaderDisplayName);
  }, [identity?.displayName, selected]);

  function showPrevious() {
    if (!hasPrev) return;
    setSelected(items[selectedIndex - 1]);
    setShowMeta(false);
  }

  function showNext() {
    if (!hasNext) return;
    setSelected(items[selectedIndex + 1]);
    setShowMeta(false);
  }

  function triggerDownload(item: MediaItem) {
    const link = document.createElement('a');
    link.href = item.originalUrl;
    if (item.originalFilename) {
      link.download = item.originalFilename;
    }
    link.rel = 'noreferrer';
    link.style.display = 'none';
    document.body.appendChild(link);
    link.click();
    link.remove();
  }

  async function startDownloads(list: MediaItem[], label: string) {
    if (!list.length) return;
    setBatchDownloading(true);
    try {
      for (const item of list) {
        triggerDownload(item);
        await new Promise((resolve) => window.setTimeout(resolve, 120));
      }
      toast({ title: `Started ${list.length} downloads`, message: label, tone: 'success' });
    } finally {
      setBatchDownloading(false);
    }
  }

  async function batchDownloadVisible() {
    await startDownloads(items, 'Visible gallery items');
  }

  async function batchDownloadSelected() {
    await startDownloads(selectedItems, 'Selected gallery items');
  }

  async function downloadAllInEvent() {
    const accepted = await confirm({
      title: 'Download all media in this event?',
      message: 'This will fetch all gallery pages and start downloads for every item.',
      confirmText: 'Download all',
      tone: 'default'
    });
    if (!accepted) return;

    setBatchDownloading(true);
    try {
      const allItems: MediaItem[] = [];
      const seen = new Set<string>();
      let cursor: string | null = null;
      let loops = 0;

      do {
        const page = await fetchGallery(code, cursor, 100);
        for (const item of page.items) {
          if (!seen.has(item.id)) {
            seen.add(item.id);
            allItems.push(item);
          }
        }
        cursor = page.nextCursor;
        loops += 1;
        if (loops > 500) break;
        if (!page.hasMore) break;
      } while (cursor);

      for (const item of allItems) {
        triggerDownload(item);
        await new Promise((resolve) => window.setTimeout(resolve, 120));
      }

      toast({ title: `Started ${allItems.length} downloads`, message: 'All event media', tone: 'success' });
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Could not start full download';
      toast({ title: 'Download-all failed', message, tone: 'error' });
    } finally {
      setBatchDownloading(false);
    }
  }

  function toggleSelection(item: MediaItem) {
    setSelectedIds((curr) => {
      const next = new Set(curr);
      if (next.has(item.id)) {
        next.delete(item.id);
      } else {
        next.add(item.id);
      }
      return next;
    });
  }

  function clearSelection() {
    setSelectedIds(new Set());
  }

  async function handleDeleteSelected() {
    if (!selected || !identity) return;
    const accepted = await confirm({
      title: 'Delete this image?',
      message: 'This will remove it from the shared gallery.',
      confirmText: 'Delete image',
      tone: 'danger'
    });
    if (!accepted) return;

    setDeleting(true);
    try {
      await deleteOwnMedia(code, selected.id, {
        membershipId: identity.membershipId,
        displayName: identity.displayName
      });
      toast({ title: 'Image deleted', tone: 'success' });
      setSelected(null);
      setShowMeta(false);
      await refetch();
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Could not delete this image';
      toast({ title: 'Delete failed', message, tone: 'error' });
    } finally {
      setDeleting(false);
    }
  }

  if (isLoading) {
    return <p className="py-8 text-center text-ink/50">Loading gallery…</p>;
  }
  if (isError) {
    return <p className="py-8 text-center text-red-600">Could not load the gallery.</p>;
  }
  if (items.length === 0) {
    return (
      <div className="flex flex-col items-center py-12 text-center">
        <EmptyGallery className="w-40" />
        <p className="mt-4 font-serif text-xl text-wine">No photos yet</p>
        <p className="mt-1 text-sm text-ink/60">Be the first to share a moment.</p>
      </div>
    );
  }

  return (
    <div>
      <div className="mb-3 flex flex-wrap items-center justify-end gap-2">
        <button
          type="button"
          onClick={() => {
            setSelectionMode((v) => !v);
            if (selectionMode) clearSelection();
          }}
          className={`rounded-full px-4 py-2 text-sm font-medium ${selectionMode ? 'bg-brand text-white' : 'bg-blush text-wine hover:bg-brand/10'}`}
        >
          {selectionMode ? 'Done selecting' : 'Select images'}
        </button>

        {selectionMode && (
          <>
            <button
              type="button"
              onClick={clearSelection}
              className="rounded-full bg-blush px-4 py-2 text-sm font-medium text-wine hover:bg-brand/10"
            >
              Clear ({selectedItems.length})
            </button>
            <button
              type="button"
              onClick={batchDownloadSelected}
              disabled={batchDownloading || selectedItems.length === 0}
              className="rounded-full bg-brand px-4 py-2 text-sm font-medium text-white hover:bg-brand/90 disabled:opacity-60"
            >
              {batchDownloading ? 'Preparing downloads…' : `Download selected (${selectedItems.length})`}
            </button>
          </>
        )}

        <button
          type="button"
          onClick={batchDownloadVisible}
          disabled={batchDownloading}
          className="rounded-full bg-wine px-4 py-2 text-sm font-medium text-white hover:bg-wine/90 disabled:opacity-60"
        >
          {batchDownloading ? 'Preparing downloads…' : `Batch download (${items.length})`}
        </button>

        <button
          type="button"
          onClick={downloadAllInEvent}
          disabled={batchDownloading}
          className="rounded-full bg-gold px-4 py-2 text-sm font-medium text-ink hover:bg-gold/90 disabled:opacity-60"
        >
          {batchDownloading ? 'Preparing downloads…' : 'Download all in event'}
        </button>
      </div>

      <div className="grid grid-cols-2 gap-2 sm:grid-cols-3 md:grid-cols-4">
        {items.map((item) => (
          <MediaTile
            key={item.id}
            item={item}
            selectionMode={selectionMode}
            selected={selectedIds.has(item.id)}
            onToggleSelect={toggleSelection}
            onOpen={(chosen) => { setSelected(chosen); setShowMeta(false); }}
          />
        ))}
      </div>
      <div ref={sentinel} className="h-10" />
      {isFetchingNextPage && (
        <p className="py-4 text-center text-sm text-ink/50">Loading more…</p>
      )}

      {selected && (
        <div className="fixed inset-0 z-[9997] flex items-center justify-center bg-black/75 p-4">
          <div className="relative w-full max-w-5xl rounded-2xl bg-black p-3 shadow-2xl">
            <button
              type="button"
              onClick={showPrevious}
              disabled={!hasPrev}
              aria-label="Previous image"
              className="absolute left-3 top-1/2 z-20 -translate-y-1/2 rounded-full bg-white/90 p-2 text-black hover:bg-white disabled:opacity-40"
            >
              <svg viewBox="0 0 24 24" className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
                <path d="M15 18l-6-6 6-6" />
              </svg>
            </button>

            <button
              type="button"
              onClick={showNext}
              disabled={!hasNext}
              aria-label="Next image"
              className="absolute right-14 top-1/2 z-20 -translate-y-1/2 rounded-full bg-white/90 p-2 text-black hover:bg-white disabled:opacity-40"
            >
              <svg viewBox="0 0 24 24" className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
                <path d="M9 18l6-6-6-6" />
              </svg>
            </button>

            <button
              type="button"
              onClick={() => { setSelected(null); setShowMeta(false); }}
              aria-label="Close image dialog"
              className="absolute right-3 top-3 z-20 rounded-full bg-white/90 p-2 text-black hover:bg-white"
            >
              <svg viewBox="0 0 24 24" className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
                <path d="M6 6l12 12M18 6L6 18" />
              </svg>
            </button>

            <div className="flex max-h-[80vh] items-center justify-center overflow-hidden rounded-xl bg-black">
              {selected.mediaType === 'VIDEO' ? (
                <video
                  src={selected.originalUrl}
                  controls
                  className="max-h-[80vh] w-auto max-w-full"
                />
              ) : (
                // eslint-disable-next-line @next/next/no-img-element
                <img
                  src={selected.originalUrl}
                  alt={selected.originalFilename ?? 'Event media'}
                  className="max-h-[80vh] w-auto max-w-full object-contain"
                />
              )}
            </div>

            <div className="mt-3 flex flex-wrap items-center gap-2 text-sm">
              <a
                href={selected.originalUrl}
                download={selected.originalFilename ?? undefined}
                className="rounded-full bg-brand px-4 py-2 font-medium text-white hover:bg-brand/90"
              >
                Download
              </a>

              {canDeleteSelected && (
                <button
                  type="button"
                  onClick={handleDeleteSelected}
                  disabled={deleting}
                  className="rounded-full bg-red-600 px-4 py-2 font-medium text-white hover:bg-red-700 disabled:opacity-60"
                >
                  {deleting ? 'Deleting…' : 'Delete'}
                </button>
              )}

              <button
                type="button"
                onClick={() => setShowMeta((v) => !v)}
                className="ml-auto rounded-full bg-white/90 p-2 text-black hover:bg-white"
                aria-label="Show image metadata"
              >
                <svg viewBox="0 0 24 24" className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
                  <path d="M1 12s4-7 11-7 11 7 11 7-4 7-11 7S1 12 1 12z" />
                  <circle cx="12" cy="12" r="3" />
                </svg>
              </button>
            </div>

            <p className="mt-2 text-center text-xs text-white/80">
              {selectedIndex + 1} of {items.length}
            </p>

            {showMeta && (
              <div className="absolute bottom-16 right-3 z-20 w-72 rounded-xl bg-white p-3 text-sm shadow-xl">
                <p className="font-semibold text-wine">Image details</p>
                <p className="mt-2 text-ink/80"><span className="font-medium">Captured by:</span> {selected.uploaderDisplayName ?? 'Anonymous guest'}</p>
                <p className="mt-1 text-ink/80"><span className="font-medium">Timestamp:</span> {new Date(selected.createdAt).toLocaleString()}</p>
                <p className="mt-1 text-ink/80"><span className="font-medium">Size:</span> {formatBytes(selected.sizeBytes)}</p>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}

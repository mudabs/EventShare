'use client';

import { useInfiniteQuery } from '@tanstack/react-query';
import { useEffect, useMemo, useRef, useState } from 'react';
import { deleteOwnMedia, fetchGallery } from '@/lib/api';
import { downloadFile, downloadIndividually, downloadZip, zipFileName, ZipTooLargeError } from '@/lib/download';
import type { MediaItem } from '@/lib/types';
import { queryKeys } from '@/lib/queryKeys';
import { useGuestStore } from '@/store/guestStore';
import { useFeedback } from './feedback/AppFeedback';
import { MediaTile } from './MediaTile';
import { ReportDialog } from './ReportDialog';
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

interface GalleryProps {
  code: string;
  eventName?: string;
  /** Host setting "Allow guest downloads". When false, no download controls are shown. */
  allowDownloads?: boolean;
  /** Host's plan includes ZIP downloads (paid plans). Free plan: no "Download all", no ZIP. */
  zipDownloads?: boolean;
}

export function Gallery({ code, eventName, allowDownloads = true, zipDownloads = false }: GalleryProps) {
  const { confirm, toast } = useFeedback();
  const identity = useGuestStore((s) => s.identities[code]);
  const [selected, setSelected] = useState<MediaItem | null>(null);
  const [showMeta, setShowMeta] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [reporting, setReporting] = useState(false);
  const [selectionMode, setSelectionMode] = useState(false);
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  // Progress label while a download job runs, e.g. "Zipping 3 of 17". Null when idle.
  const [downloadStatus, setDownloadStatus] = useState<string | null>(null);
  const busy = downloadStatus !== null;
  const {
    data, fetchNextPage, hasNextPage, isFetchingNextPage, isLoading, isError, refetch
  } = useInfiniteQuery({
    // membershipId is part of the key so ownership flags refresh when the guest joins.
    queryKey: [...queryKeys.gallery(code), identity?.membershipId ?? null],
    queryFn: ({ pageParam }) => fetchGallery(code, pageParam, 30, identity?.membershipId),
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
  // 1-based position shown in the viewer ("1 of 17"), never 0.
  const position = selectedIndex >= 0 ? selectedIndex + 1 : null;
  const hasPrev = selectedIndex > 0;
  const hasNext = selectedIndex >= 0 && selectedIndex < items.length - 1;

  // Ownership is decided by the API from the guest's membership id (change C2).
  // Matching on display name was removed because names are public and not unique.
  const canDeleteSelected = useMemo(
    () => Boolean(selected?.ownedByRequester && identity?.membershipId),
    [identity?.membershipId, selected]
  );

  // If the open item leaves the gallery (deleted, hidden by the host), close the viewer
  // instead of showing a stale photo with no valid position.
  useEffect(() => {
    if (selected && !isLoading && selectedIndex === -1) {
      setSelected(null);
      setShowMeta(false);
    }
  }, [selected, selectedIndex, isLoading]);

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

  /** Fetches every gallery page so "Download all" covers items not scrolled into view yet. */
  async function fetchAllItems(): Promise<MediaItem[]> {
    const all: MediaItem[] = [];
    const seen = new Set<string>();
    let cursor: string | null = null;
    for (let loops = 0; loops < 500; loops += 1) {
      const page = await fetchGallery(code, cursor, 100, identity?.membershipId);
      for (const item of page.items) {
        if (!seen.has(item.id)) {
          seen.add(item.id);
          all.push(item);
        }
      }
      if (!page.hasMore || !page.nextCursor) break;
      cursor = page.nextCursor;
    }
    return all;
  }

  /** Paid plans: one ZIP. Free plan: the files one by one. */
  async function runDownload(source: MediaItem[] | (() => Promise<MediaItem[]>), zipSuffix: string) {
    try {
      if (zipDownloads) {
        setDownloadStatus('Preparing ZIP');
        const saved = await downloadZip(source, zipFileName(eventName, zipSuffix), (done, total) =>
          setDownloadStatus(`Zipping ${done} of ${total}`)
        );
        if (saved) toast({ title: 'ZIP saved', tone: 'success' });
      } else {
        const list = typeof source === 'function' ? await source() : source;
        if (!list.length) return;
        setDownloadStatus(`Downloading 0 of ${list.length}`);
        await downloadIndividually(list, (done) => setDownloadStatus(`Downloading ${done} of ${list.length}`));
        toast({ title: `Started ${list.length} download${list.length === 1 ? '' : 's'}`, tone: 'success' });
      }
    } catch (err) {
      const message = err instanceof ZipTooLargeError || err instanceof Error ? err.message : 'Download failed';
      toast({ title: 'Download failed', message, tone: 'error' });
    } finally {
      setDownloadStatus(null);
    }
  }

  async function downloadSelected() {
    await runDownload(selectedItems, 'selected');
  }

  async function downloadAllInEvent() {
    const accepted = await confirm({
      title: 'Download everything as a ZIP?',
      message: 'All photos and videos in this event will be saved into one ZIP file.',
      confirmText: 'Download ZIP',
      tone: 'default'
    });
    if (!accepted) return;
    // The loader runs after the save dialog opens (see downloadZip).
    await runDownload(fetchAllItems, 'all');
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
    if (!selected || !identity?.membershipId) return;
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
      {allowDownloads && (
        <div className="mb-3 flex flex-wrap items-center justify-end gap-2">
          <button
            type="button"
            onClick={() => {
              setSelectionMode((v) => !v);
              if (selectionMode) clearSelection();
            }}
            className={`rounded-full px-4 py-2 text-sm font-medium ${selectionMode ? 'bg-brand text-white' : 'bg-blush text-wine hover:bg-brand/10'}`}
          >
            {selectionMode ? 'Done selecting' : 'Select'}
          </button>

          {/* Selection actions appear only once something is selected, to keep the bar uncluttered. */}
          {selectionMode && selectedItems.length > 0 && (
            <>
              <button
                type="button"
                onClick={clearSelection}
                disabled={busy}
                className="rounded-full bg-blush px-4 py-2 text-sm font-medium text-wine hover:bg-brand/10 disabled:opacity-60"
              >
                Clear
              </button>
              <button
                type="button"
                onClick={downloadSelected}
                disabled={busy}
                className="rounded-full bg-brand px-4 py-2 text-sm font-medium text-white hover:bg-brand/90 disabled:opacity-60"
              >
                {busy ? downloadStatus : zipDownloads ? `Download ZIP (${selectedItems.length})` : `Download (${selectedItems.length})`}
              </button>
            </>
          )}

          {/* "Download all" is a paid-plan feature (Basic, Wedding Pro, Lifetime). */}
          {zipDownloads && !selectionMode && (
            <button
              type="button"
              onClick={downloadAllInEvent}
              disabled={busy}
              className="rounded-full bg-wine px-4 py-2 text-sm font-medium text-white hover:bg-wine/90 disabled:opacity-60"
            >
              {busy ? downloadStatus : 'Download all (ZIP)'}
            </button>
          )}
        </div>
      )}

      <div className="grid grid-cols-2 gap-2 sm:grid-cols-3 md:grid-cols-4">
        {items.map((item) => (
          <MediaTile
            key={item.id}
            item={item}
            selectionMode={allowDownloads && selectionMode}
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
              {allowDownloads && (
                <button
                  type="button"
                  onClick={() => downloadFile(selected)}
                  className="rounded-full bg-brand px-4 py-2 font-medium text-white hover:bg-brand/90"
                >
                  Download
                </button>
              )}

              {!selected.ownedByRequester && (
                <button
                  type="button"
                  onClick={() => setReporting(true)}
                  className="rounded-full bg-white/15 px-4 py-2 font-medium text-white hover:bg-white/25"
                >
                  Report
                </button>
              )}

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
              {position !== null ? `${position} of ${items.length}` : null}
            </p>

            {reporting && (
              <ReportDialog
                code={code}
                mediaId={selected.id}
                onClose={() => setReporting(false)}
                onReported={async () => {
                  setReporting(false);
                  toast({ title: 'Report sent', message: 'Thank you. The host has been notified.', tone: 'success' });
                  // A report can hide the photo (child safety, or several reports); refresh the gallery.
                  await refetch();
                }}
              />
            )}

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

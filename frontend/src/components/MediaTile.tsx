import type { MediaItem } from '@/lib/types';

export function MediaTile({ item }: { item: MediaItem }) {
  const previewSrc = item.thumbnailUrl ?? item.originalUrl;
  const processing = item.status !== 'PROCESSED';

  return (
    <a
      href={item.originalUrl}
      target="_blank"
      rel="noreferrer"
      className="group relative block aspect-square overflow-hidden rounded-xl bg-blush ring-1 ring-brand/10"
      title={item.originalFilename ?? 'media'}
    >
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img
        src={previewSrc}
        alt={item.originalFilename ?? 'Event media'}
        loading="lazy"
        className="h-full w-full object-cover transition duration-300 group-hover:scale-105"
      />
      {item.mediaType === 'VIDEO' && (
        <span className="absolute inset-0 flex items-center justify-center">
          <span className="flex h-11 w-11 items-center justify-center rounded-full bg-black/45 backdrop-blur-sm">
            <svg viewBox="0 0 24 24" fill="currentColor" className="h-5 w-5 text-white" aria-hidden="true">
              <path d="M8 5.14v13.72a1 1 0 0 0 1.54.84l10.29-6.86a1 1 0 0 0 0-1.68L9.54 4.3A1 1 0 0 0 8 5.14z" />
            </svg>
          </span>
        </span>
      )}
      {processing && (
        <span className="absolute bottom-1 left-1 rounded bg-black/60 px-1.5 py-0.5 text-[10px] text-white">
          processing…
        </span>
      )}
      {item.uploaderDisplayName && (
        <span className="absolute bottom-0 w-full truncate bg-gradient-to-t from-black/60 to-transparent px-2 py-1 text-[11px] text-white opacity-0 transition group-hover:opacity-100">
          {item.uploaderDisplayName}
        </span>
      )}
    </a>
  );
}

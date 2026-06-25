'use client';

import { useInfiniteQuery } from '@tanstack/react-query';
import { useEffect, useRef } from 'react';
import { fetchGallery } from '@/lib/api';
import { queryKeys } from '@/lib/queryKeys';
import { MediaTile } from './MediaTile';

export function Gallery({ code }: { code: string }) {
  const {
    data, fetchNextPage, hasNextPage, isFetchingNextPage, isLoading, isError
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

  if (isLoading) {
    return <p className="py-8 text-center text-slate-500">Loading gallery…</p>;
  }
  if (isError) {
    return <p className="py-8 text-center text-red-600">Could not load the gallery.</p>;
  }
  if (items.length === 0) {
    return <p className="py-8 text-center text-slate-500">No photos yet. Be the first to share!</p>;
  }

  return (
    <div>
      <div className="grid grid-cols-2 gap-2 sm:grid-cols-3 md:grid-cols-4">
        {items.map((item) => (
          <MediaTile key={item.id} item={item} />
        ))}
      </div>
      <div ref={sentinel} className="h-10" />
      {isFetchingNextPage && (
        <p className="py-4 text-center text-sm text-slate-500">Loading more…</p>
      )}
    </div>
  );
}

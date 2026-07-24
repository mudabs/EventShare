'use client';

import { useAuth } from '@clerk/nextjs';
import { useQuery } from '@tanstack/react-query';
import { fetchOwnerDashboard } from '@/lib/api';
import type { DayCount } from '@/lib/types';

function formatBytes(bytes: number): string {
  if (bytes <= 0) return '0 B';
  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  const i = Math.floor(Math.log(bytes) / Math.log(1024));
  return `${(bytes / Math.pow(1024, i)).toFixed(1)} ${units[i]}`;
}

function Stat({ label, value }: { label: string; value: string | number }) {
  return (
    <div className="card p-4">
      <div className="font-serif text-2xl font-semibold text-brand">{value}</div>
      <div className="text-xs text-ink/50">{label}</div>
    </div>
  );
}

function ActivityChart({ data }: { data: DayCount[] }) {
  const max = Math.max(1, ...data.map((d) => d.count));
  const total = data.reduce((sum, d) => sum + d.count, 0);

  return (
    <div className="card p-4">
      <div className="mb-3 flex items-baseline justify-between">
        <span className="text-sm font-medium text-wine/80">Upload activity (14 days)</span>
        <span className="text-xs text-ink/50">{total} total</span>
      </div>
      {total === 0 ? (
        <p className="py-8 text-center text-sm text-ink/50">No uploads in the last 14 days.</p>
      ) : (
        <div className="flex h-40 items-stretch gap-1">
          {data.map((d, i) => (
            <div key={d.date} className="flex flex-1 flex-col items-center gap-1" title={`${d.date}: ${d.count}`}>
              <div className="flex w-full flex-1 items-end">
                <div
                  className="w-full rounded-t bg-brand transition-all"
                  style={{ height: `${(d.count / max) * 100}%`, minHeight: d.count > 0 ? '3px' : '0' }}
                />
              </div>
              <span className="text-[9px] text-ink/40">{i % 2 === 0 ? d.date.slice(8) : ''}</span>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

export function OwnerDashboard({ eventId }: { eventId: string }) {
  const { getToken } = useAuth();
  const { data, isLoading, isError } = useQuery({
    queryKey: ['ownerDashboard', eventId],
    queryFn: async () => fetchOwnerDashboard((await getToken()) ?? '', eventId)
  });

  if (isLoading) {
    return <div className="h-48 animate-pulse rounded-2xl bg-blush" />;
  }
  if (isError || !data) {
    return <p className="text-red-600">Could not load analytics for this event.</p>;
  }

  return (
    <div className="space-y-4">
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        <Stat label="Photos" value={data.totalPhotos} />
        <Stat label="Videos" value={data.totalVideos} />
        <Stat label="Uploads today" value={data.uploadsToday} />
        <Stat label="Storage used" value={formatBytes(data.storageUsedBytes)} />
        <Stat label="Guests" value={data.totalGuests} />
        <Stat label="Unique visitors" value={data.uniqueVisitors} />
        <Stat label="Active (7d)" value={data.activeGuests} />
        <Stat label="Created" value={new Date(data.createdAt).toLocaleDateString()} />
      </div>
      <ActivityChart data={data.uploadActivity} />
      {data.expiresAt && (
        <p className="text-xs text-ink/50">
          Expires {new Date(data.expiresAt).toLocaleDateString()}
        </p>
      )}
    </div>
  );
}

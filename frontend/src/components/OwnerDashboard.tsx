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
    <div className="rounded-lg border border-slate-200 bg-white p-4">
      <div className="text-2xl font-bold text-slate-900">{value}</div>
      <div className="text-xs text-slate-500">{label}</div>
    </div>
  );
}

function ActivityChart({ data }: { data: DayCount[] }) {
  const max = Math.max(1, ...data.map((d) => d.count));
  return (
    <div className="rounded-lg border border-slate-200 bg-white p-4">
      <div className="mb-2 text-sm font-medium text-slate-700">Upload activity (14 days)</div>
      <div className="flex h-32 items-end gap-1">
        {data.map((d) => (
          <div key={d.date} className="flex flex-1 flex-col items-center justify-end" title={`${d.date}: ${d.count}`}>
            <div
              className="w-full rounded-t bg-brand"
              style={{ height: `${(d.count / max) * 100}%`, minHeight: d.count > 0 ? '4px' : '0' }}
            />
          </div>
        ))}
      </div>
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
    return <div className="h-48 animate-pulse rounded-lg bg-slate-100" />;
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
        <p className="text-xs text-slate-500">
          Expires {new Date(data.expiresAt).toLocaleDateString()}
        </p>
      )}
    </div>
  );
}

'use client';

import { useAuth } from '@clerk/nextjs';
import { useQuery } from '@tanstack/react-query';
import { adminStats } from '@/lib/api';
import { formatBytes } from '@/lib/format';

function Stat({ label, value }: { label: string; value: string | number }) {
  return (
    <div className="rounded-lg border border-slate-200 bg-white p-4">
      <div className="text-2xl font-bold">{value}</div>
      <div className="text-xs text-slate-500">{label}</div>
    </div>
  );
}

export function AdminAnalytics() {
  const { getToken } = useAuth();
  const { data } = useQuery({ queryKey: ['adminStats'], queryFn: async () => adminStats((await getToken()) ?? '') });
  if (!data) return <div className="h-32 animate-pulse rounded-lg bg-slate-100" />;

  const max = Math.max(1, ...data.monthlyGrowth.map((m) => m.count));

  return (
    <div className="space-y-4">
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        <Stat label="Users" value={data.totalUsers} />
        <Stat label="Events" value={data.totalEvents} />
        <Stat label="Uploads" value={data.totalUploads} />
        <Stat label="Storage" value={formatBytes(data.totalStorageBytes)} />
      </div>
      <div className="rounded-lg border border-slate-200 bg-white p-4">
        <div className="mb-2 text-sm font-medium text-slate-700">New users (6 months)</div>
        <div className="flex h-32 items-end gap-2">
          {data.monthlyGrowth.map((m) => (
            <div key={m.month} className="flex flex-1 flex-col items-center justify-end" title={`${m.month}: ${m.count}`}>
              <div className="w-full rounded-t bg-brand" style={{ height: `${(m.count / max) * 100}%`, minHeight: m.count > 0 ? '4px' : '0' }} />
              <span className="mt-1 text-[10px] text-slate-400">{m.month.slice(5)}</span>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

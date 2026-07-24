'use client';

import { useAuth } from '@clerk/nextjs';
import { useQuery } from '@tanstack/react-query';
import { adminStats } from '@/lib/api';
import { formatBytes } from '@/lib/format';

function Stat({ label, value }: { label: string; value: string | number }) {
  return (
    <div className="card p-4">
      <div className="font-serif text-2xl font-semibold text-brand">{value}</div>
      <div className="text-xs text-ink/50">{label}</div>
    </div>
  );
}

export function AdminAnalytics() {
  const { getToken } = useAuth();
  const { data } = useQuery({ queryKey: ['adminStats'], queryFn: async () => adminStats((await getToken()) ?? '') });
  if (!data) return <div className="h-32 animate-pulse rounded-2xl bg-blush" />;

  const max = Math.max(1, ...data.monthlyGrowth.map((m) => m.count));

  return (
    <div className="space-y-4">
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        <Stat label="Users" value={data.totalUsers} />
        <Stat label="Events" value={data.totalEvents} />
        <Stat label="Uploads" value={data.totalUploads} />
        <Stat label="Storage" value={formatBytes(data.totalStorageBytes)} />
      </div>
      <div className="card p-4">
        <div className="mb-2 text-sm font-medium text-wine/80">New users (6 months)</div>
        <div className="flex h-40 items-stretch gap-2">
          {data.monthlyGrowth.map((m) => (
            <div key={m.month} className="flex flex-1 flex-col items-center gap-1" title={`${m.month}: ${m.count}`}>
              <div className="flex w-full flex-1 items-end">
                <div className="w-full rounded-t bg-brand transition-all" style={{ height: `${(m.count / max) * 100}%`, minHeight: m.count > 0 ? '3px' : '0' }} />
              </div>
              <span className="text-[10px] text-ink/40">{m.month.slice(5)}</span>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

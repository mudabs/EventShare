'use client';

import { useAuth } from '@clerk/nextjs';
import { useQuery } from '@tanstack/react-query';
import { adminStats } from '@/lib/api';
import { formatBytes } from '@/lib/format';
import { BarChart } from './BarChart';

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

  const growth = data.monthlyGrowth;
  const newInPeriod = growth.reduce((sum, m) => sum + m.count, 0);

  return (
    <div className="space-y-4">
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        <Stat label="Users" value={data.totalUsers} />
        <Stat label="Events" value={data.totalEvents} />
        <Stat label="Uploads" value={data.totalUploads} />
        <Stat label="Storage" value={formatBytes(data.totalStorageBytes)} />
      </div>
      <div className="card p-4">
        <div className="mb-3 flex items-baseline justify-between gap-2">
          <span className="text-sm font-medium text-wine/80">New users per month</span>
          <span className="text-xs text-ink/50">
            {newInPeriod} new in {growth.length} months · {data.totalUsers} total
          </span>
        </div>
        <BarChart
          ariaLabel="New users per month"
          showZeroValues
          data={growth.map((m, i) => ({
            key: m.month,
            label: monthLabel(m.month, i === 0),
            value: m.count,
            title: `${monthLabel(m.month, true)}: ${m.count} new user${m.count === 1 ? '' : 's'}`
          }))}
        />
      </div>
    </div>
  );
}

/**
 * "2026-05" -> "May", or "May 2026" for the first bar and every January so the year is
 * always visible. Built in UTC so the month never shifts with the viewer's time zone.
 */
function monthLabel(yearMonth: string, withYear: boolean): string {
  const [y, m] = yearMonth.split('-').map(Number);
  if (!y || !m) return yearMonth;
  const date = new Date(Date.UTC(y, m - 1, 1));
  const month = date.toLocaleString('en-US', { month: 'short', timeZone: 'UTC' });
  return withYear || m === 1 ? `${month} ${y}` : month;
}

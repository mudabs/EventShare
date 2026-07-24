'use client';

import { useAuth } from '@clerk/nextjs';
import { useQuery } from '@tanstack/react-query';
import { adminPerformance } from '@/lib/api';
import { formatBytes } from '@/lib/format';

function uptime(seconds: number) {
  const s = Math.floor(seconds);
  const d = Math.floor(s / 86400);
  const h = Math.floor((s % 86400) / 3600);
  const m = Math.floor((s % 3600) / 60);
  if (d > 0) return `${d}d ${h}h`;
  if (h > 0) return `${h}h ${m}m`;
  return `${m}m`;
}

function Metric({ label, value, sub, tone }: { label: string; value: string; sub?: string; tone?: 'good' | 'warn' | 'bad' }) {
  const valueColor = tone === 'bad' ? 'text-red-600' : tone === 'warn' ? 'text-amber-600' : 'text-brand';
  return (
    <div className="card p-4">
      <div className={`font-serif text-2xl font-semibold ${valueColor}`}>{value}</div>
      <div className="text-xs uppercase tracking-wide text-ink/50">{label}</div>
      {sub && <div className="mt-0.5 text-xs text-ink/40">{sub}</div>}
    </div>
  );
}

function Bar({ pct, tone }: { pct: number; tone: 'good' | 'warn' | 'bad' }) {
  const color = tone === 'bad' ? 'bg-red-500' : tone === 'warn' ? 'bg-amber-500' : 'bg-brand';
  return (
    <div className="mt-2 h-1.5 w-full overflow-hidden rounded-full bg-blush">
      <div className={`h-full rounded-full ${color}`} style={{ width: `${Math.min(100, Math.max(2, pct))}%` }} />
    </div>
  );
}

export function AdminPerformance() {
  const { getToken } = useAuth();
  const { data, isLoading, isError } = useQuery({
    queryKey: ['adminPerformance'],
    queryFn: async () => adminPerformance((await getToken()) ?? ''),
    refetchInterval: 10_000
  });

  if (isLoading) return <div className="h-40 animate-pulse rounded-2xl bg-blush" />;
  if (isError || !data) return <p className="py-8 text-center text-red-600">Could not load performance metrics.</p>;

  const heapPct = data.heapMaxBytes > 0 ? (data.heapUsedBytes / data.heapMaxBytes) * 100 : 0;
  const heapTone = heapPct > 90 ? 'bad' : heapPct > 75 ? 'warn' : 'good';
  const cpuPct = data.cpuUsage * 100;
  const cpuTone = cpuPct > 85 ? 'bad' : cpuPct > 60 ? 'warn' : 'good';
  const errorRate = data.totalRequests > 0 ? (data.serverErrors / data.totalRequests) * 100 : 0;
  const errTone = errorRate > 5 ? 'bad' : errorRate > 1 ? 'warn' : 'good';
  const latTone = data.avgLatencyMs > 800 ? 'bad' : data.avgLatencyMs > 300 ? 'warn' : 'good';
  const dbPct = data.dbMax > 0 ? (data.dbActive / data.dbMax) * 100 : 0;
  const dbTone = dbPct > 90 ? 'bad' : dbPct > 70 ? 'warn' : 'good';

  return (
    <div className="space-y-4">
      <div className="flex items-baseline justify-between">
        <h2 className="text-xl font-semibold">Performance</h2>
        <span className="flex items-center gap-1.5 text-xs text-ink/50">
          <span className="h-2 w-2 animate-pulse rounded-full bg-emerald-500" /> live · refreshes 10s
        </span>
      </div>

      <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        <Metric label="Uptime" value={uptime(data.uptimeSeconds)} />
        <Metric label="CPU" value={`${cpuPct.toFixed(0)}%`} tone={cpuTone} />
        <Metric label="Avg latency" value={`${data.avgLatencyMs.toFixed(0)} ms`} tone={latTone} />
        <Metric label="Error rate" value={`${errorRate.toFixed(1)}%`} sub={`${data.serverErrors} of ${data.totalRequests}`} tone={errTone} />
        <Metric label="Requests" value={data.totalRequests.toLocaleString()} />
        <Metric label="Threads" value={String(data.liveThreads)} />
        <Metric label="Media processed" value={data.mediaProcessed.toLocaleString()} sub={data.mediaFailed > 0 ? `${data.mediaFailed} failed` : undefined} tone={data.mediaFailed > 0 ? 'warn' : 'good'} />
        <Metric label="DB pool" value={`${data.dbActive.toFixed(0)}/${data.dbMax.toFixed(0)}`} sub={`${data.dbIdle.toFixed(0)} idle`} tone={dbTone} />
      </div>

      <div className="grid gap-3 sm:grid-cols-2">
        <div className="card p-4">
          <div className="flex items-baseline justify-between text-sm">
            <span className="font-medium text-wine/80">Heap memory</span>
            <span className="text-ink/50">{formatBytes(data.heapUsedBytes)} / {formatBytes(data.heapMaxBytes)}</span>
          </div>
          <Bar pct={heapPct} tone={heapTone} />
        </div>
        <div className="card p-4">
          <div className="flex items-baseline justify-between text-sm">
            <span className="font-medium text-wine/80">DB connections</span>
            <span className="text-ink/50">{data.dbActive.toFixed(0)} active / {data.dbMax.toFixed(0)} max</span>
          </div>
          <Bar pct={dbPct} tone={dbTone} />
        </div>
      </div>

      <div className="card overflow-x-auto p-2">
        <table className="w-full text-left text-sm">
          <thead className="text-xs uppercase text-ink/50">
            <tr><th className="py-2 pl-2">Endpoint</th><th>Requests</th><th>Avg</th><th>Errors</th></tr>
          </thead>
          <tbody>
            {data.topEndpoints.length === 0 ? (
              <tr><td colSpan={4} className="py-4 text-center text-ink/50">No requests recorded yet.</td></tr>
            ) : (
              data.topEndpoints.map((e) => (
                <tr key={e.uri} className="border-t border-brand/10">
                  <td className="py-2 pl-2 font-mono text-xs text-wine">{e.uri}</td>
                  <td>{e.count.toLocaleString()}</td>
                  <td>{e.avgMs.toFixed(0)} ms</td>
                  <td className={e.errors > 0 ? 'text-red-600' : 'text-ink/40'}>{e.errors}</td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      <p className="text-xs text-ink/40">
        Read live from the app's metrics registry. For historical charts and alerting, enable the
        Grafana/Prometheus stack (the <code className="rounded bg-blush px-1">monitoring</code> compose profile).
      </p>
    </div>
  );
}

'use client';

import Link from 'next/link';
import { useQuery } from '@tanstack/react-query';
import { fetchDemoInfo } from '@/lib/api';

/**
 * "Try the demo" panel on the landing page. Renders nothing unless the API runs with
 * DEMO_ENABLED=true. Logins are listed only when the server also sets
 * DEMO_SHOW_CREDENTIALS=true (throwaway demo accounts only).
 */
export function DemoBanner() {
  const { data } = useQuery({
    queryKey: ['demoInfo'],
    queryFn: fetchDemoInfo,
    staleTime: 5 * 60_000,
    retry: false
  });

  if (!data?.enabled || !data.inviteCode) return null;

  const guestUploadsEnabled = data.guestUploadsEnabled !== false;

  return (
    <div className="card w-full max-w-md p-5 text-left">
      <p className="text-xs font-semibold uppercase tracking-wide text-brand">Live demo</p>
      <h2 className="mt-1 text-xl font-semibold">Try it without signing up</h2>
      <p className="mt-1 text-sm text-ink/70">
        Open the sample wedding as a guest{guestUploadsEnabled ? ' and upload a photo' : ''}, or
        sign in as the host to see moderation, analytics and the QR share card. Demo data resets
        every night.
      </p>
      <div className="mt-4 flex flex-wrap gap-2">
        <Link href={`/e/${data.inviteCode}`} className="btn-primary px-4 py-2 text-sm">
          Open guest gallery
        </Link>
        <Link href="/sign-in" className="btn-outline px-4 py-2 text-sm">
          Sign in as host
        </Link>
      </div>
      <p className="mt-3 text-xs text-ink/50">
        Guest code <span className="font-mono">{data.inviteCode}</span>
      </p>
      {data.logins && data.logins.length > 0 && (
        <dl className="mt-3 space-y-1 rounded-lg bg-blush/60 p-3 text-xs">
          {data.logins.map((l) => (
            <div key={l.role} className="flex flex-wrap gap-x-2">
              <dt className="font-semibold">{l.role}:</dt>
              <dd>
                username <span className="font-mono">{l.username}</span>
              </dd>
              <dd>
                password <span className="font-mono">{l.password}</span>
              </dd>
            </div>
          ))}
        </dl>
      )}
    </div>
  );
}

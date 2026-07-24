'use client';

import { useAuth } from '@clerk/nextjs';
import { useQuery } from '@tanstack/react-query';
import Link from 'next/link';
import { createPortalSession, fetchSubscription } from '@/lib/api';

export function SubscriptionBanner() {
  const { getToken, isSignedIn } = useAuth();
  const { data } = useQuery({
    queryKey: ['subscription'],
    queryFn: async () => fetchSubscription((await getToken()) ?? ''),
    enabled: !!isSignedIn
  });

  if (!data) return null;

  async function manageBilling() {
    try {
      const token = (await getToken()) ?? '';
      const { url } = await createPortalSession(token);
      window.location.href = url;
    } catch {
      // user has no billing account yet
    }
  }

  const isFree = data.effectivePlan.code === 'FREE';

  return (
    <div className="card flex items-center justify-between px-4 py-3">
      <div className="text-sm">
        <span className="text-ink/50">Plan:</span>{' '}
        <span className="font-semibold text-wine">{data.planName}</span>
        {data.whitelisted && (
          <span className="ml-2 rounded-full bg-emerald-100 px-2 py-0.5 text-[10px] font-semibold text-emerald-700">VIP</span>
        )}
      </div>
      {isFree ? (
        <Link href="/pricing" className="btn-primary px-4 py-1.5 text-sm">
          Upgrade
        </Link>
      ) : (
        <button onClick={manageBilling} className="btn-outline px-4 py-1.5 text-sm">
          Manage billing
        </button>
      )}
    </div>
  );
}

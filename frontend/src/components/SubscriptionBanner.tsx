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
    <div className="flex items-center justify-between rounded-lg border border-slate-200 bg-white px-4 py-3">
      <div className="text-sm">
        <span className="text-slate-500">Plan:</span>{' '}
        <span className="font-semibold text-slate-900">{data.planName}</span>
        {data.whitelisted && (
          <span className="ml-2 rounded-full bg-emerald-100 px-2 py-0.5 text-[10px] font-semibold text-emerald-700">VIP</span>
        )}
      </div>
      {isFree ? (
        <Link href="/pricing" className="rounded-md bg-brand px-3 py-1.5 text-sm font-medium text-white hover:bg-brand-dark">
          Upgrade
        </Link>
      ) : (
        <button onClick={manageBilling} className="rounded-md border border-slate-300 px-3 py-1.5 text-sm font-medium text-slate-700 hover:bg-slate-50">
          Manage billing
        </button>
      )}
    </div>
  );
}

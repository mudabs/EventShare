'use client';

import { useAuth } from '@clerk/nextjs';
import { useQuery } from '@tanstack/react-query';
import { useRouter } from 'next/navigation';
import { useState } from 'react';
import { createCheckoutSession, fetchPlans, fetchSubscription } from '@/lib/api';
import { formatBytes, formatLimit, formatPrice } from '@/lib/format';
import type { Plan } from '@/lib/types';

export function PricingPlans() {
  const { getToken, isSignedIn } = useAuth();
  const router = useRouter();
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const { data: plans } = useQuery({ queryKey: ['plans'], queryFn: fetchPlans });
  const { data: sub } = useQuery({
    queryKey: ['subscription'],
    queryFn: async () => fetchSubscription((await getToken()) ?? ''),
    enabled: !!isSignedIn
  });

  async function choose(plan: Plan) {
    setError(null);
    if (!isSignedIn) {
      router.push('/sign-in');
      return;
    }
    if (plan.priceCents === 0) {
      return;
    }
    setBusy(plan.code);
    try {
      const token = (await getToken()) ?? '';
      const { url } = await createCheckoutSession(token, plan.code);
      window.location.href = url;
    } catch (e) {
      setBusy(null);
      setError(e instanceof Error ? e.message : 'Could not start checkout');
    }
  }

  if (!plans) {
    return <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">{Array.from({ length: 4 }).map((_, i) => <div key={i} className="h-80 animate-pulse rounded-lg bg-slate-100" />)}</div>;
  }

  return (
    <div>
      {error && <p className="mb-4 text-center text-sm text-red-600">{error}</p>}
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {plans.map((plan) => {
          const current = sub?.effectivePlan.code === plan.code;
          return (
            <div key={plan.code} className="flex flex-col rounded-lg border border-slate-200 bg-white p-5">
              <h3 className="text-lg font-bold">{plan.name}</h3>
              <div className="mt-1 text-2xl font-extrabold text-brand">{formatPrice(plan)}</div>
              <ul className="mt-4 flex-1 space-y-1 text-sm text-slate-600">
                <li>{formatLimit(plan.maxEvents)} events</li>
                <li>{formatLimit(plan.maxGuestsPerEvent)} guests / event</li>
                <li>{formatLimit(plan.maxPhotosPerEvent)} photos / event</li>
                <li>{formatLimit(plan.maxVideosPerEvent)} videos / event</li>
                <li>{formatBytes(plan.storageBytes)} storage</li>
                <li>{plan.zipExport ? 'ZIP export' : 'No ZIP export'}</li>
                <li>{plan.advancedAnalytics ? 'Advanced analytics' : 'Basic analytics'}</li>
              </ul>
              <button
                onClick={() => choose(plan)}
                disabled={current || busy === plan.code}
                className={`mt-4 rounded-md px-4 py-2 text-sm font-medium ${
                  current
                    ? 'cursor-default bg-slate-100 text-slate-500'
                    : 'bg-brand text-white hover:bg-brand-dark'
                } disabled:opacity-60`}
              >
                {current ? 'Current plan' : busy === plan.code ? 'Redirecting...' : plan.priceCents === 0 ? 'Included' : 'Choose'}
              </button>
            </div>
          );
        })}
      </div>
    </div>
  );
}

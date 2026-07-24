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
    return <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">{Array.from({ length: 4 }).map((_, i) => <div key={i} className="h-80 animate-pulse rounded-2xl bg-blush" />)}</div>;
  }

  return (
    <div>
      {error && <p className="mb-4 text-center text-sm text-red-600">{error}</p>}
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {plans.map((plan) => {
          const current = sub?.effectivePlan.code === plan.code;
          return (
            <div
              key={plan.code}
              className={`card flex flex-col p-6 transition ${current ? 'ring-2 ring-brand' : 'hover:-translate-y-1 hover:shadow-soft'}`}
            >
              <h3 className="text-xl font-semibold">{plan.name}</h3>
              <div className="mt-1 font-serif text-3xl font-semibold text-brand">{formatPrice(plan)}</div>
              <ul className="mt-4 flex-1 space-y-1.5 text-sm text-ink/70">
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
                className={`mt-5 rounded-full px-4 py-2.5 text-sm font-medium transition-all ${
                  current
                    ? 'cursor-default bg-blush text-ink/40'
                    : 'bg-brand text-white shadow-soft hover:bg-brand-dark hover:-translate-y-0.5'
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

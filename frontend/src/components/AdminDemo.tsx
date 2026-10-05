'use client';

import { useAuth } from '@clerk/nextjs';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { fetchDemoInfo, resetDemo } from '@/lib/api';
import { useFeedback } from './feedback/AppFeedback';

/** Admin tab for demo mode: shows the demo setup and resets it on demand. */
export function AdminDemo() {
  const { getToken } = useAuth();
  const { confirm, toast } = useFeedback();
  const queryClient = useQueryClient();
  const { data, isLoading } = useQuery({ queryKey: ['demoInfo'], queryFn: fetchDemoInfo });

  const reset = useMutation({
    mutationFn: async () => resetDemo((await getToken()) ?? ''),
    onSuccess: (result) => {
      toast({
        title: 'Demo reset',
        message: `${result.photosSeeded} photos seeded in ${(result.durationMs / 1000).toFixed(1)} s. Thumbnails appear within a few seconds.`,
        tone: 'success'
      });
      queryClient.invalidateQueries();
    },
    onError: (err) =>
      toast({ title: 'Reset failed', message: err instanceof Error ? err.message : 'Unknown error', tone: 'error' })
  });

  if (isLoading) return <div className="h-40 animate-pulse rounded-2xl bg-blush" />;
  if (!data?.enabled) {
    return (
      <p className="py-8 text-center text-ink/70">
        Demo mode is off. Set DEMO_ENABLED=true on the API to enable it (see docs/DEMO.md).
      </p>
    );
  }

  async function handleReset() {
    const ok = await confirm({
      title: 'Reset the demo?',
      message: 'All events owned by the demo accounts are deleted and the showcase is seeded again. Real users are not affected.',
      confirmText: 'Reset demo',
      tone: 'danger'
    });
    if (ok) reset.mutate();
  }

  return (
    <div className="card space-y-3 p-5">
      <h2 className="text-xl font-semibold">Demo mode</h2>
      <dl className="grid grid-cols-[max-content_1fr] gap-x-4 gap-y-1 text-sm">
        <dt className="text-ink/60">Guest link</dt>
        <dd className="font-mono break-all">{data.guestUrl}</dd>
        <dt className="text-ink/60">Archived event code</dt>
        <dd className="font-mono">{data.secondaryInviteCode}</dd>
        <dt className="text-ink/60">Promo code</dt>
        <dd className="font-mono">{data.promoCode}</dd>
        <dt className="text-ink/60">Automatic reset</dt>
        <dd className="font-mono">
          {data.resetCron} ({data.resetZone})
        </dd>
      </dl>
      <button className="btn-primary" onClick={handleReset} disabled={reset.isPending}>
        {reset.isPending ? 'Resetting...' : 'Reset demo now'}
      </button>
    </div>
  );
}

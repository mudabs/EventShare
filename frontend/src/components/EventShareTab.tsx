'use client';

import { useAuth } from '@clerk/nextjs';
import { useQuery } from '@tanstack/react-query';
import { fetchEvent } from '@/lib/api';
import { EventShareCard } from './EventShareCard';

export function EventShareTab({ eventId }: { eventId: string }) {
  const { getToken, isSignedIn } = useAuth();
  const { data, isLoading, isError } = useQuery({
    queryKey: ['event', eventId],
    queryFn: async () => fetchEvent((await getToken()) ?? '', eventId),
    enabled: !!isSignedIn
  });

  if (isLoading) {
    return <div className="h-72 animate-pulse rounded-lg bg-slate-100" />;
  }
  if (isError || !data) {
    return <p className="text-red-600">Could not load this event.</p>;
  }
  return (
    <div className="mx-auto max-w-md">
      <EventShareCard event={data} />
    </div>
  );
}

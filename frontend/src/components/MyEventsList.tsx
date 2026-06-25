'use client';

import { useAuth } from '@clerk/nextjs';
import { useQuery } from '@tanstack/react-query';
import Link from 'next/link';
import type { MyEventCard } from '@/lib/types';
import { fetchMyEvents } from '@/lib/api';

function RoleBadge({ role }: { role: 'OWNER' | 'GUEST' }) {
  const styles = role === 'OWNER' ? 'bg-indigo-100 text-indigo-700' : 'bg-slate-100 text-slate-600';
  return <span className={`rounded-full px-2 py-0.5 text-[10px] font-semibold ${styles}`}>{role}</span>;
}

function EventCard({ event }: { event: MyEventCard }) {
  return (
    <div className="flex flex-col overflow-hidden rounded-lg border border-slate-200 bg-white transition hover:shadow-md">
      <Link href={`/e/${event.inviteCode}`} className="block">
        <div className="relative aspect-video bg-gradient-to-br from-indigo-500 to-purple-500">
          {event.coverImageUrl && (
            /* eslint-disable-next-line @next/next/no-img-element */
            <img src={event.coverImageUrl} alt={event.name} className="h-full w-full object-cover" />
          )}
          <span className="absolute right-2 top-2"><RoleBadge role={event.role} /></span>
        </div>
      </Link>
      <div className="space-y-2 p-3">
        <div className="truncate font-semibold text-slate-900">{event.name}</div>
        <div className="text-xs text-slate-500">
          {event.eventDate ?? 'No date'} - {event.photoCount} photos - {event.videoCount} videos
        </div>
        <div className="flex gap-2 pt-1">
          <Link
            href={`/e/${event.inviteCode}`}
            className="rounded-md border border-slate-300 px-3 py-1 text-xs font-medium text-slate-700 hover:bg-slate-50"
          >
            Gallery
          </Link>
          {event.role === 'OWNER' && (
            <Link
              href={`/events/${event.id}/manage`}
              className="rounded-md border border-brand px-3 py-1 text-xs font-medium text-brand hover:bg-indigo-50"
            >
              Manage
            </Link>
          )}
          {event.status === 'ARCHIVED' && (
            <span className="ml-auto text-[10px] font-medium text-amber-600">Archived</span>
          )}
        </div>
      </div>
    </div>
  );
}

function Skeletons() {
  return (
    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
      {Array.from({ length: 3 }).map((_, i) => (
        <div key={i} className="animate-pulse overflow-hidden rounded-lg border border-slate-200 bg-white">
          <div className="aspect-video bg-slate-200" />
          <div className="space-y-2 p-3">
            <div className="h-4 w-2/3 rounded bg-slate-200" />
            <div className="h-3 w-1/2 rounded bg-slate-100" />
          </div>
        </div>
      ))}
    </div>
  );
}

export function MyEventsList() {
  const { getToken, isSignedIn } = useAuth();
  const { data, isLoading, isError } = useQuery({
    queryKey: ['myEvents'],
    queryFn: async () => fetchMyEvents((await getToken()) ?? ''),
    enabled: !!isSignedIn
  });

  if (isLoading) return <Skeletons />;
  if (isError) return <p className="py-8 text-center text-red-600">Could not load your events.</p>;

  const events = data ?? [];
  if (events.length === 0) {
    return (
      <div className="rounded-lg border border-dashed border-slate-300 p-10 text-center text-slate-500">
        No events yet. Create one or join with a code to see it here.
      </div>
    );
  }
  return (
    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
      {events.map((event) => (
        <EventCard key={event.id} event={event} />
      ))}
    </div>
  );
}

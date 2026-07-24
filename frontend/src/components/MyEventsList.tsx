'use client';

import { useAuth } from '@clerk/nextjs';
import { useQuery } from '@tanstack/react-query';
import Link from 'next/link';
import type { MyEventCard } from '@/lib/types';
import { fetchMyEvents } from '@/lib/api';
import { EmptyGallery } from './illustrations';

function RoleBadge({ role }: { role: 'OWNER' | 'GUEST' }) {
  const styles = role === 'OWNER' ? 'bg-brand text-white' : 'bg-white/85 text-wine';
  return <span className={`rounded-full px-2 py-0.5 text-[10px] font-semibold ${styles}`}>{role}</span>;
}

function EventCard({ event }: { event: MyEventCard }) {
  return (
    <div className="card flex flex-col overflow-hidden transition hover:-translate-y-0.5 hover:shadow-soft">
      <Link href={`/e/${event.inviteCode}`} className="block">
        <div className="relative flex aspect-video items-center justify-center bg-gradient-to-br from-brand-soft via-brand to-gold-soft">
          {event.coverImageUrl ? (
            /* eslint-disable-next-line @next/next/no-img-element */
            <img src={event.coverImageUrl} alt={event.name} className="h-full w-full object-cover" />
          ) : (
            <span className="font-script text-5xl text-white">{event.name.charAt(0).toUpperCase()}</span>
          )}
          <span className="absolute right-2 top-2">
            <RoleBadge role={event.role} />
          </span>
        </div>
      </Link>
      <div className="space-y-2 p-4">
        <div className="truncate font-serif text-lg font-semibold text-wine">{event.name}</div>
        <div className="text-xs text-ink/50">
          {event.eventDate ?? 'No date'} · {event.photoCount} photos · {event.videoCount} videos
        </div>
        <div className="flex items-center gap-2 pt-1">
          <Link
            href={`/e/${event.inviteCode}`}
            className="rounded-full border border-brand/25 px-3 py-1 text-xs font-medium text-wine transition-colors hover:border-brand hover:text-brand"
          >
            Gallery
          </Link>
          {event.role === 'OWNER' && (
            <Link
              href={`/events/${event.id}/manage`}
              className="rounded-full bg-brand px-3 py-1 text-xs font-medium text-white transition-colors hover:bg-brand-dark"
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
        <div key={i} className="card animate-pulse overflow-hidden">
          <div className="aspect-video bg-blush" />
          <div className="space-y-2 p-4">
            <div className="h-4 w-2/3 rounded bg-blush" />
            <div className="h-3 w-1/2 rounded bg-blush" />
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
      <div className="card flex flex-col items-center border-dashed border-brand/20 p-12 text-center">
        <EmptyGallery className="w-36" />
        <p className="mt-4 font-serif text-xl text-wine">No events yet</p>
        <p className="mt-1 text-sm text-ink/60">Create one, or join with a code, to see it here.</p>
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

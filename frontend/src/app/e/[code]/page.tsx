'use client';

import { SignInButton, useUser } from '@clerk/nextjs';
import { useQuery } from '@tanstack/react-query';
import { useParams } from 'next/navigation';
import { AuthedEventJoin } from '@/components/AuthedEventJoin';
import { Gallery } from '@/components/Gallery';
import { Header } from '@/components/Header';
import { JoinPrompt } from '@/components/JoinPrompt';
import { UploadButton } from '@/components/UploadButton';
import { getPublicEvent } from '@/lib/api';
import { queryKeys } from '@/lib/queryKeys';
import { useGuestStore } from '@/store/guestStore';

export default function EventPage() {
  const params = useParams<{ code: string }>();
  const code = params.code;
  const identity = useGuestStore((s) => s.identities[code]);
  const { isSignedIn } = useUser();

  const { data: event, isLoading, isError } = useQuery({
    queryKey: queryKeys.publicEvent(code),
    queryFn: () => getPublicEvent(code)
  });

  const returnUrl = `/e/${code}`;

  return (
    <div className="min-h-screen">
      <Header />
      <main className="mx-auto max-w-3xl px-4 py-6">
        {isLoading && <p className="py-12 text-center text-ink/50">Loading event...</p>}

        {(isError || (!isLoading && !event)) && (
          <p className="py-12 text-center text-ink/70">
            We could not find an event for code <span className="font-mono font-semibold">{code}</span>.
          </p>
        )}

        {event && (
          <div className="space-y-6">
            {/* Join landing: one cover image + event name */}
            <div className="card overflow-hidden">
              {event.coverImageUrl ? (
                /* eslint-disable-next-line @next/next/no-img-element */
                <img src={event.coverImageUrl} alt={event.name} className="aspect-video w-full object-cover" />
              ) : (
                <div className="flex aspect-video w-full items-center justify-center bg-gradient-to-br from-brand-soft via-brand to-gold-soft font-script text-6xl text-white">
                  {event.name.charAt(0).toUpperCase()}
                </div>
              )}
              <div className="p-6 text-center">
                <p className="script text-2xl">You're invited to</p>
                <h1 className="mt-1 text-3xl font-semibold">{event.name}</h1>
                <p className="mt-1 text-sm uppercase tracking-wide text-ink/50">
                  {event.eventType.charAt(0) + event.eventType.slice(1).toLowerCase()}
                </p>
              </div>
            </div>

            {event.active ? (
              identity ? (
                <UploadButton code={code} />
              ) : isSignedIn ? (
                <AuthedEventJoin code={code} onJoined={() => undefined} />
              ) : (
                <div className="card space-y-3 p-6 text-center">
                  <p className="text-sm text-ink/70">
                    Join this event to add your photos and videos.
                  </p>
                  <SignInButton mode="modal" forceRedirectUrl={returnUrl} signUpForceRedirectUrl={returnUrl}>
                    <button className="btn-primary w-full py-3">Sign in to join</button>
                  </SignInButton>
                  <div className="flex items-center gap-3 text-xs uppercase tracking-wide text-ink/40">
                    <span className="h-px flex-1 bg-brand/10" />
                    or continue as a guest
                    <span className="h-px flex-1 bg-brand/10" />
                  </div>
                  <JoinPrompt code={code} onJoined={() => undefined} />
                </div>
              )
            ) : (
              <p className="rounded-xl bg-amber-50 p-3 text-sm text-amber-800">
                This event is archived. You can still browse the gallery below.
              </p>
            )}

            <Gallery code={code} />
          </div>
        )}
      </main>
    </div>
  );
}

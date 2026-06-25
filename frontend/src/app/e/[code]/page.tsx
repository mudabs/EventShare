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
        {isLoading && <p className="py-12 text-center text-slate-500">Loading event...</p>}

        {(isError || (!isLoading && !event)) && (
          <p className="py-12 text-center text-slate-600">
            We could not find an event for code <span className="font-mono font-semibold">{code}</span>.
          </p>
        )}

        {event && (
          <div className="space-y-6">
            {/* Join landing: one cover image + event name */}
            <div className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
              {event.coverImageUrl ? (
                /* eslint-disable-next-line @next/next/no-img-element */
                <img src={event.coverImageUrl} alt={event.name} className="aspect-video w-full object-cover" />
              ) : (
                <div className="flex aspect-video w-full items-center justify-center bg-gradient-to-br from-indigo-500 to-purple-500 text-5xl text-white/80">
                  {event.name.charAt(0).toUpperCase()}
                </div>
              )}
              <div className="p-5 text-center">
                <h1 className="text-2xl font-bold">{event.name}</h1>
                <p className="text-sm text-slate-500">
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
                <div className="space-y-3 rounded-lg border border-slate-200 bg-white p-5 text-center">
                  <p className="text-sm text-slate-600">
                    Join this event to add your photos and videos.
                  </p>
                  <SignInButton mode="modal" forceRedirectUrl={returnUrl} signUpForceRedirectUrl={returnUrl}>
                    <button className="w-full rounded-md bg-brand px-4 py-3 font-medium text-white hover:bg-brand-dark">
                      Sign in to join
                    </button>
                  </SignInButton>
                  <div className="text-xs uppercase tracking-wide text-slate-400">or continue as a guest</div>
                  <JoinPrompt code={code} onJoined={() => undefined} />
                </div>
              )
            ) : (
              <p className="rounded-md bg-amber-50 p-3 text-sm text-amber-800">
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

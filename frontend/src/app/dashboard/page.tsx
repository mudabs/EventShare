'use client';

import { useAuth } from '@clerk/nextjs';
import { useQueryClient } from '@tanstack/react-query';
import { useRouter } from 'next/navigation';
import { useEffect, useState } from 'react';
import { CreateEventForm } from '@/components/CreateEventForm';
import { DashboardStats } from '@/components/DashboardStats';
import { EventShareCard } from '@/components/EventShareCard';
import { Header } from '@/components/Header';
import { MyEventsList } from '@/components/MyEventsList';
import { SubscriptionBanner } from '@/components/SubscriptionBanner';
import type { EventResponse } from '@/lib/types';

export default function DashboardPage() {
  const { isLoaded, isSignedIn } = useAuth();
  const router = useRouter();
  const [created, setCreated] = useState<EventResponse | null>(null);
  const [creating, setCreating] = useState(false);
  const queryClient = useQueryClient();

  useEffect(() => {
    if (isLoaded && !isSignedIn) {
      router.replace('/sign-in');
    }
  }, [isLoaded, isSignedIn, router]);

  function handleCreated(event: EventResponse) {
    setCreated(event);
    setCreating(false);
    queryClient.invalidateQueries({ queryKey: ['myEvents'] });
  }

  if (!isLoaded || !isSignedIn) {
    return (
      <div className="min-h-screen">
        <Header />
        <main className="px-4 py-16 text-center text-slate-500">Sign in to view your events.</main>
      </div>
    );
  }

  return (
    <div className="min-h-screen">
      <Header />
      <main className="mx-auto max-w-5xl px-4 py-8">
        {created ? (
          <div className="mx-auto max-w-xl space-y-6">
            <EventShareCard event={created} />
            <button
              onClick={() => setCreated(null)}
              className="w-full rounded-md border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-100"
            >
              Back to my events
            </button>
          </div>
        ) : creating ? (
          <div className="mx-auto max-w-xl space-y-4">
            <div className="flex items-center justify-between">
              <h1 className="text-2xl font-bold">Create a new event</h1>
              <button onClick={() => setCreating(false)} className="text-sm text-slate-500 hover:text-slate-700">
                Cancel
              </button>
            </div>
            <CreateEventForm onCreated={handleCreated} />
          </div>
        ) : (
          <div className="space-y-6">
            <div className="flex items-center justify-between">
              <h1 className="text-2xl font-bold">My Events</h1>
              <button
                onClick={() => setCreating(true)}
                className="rounded-md bg-brand px-4 py-2 font-medium text-white hover:bg-brand-dark"
              >
                + New event
              </button>
            </div>
            <SubscriptionBanner />
            <DashboardStats />
            <MyEventsList />
          </div>
        )}
      </main>
    </div>
  );
}

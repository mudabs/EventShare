'use client';

import { useAuth } from '@clerk/nextjs';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { AdminAnalytics } from '@/components/AdminAnalytics';
import { AdminEvents } from '@/components/AdminEvents';
import { AdminPromo } from '@/components/AdminPromo';
import { AdminUsers } from '@/components/AdminUsers';
import { AdminWhitelist } from '@/components/AdminWhitelist';
import { Header } from '@/components/Header';
import { fetchProfile } from '@/lib/api';

type Tab = 'users' | 'events' | 'promo' | 'whitelist' | 'analytics';
const TABS: { id: Tab; label: string }[] = [
  { id: 'users', label: 'Users' },
  { id: 'events', label: 'Events' },
  { id: 'promo', label: 'Promo' },
  { id: 'whitelist', label: 'Whitelist' },
  { id: 'analytics', label: 'Analytics' }
];

export default function AdminPage() {
  const { getToken } = useAuth();
  const [tab, setTab] = useState<Tab>('users');
  const { data: profile, isLoading } = useQuery({
    queryKey: ['profile'],
    queryFn: async () => fetchProfile((await getToken()) ?? '')
  });

  return (
    <div className="min-h-screen">
      <Header />
      <main className="mx-auto max-w-5xl px-4 py-6">
        {isLoading ? (
          <p className="py-12 text-center text-slate-500">Loading...</p>
        ) : profile?.role !== 'ADMIN' ? (
          <p className="py-12 text-center text-slate-600">You do not have administrator access.</p>
        ) : (
          <>
            <h1 className="mb-4 text-2xl font-bold">Platform admin</h1>
            <div className="mb-5 flex flex-wrap gap-2 border-b border-slate-200">
              {TABS.map((t) => (
                <button
                  key={t.id}
                  onClick={() => setTab(t.id)}
                  className={`-mb-px border-b-2 px-4 py-2 text-sm font-medium ${tab === t.id ? 'border-brand text-brand' : 'border-transparent text-slate-500 hover:text-slate-700'}`}
                >
                  {t.label}
                </button>
              ))}
            </div>
            {tab === 'users' && <AdminUsers />}
            {tab === 'events' && <AdminEvents />}
            {tab === 'promo' && <AdminPromo />}
            {tab === 'whitelist' && <AdminWhitelist />}
            {tab === 'analytics' && <AdminAnalytics />}
          </>
        )}
      </main>
    </div>
  );
}

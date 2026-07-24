'use client';

import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useState } from 'react';
import { EventSettingsPanel } from '@/components/EventSettingsPanel';
import { EventShareTab } from '@/components/EventShareTab';
import { Header } from '@/components/Header';
import { OwnerDashboard } from '@/components/OwnerDashboard';
import { OwnerGallery } from '@/components/OwnerGallery';

type Tab = 'share' | 'analytics' | 'gallery' | 'settings';

const TABS: { id: Tab; label: string }[] = [
  { id: 'share', label: 'Share' },
  { id: 'analytics', label: 'Analytics' },
  { id: 'gallery', label: 'Gallery' },
  { id: 'settings', label: 'Settings' }
];

export default function ManageEventPage() {
  const params = useParams<{ id: string }>();
  const eventId = params.id;
  const [tab, setTab] = useState<Tab>('share');

  return (
    <div className="min-h-screen">
      <Header />
      <main className="mx-auto max-w-4xl px-4 py-6">
        <nav className="mb-4 text-sm text-ink/50">
          <Link href="/dashboard" className="hover:text-brand">My Events</Link>
          <span className="px-1">/</span>
          <span className="text-wine">Manage</span>
        </nav>

        <div className="mb-5 flex flex-wrap gap-2 border-b border-brand/15">
          {TABS.map((t) => (
            <button
              key={t.id}
              onClick={() => setTab(t.id)}
              className={`-mb-px border-b-2 px-4 py-2 text-sm font-medium transition-colors ${
                tab === t.id ? 'border-brand text-brand' : 'border-transparent text-ink/50 hover:text-brand'
              }`}
            >
              {t.label}
            </button>
          ))}
        </div>

        {tab === 'share' && <EventShareTab eventId={eventId} />}
        {tab === 'analytics' && <OwnerDashboard eventId={eventId} />}
        {tab === 'gallery' && <OwnerGallery eventId={eventId} />}
        {tab === 'settings' && <EventSettingsPanel eventId={eventId} />}
      </main>
    </div>
  );
}

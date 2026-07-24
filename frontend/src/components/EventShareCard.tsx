'use client';

import Link from 'next/link';
import { QRCodeSVG } from 'qrcode.react';
import { useState } from 'react';
import type { EventResponse } from '@/lib/types';

export function EventShareCard({ event }: { event: EventResponse }) {
  const [copied, setCopied] = useState(false);

  async function copyLink() {
    await navigator.clipboard.writeText(event.inviteUrl);
    setCopied(true);
    setTimeout(() => setCopied(false), 1500);
  }

  return (
    <div className="card space-y-4 p-6 text-center">
      <div>
        <p className="script text-2xl">Your event is ready</p>
        <h2 className="text-2xl font-semibold">{event.name}</h2>
      </div>
      <p className="text-sm text-ink/70">
        Share this code or QR with your guests. No app or account needed to join.
      </p>

      <div className="flex justify-center">
        <div className="rounded-2xl bg-white p-3 shadow-card ring-1 ring-brand/10">
          <QRCodeSVG value={event.inviteUrl} size={176} fgColor="#831843" />
        </div>
      </div>

      <div className="font-mono text-2xl font-bold tracking-[0.3em] text-brand">
        {event.inviteCode}
      </div>

      <div className="flex items-center gap-2">
        <input readOnly value={event.inviteUrl} className="input flex-1 truncate text-sm" />
        <button
          onClick={copyLink}
          className="rounded-xl bg-wine px-4 py-2.5 text-sm font-medium text-white transition-colors hover:bg-wine/90"
        >
          {copied ? 'Copied' : 'Copy'}
        </button>
      </div>

      <Link href={`/e/${event.inviteCode}`} className="btn-primary inline-flex">
        Open shared gallery
      </Link>
    </div>
  );
}

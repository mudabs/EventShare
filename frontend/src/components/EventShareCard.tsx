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
    <div className="space-y-4 rounded-lg border border-slate-200 bg-white p-6 text-center">
      <h2 className="text-xl font-semibold">{event.name} is ready</h2>
      <p className="text-sm text-slate-600">
        Share this code or QR with your guests. No app or account needed to join.
      </p>

      <div className="flex justify-center">
        <div className="rounded-lg bg-white p-3 shadow-sm ring-1 ring-slate-200">
          <QRCodeSVG value={event.inviteUrl} size={176} />
        </div>
      </div>

      <div className="text-2xl font-mono font-bold tracking-widest text-brand">
        {event.inviteCode}
      </div>

      <div className="flex items-center gap-2">
        <input
          readOnly
          value={event.inviteUrl}
          className="flex-1 truncate rounded-md border border-slate-300 px-3 py-2 text-sm"
        />
        <button
          onClick={copyLink}
          className="rounded-md bg-slate-800 px-3 py-2 text-sm font-medium text-white hover:bg-slate-700"
        >
          {copied ? 'Copied' : 'Copy'}
        </button>
      </div>

      <Link
        href={`/e/${event.inviteCode}`}
        className="inline-block rounded-md bg-brand px-4 py-2 font-medium text-white hover:bg-brand-dark"
      >
        Open shared gallery
      </Link>
    </div>
  );
}

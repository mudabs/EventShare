'use client';

import Link from 'next/link';
import { useState } from 'react';
import { reportMedia, type ReportReason } from '@/lib/api';

const REASONS: { value: ReportReason; label: string; hint?: string }[] = [
  { value: 'ME_REMOVE', label: 'This shows me and I’d like it removed' },
  { value: 'INAPPROPRIATE', label: 'Inappropriate or offensive' },
  { value: 'COPYRIGHT', label: 'I own the copyright' },
  { value: 'CHILD_SAFETY', label: 'Child safety concern', hint: 'Hidden immediately until the host reviews it.' },
  { value: 'OTHER', label: 'Something else' }
];

/**
 * Lets any gallery viewer report a photo (Terms of Use, "Reporting and removing content").
 * The host sees reports in the manage gallery; several reports, or a child-safety report,
 * hide the photo automatically.
 */
export function ReportDialog({
  code,
  mediaId,
  onClose,
  onReported
}: {
  code: string;
  mediaId: string;
  onClose: () => void;
  onReported: () => void;
}) {
  const [reason, setReason] = useState<ReportReason>('ME_REMOVE');
  const [details, setDetails] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await reportMedia(code, mediaId, { reason, details: details.trim() || undefined });
      onReported();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not send the report');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div
      className="fixed inset-0 z-[9998] flex items-center justify-center bg-black/60 p-4"
      role="dialog"
      aria-modal="true"
      aria-labelledby="report-title"
    >
      <form onSubmit={submit} className="w-full max-w-md space-y-4 rounded-2xl bg-white p-5 text-ink shadow-2xl">
        <div>
          <h2 id="report-title" className="text-xl font-semibold">Report this photo</h2>
          <p className="mt-1 text-sm text-ink/60">
            The event host will be notified. Your name is not shared with them.
          </p>
        </div>
        <fieldset className="space-y-2">
          <legend className="sr-only">Reason</legend>
          {REASONS.map((r) => (
            <label key={r.value} className="flex cursor-pointer items-start gap-2 text-sm">
              <input
                type="radio"
                name="reason"
                value={r.value}
                checked={reason === r.value}
                onChange={() => setReason(r.value)}
                className="mt-0.5 accent-brand"
              />
              <span>
                {r.label}
                {r.hint && <span className="block text-xs text-ink/50">{r.hint}</span>}
              </span>
            </label>
          ))}
        </fieldset>
        <label className="block text-sm">
          <span className="text-ink/70">Anything the host should know? (optional)</span>
          <textarea
            value={details}
            onChange={(e) => setDetails(e.target.value)}
            maxLength={500}
            rows={3}
            className="input mt-1"
          />
        </label>
        {error && <p className="text-sm text-red-600">{error}</p>}
        <p className="text-xs text-ink/50">
          See <Link href="/terms#reporting" className="underline underline-offset-2">how reports work</Link>.
        </p>
        <div className="flex justify-end gap-2">
          <button type="button" onClick={onClose} className="btn-ghost">Cancel</button>
          <button type="submit" disabled={busy} className="btn-primary">
            {busy ? 'Sending…' : 'Send report'}
          </button>
        </div>
      </form>
    </div>
  );
}

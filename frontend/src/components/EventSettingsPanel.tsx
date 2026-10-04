'use client';

import { useAuth } from '@clerk/nextjs';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useRouter } from 'next/navigation';
import { useEffect, useState } from 'react';
import { useFeedback } from '@/components/feedback/AppFeedback';
import { deleteOwnedEvent, getEventSettings, updateEventSettings } from '@/lib/api';
import type { EventSettings } from '@/lib/types';

export function EventSettingsPanel({ eventId }: { eventId: string }) {
  const { getToken } = useAuth();
  const { confirm, toast } = useFeedback();
  const router = useRouter();
  const queryClient = useQueryClient();
  const { data } = useQuery({
    queryKey: ['eventSettings', eventId],
    queryFn: async () => getEventSettings((await getToken()) ?? '', eventId)
  });

  const [form, setForm] = useState<EventSettings | null>(null);
  const [saved, setSaved] = useState(false);
  const [saving, setSaving] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (data) setForm(data);
  }, [data]);

  if (!form) {
    return <div className="h-40 animate-pulse rounded-2xl bg-blush" />;
  }

  function update<K extends keyof EventSettings>(key: K, value: EventSettings[K]) {
    setForm((f) => (f ? { ...f, [key]: value } : f));
    setSaved(false);
  }

  async function save() {
    if (!form) return;
    setSaving(true);
    setError(null);
    const token = (await getToken()) ?? '';
    try {
      await updateEventSettings(token, eventId, {
        name: form.name,
        eventDate: form.eventDate,
        uploaderVisibility: form.uploaderVisibility,
        showUploadTimestamps: form.showUploadTimestamps,
        showUploaderNames: form.showUploaderNames,
        showUploadStats: form.showUploadStats
      });
      await queryClient.invalidateQueries({ queryKey: ['eventSettings', eventId] });
      await queryClient.invalidateQueries({ queryKey: ['myEvents'] });
      setSaved(true);
      toast({ title: 'Event settings saved', tone: 'success' });
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Could not save settings';
      setError(message);
      toast({ title: 'Save failed', message, tone: 'error' });
    } finally {
      setSaving(false);
    }
  }

  async function removeEvent() {
    const accepted = await confirm({
      title: 'Delete this event?',
      message: 'This will remove it from your dashboard and hide it from normal views.',
      confirmText: 'Delete event',
      tone: 'danger'
    });
    if (!accepted) {
      return;
    }
    setDeleting(true);
    setError(null);
    try {
      const token = (await getToken()) ?? '';
      await deleteOwnedEvent(token, eventId);
      await queryClient.invalidateQueries({ queryKey: ['myEvents'] });
      toast({ title: 'Event deleted', message: 'The event was removed from your account view.', tone: 'success' });
      router.push('/dashboard');
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Could not delete event';
      setError(message);
      toast({ title: 'Delete failed', message, tone: 'error' });
      setDeleting(false);
    }
  }

  return (
    <div className="card space-y-5 p-6">
      <div>
        <label className="label">Event name</label>
        <input
          value={form.name}
          onChange={(e) => update('name', e.target.value)}
          className="input mt-1.5"
        />
      </div>

      <div>
        <span className="label">Uploader visibility</span>
        <div className="mt-2 space-y-1 text-sm">
          <label className="flex items-center gap-2">
            <input type="radio" checked={form.uploaderVisibility === 'NAMED'} onChange={() => update('uploaderVisibility', 'NAMED')} />
            Named uploads (show who uploaded)
          </label>
          <label className="flex items-center gap-2">
            <input type="radio" checked={form.uploaderVisibility === 'ANONYMOUS'} onChange={() => update('uploaderVisibility', 'ANONYMOUS')} />
            Anonymous (only you see uploaders)
          </label>
        </div>
      </div>

      <div className="space-y-2 text-sm">
        <label className="flex items-center gap-2">
          <input type="checkbox" checked={form.showUploadTimestamps} onChange={(e) => update('showUploadTimestamps', e.target.checked)} />
          Show upload timestamps to guests
        </label>
        <label className="flex items-center gap-2">
          <input type="checkbox" checked={form.showUploaderNames} onChange={(e) => update('showUploaderNames', e.target.checked)} />
          Show uploader names to guests
        </label>
        <label className="flex items-center gap-2">
          <input type="checkbox" checked={form.showUploadStats} onChange={(e) => update('showUploadStats', e.target.checked)} />
          Show upload statistics to guests
        </label>
      </div>

      <div className="flex items-center gap-3">
        <button onClick={save} disabled={saving} className="btn-primary">
          {saving ? 'Saving...' : 'Save settings'}
        </button>
        {saved && <span className="text-sm text-green-600">Saved</span>}
      </div>

      {error && <p className="text-sm text-red-600">{error}</p>}

      <div className="border-t border-brand/15 pt-4">
        <p className="mb-2 text-sm text-ink/60">Danger zone</p>
        <button
          onClick={removeEvent}
          disabled={deleting}
          className="rounded bg-red-50 px-3 py-2 text-sm text-red-700 hover:bg-red-100 disabled:opacity-60"
        >
          {deleting ? 'Deleting...' : 'Delete event'}
        </button>
      </div>
    </div>
  );
}

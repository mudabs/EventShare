'use client';

import { useAuth } from '@clerk/nextjs';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { getEventSettings, updateEventSettings } from '@/lib/api';
import type { EventSettings } from '@/lib/types';

export function EventSettingsPanel({ eventId }: { eventId: string }) {
  const { getToken } = useAuth();
  const queryClient = useQueryClient();
  const { data } = useQuery({
    queryKey: ['eventSettings', eventId],
    queryFn: async () => getEventSettings((await getToken()) ?? '', eventId)
  });

  const [form, setForm] = useState<EventSettings | null>(null);
  const [saved, setSaved] = useState(false);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (data) setForm(data);
  }, [data]);

  if (!form) {
    return <div className="h-40 animate-pulse rounded-lg bg-slate-100" />;
  }

  function update<K extends keyof EventSettings>(key: K, value: EventSettings[K]) {
    setForm((f) => (f ? { ...f, [key]: value } : f));
    setSaved(false);
  }

  async function save() {
    if (!form) return;
    setSaving(true);
    const token = (await getToken()) ?? '';
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
    setSaving(false);
    setSaved(true);
  }

  return (
    <div className="space-y-5 rounded-lg border border-slate-200 bg-white p-5">
      <div>
        <label className="block text-sm font-medium text-slate-700">Event name</label>
        <input
          value={form.name}
          onChange={(e) => update('name', e.target.value)}
          className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 focus:border-brand focus:outline-none"
        />
      </div>

      <div>
        <span className="block text-sm font-medium text-slate-700">Uploader visibility</span>
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
        <button
          onClick={save}
          disabled={saving}
          className="rounded-md bg-brand px-4 py-2 font-medium text-white hover:bg-brand-dark disabled:opacity-60"
        >
          {saving ? 'Saving...' : 'Save settings'}
        </button>
        {saved && <span className="text-sm text-green-600">Saved</span>}
      </div>
    </div>
  );
}

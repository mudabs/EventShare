'use client';

import { useAuth } from '@clerk/nextjs';
import { useState } from 'react';
import { createEvent } from '@/lib/api';
import type { EventResponse, EventType } from '@/lib/types';

const EVENT_TYPES: EventType[] = [
  'WEDDING', 'FAMILY', 'GRADUATION', 'CHURCH', 'CONFERENCE', 'BIRTHDAY', 'REUNION', 'OTHER'
];

export function CreateEventForm({ onCreated }: { onCreated: (event: EventResponse) => void }) {
  const { getToken } = useAuth();
  const [name, setName] = useState('');
  const [eventType, setEventType] = useState<EventType>('WEDDING');
  const [eventDate, setEventDate] = useState('');
  const [description, setDescription] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      const token = await getToken();
      if (!token) {
        throw new Error('Your session expired. Please sign in again.');
      }
      const created = await createEvent(token, {
        name: name.trim(),
        eventType,
        description: description.trim() || undefined,
        eventDate: eventDate || undefined
      });
      onCreated(created);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not create the event');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-4 rounded-lg border border-slate-200 bg-white p-6">
      <div>
        <label className="block text-sm font-medium text-slate-700">Event name</label>
        <input
          required
          maxLength={200}
          value={name}
          onChange={(e) => setName(e.target.value)}
          className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 focus:border-brand focus:outline-none"
          placeholder="Sam & Tari's Wedding"
        />
      </div>

      <div className="grid grid-cols-2 gap-4">
        <div>
          <label className="block text-sm font-medium text-slate-700">Type</label>
          <select
            value={eventType}
            onChange={(e) => setEventType(e.target.value as EventType)}
            className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 focus:border-brand focus:outline-none"
          >
            {EVENT_TYPES.map((t) => (
              <option key={t} value={t}>
                {t.charAt(0) + t.slice(1).toLowerCase()}
              </option>
            ))}
          </select>
        </div>
        <div>
          <label className="block text-sm font-medium text-slate-700">Date (optional)</label>
          <input
            type="date"
            value={eventDate}
            onChange={(e) => setEventDate(e.target.value)}
            className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 focus:border-brand focus:outline-none"
          />
        </div>
      </div>

      <div>
        <label className="block text-sm font-medium text-slate-700">Description (optional)</label>
        <textarea
          maxLength={5000}
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          rows={2}
          className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2 focus:border-brand focus:outline-none"
        />
      </div>

      {error && <p className="text-sm text-red-600">{error}</p>}

      <button
        type="submit"
        disabled={submitting}
        className="w-full rounded-md bg-brand px-4 py-2 font-medium text-white hover:bg-brand-dark disabled:opacity-60"
      >
        {submitting ? 'Creating…' : 'Create event'}
      </button>
    </form>
  );
}

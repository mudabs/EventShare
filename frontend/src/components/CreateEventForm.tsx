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
    <form onSubmit={handleSubmit} className="card space-y-4 p-6">
      <div>
        <label className="label">Event name</label>
        <input
          required
          maxLength={200}
          value={name}
          onChange={(e) => setName(e.target.value)}
          className="input mt-1.5"
          placeholder="Sam & Tari's Wedding"
        />
      </div>

      <div className="grid grid-cols-2 gap-4">
        <div>
          <label className="label">Type</label>
          <select
            value={eventType}
            onChange={(e) => setEventType(e.target.value as EventType)}
            className="input mt-1.5"
          >
            {EVENT_TYPES.map((t) => (
              <option key={t} value={t}>
                {t.charAt(0) + t.slice(1).toLowerCase()}
              </option>
            ))}
          </select>
        </div>
        <div>
          <label className="label">Date (optional)</label>
          <input
            type="date"
            value={eventDate}
            onChange={(e) => setEventDate(e.target.value)}
            className="input mt-1.5"
          />
        </div>
      </div>

      <div>
        <label className="label">Description (optional)</label>
        <textarea
          maxLength={5000}
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          rows={2}
          className="input mt-1.5"
        />
      </div>

      {error && <p className="text-sm text-red-600">{error}</p>}

      <button type="submit" disabled={submitting} className="btn-primary w-full">
        {submitting ? 'Creating…' : 'Create event'}
      </button>
    </form>
  );
}

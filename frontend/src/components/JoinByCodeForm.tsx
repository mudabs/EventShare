'use client';

import { useRouter } from 'next/navigation';
import { useState } from 'react';

export function JoinByCodeForm() {
  const router = useRouter();
  const [code, setCode] = useState('');

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    const trimmed = code.trim().toUpperCase();
    if (trimmed) {
      router.push(`/e/${encodeURIComponent(trimmed)}`);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="flex w-full max-w-sm gap-2">
      <input
        value={code}
        onChange={(e) => setCode(e.target.value)}
        placeholder="Enter event code"
        aria-label="Event code"
        className="flex-1 rounded-md border border-slate-300 px-3 py-2 uppercase tracking-wide focus:border-brand focus:outline-none"
      />
      <button
        type="submit"
        className="rounded-md bg-brand px-4 py-2 font-medium text-white hover:bg-brand-dark"
      >
        Join
      </button>
    </form>
  );
}

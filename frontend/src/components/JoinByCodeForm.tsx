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
        className="input flex-1 text-center uppercase tracking-[0.2em]"
      />
      <button type="submit" className="btn-primary shrink-0">
        Join
      </button>
    </form>
  );
}

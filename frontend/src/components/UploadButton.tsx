'use client';

import { useQueryClient } from '@tanstack/react-query';
import { useRef, useState } from 'react';
import { queryKeys } from '@/lib/queryKeys';
import { uploadCapturedFile } from '@/lib/upload';
import { useGuestStore } from '@/store/guestStore';
import { CameraCapture } from './CameraCapture';

export function UploadButton({ code }: { code: string }) {
  const identity = useGuestStore((s) => s.identities[code]);
  const queryClient = useQueryClient();
  const fileInput = useRef<HTMLInputElement>(null);
  const [progress, setProgress] = useState<{ done: number; total: number } | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [cameraOpen, setCameraOpen] = useState(false);

  async function refreshGallery() {
    await queryClient.invalidateQueries({ queryKey: queryKeys.gallery(code) });
  }

  async function handleFiles(files: FileList | null) {
    if (!files || files.length === 0) return;
    setError(null);
    const list = Array.from(files);
    setProgress({ done: 0, total: list.length });
    for (let i = 0; i < list.length; i++) {
      try {
        await uploadCapturedFile(code, list[i], identity);
      } catch (err) {
        setError(err instanceof Error ? err.message : 'An upload failed');
      }
      setProgress({ done: i + 1, total: list.length });
    }
    await refreshGallery();
    setProgress(null);
    if (fileInput.current) fileInput.current.value = '';
  }

  async function handleCaptured(file: File) {
    setError(null);
    setProgress({ done: 0, total: 1 });
    try {
      await uploadCapturedFile(code, file, identity);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Upload failed');
    }
    await refreshGallery();
    setProgress(null);
  }

  const busy = progress !== null;

  return (
    <div className="space-y-2">
      <div className="flex gap-2">
        <button
          onClick={() => fileInput.current?.click()}
          disabled={busy}
          className="flex-1 rounded-md bg-brand px-4 py-3 font-medium text-white hover:bg-brand-dark disabled:opacity-60"
        >
          Add photos & videos
        </button>
        <button
          onClick={() => setCameraOpen(true)}
          disabled={busy}
          aria-label="Capture from camera"
          title="Capture from camera"
          className="flex items-center justify-center rounded-md border border-brand px-4 py-3 text-brand hover:bg-indigo-50 disabled:opacity-60"
        >
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <path d="M14.5 4l1.5 2h3a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h3l1.5-2z" />
            <circle cx="12" cy="13" r="3.5" />
          </svg>
        </button>
      </div>

      <input
        ref={fileInput}
        type="file"
        accept="image/*,video/*"
        multiple
        hidden
        onChange={(e) => handleFiles(e.target.files)}
      />

      {busy && progress && (
        <p className="text-sm text-slate-600">Uploading {progress.done} of {progress.total}...</p>
      )}
      {error && <p className="text-sm text-red-600">{error}</p>}

      {cameraOpen && <CameraCapture onCapture={handleCaptured} onClose={() => setCameraOpen(false)} />}
    </div>
  );
}

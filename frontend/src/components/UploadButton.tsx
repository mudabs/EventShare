'use client';

import { useQueryClient } from '@tanstack/react-query';
import { useRef, useState } from 'react';
import { queryKeys } from '@/lib/queryKeys';
import { uploadCapturedFile } from '@/lib/upload';
import { useGuestStore } from '@/store/guestStore';
import { CameraCapture } from './CameraCapture';
import { ConsentNote } from './SiteFooter';

interface UploadButtonProps {
  code: string;
  /** Demo event: uploads this visitor has left. Extra files beyond it are not attempted. */
  remaining?: number | null;
  /** Demo event: per-file size cap; larger files are rejected before any upload starts. */
  maxBytes?: number | null;
}

function formatMb(bytes: number) {
  const mb = bytes / (1024 * 1024);
  return Number.isInteger(mb) ? `${mb} MB` : `${mb.toFixed(1)} MB`;
}

export function UploadButton({ code, remaining = null, maxBytes = null }: UploadButtonProps) {
  const identity = useGuestStore((s) => s.identities[code]);
  const queryClient = useQueryClient();
  const fileInput = useRef<HTMLInputElement>(null);
  const [progress, setProgress] = useState<{ done: number; total: number } | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [cameraOpen, setCameraOpen] = useState(false);

  async function refreshGallery() {
    await queryClient.invalidateQueries({ queryKey: queryKeys.gallery(code) });
    // The public event carries the demo "uploads left" count; refresh it too.
    await queryClient.invalidateQueries({ queryKey: queryKeys.publicEvent(code) });
  }

  /** Applies the demo limits in the browser so visitors get a clear message up front. */
  function applyDemoLimits(files: File[]): File[] {
    let list = files;
    if (maxBytes != null) {
      const tooBig = list.filter((f) => f.size > maxBytes);
      if (tooBig.length) {
        setError(`Demo uploads are limited to ${formatMb(maxBytes)} per file; skipped ${tooBig.length} file${tooBig.length === 1 ? '' : 's'}.`);
        list = list.filter((f) => f.size <= maxBytes);
      }
    }
    if (remaining != null && list.length > remaining) {
      setError(`You can add ${remaining} more photo${remaining === 1 ? '' : 's'} to the demo; only the first ${remaining} will be uploaded.`);
      list = list.slice(0, Math.max(0, remaining));
    }
    return list;
  }

  async function handleFiles(files: FileList | null) {
    if (!files || files.length === 0) return;
    setError(null);
    const list = applyDemoLimits(Array.from(files));
    if (list.length === 0) {
      if (fileInput.current) fileInput.current.value = '';
      return;
    }
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
    if (applyDemoLimits([file]).length === 0) return;
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
          className="btn-primary flex-1 py-3"
        >
          Add photos &amp; videos
        </button>
        <button
          onClick={() => setCameraOpen(true)}
          disabled={busy}
          aria-label="Capture from camera"
          title="Capture from camera"
          className="flex items-center justify-center rounded-full border border-brand/40 px-4 py-3 text-brand transition-colors hover:bg-brand/10 disabled:opacity-60"
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
        <p className="text-sm text-ink/60">Uploading {progress.done} of {progress.total}...</p>
      )}
      {error && <p className="text-sm text-red-600">{error}</p>}
      <ConsentNote action="uploading" />

      {cameraOpen && <CameraCapture onCapture={handleCaptured} onClose={() => setCameraOpen(false)} />}
    </div>
  );
}

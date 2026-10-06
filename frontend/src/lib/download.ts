import type { MediaItem } from './types';

/**
 * Browser download helpers for the guest gallery.
 *
 * Single files use `downloadUrl`, a presigned R2 URL that answers with
 * `Content-Disposition: attachment`, so the browser saves the file instead of opening it.
 * (A cross-origin `<a download>` is ignored by browsers, which is why "Download" used to
 * open the photo on its own page.)
 *
 * ZIPs are built in the browser with client-zip, fetching the originals straight from R2.
 * That keeps the multi-gigabyte traffic off the home server. Where the File System Access
 * API exists (Chrome, Edge) the ZIP streams straight to disk; elsewhere it is assembled in
 * memory, so very large selections are refused with a clear message.
 */

const MAX_IN_MEMORY_ZIP_BYTES = 1.5 * 1024 * 1024 * 1024;

function clickLink(href: string, filename?: string) {
  const link = document.createElement('a');
  link.href = href;
  if (filename) link.download = filename;
  link.rel = 'noopener';
  link.style.display = 'none';
  document.body.appendChild(link);
  link.click();
  link.remove();
}

function fileNameFor(item: MediaItem, index: number) {
  if (item.originalFilename && item.originalFilename.trim()) return item.originalFilename.trim();
  const ext = item.mediaType === 'VIDEO' ? 'mp4' : 'jpg';
  return `eventshare-${String(index + 1).padStart(3, '0')}.${ext}`;
}

/** Saves one file to the device. */
export function downloadFile(item: MediaItem) {
  clickLink(item.downloadUrl ?? item.originalUrl, item.originalFilename ?? undefined);
}

/** Saves several files one by one (free plan). Browsers may ask once to allow multiple downloads. */
export async function downloadIndividually(items: MediaItem[], onProgress?: (done: number) => void) {
  for (let i = 0; i < items.length; i += 1) {
    downloadFile(items[i]);
    onProgress?.(i + 1);
    // Small gap so the browser does not drop rapid consecutive downloads.
    await new Promise((resolve) => window.setTimeout(resolve, 350));
  }
}

/** Makes names unique inside the ZIP: "IMG_1.jpg", "IMG_1 (2).jpg", ... */
function uniqueNames(items: MediaItem[]) {
  const used = new Map<string, number>();
  return items.map((item, i) => {
    const name = fileNameFor(item, i);
    const count = used.get(name.toLowerCase()) ?? 0;
    used.set(name.toLowerCase(), count + 1);
    if (count === 0) return name;
    const dot = name.lastIndexOf('.');
    return dot > 0 ? `${name.slice(0, dot)} (${count + 1})${name.slice(dot)}` : `${name} (${count + 1})`;
  });
}

type SavePicker = (options: {
  suggestedName: string;
  types?: { description: string; accept: Record<string, string[]> }[];
}) => Promise<{ createWritable: () => Promise<WritableStream<Uint8Array>> }>;

export class ZipTooLargeError extends Error {}

/**
 * Downloads items as one ZIP named `zipName`. `source` is either the items or a loader
 * (used by "Download all", which first has to list every gallery page).
 *
 * The save dialog is opened before any network work, because browsers only allow
 * showSaveFilePicker shortly after a click. If the dialog is unavailable or refused for
 * that reason, the ZIP is built in memory instead (with a size limit).
 * Resolves `false` if the user cancelled the save dialog.
 */
export async function downloadZip(
  source: MediaItem[] | (() => Promise<MediaItem[]>),
  zipName: string,
  onProgress?: (done: number, total: number) => void
): Promise<boolean> {
  const picker = (window as unknown as { showSaveFilePicker?: SavePicker }).showSaveFilePicker;

  let writable: WritableStream<Uint8Array> | null = null;
  if (picker) {
    try {
      const handle = await picker({
        suggestedName: zipName,
        types: [{ description: 'ZIP archive', accept: { 'application/zip': ['.zip'] } }]
      });
      writable = await handle.createWritable();
    } catch (err) {
      if (err instanceof DOMException && err.name === 'AbortError') return false;
      writable = null; // e.g. SecurityError: fall back to the in-memory path below
    }
  }

  const items = typeof source === 'function' ? await source() : source;
  if (!items.length) {
    if (writable) await writable.abort();
    return false;
  }
  const totalBytes = items.reduce((sum, i) => sum + (i.sizeBytes ?? 0), 0);
  if (!writable && totalBytes > MAX_IN_MEMORY_ZIP_BYTES) {
    throw new ZipTooLargeError(
      'This selection is too large to zip in this browser. Select fewer items, or use Chrome or Edge on a computer.'
    );
  }

  const { downloadZip: makeZipResponse } = await import('client-zip');
  const names = uniqueNames(items);
  let done = 0;
  async function* files() {
    for (let i = 0; i < items.length; i += 1) {
      const response = await fetch(items[i].originalUrl);
      if (!response.ok) throw new Error(`Could not fetch ${names[i]} (HTTP ${response.status})`);
      yield { name: names[i], lastModified: new Date(items[i].createdAt), input: response };
      done += 1;
      onProgress?.(done, items.length);
    }
  }

  if (writable) {
    const body = makeZipResponse(files()).body;
    if (!body) throw new Error('ZIP stream unavailable');
    await body.pipeTo(writable);
    return true;
  }

  const blob = await makeZipResponse(files()).blob();
  const url = URL.createObjectURL(blob);
  try {
    clickLink(url, zipName);
  } finally {
    window.setTimeout(() => URL.revokeObjectURL(url), 60_000);
  }
  return true;
}

/** "amara-kofis-wedding-2026-10-06.zip" */
export function zipFileName(eventName: string | undefined, suffix?: string) {
  const base = (eventName ?? 'eventshare')
    .toLowerCase()
    .replace(/['’]/g, '')
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 60) || 'eventshare';
  const date = new Date().toISOString().slice(0, 10);
  return `${base}${suffix ? `-${suffix}` : ''}-${date}.zip`;
}

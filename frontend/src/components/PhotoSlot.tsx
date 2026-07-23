'use client';

import { useState } from 'react';

type PhotoSlotProps = {
  /** Path under /public, e.g. "/images/gallery-1.jpg". If missing/broken, an
   *  on-brand placeholder is shown instead, so the layout never looks empty. */
  src?: string;
  alt?: string;
  /** Tailwind aspect ratio class, e.g. "aspect-[4/3]" (default), "aspect-square". */
  ratio?: string;
  rounded?: string;
  className?: string;
  label?: string;
};

/**
 * A picture "slot": drops in a real photo when the file exists, otherwise renders
 * a soft blush placeholder with a small illustration. Lets us ship life now and
 * fill in real event photos later just by adding files to /public/images.
 */
export function PhotoSlot({
  src,
  alt = '',
  ratio = 'aspect-[4/3]',
  rounded = 'rounded-2xl',
  className = '',
  label
}: PhotoSlotProps) {
  const [failed, setFailed] = useState(false);
  const showImage = src && !failed;

  return (
    <div
      className={`relative ${ratio} ${rounded} w-full overflow-hidden ring-1 ring-brand/10 ${className}`}
    >
      {showImage ? (
        /* eslint-disable-next-line @next/next/no-img-element */
        <img
          src={src}
          alt={alt}
          loading="lazy"
          onError={() => setFailed(true)}
          className="h-full w-full object-cover"
        />
      ) : (
        <div className="flex h-full w-full flex-col items-center justify-center gap-2 bg-gradient-to-br from-blush to-white">
          <svg viewBox="0 0 48 48" aria-hidden="true" className="h-9 w-9">
            <rect x="7" y="14" width="34" height="24" rx="5" fill="#FFFFFF" stroke="#FBCFE8" strokeWidth="2" />
            <path d="M18 14l3-4h6l3 4" fill="none" stroke="#FBCFE8" strokeWidth="2" strokeLinejoin="round" />
            <circle cx="24" cy="27" r="7" fill="#FDE7F1" stroke="#F472B6" strokeWidth="2" />
            <circle cx="24" cy="27" r="3" fill="#DB2777" opacity="0.8" />
          </svg>
          {label && <span className="text-[11px] font-medium text-brand/60">{label}</span>}
        </div>
      )}
    </div>
  );
}

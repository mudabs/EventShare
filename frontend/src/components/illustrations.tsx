/**
 * On-brand SVG illustrations for EventShare (romantic wedding palette).
 * Pure vector, no external assets — crisp at any size, instant load.
 *
 * Palette: rose #DB2777 · soft #F472B6 · wine #831843 · gold #C99A3B · blush #FDF2F8
 */

type SvgProps = { className?: string };

/** Hero motif: a shared-gallery frame filling with photos, hearts and florals. */
export function HeroScene({ className = '' }: SvgProps) {
  return (
    <svg
      viewBox="0 0 420 320"
      role="img"
      aria-label="A shared gallery filling with guests' photos"
      className={className}
      xmlns="http://www.w3.org/2000/svg"
    >
      {/* soft backdrop */}
      <ellipse cx="210" cy="286" rx="150" ry="18" fill="#FBCFE8" opacity="0.5" />

      {/* gallery frame */}
      <g transform="rotate(-4 210 160)">
        <rect x="96" y="60" width="228" height="200" rx="18" fill="#FFFFFF" stroke="#FBCFE8" strokeWidth="2" />
        {/* photo tiles */}
        <g>
          <rect x="112" y="78" width="60" height="60" rx="8" fill="#FDE7F1" />
          <path d="M112 122l16-16 14 12 12-10 18 20v10a8 8 0 0 1-8 8h-44a8 8 0 0 1-8-8z" fill="#F472B6" />
          <circle cx="130" cy="96" r="7" fill="#C99A3B" />

          <rect x="180" y="78" width="60" height="60" rx="8" fill="#FCE9DB" />
          <circle cx="210" cy="108" r="16" fill="#DB2777" opacity="0.85" />

          <rect x="248" y="78" width="60" height="60" rx="8" fill="#F3E8F6" />
          <path d="M248 128l14-14 12 10 10-12 24 26v6a4 4 0 0 1-4 4h-52a4 4 0 0 1-4-4z" fill="#831843" opacity="0.75" />

          <rect x="112" y="146" width="60" height="60" rx="8" fill="#FCE9DB" />
          <path d="M142 158l8 14h-16z" fill="#C99A3B" />
          <path d="M132 172l10 16h-20z" fill="#C99A3B" opacity="0.8" />

          <rect x="180" y="146" width="60" height="60" rx="8" fill="#FDE7F1" />
          <circle cx="210" cy="176" r="15" fill="#F472B6" />

          <rect x="248" y="146" width="60" height="60" rx="8" fill="#F3E8F6" />
          <path d="M278 160c6-9 20-4 0 12-20-16-6-21 0-12z" fill="#DB2777" />
        </g>
        {/* upload chip */}
        <rect x="150" y="222" width="120" height="22" rx="11" fill="#FDF2F8" />
        <rect x="162" y="230" width="60" height="6" rx="3" fill="#DB2777" opacity="0.55" />
        <circle cx="252" cy="233" r="7" fill="#DB2777" />
        <path d="M252 229v8M248 233h8" stroke="#FFF" strokeWidth="1.6" strokeLinecap="round" />
      </g>

      {/* floating hearts */}
      <path d="M330 70c8-12 26-5 0 16-26-21-8-28 0-16z" fill="#DB2777" />
      <path d="M356 128c5-8 17-3 0 10-17-13-5-18 0-10z" fill="#F472B6" />
      <path d="M78 150c6-9 20-4 0 12-20-16-6-21 0-12z" fill="#F472B6" opacity="0.85" />

      {/* floral sprig, lower-left */}
      <g stroke="#C99A3B" strokeWidth="2.5" fill="none" strokeLinecap="round">
        <path d="M70 262c14-10 22-30 20-52" />
        <path d="M84 230c10 2 18-2 24-12" />
        <path d="M80 246c-10 0-18-5-22-14" />
      </g>
      <circle cx="90" cy="208" r="9" fill="#DB2777" />
      <circle cx="110" cy="216" r="7" fill="#F472B6" />
      <circle cx="56" cy="230" r="7" fill="#C99A3B" />
    </svg>
  );
}

/** Step 1 — create an event (heart on a card). */
export function StepCreate({ className = '' }: SvgProps) {
  return (
    <svg viewBox="0 0 64 64" aria-hidden="true" className={className} xmlns="http://www.w3.org/2000/svg">
      <rect x="12" y="10" width="40" height="44" rx="8" fill="#FDF2F8" stroke="#FBCFE8" strokeWidth="2" />
      <path d="M32 44c-12-8-16-16-9-22 5-4 9 0 9 3 0-3 4-7 9-3 7 6 3 14-9 22z" fill="#DB2777" />
    </svg>
  );
}

/** Step 2 — share a QR / link. */
export function StepShare({ className = '' }: SvgProps) {
  return (
    <svg viewBox="0 0 64 64" aria-hidden="true" className={className} xmlns="http://www.w3.org/2000/svg">
      <rect x="12" y="12" width="40" height="40" rx="8" fill="#FDF2F8" stroke="#FBCFE8" strokeWidth="2" />
      <g fill="#831843">
        <rect x="19" y="19" width="10" height="10" rx="2" />
        <rect x="35" y="19" width="10" height="10" rx="2" />
        <rect x="19" y="35" width="10" height="10" rx="2" />
      </g>
      <rect x="37" y="37" width="4" height="4" fill="#DB2777" />
      <rect x="43" y="37" width="2" height="4" fill="#DB2777" />
      <rect x="37" y="43" width="8" height="2" fill="#DB2777" />
    </svg>
  );
}

/** Step 3 — the gallery fills (photo stack). */
export function StepGallery({ className = '' }: SvgProps) {
  return (
    <svg viewBox="0 0 64 64" aria-hidden="true" className={className} xmlns="http://www.w3.org/2000/svg">
      <rect x="14" y="20" width="34" height="28" rx="5" fill="#FCE9DB" transform="rotate(-8 31 34)" />
      <rect x="18" y="16" width="34" height="28" rx="5" fill="#FFFFFF" stroke="#FBCFE8" strokeWidth="2" />
      <circle cx="28" cy="26" r="4" fill="#C99A3B" />
      <path d="M20 40l9-9 6 6 6-7 8 10v2a1 1 0 0 1-1 1H21a1 1 0 0 1-1-1z" fill="#F472B6" />
    </svg>
  );
}

/** Empty-gallery state: a camera waiting for the first photo. */
export function EmptyGallery({ className = '' }: SvgProps) {
  return (
    <svg viewBox="0 0 120 96" role="img" aria-label="No photos yet" className={className} xmlns="http://www.w3.org/2000/svg">
      <rect x="14" y="30" width="92" height="54" rx="12" fill="#FDF2F8" stroke="#FBCFE8" strokeWidth="2" />
      <path d="M46 30l6-9h16l6 9" fill="none" stroke="#FBCFE8" strokeWidth="2" strokeLinejoin="round" />
      <circle cx="60" cy="57" r="17" fill="#FFFFFF" stroke="#F472B6" strokeWidth="2" />
      <circle cx="60" cy="57" r="8" fill="#DB2777" opacity="0.85" />
      <circle cx="92" cy="42" r="3.5" fill="#C99A3B" />
      <path d="M96 74c5-7 16-3 0 9-16-12-5-16 0-9z" fill="#F472B6" opacity="0.8" />
    </svg>
  );
}

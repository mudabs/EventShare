import Link from 'next/link';

/** Site-wide footer with the legal links (rendered once in the root layout). */
export function SiteFooter() {
  const year = new Date().getFullYear();
  return (
    <footer className="mt-16 border-t border-brand/10 py-10 text-center text-sm text-ink/50">
      <span className="script text-xl">EventShare</span>
      <p className="mt-1">Made for the moments worth keeping.</p>
      <nav className="mt-4 flex justify-center gap-5" aria-label="Legal">
        <Link href="/privacy" className="hover:text-brand">Privacy</Link>
        <Link href="/terms" className="hover:text-brand">Terms</Link>
        <Link href="/pricing" className="hover:text-brand">Pricing</Link>
      </nav>
      <p className="mt-3 text-xs text-ink/40">© {year} EventShare</p>
    </footer>
  );
}

/** One-line agreement notice shown next to join, upload and create actions. */
export function ConsentNote({ action, host = false }: { action: string; host?: boolean }) {
  return (
    <p className="text-xs text-ink/50">
      By {action}, you agree to the{' '}
      <Link href="/terms" className="underline underline-offset-2 hover:text-brand">Terms</Link>
      {host
        ? ' and will let your guests know their photos are shared in the event gallery. '
        : ' and confirm you have the right to share what you upload. '}
      See how we handle data in the{' '}
      <Link href="/privacy" className="underline underline-offset-2 hover:text-brand">Privacy Policy</Link>.
    </p>
  );
}

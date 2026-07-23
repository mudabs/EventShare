'use client';

import Link from 'next/link';
import { SignedIn, SignedOut, SignInButton, UserButton } from '@clerk/nextjs';
import { AdminNavLink } from './AdminNavLink';

export function Header() {
  return (
    <header className="sticky top-0 z-30 flex items-center justify-between border-b border-brand/10 bg-cream/80 px-4 py-3 backdrop-blur-md sm:px-6">
      <Link href="/" className="flex items-baseline gap-1.5" aria-label="EventShare home">
        <span className="script text-3xl leading-none">Event</span>
        <span className="font-serif text-xl font-semibold text-wine">Share</span>
      </Link>
      <nav className="flex items-center gap-3 sm:gap-5">
        <Link
          href="/pricing"
          className="hidden text-sm font-medium text-ink/70 hover:text-brand sm:inline"
        >
          Pricing
        </Link>
        <SignedOut>
          <SignInButton mode="modal">
            <button className="btn-primary">Sign in</button>
          </SignInButton>
        </SignedOut>
        <SignedIn>
          <Link href="/dashboard" className="text-sm font-medium text-ink/70 hover:text-brand">
            Dashboard
          </Link>
          <AdminNavLink />
          <UserButton afterSignOutUrl="/" />
        </SignedIn>
      </nav>
    </header>
  );
}

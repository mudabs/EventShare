'use client';

import Link from 'next/link';
import { SignedIn, SignedOut, SignInButton, UserButton } from '@clerk/nextjs';
import { AdminNavLink } from './AdminNavLink';

export function Header() {
  return (
    <header className="flex items-center justify-between border-b border-slate-200 bg-white px-4 py-3">
      <Link href="/" className="text-lg font-bold text-brand">
        EventShare
      </Link>
      <nav className="flex items-center gap-4">
        <Link href="/pricing" className="text-sm font-medium text-slate-600 hover:text-brand">
          Pricing
        </Link>
        <SignedOut>
          <SignInButton mode="modal">
            <button className="rounded-md bg-brand px-4 py-2 text-sm font-medium text-white hover:bg-brand-dark">
              Sign in
            </button>
          </SignInButton>
        </SignedOut>
        <SignedIn>
          <Link href="/dashboard" className="text-sm font-medium text-slate-700 hover:text-brand">
            Dashboard
          </Link>
          <AdminNavLink />
          <UserButton afterSignOutUrl="/" />
        </SignedIn>
      </nav>
    </header>
  );
}

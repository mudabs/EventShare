'use client';

import { SignedIn } from '@clerk/nextjs';
import Link from 'next/link';
import { usePathname } from 'next/navigation';

const items = [
  { href: '/', label: 'Home' },
  { href: '/dashboard', label: 'My Events' },
  { href: '/pricing', label: 'Plans' }
];

export function BottomNav() {
  const pathname = usePathname();
  return (
    <SignedIn>
      <nav className="fixed inset-x-0 bottom-0 z-40 flex border-t border-slate-200 bg-white sm:hidden">
        {items.map((item) => {
          const active = pathname === item.href;
          return (
            <Link
              key={item.href}
              href={item.href}
              className={`flex-1 py-3 text-center text-xs ${active ? 'font-semibold text-brand' : 'text-slate-500'}`}
            >
              {item.label}
            </Link>
          );
        })}
      </nav>
      <div className="h-14 sm:hidden" aria-hidden="true" />
    </SignedIn>
  );
}

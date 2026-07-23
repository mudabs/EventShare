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
      <nav className="fixed inset-x-0 bottom-0 z-40 flex border-t border-brand/10 bg-cream/90 backdrop-blur-md sm:hidden">
        {items.map((item) => {
          const active = pathname === item.href;
          return (
            <Link
              key={item.href}
              href={item.href}
              className={`flex-1 py-3 text-center text-xs transition-colors ${
                active ? 'font-semibold text-brand' : 'text-ink/50 hover:text-brand'
              }`}
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

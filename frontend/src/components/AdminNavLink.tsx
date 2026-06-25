'use client';

import { useAuth } from '@clerk/nextjs';
import { useQuery } from '@tanstack/react-query';
import Link from 'next/link';
import { fetchProfile } from '@/lib/api';

export function AdminNavLink() {
  const { getToken, isSignedIn } = useAuth();
  const { data } = useQuery({
    queryKey: ['profile'],
    queryFn: async () => fetchProfile((await getToken()) ?? ''),
    enabled: !!isSignedIn
  });
  if (data?.role !== 'ADMIN') {
    return null;
  }
  return (
    <Link href="/admin" className="text-sm font-medium text-amber-600 hover:text-amber-700">
      Admin
    </Link>
  );
}

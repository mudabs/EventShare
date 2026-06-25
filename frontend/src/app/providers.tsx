'use client';

import { useAuth } from '@clerk/nextjs';
import { QueryClient, QueryClientProvider, useQueryClient } from '@tanstack/react-query';
import { useEffect, useRef, useState, type ReactNode } from 'react';

/** Wipes all cached query data when the user signs out, so no stale tenant data lingers. */
function AuthCacheSync() {
  const { isLoaded, isSignedIn } = useAuth();
  const queryClient = useQueryClient();
  const wasSignedIn = useRef<boolean | undefined>(undefined);

  useEffect(() => {
    if (!isLoaded) return;
    if (wasSignedIn.current === true && !isSignedIn) {
      queryClient.clear();
    }
    wasSignedIn.current = !!isSignedIn;
  }, [isLoaded, isSignedIn, queryClient]);

  return null;
}

export function Providers({ children }: { children: ReactNode }) {
  const [client] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: { staleTime: 15_000, refetchOnWindowFocus: false, retry: 1 }
        }
      })
  );
  return (
    <QueryClientProvider client={client}>
      <AuthCacheSync />
      {children}
    </QueryClientProvider>
  );
}

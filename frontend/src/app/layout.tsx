import './globals.css';
import type { Metadata, Viewport } from 'next';
import { ClerkProvider } from '@clerk/nextjs';
import { BottomNav } from '@/components/BottomNav';
import { ServiceWorkerRegister } from '@/components/ServiceWorkerRegister';
import { Providers } from './providers';

export const metadata: Metadata = {
  title: 'EventShare',
  description: 'Collect every photo and video from your event in one shared gallery.',
  manifest: '/manifest.webmanifest',
  applicationName: 'EventShare'
};

export const viewport: Viewport = {
  themeColor: '#4f46e5',
  width: 'device-width',
  initialScale: 1
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <ClerkProvider>
      <html lang="en">
        <body>
          <Providers>
            {children}
            <BottomNav />
          </Providers>
          <ServiceWorkerRegister />
        </body>
      </html>
    </ClerkProvider>
  );
}

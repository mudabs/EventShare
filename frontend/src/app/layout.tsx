import './globals.css';
import type { Metadata, Viewport } from 'next';
import { Cormorant_Garamond, Inter, Great_Vibes } from 'next/font/google';
import { ClerkProvider } from '@clerk/nextjs';
import { BottomNav } from '@/components/BottomNav';
import { ServiceWorkerRegister } from '@/components/ServiceWorkerRegister';
import { Providers } from './providers';

const sans = Inter({
  subsets: ['latin'],
  variable: '--font-inter',
  display: 'swap'
});

const serif = Cormorant_Garamond({
  subsets: ['latin'],
  weight: ['400', '500', '600', '700'],
  variable: '--font-cormorant',
  display: 'swap'
});

const script = Great_Vibes({
  subsets: ['latin'],
  weight: '400',
  variable: '--font-greatvibes',
  display: 'swap'
});

export const metadata: Metadata = {
  title: 'EventShare',
  description: 'Collect every photo and video from your event in one shared gallery.',
  manifest: '/manifest.webmanifest',
  applicationName: 'EventShare'
};

export const viewport: Viewport = {
  themeColor: '#DB2777',
  width: 'device-width',
  initialScale: 1
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <ClerkProvider
      appearance={{
        variables: {
          colorPrimary: '#DB2777',
          colorText: '#4A3540',
          borderRadius: '0.75rem'
        }
      }}
    >
      <html lang="en" className={`${sans.variable} ${serif.variable} ${script.variable}`}>
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

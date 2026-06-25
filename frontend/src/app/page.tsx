import Link from 'next/link';
import { Header } from '@/components/Header';
import { JoinByCodeForm } from '@/components/JoinByCodeForm';

export default function HomePage() {
  return (
    <div className="min-h-screen">
      <Header />
      <main className="mx-auto max-w-3xl px-4 py-16 text-center">
        <h1 className="text-4xl font-bold tracking-tight text-slate-900">
          Every photo from your event, in one place.
        </h1>
        <p className="mx-auto mt-4 max-w-xl text-lg text-slate-600">
          Create an event, share a QR code, and let your guests upload their photos and
          videos straight to a shared gallery. No app, no accounts for guests.
        </p>

        <div className="mt-10 flex flex-col items-center gap-6">
          <Link
            href="/dashboard"
            className="rounded-md bg-brand px-6 py-3 text-lg font-medium text-white hover:bg-brand-dark"
          >
            Create an event
          </Link>

          <div className="flex w-full max-w-sm flex-col items-center gap-2">
            <span className="text-sm text-slate-500">Got a code from a host?</span>
            <JoinByCodeForm />
          </div>
        </div>
      </main>
    </div>
  );
}

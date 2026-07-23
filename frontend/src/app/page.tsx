import Link from 'next/link';
import { Header } from '@/components/Header';
import { JoinByCodeForm } from '@/components/JoinByCodeForm';

const STEPS = [
  {
    title: 'Create your event',
    body: 'Set up a room for your wedding or celebration in seconds.'
  },
  {
    title: 'Share a QR or link',
    body: 'Guests scan and join instantly. No app, no accounts to create.'
  },
  {
    title: 'Watch the gallery fill',
    body: 'Every photo and video lands in one beautiful shared album.'
  }
];

export default function HomePage() {
  return (
    <div className="min-h-screen">
      <Header />

      <main>
        {/* Hero */}
        <section className="mx-auto max-w-3xl px-4 pb-16 pt-20 text-center sm:pt-28">
          <p className="script text-4xl sm:text-5xl">Every moment, together</p>
          <h1 className="mt-3 text-4xl font-semibold leading-tight sm:text-6xl">
            Every photo from your event, in one place.
          </h1>
          <p className="mx-auto mt-5 max-w-xl text-lg text-ink/70">
            Create an event, share a QR code, and let your guests upload their photos and
            videos straight to a shared gallery. No app, no accounts for guests.
          </p>

          <div className="mt-9 flex flex-col items-center gap-8">
            <Link href="/dashboard" className="btn-primary px-8 py-3.5 text-base">
              Create an event
            </Link>

            <div className="flex w-full max-w-sm flex-col items-center gap-2">
              <span className="text-sm text-ink/50">Got a code from a host?</span>
              <JoinByCodeForm />
            </div>
          </div>
        </section>

        <div className="divider-flourish" />

        {/* How it works */}
        <section className="mx-auto max-w-5xl px-4 py-16">
          <h2 className="text-center text-3xl font-semibold">How it works</h2>
          <div className="mt-10 grid gap-6 sm:grid-cols-3">
            {STEPS.map((step, i) => (
              <div key={step.title} className="card p-6 text-center">
                <div className="mx-auto flex h-11 w-11 items-center justify-center rounded-full bg-blush font-serif text-lg font-semibold text-brand">
                  {i + 1}
                </div>
                <h3 className="mt-4 text-xl font-semibold">{step.title}</h3>
                <p className="mt-2 text-sm text-ink/70">{step.body}</p>
              </div>
            ))}
          </div>
        </section>

        <footer className="border-t border-brand/10 py-10 text-center text-sm text-ink/50">
          <span className="script text-xl">EventShare</span>
          <p className="mt-1">Made for the moments worth keeping.</p>
        </footer>
      </main>
    </div>
  );
}

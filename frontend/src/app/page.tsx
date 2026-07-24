import Link from 'next/link';
import { Header } from '@/components/Header';
import { JoinByCodeForm } from '@/components/JoinByCodeForm';
import { PhotoSlot } from '@/components/PhotoSlot';
import { HeroScene, StepCreate, StepShare, StepGallery } from '@/components/illustrations';
import { galleryPreview } from '@/lib/stockImages';

const STEPS = [
  {
    Icon: StepCreate,
    title: 'Create your event',
    body: 'Set up a room for your wedding or celebration in seconds.'
  },
  {
    Icon: StepShare,
    title: 'Share a QR or link',
    body: 'Guests scan and join instantly. No app, no accounts to create.'
  },
  {
    Icon: StepGallery,
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
        <section className="mx-auto max-w-3xl px-4 pb-10 pt-20 text-center sm:pt-24">
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

          <HeroScene className="mx-auto mt-12 w-full max-w-md" />
        </section>

        <div className="divider-flourish" />

        {/* How it works */}
        <section className="mx-auto max-w-5xl px-4 py-16">
          <h2 className="text-center text-3xl font-semibold">How it works</h2>
          <div className="mt-10 grid gap-6 sm:grid-cols-3">
            {STEPS.map(({ Icon, title, body }) => (
              <div key={title} className="card p-6 text-center">
                <Icon className="mx-auto h-16 w-16" />
                <h3 className="mt-3 text-xl font-semibold">{title}</h3>
                <p className="mt-2 text-sm text-ink/70">{body}</p>
              </div>
            ))}
          </div>
        </section>

        {/* Gallery preview (fillable photo slots) */}
        <section className="mx-auto max-w-5xl px-4 pb-16">
          <div className="text-center">
            <p className="script text-3xl">A peek at the gallery</p>
            <h2 className="mt-1 text-3xl font-semibold">Memories from every guest</h2>
          </div>
          <div className="mt-8 columns-2 gap-4 sm:columns-3 [&>*]:mb-4">
            {galleryPreview.map((p, i) => (
              <PhotoSlot key={i} src={p.src} ratio={p.ratio} alt="Event photo" className="break-inside-avoid" />
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

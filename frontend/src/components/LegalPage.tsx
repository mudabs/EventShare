import Link from 'next/link';
import { Header } from './Header';
import { LEGAL } from '@/lib/legal';

/** Shared layout for /privacy and /terms: readable measure, numbered sections, cross-links. */
export function LegalPage({
  title,
  intro,
  children
}: {
  title: string;
  intro: React.ReactNode;
  children: React.ReactNode;
}) {
  return (
    <div className="min-h-screen">
      <Header />
      <main className="mx-auto max-w-2xl px-4 py-10">
        <p className="text-xs uppercase tracking-wide text-ink/50">Effective {LEGAL.effectiveDate}</p>
        <h1 className="mt-1 text-4xl font-semibold">{title}</h1>
        <div className="mt-4 text-ink/80">{intro}</div>
        <div className="legal mt-8 space-y-8 text-[15px] leading-relaxed text-ink/80">{children}</div>
        <p className="mt-12 border-t border-brand/10 pt-6 text-sm text-ink/60">
          See also the <Link href="/privacy" className="underline underline-offset-2 hover:text-brand">Privacy Policy</Link>{' '}
          and <Link href="/terms" className="underline underline-offset-2 hover:text-brand">Terms of Use</Link>.
        </p>
      </main>
    </div>
  );
}

export function Section({ id, title, children }: { id: string; title: string; children: React.ReactNode }) {
  return (
    <section id={id} className="scroll-mt-20">
      <h2 className="mb-2 font-serif text-2xl font-semibold text-wine">{title}</h2>
      <div className="space-y-3">{children}</div>
    </section>
  );
}

export function Bullets({ items }: { items: React.ReactNode[] }) {
  return (
    <ul className="list-disc space-y-1.5 pl-5">
      {items.map((item, i) => (
        <li key={i}>{item}</li>
      ))}
    </ul>
  );
}

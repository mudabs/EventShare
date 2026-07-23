import { Header } from '@/components/Header';
import { PricingPlans } from '@/components/PricingPlans';
import { RedeemPromo } from '@/components/RedeemPromo';

export default function PricingPage() {
  return (
    <div className="min-h-screen">
      <Header />
      <main className="mx-auto max-w-6xl px-4 py-10">
        <div className="mb-10 text-center">
          <p className="script text-3xl">Simple pricing</p>
          <h1 className="mt-1 text-4xl font-semibold">Plans for every event</h1>
          <p className="mt-3 text-ink/70">Start free. Upgrade when your event grows.</p>
          <div className="divider-flourish mt-6" />
        </div>
        <PricingPlans />
        <RedeemPromo />
      </main>
    </div>
  );
}

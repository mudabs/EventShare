import { Header } from '@/components/Header';
import { PricingPlans } from '@/components/PricingPlans';
import { RedeemPromo } from '@/components/RedeemPromo';

export default function PricingPage() {
  return (
    <div className="min-h-screen">
      <Header />
      <main className="mx-auto max-w-6xl px-4 py-10">
        <div className="mb-8 text-center">
          <h1 className="text-3xl font-bold">Plans for every event</h1>
          <p className="mt-2 text-slate-600">Start free. Upgrade when your event grows.</p>
        </div>
        <PricingPlans />
        <RedeemPromo />
      </main>
    </div>
  );
}

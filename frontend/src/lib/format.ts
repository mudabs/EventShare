import type { Plan } from './types';

export function formatBytes(bytes: number | null): string {
  if (bytes === null) return 'Unlimited';
  if (bytes >= 1024 ** 3) return `${(bytes / 1024 ** 3).toFixed(0)} GB`;
  if (bytes >= 1024 ** 2) return `${(bytes / 1024 ** 2).toFixed(0)} MB`;
  return `${bytes} B`;
}

export function formatLimit(n: number | null): string {
  return n === null ? 'Unlimited' : n.toLocaleString();
}

export function formatPrice(plan: Plan): string {
  if (plan.priceCents === 0) return 'Free';
  const dollars = (plan.priceCents / 100).toFixed(0);
  const suffix =
    plan.billingInterval === 'ONE_TIME'
      ? ' once'
      : plan.billingInterval === 'MONTH'
        ? ' /mo'
        : plan.billingInterval === 'YEAR'
          ? ' /yr'
          : '';
  return `$${dollars}${suffix}`;
}

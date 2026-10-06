/**
 * Small dependency-free bar chart used by the event and platform analytics.
 *
 * Bars and labels live in separate rows so every bar shares one baseline (a shared column
 * once let unlabelled bars drop below it). Each bar shows its value above it, so the chart
 * stays readable without an axis. `showZeroValues` prints "0" over empty periods, which
 * makes sparse series (one busy month among quiet ones) easier to read.
 */
export interface BarDatum {
  key: string;
  label: string;
  value: number;
  /** Tooltip text; defaults to "label: value". */
  title?: string;
}

export function BarChart({
  data,
  ariaLabel,
  showZeroValues = false,
  hideOddLabelsOnPhones = false,
  heightClass = 'h-40'
}: {
  data: BarDatum[];
  ariaLabel: string;
  showZeroValues?: boolean;
  /** For dense series (e.g. 14 days): label every other bar on phones, every bar on wider screens. */
  hideOddLabelsOnPhones?: boolean;
  heightClass?: string;
}) {
  const max = Math.max(1, ...data.map((d) => d.value));
  const lastIndex = data.length - 1;
  const summary = data.map((d) => `${d.label}: ${d.value}`).join(', ');

  return (
    <div role="img" aria-label={`${ariaLabel}. ${summary}`}>
      <div className={`flex ${heightClass} items-end gap-1 border-b border-brand/20`}>
        {data.map((d) => (
          <div
            key={d.key}
            className="flex h-full flex-1 flex-col items-center justify-end"
            title={d.title ?? `${d.label}: ${d.value}`}
          >
            {(d.value > 0 || showZeroValues) && (
              <span
                className={`mb-0.5 text-[10px] tabular-nums leading-none ${d.value > 0 ? 'text-ink/60' : 'text-ink/30'}`}
              >
                {d.value}
              </span>
            )}
            <div
              className="w-full rounded-t bg-brand transition-all"
              // 85% leaves headroom for the value label above the tallest bar.
              style={{ height: `${(d.value / max) * 85}%`, minHeight: d.value > 0 ? '3px' : '0' }}
            />
          </div>
        ))}
      </div>
      <div className="mt-1 flex gap-1" aria-hidden="true">
        {data.map((d, i) => (
          <span
            key={d.key}
            className={`h-3 flex-1 truncate text-center text-[10px] leading-3 text-ink/40 ${
              hideOddLabelsOnPhones && i % 2 === 1 && i !== lastIndex ? 'invisible sm:visible' : ''
            }`}
          >
            {d.label}
          </span>
        ))}
      </div>
    </div>
  );
}

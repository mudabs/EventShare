import type { Config } from 'tailwindcss';

const config: Config = {
  content: ['./src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        // Romantic wedding palette. `brand` stays the primary token so existing
        // bg-brand / text-brand usages instantly become rose.
        brand: {
          DEFAULT: '#DB2777', // rose
          dark: '#BE185D',
          soft: '#F472B6'
        },
        wine: '#831843', // deep headings
        gold: {
          DEFAULT: '#A16207',
          soft: '#C99A3B'
        },
        blush: '#FDF2F8', // page background
        cream: '#FFFBF8',
        ink: '#4A3540' // warm body text
      },
      fontFamily: {
        sans: ['var(--font-inter)', 'ui-sans-serif', 'system-ui', 'sans-serif'],
        serif: ['var(--font-cormorant)', 'Georgia', 'Cambria', 'serif'],
        script: ['var(--font-greatvibes)', 'cursive']
      },
      boxShadow: {
        soft: '0 18px 40px -20px rgba(131, 24, 67, 0.28)',
        card: '0 1px 3px rgba(131, 24, 67, 0.08), 0 8px 24px -16px rgba(131, 24, 67, 0.20)'
      },
      backgroundImage: {
        'blush-radial':
          'radial-gradient(1200px 500px at 50% -10%, rgba(244,114,182,0.18), transparent 60%)'
      }
    }
  },
  plugins: []
};

export default config;

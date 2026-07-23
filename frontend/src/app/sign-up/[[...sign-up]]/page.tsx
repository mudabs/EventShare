import Link from 'next/link';
import { SignUp } from '@clerk/nextjs';

export default function SignUpPage() {
  return (
    <main className="flex min-h-screen flex-col items-center justify-center px-4 py-12">
      <Link href="/" className="mb-6 flex items-baseline gap-1.5" aria-label="EventShare home">
        <span className="script text-4xl leading-none">Event</span>
        <span className="font-serif text-2xl font-semibold text-wine">Share</span>
      </Link>
      <SignUp />
    </main>
  );
}

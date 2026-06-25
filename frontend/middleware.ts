import { clerkMiddleware, createRouteMatcher } from '@clerk/nextjs/server';

// Only the host dashboard requires authentication. Guest pages (/e/<code>) and
// the landing page stay public so guests can join with zero friction.
const isProtectedRoute = createRouteMatcher(['/dashboard(.*)', '/events(.*)', '/admin(.*)']);

export default clerkMiddleware(async (auth, req) => {
  if (isProtectedRoute(req)) {
    await auth.protect();
  }
});

export const config = {
  matcher: [
    // Run on everything except static assets and Next internals.
    '/((?!_next|[^?]*\\.(?:html?|css|js(?!on)|jpe?g|webp|png|gif|svg|ttf|woff2?|ico|csv|docx?|xlsx?|zip|webmanifest)).*)',
    '/(api|trpc)(.*)'
  ]
};

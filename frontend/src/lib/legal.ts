/**
 * Facts shared by the Privacy Policy, Terms of Use and consent notes. Keep these in sync
 * with what the code actually does; the pages promise exactly this behaviour.
 * See docs/changes/2026-10-05-privacy-terms-and-reporting.md.
 */
export const LEGAL = {
  serviceName: 'EventShare',
  operator: 'Munashe Mudabura',
  location: 'St. Louis, Missouri, United States',
  /** Set NEXT_PUBLIC_CONTACT_EMAIL at build time. */
  contactEmail: process.env.NEXT_PUBLIC_CONTACT_EMAIL?.trim() || '',
  effectiveDate: 'October 5, 2026',
  auditIpRetentionDays: 90,
  minimumAge: 13
} as const;

/** "email us at x" when configured, otherwise the in-app route. */
export function contactSentence(): string {
  return LEGAL.contactEmail
    ? `email ${LEGAL.contactEmail}`
    : 'use the Report button on the photo, or contact the operator through munashemudabura.com';
}

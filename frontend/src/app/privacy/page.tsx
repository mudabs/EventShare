import type { Metadata } from 'next';
import Link from 'next/link';
import { Bullets, LegalPage, Section } from '@/components/LegalPage';
import { LEGAL, contactSentence } from '@/lib/legal';

export const metadata: Metadata = {
  title: 'Privacy Policy | EventShare',
  description: 'What EventShare collects, why, who can see it, and how to have it removed.'
};

/*
 * Every statement here describes behaviour that exists in the code. When the code changes
 * (new data, new provider, new retention), update this page in the same change.
 * See docs/changes/2026-10-05-privacy-terms-and-reporting.md for the mapping.
 */
export default function PrivacyPage() {
  return (
    <LegalPage
      title="Privacy Policy"
      intro={
        <p>
          {LEGAL.serviceName} lets a host create an event and invite guests to share photos and videos in
          one gallery. This page explains, in plain language, what we collect, why, who can see it, and how
          you can have it removed. {LEGAL.serviceName} is run by {LEGAL.operator}, {LEGAL.location}.
        </p>
      }
    >
      <Section id="summary" title="The short version">
        <Bullets
          items={[
            'Anyone with an event’s invite link or QR code can view that event’s gallery. Share links with care.',
            'We use your information only to run the service. We do not sell it, and we do not show ads.',
            'Photos are stored exactly as uploaded, including any location data your camera saved in the file.',
            'You can ask us to delete your account, your photos, or a photo of you at any time.'
          ]}
        />
      </Section>

      <Section id="collect" title="What we collect">
        <p>
          <strong>If you create an account</strong> (hosts, and guests who sign in): your name, email address and
          profile picture, provided through our sign-in provider, Clerk. We never see your password.
        </p>
        <p>
          <strong>If you join an event as a guest</strong> without an account: the display name you type. Your
          browser keeps a small guest identifier for that event so you can delete your own uploads later.
        </p>
        <p>
          <strong>Photos and videos you upload</strong>, with their file name, size, dimensions, upload time and a
          digital fingerprint (used to spot exact duplicates). Files are kept as uploaded, so any information
          embedded in them, such as camera model or GPS location, is kept too. If you prefer, turn off location
          tagging on your camera or remove it before uploading.
        </p>
        <p>
          <strong>Technical information</strong>: your IP address, recorded with security-relevant actions such as
          uploads, joins and reports, and cleared from those records after {LEGAL.auditIpRetentionDays} days. To
          count unique visitors and enforce fair-use limits we also store one-way scrambled (hashed) versions of
          your IP address, which we cannot turn back into the address.
        </p>
        <p>
          <strong>Payments</strong>: paid plans are processed by Stripe. We store your Stripe customer reference and
          subscription status. Card details go directly to Stripe; we never see or store them.
        </p>
        <p>
          <strong>Reports</strong>: if you report a photo, we store the reason and any note you add.
        </p>
      </Section>

      <Section id="use" title="How we use it">
        <Bullets
          items={[
            'To run events and galleries: show photos to the people the host invited, make previews, detect duplicates, and let hosts moderate.',
            'To keep the service safe: prevent abuse and spam, enforce plan and fair-use limits, and investigate reports.',
            'To handle accounts and billing.',
            'To understand basic usage of an event (for example, how many people visited), shown only to that event’s host.'
          ]}
        />
        <p>
          We do not sell or share your personal information for advertising, we do not use advertising
          trackers, and we do not use facial recognition. If we ever add features that analyse faces, we will
          ask for your consent first.
        </p>
      </Section>

      <Section id="visibility" title="Who can see what">
        <Bullets
          items={[
            'An event’s gallery is visible to anyone who has its invite link or QR code. Links can be forwarded, so treat them like an unlisted album.',
            'Uploader names are shown next to photos unless the host turns on anonymous uploads.',
            'The host can see the guest list, uploads, visitor counts and reports for their event, and can hide or remove any photo.',
            'Our service providers process data on our behalf, only to provide the service: Clerk (sign-in), Stripe (payments), Cloudflare (file storage and delivery) and our hosting providers.',
            'We may disclose information if the law requires it, or to protect people from harm. We report child sexual abuse material to the U.S. National Center for Missing & Exploited Children (NCMEC), as U.S. law requires.'
          ]}
        />
      </Section>

      <Section id="cookies" title="Cookies and browser storage">
        <p>
          We use only what the service needs to work: sign-in cookies set by Clerk, browser storage that
          remembers your guest identity for each event you join, and the app’s offline files so it loads
          quickly. We do not use advertising
          or analytics cookies, so there is nothing to opt out of.
        </p>
      </Section>

      <Section id="retention" title="How long we keep it">
        <Bullets
          items={[
            'Account information: until you ask us to delete your account.',
            'Photos and videos: until the uploader or host deletes them, or the event or account is deleted. Deleted items disappear from the gallery immediately and are erased permanently, including stored copies, 30 days later (the host can restore them until then). Deleted events are erased the same way after 30 days.',
            `IP addresses in security records: ${LEGAL.auditIpRetentionDays} days.`,
            'The public demo event is wiped and recreated every night.',
            'Billing records: as long as tax and accounting law requires.'
          ]}
        />
      </Section>

      <Section id="rights" title="Your choices and rights">
        <p>
          You can delete your own uploads from the gallery at any time, and use <strong>Report</strong> on any photo
          to ask the host to remove it, for example a photo of you. To access, correct or delete your information,
          or to have something erased permanently, {contactSentence()}. We will respond within 30 days.
        </p>
        <p>
          Depending on where you live (for example in the EU, UK or California), you may also have the right to
          object to or restrict some uses, to receive a copy of your data, and to complain to your data protection
          authority. We will not treat you differently for using these rights.
        </p>
      </Section>

      <Section id="children" title="Children">
        <p>
          {LEGAL.serviceName} is not meant for children under {LEGAL.minimumAge}, and we do not knowingly collect
          information from them. Events often include photos of children taken by adult guests; parents or
          guardians can report such a photo or contact us to have it removed.
        </p>
      </Section>

      <Section id="security" title="Security and where data is stored">
        <p>
          Data is sent over encrypted connections, files are delivered through short-lived signed links, and
          only the event host can manage an event. No system is perfectly secure, so please share invite links
          only with people you trust. We are based in the United States and process data there and with the
          providers listed above.
        </p>
      </Section>

      <Section id="changes" title="Changes and contact">
        <p>
          If we change this policy we will update the date at the top, and tell account holders about important
          changes. Questions or requests: {contactSentence()}. See also our{' '}
          <Link href="/terms" className="underline underline-offset-2 hover:text-brand">Terms of Use</Link>.
        </p>
      </Section>
    </LegalPage>
  );
}

import type { Metadata } from 'next';
import Link from 'next/link';
import { Bullets, LegalPage, Section } from '@/components/LegalPage';
import { LEGAL, contactSentence } from '@/lib/legal';

export const metadata: Metadata = {
  title: 'Terms of Use | EventShare',
  description: 'The rules for hosting events and sharing photos on EventShare.'
};

export default function TermsPage() {
  return (
    <LegalPage
      title="Terms of Use"
      intro={
        <p>
          These terms are the agreement between you and {LEGAL.operator}, who runs {LEGAL.serviceName}. By creating
          an event, joining one, or uploading to one, you agree to them. Please also read the{' '}
          <Link href="/privacy" className="underline underline-offset-2 hover:text-brand">Privacy Policy</Link>.
        </p>
      }
    >
      <Section id="eligibility" title="Who can use EventShare">
        <p>
          You must be at least {LEGAL.minimumAge} years old. If you are under the age of majority where you live,
          use {LEGAL.serviceName} only with a parent or guardian’s permission. If you use it for an organisation,
          you confirm you are allowed to accept these terms for it.
        </p>
      </Section>

      <Section id="hosts" title="Hosting an event">
        <Bullets
          items={[
            'You are responsible for your event and for who you share its invite link or QR code with. Anyone with the link can view the gallery.',
            'Let your guests know photos will be shared in a gallery, and respect anyone who asks not to be included.',
            'Use the moderation tools to hide or remove photos that should not be there, and act on reports from your guests.',
            'Keep your account secure. You are responsible for activity on it.'
          ]}
        />
      </Section>

      <Section id="content" title="Your photos and videos">
        <p>
          <strong>You keep ownership</strong> of what you upload. To run the service you give us a limited licence
          to store, copy, process (for example, to make previews) and display your uploads to the people who have
          access to that event, and to let them download them if the host allows downloads. The licence ends when
          your upload is deleted, apart from copies we must keep briefly for backups or by law.
        </p>
        <p>When you upload something, you confirm that:</p>
        <Bullets
          items={[
            'you took it or have the right to share it;',
            'people who can be identified in it would not reasonably object to it being shared with the event’s guests, and you have their consent where the law requires it;',
            'it does not break these terms or the law.'
          ]}
        />
      </Section>

      <Section id="rules" title="What is not allowed">
        <Bullets
          items={[
            'Sexual content involving anyone under 18. We remove it and report it to NCMEC and law enforcement.',
            'Intimate or sexual images of anyone without their consent.',
            'Content that harasses, threatens, bullies or exposes private information about someone.',
            'Content that infringes someone else’s copyright or other rights.',
            'Illegal content, malware, or anything designed to harm people or systems.',
            'Scraping, overloading or attacking the service, getting around plan or demo limits, or accessing events you were not invited to.'
          ]}
        />
      </Section>

      <Section id="reporting" title="Reporting and removing content">
        <p>
          Every photo has a <strong>Report</strong> option, for example if it shows you and you want it removed, or
          if it breaks these rules. Reports go to the event host. When several people report a photo, or anyone
          reports a child-safety concern, it is hidden automatically until the host reviews it. We may also remove
          content or restrict accounts that break these terms. To reach us directly, {contactSentence()}.
        </p>
      </Section>

      <Section id="copyright" title="Copyright complaints">
        <p>
          If you believe something on {LEGAL.serviceName} infringes your copyright, send a notice to our designated
          agent, {LEGAL.operator}
          {LEGAL.contactEmail ? `, at ${LEGAL.contactEmail}` : ''}, with: your contact details; the work you own;
          the link to the item; a statement that you believe in good faith the use is not authorised; a statement,
          under penalty of perjury, that your notice is accurate and you are the owner or authorised to act for
          them; and your signature. If your content was removed and you believe that was a mistake, you can send a
          counter-notice. We disable accounts of people who repeatedly infringe.
        </p>
      </Section>

      <Section id="plans" title="Plans and payments">
        <p>
          Paid plans are billed through Stripe at the price shown when you buy. Monthly plans renew until you
          cancel, which you can do at any time from the billing portal; cancelling stops future renewals. Plan features and limits are described on the{' '}
          <Link href="/pricing" className="underline underline-offset-2 hover:text-brand">Pricing</Link> page. If
          you think you were charged in error, {contactSentence()}.
        </p>
      </Section>

      <Section id="demo" title="The public demo">
        <p>
          The demo event and demo accounts are shared with everyone and reset every night. Do not upload anything
          private to the demo. Demo uploads are limited and may be removed at any time.
        </p>
      </Section>

      <Section id="service" title="The service itself">
        <p>
          We work to keep {LEGAL.serviceName} available and your uploads safe, but it is provided “as is”, without
          warranties of any kind, and we cannot promise it will always be available or error-free. Keep your own
          copies of photos that matter to you. To the extent the law allows, we are not liable for indirect or
          consequential losses, and our total liability is limited to the amount you paid us in the 12 months before
          the claim. You agree to cover claims against us that arise from content you upload in breach of these
          terms.
        </p>
        <p>
          You can stop using {LEGAL.serviceName} at any time. We may suspend or end access for anyone who breaks
          these terms.
        </p>
      </Section>

      <Section id="general" title="Changes and governing law">
        <p>
          We may update these terms; the date at the top shows the latest version, and we will tell account holders
          about important changes. These terms are governed by the laws of the State of Missouri, USA, except where
          your local consumer law gives you rights that cannot be waived. Questions: {contactSentence()}.
        </p>
      </Section>
    </LegalPage>
  );
}

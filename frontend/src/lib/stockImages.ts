/**
 * Curated stock imagery (Unsplash, free license — no attribution required).
 * These are hotlinked from the Unsplash CDN and rendered via <PhotoSlot>, which
 * shows an on-brand placeholder if any URL fails to load, so nothing breaks.
 *
 * To use your OWN photos instead, drop files in /public/images and point these
 * at e.g. "/images/gallery-1.jpg" — local paths take precedence and remove the
 * external dependency. See public/images/README.md.
 */

const U = (id: string, w = 800) =>
  `https://images.unsplash.com/photo-${id}?auto=format&fit=crop&w=${w}&q=70`;

/** Landing "peek at the gallery" mosaic. */
export const galleryPreview: { src: string; ratio: string }[] = [
  { src: U('1519741497674-611481863552'), ratio: 'aspect-[3/4]' }, // wedding rings/hands
  { src: U('1511285560929-80b456fea0bc'), ratio: 'aspect-square' }, // reception table
  { src: U('1465495976277-4387d4b0b4c6'), ratio: 'aspect-square' }, // couple
  { src: U('1519225421980-715cb0215aed'), ratio: 'aspect-[3/4]' }, // celebration toast
  { src: U('1470217957101-da7150b9b681'), ratio: 'aspect-square' }, // guests dancing
  { src: U('1522673607200-164d1b6ce486'), ratio: 'aspect-square' } // bouquet
];

/** Wide hero/marketing image. */
export const heroPhoto = U('1519741497674-611481863552', 1200);

/** Optional per-event-type cover fallback (used where an event has no cover). */
export const eventTypeCover: Record<string, string> = {
  WEDDING: U('1465495976277-4387d4b0b4c6'),
  BIRTHDAY: U('1530103862676-de8c9debad1d'),
  GRADUATION: U('1523050854058-8df90110c9f1'),
  CONFERENCE: U('1540575467063-178a50c2df87'),
  FAMILY: U('1511895426328-dc8714191300'),
  REUNION: U('1529156069898-49953e39b3ac'),
  CHURCH: U('1438032005730-c779502df39b'),
  OTHER: U('1492684223066-81342ee5ff30')
};

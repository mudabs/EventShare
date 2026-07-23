# Drop-in photos

The landing page and photo slots auto-fill from files placed here. Until a file
exists, an on-brand placeholder is shown instead (nothing breaks if a file is
missing), so you can add these whenever you like.

## Expected files (landing gallery preview)

| File | Suggested shape | Used by |
|------|-----------------|---------|
| `gallery-1.jpg` | portrait (3:4) | Home → "A peek at the gallery" |
| `gallery-2.jpg` | square | " |
| `gallery-3.jpg` | square | " |
| `gallery-4.jpg` | portrait (3:4) | " |
| `gallery-5.jpg` | square | " |
| `gallery-6.jpg` | square | " |

## Tips

- Keep each image under ~300 KB (export as optimized JPG or WebP) for fast loads.
- Use real event photos you have rights to, or free-license sources (e.g. Unsplash).
- `.webp` works too — if you use it, update the `src` extensions in
  `src/app/page.tsx` (`GALLERY_PREVIEW`).
- To add a new slot elsewhere, use the `PhotoSlot` component:
  `<PhotoSlot src="/images/your-file.jpg" alt="..." ratio="aspect-square" />`

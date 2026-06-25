# EventShare Mobile

Separate Expo app for the mobile experience.

## What this includes

- guest join flow by invite code
- event gallery browsing
- upload from the device photo library or camera roll
- a host tools screen for authenticated event creation
- pull-to-refresh and upload hashing for stronger upload integrity

## Configure

Set the API URL for your backend:

```bash
EXPO_PUBLIC_API_BASE_URL=http://localhost:8080/api
```

## Run

```bash
cd mobile
npm install
npm run start
```

## Notes

- This project is intentionally separate from the existing web app.
- It reuses the same API surface, so backend work stays shared.
- The guest join and upload flows are the primary mobile experience.
- Host authentication can be swapped from the token placeholder to Clerk mobile auth next without touching the backend.

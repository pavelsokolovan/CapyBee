# 16 — PWA App Shell (Instant Load Regardless of Server State)

## Purpose

`13-session-persistence-instruction.md` and `14-offline-write-queue-instruction.md` fix session loss and make writes resilient to a sleeping Fly machine. This spec addresses the remaining piece: the React app itself (HTML/JS/CSS) is currently served *by* Spring Boot, so opening the app at all requires the same slow machine to be awake first. A cached app shell via a service worker means the app opens instantly — CapyBee's face, the landing/home layout, everything except live data — even while the API is still waking up underneath it.

This is should-have polish, not a blocker for `13`/`14`. Ship after those land.

---

## 1. Add the PWA plugin

```
npm install -D vite-plugin-pwa
```

### `app/ui/vite.config.ts`

```ts
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { VitePWA } from 'vite-plugin-pwa';

export default defineConfig({
  plugins: [
    react(),
    VitePWA({
      registerType: 'autoUpdate',
      includeAssets: ['favicon.ico'],
      manifest: {
        name: 'CapyBee',
        short_name: 'CapyBee',
        description: 'Together we build a new hive',
        theme_color: '#F5C842',
        background_color: '#FDF3D9',
        display: 'standalone',
        icons: [
          // reuse existing capybee avatar/icon assets sized to these — see
          // app/ui/src/assets for source art, export 192/512 png copies
          { src: '/icons/capybee-192.png', sizes: '192x192', type: 'image/png' },
          { src: '/icons/capybee-512.png', sizes: '512x512', type: 'image/png' }
        ]
      },
      workbox: {
        // Precache the built app shell (JS/CSS/HTML) — this is what makes
        // "open the app while the server is asleep" instant.
        globPatterns: ['**/*.{js,css,html,svg,png,jpg,jpeg,woff2}'],
        runtimeCaching: [
          {
            // Never cache API responses here — 14-offline-write-queue-instruction.md
            // owns write resilience, and stale cached reads (old mood, old missions)
            // would be actively misleading for a check-in app. Always hit network.
            urlPattern: /^\/api\//,
            handler: 'NetworkOnly'
          }
        ]
      }
    })
  ]
});
```

The `NetworkOnly` rule for `/api/**` is deliberate: this spec only caches the static shell, never data. Caching stale check-ins or mission lists would contradict the "never promise things will get better, always show what's real" philosophy in `CapyBee_concept.md` — a kid should never see yesterday's mood reflected back as if it's today's.

---

## 2. Register the service worker

Vite's PWA plugin auto-injects registration when `registerType: 'autoUpdate'` is set — no manual `main.tsx` changes needed beyond what's already there from `14-offline-write-queue-instruction.md`'s warm-up ping.

---

## 3. Icons

Export two PNGs from the existing CapyBee avatar art (`app/ui/src/assets`) at 192×192 and 512×512, save to `app/ui/public/icons/capybee-192.png` and `capybee-512.png`. Use the default/waving expression, matching the icon already used for the landing hero.

---

## 4. Verify

1. `npm run build && npm run preview` locally, confirm the service worker registers (DevTools → Application → Service Workers).
2. Load the app once while online, then simulate offline (DevTools → Network → Offline) and reload — the app shell (landing page or authenticated home layout) should render instantly instead of a blank/failed page. API-dependent content should show existing empty/loading states rather than crash, since `NetworkOnly` means those requests will fail as expected while offline — that's correct behavior, not a bug, since `14` handles write resilience separately and this spec only covers shell load.
3. Deploy to Fly, confirm `Add to Home Screen` prompt / install banner appears on a mobile browser, and that a fresh install opens instantly on a cold machine (compare timing before/after this change).

---

## Out of scope for this pass

- Precaching or offline-serving of `GET` API data — explicitly excluded (see `NetworkOnly` note above). If offline reads become a priority later, that's a new spec, not an extension of this one, since it has different tone/correctness tradeoffs.
- Push notifications — not part of this spec even though the PWA plugin makes them technically easier to add later.
- iOS-specific install-prompt quirks (Safari doesn't support the standard install banner) — acceptable gap for MVP; Android/Chrome gets the full benefit, iOS still gets the precached-shell speed benefit once added to home screen manually.

---

## Traceability

- Completes the cold-start experience alongside `13-session-persistence-instruction.md` (stays logged in) and `14-offline-write-queue-instruction.md` (writes never block) — together these three specs are the full answer to "app was already open before, comes back later, no delay."
- Supports US-020 (mobile-first experience) — an installable, instantly-opening shell is part of a phone-first experience, not just a responsive layout.

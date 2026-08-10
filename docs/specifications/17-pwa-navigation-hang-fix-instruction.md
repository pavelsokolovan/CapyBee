# 17 — Fix: PWA Shell Not Actually Serving Cached Navigations

## Purpose

`15-pwa-app-shell-instruction.md` precached the app shell but never routed navigation requests through that cache. `WebConfig.java` serves `/` via a normal Spring controller, and `vite.config.ts`'s `workbox` config has no `navigateFallback`, so opening the app — cold browser tab or installed PWA resuming after being backgrounded — always goes straight to network and therefore always waits on the Fly machine, exactly as before spec 15. This is the direct cause of the reported "installed PWA hangs ~1 minute, then loads 10-15s" when returning to the app after switching away.

Three targeted fixes, in order of impact.

---

## 1. Make navigations actually hit the precached shell

### `app/ui/vite.config.ts`

```ts
workbox: {
  cleanupOutdatedCaches: true,
  clientsClaim: true,
  skipWaiting: true,
  globPatterns: ['**/*.{js,css,html,svg,png,jpg,jpeg,woff2}'],
  navigateFallback: '/index.html',
  navigateFallbackDenylist: [
    /^\/api\//,
    /^\/oauth2\//,
    /^\/login/,
    /^\/logout/,
    /^\/actuator\//
  ],
  runtimeCaching: [
    {
      urlPattern: ({ url }) => url.pathname.startsWith('/api/'),
      handler: 'NetworkOnly'
    }
  ]
}
```

`navigateFallback` tells the service worker to answer any navigation request (opening `/`, or the PWA's `start_url`) with the precached `index.html` instantly, instead of waiting on the network. `navigateFallbackDenylist` excludes the routes that must always hit the real backend directly — the OAuth2 login/callback chain and API calls — so auth and data still work correctly; only the static shell becomes cache-first.

This is the fix that actually delivers what spec 15 was meant to: after the first successful visit, every later open (browser tab or installed PWA) renders CapyBee's shell immediately, and only the auth check / data underneath has to wait on a possibly-sleeping server.

---

## 2. Stop firing three redundant wake-up requests at once

Currently `main.tsx`'s warm-up ping, `App.tsx`'s `checkAuthStatus()`, and the sync engine's `visibilitychange` handler (from `14-offline-write-queue-instruction.md`) can all fire `/api/auth-status`-adjacent requests within the same moment — worst case exactly when the PWA regains foreground after being backgrounded, piling redundant load on a JVM that may still be mid-boot on a memory-constrained machine.

### `app/ui/src/main.tsx`

Remove the standalone ping — `App.tsx`'s `checkAuthStatus()` already fires on mount and serves the same wake-up purpose, now that navigation itself no longer waits on the network:

```ts
import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App';
import './styles.css';

ReactDOM.createRoot(document.getElementById('root') as HTMLElement).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);
```

### `app/ui/src/offline/syncEngine.ts`

In `startSyncLoop`, keep the `visibilitychange`-triggered `flushQueue()` (it's useful for actually-queued writes) but don't let it race the auth check — add a small delay so it runs after `checkAuthStatus` has had a chance to land first:

```ts
const onVisible = () => {
  if (document.visibilityState === 'visible') {
    setTimeout(flushQueue, 1500);
  }
};
```

---

## 3. Bound the "One moment..." wait instead of letting it hang silently

`App.tsx`'s `checkAuthStatus()` has no timeout, unlike `canReachBackend()` (which already uses a 2.5s `AbortController`). If the server is slow to wake, the child sees a static "One moment..." / "Chwileczkę..." screen with no sense of progress for however long it takes — which reads as broken, not calm.

### `app/ui/src/App.tsx`

```ts
const checkAuthStatus = async () => {
  const controller = new AbortController();
  const timeout = window.setTimeout(() => controller.abort(), 25_000);

  try {
    const res = await fetch('/api/auth-status', {
      credentials: 'include',
      cache: 'no-store',
      signal: controller.signal
    });
    if (res.ok) {
      const data = await res.json();
      setUser(data);
      setSessionExpired(!data.authenticated);
    } else if (res.status === 401) {
      setSessionExpired(true);
    }
  } catch (err) {
    console.error('Failed to check auth status:', err);
    setAuthCheckTimedOut(true);
  } finally {
    window.clearTimeout(timeout);
    setLoading(false);
  }
};
```

Add `const [authCheckTimedOut, setAuthCheckTimedOut] = useState(false);` near the other state, and a gentle retry affordance in the `loading` branch once it's true — reuse the existing warm, non-alarming tone (CapyBee waving, not an error icon):

```tsx
if (loading) {
  return (
    <main className="auth-loading">
      <CapyBeeAvatar src={capyBeeAvatar.default} size={120} />
      <p>{authText.loading}</p>
    </main>
  );
}

if (authCheckTimedOut && !user?.authenticated) {
  return (
    <main className="auth-loading">
      <CapyBeeAvatar src={capyBeeAvatar.waving} size={120} />
      <CapyBeeBubble text={locale === 'pl' ? 'Ul się jeszcze budzi...' : 'The hive is still waking up...'} />
      <button className="primary-button" type="button" onClick={() => { setAuthCheckTimedOut(false); setLoading(true); checkAuthStatus(); }}>
        {locale === 'pl' ? 'Spróbuj ponownie' : 'Try again'}
      </button>
    </main>
  );
}
```

25s covers a normal cold start comfortably (the observed 10-15s plus margin) without leaving the child staring at an unbounded blank wait if something is genuinely wrong.

---

## 4. Worth checking on the Fly side (not a code change)

The reported ~1 minute delay is longer than the "10-15s" baseline you measured on a plain browser open, and `fly.toml`'s existing comment ("wait 3 min for Spring Boot cold start") suggests boot time has been inconsistent before. After deploying the fixes above, check `fly logs` around a cold start to see actual JVM startup duration. If it's regularly well past 15-20s, that's a separate, infra-level follow-up (JVM startup tuning, or reconsidering `min_machines_running` for peak hours) rather than anything the frontend can paper over — flag it if you want a dedicated spec for that once you have real numbers.

---

## Traceability

- Bug-fix correction to `15-pwa-app-shell-instruction.md` — the precache existed but nothing routed navigations through it.
- Restores the intended effect of `13`/`14`/`15` together: app opens instantly from cache, session survives sleep, writes queue safely, auth check is bounded and reassuring instead of an indefinite hang.

/*
 * sw.js — RuhRohgue — Cloud Edition
 * Service Worker: implements a PWA offline shell with network-first
 * strategy for API calls and cache-first for static assets.
 *
 * PWA Characteristics satisfied (per Wikipedia/W3C):
 *   ✓ Offline capable   — shell served from cache when network fails
 *   ✓ Installable       — combined with manifest.json
 *   ✓ Network-first API — /new and /command always hit the server when online
 *   ✓ Cache-first UI    — index.html, icons served instantly from cache
 *   ✓ Background sync   — handled by the JS offline queue in index.html
 */

'use strict';

const CACHE_NAME    = 'ruhrohgue-v4';
const STATIC_ASSETS = [
  '/',
  '/index.html',
  '/manifest.json',
  '/icons/icon-192.png',
  '/icons/icon-512.png',
];

// ── Install: pre-cache the app shell ─────────────────────────────────
self.addEventListener('install', event => {
  event.waitUntil(
    caches.open(CACHE_NAME)
      .then(cache => cache.addAll(STATIC_ASSETS))
      .then(() => {
        console.log('[RuhRohgue SW] App shell cached');
        return self.skipWaiting();
      })
  );
});

// ── Activate: delete stale caches ────────────────────────────────────
self.addEventListener('activate', event => {
  event.waitUntil(
    caches.keys()
      .then(keys => Promise.all(
        keys
          .filter(key => key !== CACHE_NAME)
          .map(key => {
            console.log('[RuhRohgue SW] Deleting stale cache:', key);
            return caches.delete(key);
          })
      ))
      .then(() => self.clients.claim())
  );
});

// ── Fetch: routing strategy ───────────────────────────────────────────
self.addEventListener('fetch', event => {
  const req = event.request;
  const url = new URL(req.url);

  // Only intercept same-origin requests
  if (url.origin !== self.location.origin) return;

  // API endpoints: network-first (game state must always be fresh)
  if (url.pathname === '/new'     ||
      url.pathname === '/command' ||
      url.pathname === '/state'   ||
      url.pathname.startsWith('/actuator/')) {
    event.respondWith(networkFirst(req));
    return;
  }

  // Static shell: cache-first (fast loading, works offline)
  event.respondWith(cacheFirst(req));
});

// ── Strategies ────────────────────────────────────────────────────────

/**
 * Network-first: try the network; fall back to cache on failure.
 * Used for game API calls so the server always processes commands.
 */
async function networkFirst(req) {
  try {
    const response = await fetch(req);
    // Cache successful GET responses for offline fallback
    if (req.method === 'GET' && response.status === 200) {
      const cache = await caches.open(CACHE_NAME);
      cache.put(req, response.clone());
    }
    return response;
  } catch {
    const cached = await caches.match(req);
    if (cached) return cached;
    // Return an offline JSON response so the frontend gets something useful
    return new Response(
      JSON.stringify({ error: 'offline' }),
      { status: 503, headers: { 'Content-Type': 'application/json' } }
    );
  }
}

/**
 * Cache-first: return from cache; fall back to network and cache result.
 * Used for static assets (HTML, icons, manifest).
 */
async function cacheFirst(req) {
  const cached = await caches.match(req);
  if (cached) return cached;

  try {
    const response = await fetch(req);
    if (response.status === 200) {
      const cache = await caches.open(CACHE_NAME);
      cache.put(req, response.clone());
    }
    return response;
  } catch {
    // Absolute fallback: serve index.html for navigation requests
    if (req.mode === 'navigate') {
      return caches.match('/index.html');
    }
    return new Response('Offline', { status: 503 });
  }
}

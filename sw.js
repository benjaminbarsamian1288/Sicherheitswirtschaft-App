const CACHE_NAME = 'bb-protect-wachbuch-v3';
const ASSETS = ['./', './index.html', './ema.html', './manifest.json', './logo.jpg', './icon-192.png', './icon-512.png',
  './vendor/three/three.module.min.js', './vendor/three/OrbitControls.js', './vendor/three/CSS2DRenderer.js'];

self.addEventListener('install', e => {
  e.waitUntil(caches.open(CACHE_NAME).then(c => c.addAll(ASSETS)));
  self.skipWaiting();
});

self.addEventListener('activate', e => {
  e.waitUntil(caches.keys().then(k => Promise.all(k.filter(n => n !== CACHE_NAME).map(n => caches.delete(n)))));
  self.clients.claim();
});

self.addEventListener('fetch', e => {
  if (e.request.method !== 'GET' || !e.request.url.startsWith(self.location.origin) || e.request.url.endsWith('.apk')) return;
  e.respondWith(fetch(e.request).then(r => {
    const clone = r.clone();
    caches.open(CACHE_NAME).then(c => c.put(e.request, clone));
    return r;
  }).catch(() => caches.match(e.request)));
});

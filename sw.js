const CACHE_NAME = 'siwi-dashboard-v4';
const ASSETS = ['./', './index.html', './manifest.json', './data.json', './icon-192.png', './icon-512.png'];

self.addEventListener('install', e => {
  e.waitUntil(caches.open(CACHE_NAME).then(c => c.addAll(ASSETS)));
  self.skipWaiting();
});

self.addEventListener('activate', e => {
  e.waitUntil(caches.keys().then(k => Promise.all(k.filter(n => n !== CACHE_NAME).map(n => caches.delete(n)))));
  self.clients.claim();
});

self.addEventListener('fetch', e => {
  e.respondWith(fetch(e.request).then(r => {
    const clone = r.clone();
    caches.open(CACHE_NAME).then(c => c.put(e.request, clone));
    return r;
  }).catch(() => caches.match(e.request)));
});

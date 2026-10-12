const CACHE_NAME = 'apex-shield-quantum-v18';
const assetsToCache = [
  '/Vpn/',
  '/Vpn/index.html',
  '/Vpn/style.css',
  '/Vpn/app.js',
  '/Vpn/manifest.json',
  '/Vpn/icon-512.png'
];

// تثبيت ملفات التخزين المؤقت عند التحميل الأول
self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => {
      return cache.addAll(assetsToCache);
    })
  );
  self.skipWaiting();
});

// تفعيل الخدمة وتنظيف النسخ القديمة
self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((cacheNames) => {
      return Promise.all(
        cacheNames.map((cacheName) => {
          if (cacheName !== CACHE_NAME) {
            return caches.delete(cacheName);
          }
        })
      );
    })
  );
  self.clients.claim();
});

// التعامل مع طلبات الشبكة والعمل دون اتصال (Offline Support)
self.addEventListener('fetch', (event) => {
  event.respondWith(
    caches.match(event.request).then((cachedResponse) => {
      return cachedResponse || fetch(event.request).catch(() => {
        if (event.request.mode === 'navigate') {
          return caches.match('/Vpn/index.html');
        }
      });
    })
  );
});

// إدارة إشعارات الهاتف ودعم التنبيهات في الخلفية
self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  event.waitUntil(
    clients.openWindow('/Vpn/index.html')
  );
});

/* ============================================================================
   APEX SHIELD AI - SERVICE WORKER (v22.0 Chrono-Photonic Sovereign)
   ============================================================================ */

const CACHE_NAME = 'apex-shield-v23-chrono-photonic-sovereign';

// قائمة الملفات المحدثة للتخزين المؤقت لضمان العمل 100% بوضع أوفلاين
const assetsToCache = [
  './Nal',
  './index.html',
  './style.css',
  './app.js',
  './manifest.json',
  './privacy-policy.html',
  './icon-192.png',
  './icon-512.png'
];

// قائمة نطاقات الإعلانات والتتبع والبث الخبيثة المحظورة كلياً من الشبكة
const BLOCKED_AD_DOMAINS = [
    'doubleclick.net', 'googlesyndication.com', 'googleadservices.com',
    'adnxs.com', 'unityads.unity3d.com', 'applovin.com', 'ironsrc.com',
    'ads.mopub.com', 'analytics.google.com', 'facebook.com/tr',
    'adservice.google.com', 'pagead2.googlesyndication.com',
    'ad.doubleclick.net', 'ads.pubmatic.com', 'securepubads.g.doubleclick.net',
    'adcolony.com', 'vungle.com', 'tapjoy.com', 'chartboost.com',
    'adsrvr.org', 'criteo.com', 'outbrain.com', 'taboola.com',
    'popads.net', 'popcash.net', 'propellerads.com', 'adsterra.com',
    'clickadu.com', 'tsyndicate.com', 'juicyads.com', 'eroadvertising.com',
    'ssp.adtarget.com', 'bidder.criteo.com', 'ad-delivery.net', 'track.voluum.com',
    'wecima', 'akoam', 'egybest', 'mycima', 'shahid', 'streambucket', 'uptobox', 'ads', 'popunder'
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

// تفعيل الخدمة وتنظيف النسخ القديمة فوراً
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

// التعامل مع طلبات الشبكة وحجب الإعلانات على مستوى السيرفر ووركر (Network-Level Interception)
self.addEventListener('fetch', (event) => {
  const url = event.request.url.toLowerCase();
  
  // 1. استثناء طلبات Firebase و SDK السحابية و API الخارجي لتعتمد على التخزين السحابي الخاص بها
  if (url.includes('firestore.googleapis.com') || 
      url.includes('gstatic.com') || 
      url.includes('ipify.org') || 
      url.includes('httpbin.org')) {
      return;
  }

  // 2. التحقق الفوري مما إذا كان الطلب ينتمي للإعلانات أو نطاقات التوجيه الخبيثة
  const isBlocked = BLOCKED_AD_DOMAINS.some(domain => url.includes(domain));
  if (isBlocked) {
      event.respondWith(
          new Response(JSON.stringify({ blocked: true, engine: 'Apex Quantum Network Firewall v18' }), {
              status: 200,
              headers: { 'Content-Type': 'application/json' }
          })
      );
      return;
  }

  // 3. تقديم الاستجابة من التخزين المؤقت أو جلبها من الشبكة
  event.respondWith(
    caches.match(event.request).then((cachedResponse) => {
      return cachedResponse || fetch(event.request).catch(() => {
        if (event.request.mode === 'navigate') {
          return caches.match('./index.html');
        }
      });
    })
  );
});

// إدارة إشعارات الهاتف والتركيز على النافذة المفتوحة
self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  event.waitUntil(
    clients.matchAll({ type: 'window', includeUncontrolled: true }).then((clientList) => {
      if (clientList.length > 0) {
        return clientList[0].focus();
      }
      return clients.openWindow('./');
    })
  );
});

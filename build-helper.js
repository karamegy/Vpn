const fs = require('fs');
const path = require('path');

console.log('=== بدء تحضير ملفات مشروع Apex Shield التلقائية ===');

// التأكد من وجود مجلد www وملفات الويب الأساسية
if (!fs.existsSync('www')) {
  fs.mkdirSync('www', { recursive: true });
}

// كتابة إعدادات Capacitor يدوياً لضمان عدم وجود أخطاء
const config = {
  appId: "com.apex.shield",
  appName: "Apex Shield AI",
  webDir: "www",
  bundledWebRuntime: false,
  android: {
    webContentsDebuggingEnabled: true,
    allowMixedContent: true
  }
};

fs.writeFileSync('capacitor.config.json', JSON.stringify(config, null, 2), 'utf8');
console.log('تم إنشاء capacitor.config.json بنجاح.');

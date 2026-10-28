name: Build Android Native APK & AAB (Apex Shield v18)

on:
  workflow_dispatch:
  push:
    branches:
      - main
      - master

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout Repository
        uses: actions/checkout@v4

      - name: Set up Java JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '17'

      - name: Set up Node.js 22
        uses: actions/setup-node@v4
        with:
          node-version: '22'

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v4

      - name: 1. Setup Node.js System & Web Assets
        run: |
          sudo apt-get update -y
          sudo apt-get install -y imagemagick

          mkdir -p www
          cp -r index.html app.js style.css manifest.json sw.js *.png *.html .well-known www/ 2>/dev/null || true
          
          if [ ! -f "package.json" ]; then
            npm init -y
          fi
          
          npm install @capacitor/core@6.2.0 @capacitor/cli@6.2.0 @capacitor/android@6.2.0

          cat << 'EOF' > capacitor.config.json
          {
            "appId": "io.github.karamegy.Vpn",
            "appName": "Apex Shield AI",
            "webDir": "www",
            "bundledWebRuntime": false,
            "android": {
              "webContentsDebuggingEnabled": true,
              "allowMixedContent": true
            }
          }
          EOF

      - name: 2. Backup Native Code, Rebuild Clean Android Platform & Merge
        run: |
          export CI=true
          
          # 1. أخذ نسخة احتياطية من ملفات الجافا والـ Manifest المخصصة
          mkdir -p /tmp/native_backup
          if [ -d "android/app/src/main" ]; then
            cp -r android/app/src/main/java /tmp/native_backup/ 2>/dev/null || true
            cp android/app/src/main/AndroidManifest.xml /tmp/native_backup/ 2>/dev/null || true
          fi

          # 2. حذف المجلد الناقص وإعادة إنشاء مشروع أندرويد كامل بملف gradlew الرسمي
          rm -rf android
          npx cap add android
          npx cap sync android

          # 3. استعادة ملفات الكود والجسر المخصص داخل مشروع أندرويد المكتمل
          if [ -d "/tmp/native_backup/java" ]; then
            cp -r /tmp/native_backup/java/* android/app/src/main/java/ 2>/dev/null || true
          fi
          if [ -f "/tmp/native_backup/AndroidManifest.xml" ]; then
            cp /tmp/native_backup/AndroidManifest.xml android/app/src/main/AndroidManifest.xml 2>/dev/null || true
          fi

          # 🎨 توليد أيقونات التطبيق تلقائياً وحذف المجلد التكيفي الافتراضي
          ICON_SOURCE="icon-512.png"
          if [ -f "$ICON_SOURCE" ]; then
            RES_DIR="android/app/src/main/res"
            
            rm -rf $RES_DIR/mipmap-anydpi-v26
            
            mkdir -p $RES_DIR/mipmap-mdpi $RES_DIR/mipmap-hdpi $RES_DIR/mipmap-xhdpi $RES_DIR/mipmap-xxhdpi $RES_DIR/mipmap-xxxhdpi
            
            convert "$ICON_SOURCE" -resize 48x48 $RES_DIR/mipmap-mdpi/ic_launcher.png || true
            convert "$ICON_SOURCE" -resize 72x72 $RES_DIR/mipmap-hdpi/ic_launcher.png || true
            convert "$ICON_SOURCE" -resize 96x96 $RES_DIR/mipmap-xhdpi/ic_launcher.png || true
            convert "$ICON_SOURCE" -resize 144x144 $RES_DIR/mipmap-xxhdpi/ic_launcher.png || true
            convert "$ICON_SOURCE" -resize 192x192 $RES_DIR/mipmap-xxxhdpi/ic_launcher.png || true

            convert "$ICON_SOURCE" -resize 48x48 $RES_DIR/mipmap-mdpi/ic_launcher_round.png || true
            convert "$ICON_SOURCE" -resize 72x72 $RES_DIR/mipmap-hdpi/ic_launcher_round.png || true
            convert "$ICON_SOURCE" -resize 96x96 $RES_DIR/mipmap-xhdpi/ic_launcher_round.png || true
            convert "$ICON_SOURCE" -resize 144x144 $RES_DIR/mipmap-xxhdpi/ic_launcher_round.png || true
            convert "$ICON_SOURCE" -resize 192x192 $RES_DIR/mipmap-xxxhdpi/ic_launcher_round.png || true
          fi

          # تعديل إعدادات gradle.properties
          cat << 'EOF' >> android/gradle.properties
          android.lint.checkReleaseBuilds=false
          android.lint.abortOnError=false
          android.enableJetifier=true
          android.useAndroidX=true
          EOF

          # حقن مكتبة Google Play Billing وتثبيت إعدادات منع حذف الجسر
          if [ -f "android/app/build.gradle" ]; then
            if ! grep -q "com.android.billingclient:billing" android/app/build.gradle; then
              sed -i '/dependencies {/a \    implementation "com.android.billingclient:billing:8.0.0"' android/app/build.gradle
            fi
            sed -i -E 's/versionCode [0-9]+/versionCode 36/' android/app/build.gradle
            sed -i -E 's/versionName "[^"]*"/versionName "8.0.1.3"/' android/app/build.gradle
            sed -i 's/minSdk [0-9]+/minSdk 24/g' android/app/build.gradle
            sed -i 's/minSdkVersion [0-9]+/minSdkVersion 24/g' android/app/build.gradle
            
            # منع R8 من حذف كلاسات الجسر في نسخة AAB
            sed -i 's/minifyEnabled true/minifyEnabled false/g' android/app/build.gradle || true
            sed -i 's/shrinkResources true/shrinkResources false/g' android/app/build.gradle || true
          fi

          # حماية كلاسات الجسر ومكتبات الدفع في ProGuard
          cat << 'PROGUARD_EOF' >> android/app/proguard-rules.pro
          -keep class **.AndroidVPNBridge { *; }
          -keep class io.github.karamegy.Vpn.** { *; }
          -keepclasseswithmembers class * {
              @com.getcapacitor.annotation.CapacitorPlugin <methods>;
              @com.getcapacitor.PluginMethod <methods>;
          }
          -keep class com.android.billingclient.** { *; }
          PROGUARD_EOF

          if [ -f "android/variables.gradle" ]; then
            sed -i -E 's/compileSdkVersion = .*/compileSdkVersion = 36/' android/variables.gradle
            sed -i -E 's/targetSdkVersion = .*/targetSdkVersion = 36/' android/variables.gradle
            sed -i -E 's/minSdkVersion = .*/minSdkVersion = 24/' android/variables.gradle
          fi

      - name: 3. Build & Sign APK & AAB Packages
        env:
          RELEASE_KEY: ${{ secrets.RELEASE_KEY }}
          STORE_PASSWORD: ${{ secrets.STORE_PASSWORD }}
          KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
          KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
        run: |
          cd android
          
          if [ -n "$RELEASE_KEY" ]; then
            printf "%s" "$RELEASE_KEY" | base64 --decode > app/signing.keystore
          fi
          
          chmod +x gradlew
          export GRADLE_OPTS="-Dfile.encoding=UTF-8"
          
          # البناء والتوقيع المباشر عبر Gradle لضمان سلامة AAB
          ./gradlew assembleRelease bundleRelease \
            -Pandroid.injected.signing.store.file="$(pwd)/app/signing.keystore" \
            -Pandroid.injected.signing.store.password="$STORE_PASSWORD" \
            -Pandroid.injected.signing.key.alias="$KEY_ALIAS" \
            -Pandroid.injected.signing.key.password="$KEY_PASSWORD" \
            -x lint -x lintVitalRelease -x lintVitalAnalyzeRelease --no-daemon

          rm -f app/signing.keystore

      - name: Upload APK
        uses: actions/upload-artifact@v4
        with:
          name: ApexShield-APK-DirectInstall
          path: android/app/build/outputs/apk/release/*.apk

      - name: Upload AAB
        uses: actions/upload-artifact@v4
        with:
          name: ApexShield-AAB-GooglePlay
          path: android/app/build/outputs/bundle/release/*.aab

# APK をビルドしてインストールする

## 前提条件

- Android SDK と JDK 17 のセットアップ済み（詳細は [開発環境構築](../tutorials/development-setup.md) を参照）
- `local.properties` は **Git 管理しない**

## デバッグ APK

```bash
./gradlew assembleDebug
```

生成物: `app/build/outputs/apk/debug/app-debug.apk`

## リリース APK

`local.properties` に署名情報を設定してからビルドします。

```properties
release.storeFile=keystore/ftvrcm-release.jks
release.storePassword=<パスワード>
release.keyAlias=ftvrcm
release.keyPassword=<パスワード>
```

```bash
./gradlew assembleRelease
```

生成物: `app/build/outputs/apk/release/app-release.apk`

## 端末へインストール（ADB サイドロード）

```bash
# Fire TV に接続（設定 → マイ Fire TV → 開発者オプション → ADB デバッグ を有効化しておく）
adb connect <FIRE_TV_IP>:5555

# インストール
adb install -r app/build/outputs/apk/release/app-release.apk
# または
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

インストール後は [AccessibilityService を有効化する](enable-accessibility-service.md) に進んでください。

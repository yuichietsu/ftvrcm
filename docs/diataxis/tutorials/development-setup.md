# 開発環境構築

ftvrcm の開発・ビルド環境をセットアップする手順です。

## 前提条件

- Android SDK（`ANDROID_SDK_ROOT=~/Android/Sdk` など）
- JDK 17
- `adb`（端末へのインストール・操作に使用）

## 1. リポジトリをクローン

```bash
git clone <repo-url>
cd ftvrcm
```

## 2. SDK ライセンス受諾とパッケージインストール

初回ビルド前に必要な SDK コンポーネントを揃えます。

```bash
# ライセンス受諾
yes | "$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" --licenses

# 必要パッケージのインストール
"$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" \
  --install "platforms;android-34" "build-tools;34.0.0" "platform-tools"
```

## 3. デバッグ APK をビルド

```bash
./gradlew :app:assembleDebug
```

生成物: `app/build/outputs/apk/debug/app-debug.apk`

## 4. 端末にインストール

```bash
adb connect <FIRE_TV_IP>:5555
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 5. リリース署名の設定（リリースビルド時のみ）

`local.properties`（Git 管理外）に署名情報を追記します。

```properties
release.storeFile=keystore/ftvrcm-release.jks
release.storePassword=<パスワード>
release.keyAlias=ftvrcm
release.keyPassword=<パスワード>
```

```bash
./gradlew :app:assembleRelease
```

生成物: `app/build/outputs/apk/release/app-release.apk`

## 6. プロキシサーバー（オプション）

ADB プロキシ方式を使う場合は Node.js（v18 以上）が必要です。
詳細は [プロキシサーバーを起動する](../how-to/setup-proxy-server.md) を参照してください。

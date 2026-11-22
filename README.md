# ftvrcm

FireTV Stick用リモコンタッチ操作アプリケーション

## 概要

ftvrcm（FireTV Remote Control Mouse）は、Fire TV Stickのリモコンで画面上のポインタを動かし、タッチ操作（タップ・スワイプ・ピンチズーム等）を行えるAndroid TVアプリです。リモコンの操作モードを「無効（通常操作）」と「有効（タッチ操作）」で切り替え、他のアプリケーション実行中でも常に操作モードを変更できます。

## 主な機能

- **操作モード切り替え**：リモコンを通常動作またはタッチ操作モードで使用
- **タッチ操作**：リモコンキーでカーソル移動・タップ/ロングタップ・4方向スワイプ/スクロール・2本指ピンチズーム
- **Shizuku連携（推奨）**：Shizuku特権Binderによる0ms遅延のタッチ注入・ネイティブ2本指ピンチ操作・アプリ内からのアクセシビリティ即時有効化
- **リモコン操作パススルー切り替え**：タッチ操作モード中でも、一時的にリモコン本来のキー操作（方向・選択・戻るなど）を透過可能
- **設定画面**：有効化/無効化キー、キーマッピング、カーソル設定、動作チューニング、Shizuku設定を変更
- **バックグラウンド監視**：他のアプリ実行中も操作モード切り替え可能
- **軽量実装**：CPU・メモリ消費を最小限に抑えた設計

## 使い方（利用者向け）

### 1. 初期設定（必須）

1. （推奨）Shizukuを起動し、アプリ内の **Shizukuの設定** → **Shizukuの詳細設定** → **Shizuku権限の許可** から権限を許可します
2. 本アプリのアクセシビリティサービスを有効化します
   - **Shizuku経由（簡単）**：**Shizukuの詳細設定** → **アクセシビリティを有効化** を選択
   - **設定UI**：端末の **設定** → **ユーザーコントロール** → **アクセシビリティ**（または「ダウンロードしたサービス」）→ `ftvrcm` をON
   - **ADB**：後述のADBコマンドで有効化
3. 設定画面下部のステータスバーで「**アクセシビリティ: 動作中**」（およびShizuku利用時は「**Shizuku: 起動中**」）になっていることを確認します

### 2. 操作モード

- **無効（通常操作）**：リモコンは通常どおり動作（十字キーでフォーカス移動、決定で選択など）
- **有効（タッチ操作）**：画面上のカーソルを動かしてタッチ操作を行います

モード切り替えは、設定の「有効化/無効化キー（デフォルト：`MENU` 長押し）」で行います。

### 3. タッチ操作（カーソル操作）

タッチ操作モード中は、設定したキーで以下を行います。

- **カーソル移動**：十字キー（上/下/左/右）でカーソル移動（押しっぱなしで連続移動・加速）
- **タップ / ロングタップ**：決定ボタン（短押し / 長押し）
- **スワイプ / スクロール**：`CH+` / `CH-`（上下）、`巻き戻し` / `早送り`（左右）
  - Shizuku有効時はスワイプジェスチャを注入し、無効時は `ACTION_SCROLL_*` を送出します
- **ズーム操作（ピンチ）**：`戻る(BACK)`（ズームイン / ピンチアウト）、`再生/一時停止`（ズームアウト / ピンチイン）

### 4. 「リモコン操作パススルー切り替え」

タッチ操作モード中でも、設定したキー（デフォルト：`MENU` 短押し）でパススルーを切り替えられます。

- **カーソル**：本アプリがキー入力を消費してタッチ操作します（カーソルは輪っか表示）
- **パススルー（方向キー）**：割り当てられたタッチ操作キーを含め、リモコン本来の操作としてシステムに渡します（カーソルはDPAD風アイコン表示）

### 5. 動作確認（タッチ操作テスト）

設定画面 → **その他** → **タッチ操作テストを開く** から起動できます。

- 画面上部に「最終ジェスチャ」が表示され、直近のタップ/スワイプ/ピンチ/スクロール命令の結果を確認できます
- ボタン（タップ/長押し）、ListView（タップ/ダブルタップ/長押し/縦スクロール）、スワイプ/ズーム確認エリアが用意されています

## 対応デバイス

- **主要対応機種**：Fire TV Stick 4K Max 2nd Gen
- **最小API Level**：28（Android 9.0）

## 技術スタック

- **言語**：Kotlin
- **ビルドツール**：Gradle
- **アーキテクチャ**：軽量構成（Preference画面 + AccessibilityService）
- **特権操作連携**：Shizuku API (`dev.rikka.shizuku`) + HiddenApiBypass
- **設定保存**：SharedPreferences
- **配布形式**：APK（野良アプリ）

## ドキュメント

詳細なドキュメントは [docs/diataxis/](docs/diataxis/) に保存されています。

### 導入（Tutorials）
- [docs/diataxis/tutorials/getting-started.md](docs/diataxis/tutorials/getting-started.md) ― はじめに：インストール〜初回設定〜基本操作
- [docs/diataxis/tutorials/development-setup.md](docs/diataxis/tutorials/development-setup.md) ― 開発環境構築：SDK セットアップ〜ビルド

### 作業ガイド（How-to）
- [docs/diataxis/how-to/setup-shizuku.md](docs/diataxis/how-to/setup-shizuku.md) ― Shizuku をセットアップする
- [docs/diataxis/how-to/enable-accessibility-service.md](docs/diataxis/how-to/enable-accessibility-service.md) ― AccessibilityService を有効化する
- [docs/diataxis/how-to/build-and-install.md](docs/diataxis/how-to/build-and-install.md) ― APK をビルドしてインストールする
- [docs/diataxis/how-to/touch-test.md](docs/diataxis/how-to/touch-test.md) ― タッチ操作テストの使い方
- [docs/diataxis/how-to/troubleshooting.md](docs/diataxis/how-to/troubleshooting.md) ― トラブルシューティング

### リファレンス（Reference）
- [docs/diataxis/reference/shared-preferences.md](docs/diataxis/reference/shared-preferences.md) ― SharedPreferences スキーマ（設定キー一覧）

### 解説（Explanation）
- [docs/diataxis/explanation/architecture.md](docs/diataxis/explanation/architecture.md) ― アーキテクチャ概要・設計思想

## 開発状況

- ✅ 基本実装（アクセシビリティ + カーソル操作）
- ✅ Shizuku特権Binderによる0msタッチ注入・2本指ピンチ操作
- ✅ 動作確認画面（タッチ操作テスト）
- ⏳ 実機検証を継続しつつ操作感を調整中

## ビルド（APK生成）

- Android SDK: `ANDROID_SDK_ROOT=~/Android/Sdk`
- JDK: 17（このリポジトリは `gradle.properties` でJDK17を使用するよう設定済み）

手元ビルド例：

1. `export ANDROID_SDK_ROOT="$HOME/Android/Sdk"`
2. （初回のみ）SDKライセンス受諾: `yes | "$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" --licenses`
3. （必要に応じて）SDKパッケージ導入: `"$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" --install "platforms;android-34" "build-tools;34.0.0" "platform-tools"`
4. `./gradlew :app:assembleDebug`
5. 生成物: `app/build/outputs/apk/debug/app-debug.apk`

## Fire TVでアクセシビリティ項目が見当たらない場合（ADBで有効化）

Fire OSのビルドや端末設定によっては、設定UIから「ダウンロードしたサービス（Accessibility Service）」のトグルが見つからないことがあります。
この場合はShizuku経由（アプリ内ボタン）またはADBシェルから有効化できます。

1. Fire TVで`開発者オプション` → `ADBデバッグ`をON
2. PCから接続（同一LAN）
   - `adb connect <FIRE_TV_IP>:5555`
3. サービス有効化（コンポーネント名は固定）
   - `adb shell settings put secure enabled_accessibility_services com.ftvrcm/com.ftvrcm.service.RemoteControlAccessibilityService`
   - `adb shell settings put secure accessibility_enabled 1`
4. 反映しない場合は再起動
   - `adb reboot`

有効化後、設定画面下部のステータスバーで「アクセシビリティ: 動作中」になることを確認してください。
補足：このリポジトリでは `local.properties` を `.gitignore` 済みで、`sdk.dir` でSDK位置を指定できます。

## 既知の注意点（Fire TV）

- 一部のFire OSでは、`BACK`キーなどの長押しがシステム側に予約され、長押しイベントがアプリに届きにくかったり、アクセシビリティサービスが無効化されたりする場合があります。
- デフォルトの「有効化/無効化キー」は `MENU` 長押しに設定されています。変更する場合も `BACK` 長押しは避けることを推奨します。

## トラブルシュート

- 設定UIにアクセシビリティのトグルが出ない場合やShizuku連携で問題が起きた場合は、[docs/diataxis/how-to/troubleshooting.md](docs/diataxis/how-to/troubleshooting.md) を参照してください。
- ポインタは出るがクリックしない場合も、同ドキュメントに原因切り分けをまとめています。

## ライセンス

このプロジェクトはMITライセンスの下で公開されています。詳細は [LICENSE](LICENSE) を参照してください。

## コミット規則

このプロジェクトでは Conventional Commits 形式を採用しています。詳細は [.github/copilot-instructions.md](.github/copilot-instructions.md) を参照してください。

# ftvrcm

FireTV Stick用リモコンマウスアプリケーション

## 概要

ftvrcm（FireTV Remote Control Mouse）は、Fire TV StickのリモコンをPCマウスとして機能させるAndroid TVアプリです。リモコンの操作モードを「通常操作」と「マウス操作」で切り替え、他のアプリケーション実行中でも常に操作モードを変更できます。

## 主な機能

- **操作モード切り替え**：リモコンを通常動作またはマウスモードで使用
- **マウス入力**：リモコンの十字キーでポインタ操作、決定ボタンでクリック
- **カスタムアクション**：リモコンのボタンに任意のアクション（アプリ起動等）を割り当て
- **設定画面**：操作モード切り替え方法、キーマッピング、アクションをカスタマイズ
- **バックグラウンド監視**：他のアプリ実行中も操作モード切り替え可能
- **軽量実装**：CPU・メモリ消費を最小限に抑えた設計

## 対応デバイス

- **主要対応機種**：Fire TV Stick 4K Max 2nd Gen
- **最小API Level**：28（Android 9.0）

## 技術スタック

- **言語**：Kotlin
- **ビルドツール**：Gradle
- **アーキテクチャ**：MVVM
- **設定保存**：SharedPreferences
- **配布形式**：APK（野良アプリ）

## ドキュメント

詳細な設計ドキュメントは [docs/](docs/) に保存されています。

- [01_プロジェクト概要](docs/01_プロジェクト概要.md)
- [02_アーキテクチャ設計](docs/02_アーキテクチャ設計.md)
- [03_機能仕様書](docs/03_機能仕様書.md)
- [04_リソース最適化戦略](docs/04_リソース最適化戦略.md)
- [05_クラス設計仕様](docs/05_クラス設計仕様.md)
- [06_SharedPreferencesスキーマ](docs/06_SharedPreferencesスキーマ.md)
- [07_トラブルシュート](docs/07_トラブルシュート.md)

## 開発状況

- ✅ 要件定義・設計ドキュメント作成
- ⏳ Androidプロジェクト初期化
- ⏳ 実装開始

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
この場合でも、ADBが使えるならシェルから有効化できることがあります。

1. Fire TVで`開発者オプション` → `ADBデバッグ`をON
2. PCから接続（同一LAN）
   - `adb connect <FIRE_TV_IP>:5555`
3. サービス有効化（コンポーネント名は固定）
   - `adb shell settings put secure enabled_accessibility_services com.ftvrcm/com.ftvrcm.service.RemoteControlAccessibilityService`
   - `adb shell settings put secure accessibility_enabled 1`
4. 反映しない場合は再起動
   - `adb reboot`

有効化後、アプリの設定画面「デバッグ → サービス状態」が`接続: ...`になれば接続できています。
補足：このリポジトリでは `local.properties` を `.gitignore` 済みで、`sdk.dir` でSDK位置を指定できます。

## 既知の注意点（Fire TV）

- 一部のFire OSでは`Menu`長押しがシステム側に予約され、長押しイベントがアプリに届きにくい場合があります。
- その場合は設定画面で「操作モード切り替えキー」を`Menu`以外（例：`再生/一時停止`など）に変更してください。

## トラブルシュート

- 設定UIにアクセシビリティのトグルが出ない場合は、[docs/07_トラブルシュート.md](docs/07_%E3%83%88%E3%83%A9%E3%83%96%E3%83%AB%E3%82%B7%E3%83%A5%E3%83%BC%E3%83%88.md) を参照してください（ADBで有効化できます）。
- ポインタは出るがクリックしない場合も、同ドキュメントに原因切り分け（`dispatchGesture`のキャンセル等）をまとめています。

## ライセンス

このプロジェクトはMITライセンスの下で公開されています。詳細は [LICENSE](LICENSE) を参照してください。

## コミット規則

このプロジェクトでは Conventional Commits 形式を採用しています。詳細は [.github/copilot-instructions.md](.github/copilot-instructions.md) を参照してください。

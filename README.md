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

補足：このリポジトリでは `local.properties` を `.gitignore` 済みで、`sdk.dir` でSDK位置を指定できます。

## ライセンス

このプロジェクトはMITライセンスの下で公開されています。詳細は [LICENSE](LICENSE) を参照してください。

## コミット規則

このプロジェクトでは Conventional Commits 形式を採用しています。詳細は [.github/copilot-instructions.md](.github/copilot-instructions.md) を参照してください。
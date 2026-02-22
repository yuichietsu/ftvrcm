---
agent: 'agent'
description: ドキュメント全体（README.md・docs/）をソースコードから最新の状態に自動更新する
---

# ドキュメント自動更新エージェント

以下のドキュメントをすべて最新の状態に更新してください。

## 更新対象

| ファイル | 更新方針 |
|--------|---------|
| `README.md` | プロジェクト概要、クイックスタート、機能一覧、ドキュメントリンク |
| `proxy-server/README.md` | プロキシサーバーの説明、起動方法、API、ADB 制御パラメータ |
| `docs/diataxis/README.md` | Diataxis 4領域のファイルインデックス |
| `docs/diataxis/tutorials/` 配下 | チュートリアル群（学習体験） |
| `docs/diataxis/how-to/` 配下 | How-to ガイド群（タスク達成） |
| `docs/diataxis/reference/` 配下 | リファレンス群（仕様・コマンド・設定） |
| `docs/diataxis/explanation/` 配下 | 解説群（設計思想・背景） |

## インプットソース

### ソースコード
- `app/src/main/java/` 配下のすべての Kotlin ソース（`list_dir` でパッケージ構成を確認してから必要なファイルを読む）

### アプリ設定・ビルド
- #file:app/src/main/AndroidManifest.xml
- #file:app/build.gradle.kts
- #file:gradle.properties

### プロキシサーバー
- #file:proxy-server/README.md
- #file:proxy-server/server.js
- #file:proxy-server/package.json

### 開発規約
- #file:.github/copilot-instructions.md

### 手動管理ドキュメント（再生成のソース）
- `docs/dev/` 配下の Markdown ファイル（存在する場合）: 手動で追加された最新の仕様・設計ドキュメント。`docs/diataxis/` 再生成のソースとして優先して参照する

## 作業手順

### Step 1: 現状把握

1. `list_dir` で以下を確認する
   - `app/src/main/java/` のパッケージ構成（詳細なディレクトリ構造はドキュメントに記載しない）
   - `docs/dev/` 配下のドキュメント一覧（存在する場合）
   - `docs/diataxis/` の全ファイル一覧（各サブディレクトリも含む）
2. `app/build.gradle.kts` から `minSdk`・`targetSdk`・`versionName` を確認する
3. `app/src/main/AndroidManifest.xml` から宣言されているコンポーネント・パーミッションを確認する
4. `docs/dev/` のドキュメントを読み、ソースコードと照合して最新の状態を把握する

### Step 2: docs/diataxis/ を再生成

Diataxis の4領域を厳守して各ファイルを更新する。ソースコードと矛盾する箇所は必ず修正する。

**tutorials/** - 学習体験を提供するガイド
- 前提条件（Android バージョン）を `app/build.gradle.kts` の `minSdk` と一致させる
- APK インストール手順（ADB サイドロード）と初回設定手順（AccessibilityService 有効化）を記述する
- 開発環境構築（Android Studio・Gradle ビルド）手順を `app/build.gradle.kts` と一致させる

**how-to/** - 特定タスクの達成を助けるガイド
- AccessibilityService の有効化・無効化手順を記述する
- キー割り当ての変更方法をソースコードから確認して記述する
- プロキシサーバーの起動方法を `proxy-server/` の実装から確認して記述する
- APK のビルドとサイドロード手順を記述する

**reference/** - 仕様・コマンド・設定のリファレンス
- SharedPreferences のすべてのキー・型・デフォルト値をソースコードから反映する（追加・削除・変更に追従する）
- 割り当て可能なリモコンキー一覧をソースコードから確認して記述する
- プロキシサーバーの API（エンドポイント・リクエスト/レスポンス形式）を `proxy-server/server.js` から確認して記述する
- `AndroidManifest.xml` に宣言されたコンポーネント（Service・Activity・Receiver・パーミッション）の一覧を記述する

**explanation/** - 設計思想・背景・決定理由の解説
- アーキテクチャの概要（AccessibilityService + オーバーレイウィンドウによる設計）を記述する
- ディレクトリ構成の詳細は記載しない（ソースコードの構成変更に追従できないため）
- バックグラウンド動作の仕組みと設計上の判断をソースコードから確認して記述する
- プロキシサーバーの役割と通信設計の背景を記述する

**docs/diataxis/README.md**
- 全4領域のファイルインデックスを、実際に存在するファイルのみで構成する

### Step 3: README.md を更新

- 主な機能一覧を現在の実装と一致させる
- クイックスタートの手順をソースコードおよび `docs/diataxis/tutorials/` と一致させる
- ドキュメントリンクを `docs/diataxis/README.md` のファイルインデックスと一致させる（存在しないファイルへのリンクを排除する）
- 前提条件の Android バージョンを `app/build.gradle.kts` の `minSdk` から確認して更新する

### Step 4: proxy-server/README.md を更新

- 起動コマンド例・環境変数を `proxy-server/server.js` の実装と一致させる
- API エンドポイント一覧（`/health`, `/tap`, `/grantAccessibility`, `/screenshot`）を最新の実装から確認して記述する
- ADB 制御パラメータ（`ADB_TIMEOUT_MS` 等）をドキュメント（特に `docs/diataxis/reference/proxy-server.md`）と一致させる

## 共通ルール

- **日本語で記述する**（コードブロック内を除く）
- **存在しないファイルへのリンクを作らない**（必ず `list_dir` または `file_search` で存在確認する）
- **変更が不要な箇所は変更しない**
- **`docs/diataxis/` は Copilot 自動生成、`docs/dev/` は手動管理** という二層構造を維持する
- **ソースコードのディレクトリ構成の詳細はドキュメントに記載しない**（構成変更による陳腐化を防ぐため）
- ソースコードと矛盾する記述は必ず修正する（特に SharedPreferences のキー名・キーイベント定数・API 仕様）

## 完了後

変更ファイルを `git add` して、以下の形式でコミットする：

```
docs: update all documentation to reflect current codebase
```

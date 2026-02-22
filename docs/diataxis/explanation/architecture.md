# アーキテクチャ概要

## 設計方針

ftvrcm は以下の方針で設計されています。

- **軽量性**：外部ライブラリ依存を最小限に抑える
- **バックグラウンド常駐**：他のアプリ実行中でも操作モードを切り替えられる
- **拡張性**：タッチ注入方法をエミュレーション方式（`AccessibilityService` / `PROXY`）で切り替え可能にする

## AccessibilityService による動作

本アプリのコア機能は Android の `AccessibilityService`（`RemoteControlAccessibilityService`）として実装されています。

- システムからすべてのキーイベントを受け取ることができる
- 他のアプリが前面に表示されていても動作する
- ジェスチャ注入（`dispatchGesture`）やノードクリック（`performAction`）を通じてタッチ操作をエミュレートできる

これにより、Fire TV Stick のリモコンで任意のアプリを「タッチ操作」できます。

## タッチ注入の 2 方式

### AccessibilityService 方式

- `AccessibilityService.dispatchGesture()` でタップ・スワイプを注入する
- スクロールは `AccessibilityNodeInfo.performAction(ACTION_SCROLL_*)` を使用する
- Fire TV アプリ内完結するためセットアップが簡単
- ただし、Fire OS の制限によりシステム UI や特定アプリへの注入がブロックされる場合がある

### PROXY 方式

- アプリ → PC 上のプロキシサーバー（HTTP）→ `adb shell input` → Fire TV という経路で操作する
- Fire OS 8 ではアプリから端末内の `adbd` へのローカル接続がブロックされるため、PC を経由する構成が必要
- `AccessibilityService` で制限される操作も PC 経由の `adb shell input` で実現できる
- スワイプ・ズームは PC 側プロキシが `adb shell input swipe` を生成して送出する

## バックグラウンド動作の仕組み

AccessibilityService は Android システムに登録されたサービスとして常時起動します。

- アプリのライフサイクルと独立して動作するため、設定画面を閉じていてもモード切り替えが機能する
- モード切り替えキーの検知（長押し/ダブルタップ/シングルタップ）はサービス内のタイマーで実装している
  - Fire TV では `KeyEvent.isLongPress` が期待通りに発火しないケースがあるため、アプリ側で判定を行っている
- カーソルオーバーレイ（`CursorOverlay`）は `WindowManager` 経由でシステムオーバーレイとして表示する

## 設定の管理

設定は SharedPreferences に保存し、アプリと AccessibilityService が共有します。

- キー名の管理は `SettingsKeys` オブジェクトに集約
- 設定変更後はサービス側でもリロードすることで反映される

設定キーの詳細は [SharedPreferences スキーマ](../reference/shared-preferences.md) を参照してください。

## プロキシサーバーの役割

`proxy-server` は Fire TV アプリと ADB の橋渡し役です。

- Node.js で実装された最小構成の HTTP サーバー（依存パッケージなし）
- ADB 接続が切れた場合にバックグラウンドで自動再接続を試みる
- スクリーンショット取得と PC 側への保存をサポートする

詳細は [プロキシサーバーリファレンス](../reference/proxy-server.md) を参照してください。

## MVVM アーキテクチャ（設定画面）

設定画面（`SettingsActivity` / `SettingsFragment`）は設定項目の表示・変更に特化しています。

設定値は SharedPreferences を直接参照するシンプルな構成をとっており、外部 DI フレームワークは使用していません。

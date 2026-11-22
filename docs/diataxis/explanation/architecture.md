# アーキテクチャ概要

## 設計方針

ftvrcm は以下の方針で設計されています。

- **軽量性**：外部ライブラリ依存を最小限（AndroidX + Shizuku API + HiddenApiBypass）に抑える
- **バックグラウンド常駐**：他のアプリ実行中でも操作モードを切り替えられる
- **低遅延・自然なタッチ操作**：Shizuku 特権 Binder を介して `IInputManager.injectInputEvent` を直接呼び出し、0ms 遅延のタッチ・スワイプ・2 本指ピンチを実現する
- **フォールバック対応**：Shizuku 未使用時は `AccessibilityService` 単独でも基本操作を実行可能にする

## AccessibilityService による動作

本アプリのコア機能は Android の `AccessibilityService`（`RemoteControlAccessibilityService`）として実装されています。

- システムからすべてのキーイベントを受け取ることができる
- 他のアプリが前面に表示されていても動作する
- タッチ操作モードの切り替え、カーソルオーバーレイ制御、各操作のディスパッチを担う

## タッチ注入の 2 方式

### Shizuku 方式（デフォルト・推奨）

- `ShizukuTouchInjector` が `ShizukuBinderWrapper` + `HiddenApiBypass` を用いて `IInputManager.injectInputEvent` を直接呼び出す
- PC プロキシや外部プロセスを介さないため、0ms 遅延での即時注入が可能
- タップ・長押し・スワイプだけでなく、ネイティブ 2 本指マルチタッチ（`MotionEvent.ACTION_POINTER_DOWN` / `ACTION_MOVE` / `ACTION_POINTER_UP`）によるピンチイン/アウトを実現する
- また、Shizuku の特権シェル（`Shizuku.newProcess`）を利用して、設定画面からアプリ自身の `AccessibilityService` を即座に有効化する機能も提供する

### AccessibilityService 方式

- `GestureController` が `AccessibilityNodeInfo.performAction()`（`ACTION_CLICK` / `ACTION_LONG_CLICK` / `ACTION_SCROLL_*`）や `dispatchGesture()` を使用して操作をエミュレートする
- Shizuku が利用できない環境でも動作する
- ただし、Fire OS の制限によりシステム UI や特定アプリへの注入が制限される場合や、スクロール命令が対象ビューによって反応しない場合がある

## バックグラウンド動作の仕組み

AccessibilityService は Android システムに登録されたサービスとして常時起動します。

- アプリのライフサイクルと独立して動作するため、設定画面を閉じていてもモード切り替えが機能する
- モード切り替えキーの検知（長押し/ダブルタップ/シングルタップ）はサービス内のタイマーで実装している
  - Fire TV では `KeyEvent.isLongPress` が期待通りに発火しないケースがあるため、アプリ側で判定を行っている
- カーソルオーバーレイ（`CursorOverlay`）は `WindowManager` 経由でシステムオーバーレイとして表示する

## 設定の管理

設定は SharedPreferences に保存し、アプリと AccessibilityService が共有します。

- キー名の管理は `SettingsKeys` オブジェクト、読み書きと初期化は `SettingsStore` に集約
- 設定変更後はサービス側でもリロード・同期される

設定キーの詳細は [SharedPreferences スキーマ](../reference/shared-preferences.md) を参照してください。

## 設定画面の構成

設定画面（`SettingsActivity` / `SettingsFragment`）は `PreferenceFragmentCompat` ベースで構成されています。

- **固定ステータスバー**：画面下部に「アクセシビリティ（動作中/停止中）」と「Shizuku（起動中/未起動）」のリアルタイム状態を表示
- **サブスクリーン階層**：`hide + add` 方式による `PreferenceScreen` 遷移を採用し、前スクリーンのスクロール位置と D-pad フォーカスを保持
- **依存関係**：外部 DI フレームワークは使用せず、SharedPreferences を直接参照するシンプルな構成

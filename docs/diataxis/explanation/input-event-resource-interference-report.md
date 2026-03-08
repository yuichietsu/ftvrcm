# 入力イベント多発時のリソース影響・イベント干渉 統合レポート

更新日: 2026-03-08

## 目的

以下 2 点を同時に評価する。

1. 常駐 `AccessibilityService` が Fire TV の CPU/メモリを過剰消費しないか
2. ジョイパッド相当の入力イベント多発時に、他アプリ操作を妨害するイベント横取りが発生しないか

## 測定環境

- 端末: Fire TV Stick 4K Max 2nd Gen（`AFTKRT`）
- 接続: `adb` over TCP（`192.168.11.12:5555`）
- 対象アプリ: `com.ftvrcm`
- 主要コマンド: `dumpsys cpuinfo`, `dumpsys meminfo`, `dumpsys accessibility`, `dumpsys activity`

## 実装上の前提（コード確認）

- キー入力処理は `RemoteControlAccessibilityService.onKeyEvent()` が担当
  - `app/src/main/java/com/ftvrcm/service/RemoteControlAccessibilityService.kt`
- `NORMAL` モードでは `if (mode != OperationMode.MOUSE) return false` によりイベントをシステムへ通す
- `onAccessibilityEvent()` と `onInterrupt()` は `no-op`
- オーバーレイは `TYPE_ACCESSIBILITY_OVERLAY` かつ `FLAG_NOT_TOUCHABLE | FLAG_NOT_FOCUSABLE`

---

## 前回調査の統合結果（常駐影響 + 横取り）

### 1) 常駐時のCPU/メモリ

- `ON`（サービス有効）CPU 15秒サンプリング
  - `ON_CPU_AVG=0.000%`
  - `ON_CPU_MAX=0.000%`
- `ON` メモリ例
  - `TOTAL PSS: 42,624 KB`
  - `TOTAL RSS: 63,268 KB`
  - `TOTAL SWAP PSS: 10,307 KB`

### 2) サービス無効時の比較

- `enabled_accessibility_services` を削除し `accessibility_enabled=0` を設定
- 直後に `Bound services:{}` / `Enabled services:{}` を確認
- ただしプロセスはキャッシュで一時残存しうる（`pidof` で生存）
- `OFF + force-stop` では `pid` 消失、`meminfo` 取得不可（プロセス非在住）

### 3) イベント横取りの確認

- `HOME` キー試験
  - 遷移前: `org.smarttube.beta/...BrowseActivity`
  - 遷移後: `com.amazon.tv.launcher/.ui.HomeActivity_vNext`
  - 重大なシステムキー妨害は確認できず
- `NORMAL` モードで割当キー送信（`19/20/23`）時、`last_gesture_at_ms` は不変

---

## 今回追加調査（ジョイパッド入力多発の追試）

### 追加した調査項目

1. `MOUSE` モードでの長時間寄り入力負荷（mapped key flood）
2. 負荷前後の `meminfo Objects` 比較（`Views`, `Activities`, Binder など）
3. 負荷中のシステムキー遷移確認（`HOME`）
4. `MOUSE` で `BACK` 割当が存在する状態での実遷移確認

### シナリオと結果

#### A. `NORMAL` + `KEYCODE_BUTTON_A(96)` x300

- `last_gesture_at_ms`: `70673629 -> 70673629`（変化なし）
- CPU: `0%`
- メモリ: `PSS 12,078 KB / RSS 32,324 KB / SWAP 6,606 KB`

#### B. `MOUSE` + `KEYCODE_BUTTON_A(96)` x300（未割当想定）

- `last_gesture_at_ms`: `70673629 -> 70673629`（変化なし）
- CPU: `0%`
- メモリ: `PSS 11,674 KB / RSS 29,776 KB / SWAP 8,553 KB`

#### C. sustained flood（`BUTTON_A` x2000、20秒CPUサンプル）

- `FLOOD_CPU_AVG=0.145%`
- `FLOOD_CPU_MAX=2.900%`

#### D. extended flood（`DPAD_UP(19)` x5000、45秒CPUサンプル）

- `EXT_FLOOD_CPU_AVG=0.000%`
- `EXT_FLOOD_CPU_MAX=0.000%`

#### E. 負荷前後メモリオブジェクト（抜粋）

- 負荷前（`MOUSE`）
  - `TOTAL PSS: 10,615 KB`
  - `Views: 0`, `Activities: 0`, `Proxy Binders: 28`
- 負荷後（`DPAD_UP` flood 後）
  - `TOTAL PSS: 17,172 KB`
  - `Views: 0`, `Activities: 0`, `Proxy Binders: 27`
- 30秒後スナップショット（アプリ画面表示状態）
  - `TOTAL PSS: 52,866 KB`
  - `Views: 28`, `Activities: 2`, `GL mtrack: 12,878 KB`

> 補足: 最後の `PSS 52MB` は設定画面が前面化した状態のスナップショットで、
> UI/描画リソースを含む値。入力処理単体の負荷と切り分けて解釈する必要がある。

#### F. 干渉追試（負荷中の遷移）

- `DPAD_UP` flood 実行中に `HOME` を送信し、`HomeActivity_vNext` へ遷移を確認
- `operation_mode=MOUSE` かつ `mouse_key_pinch_out=4`（`BACK` 割当）状態でも、
  `SettingsActivity` から `KEYCODE_BACK` でランチャーへ遷移できることを確認

---

## 最終考察

- **CPU観点**: 入力イベント多発時でも、平均CPUはほぼ `0%`〜`0.145%`、ピークは `2.9%` に留まった。
  常時高負荷で Fire TV の応答速度を劣化させる兆候は見られない。
- **メモリ観点**: 入力負荷のみの場面では `PSS` は概ね `10MB〜17MB` 帯で推移した。
  高い `PSS` スナップショットは UI 前面化・描画リソース影響が大きい。
- **イベント干渉観点**: `NORMAL` モードではイベント通過の実装・実測が一致。
  また負荷中でも `HOME` 遷移は成立し、重大なシステム操作妨害は再現しなかった。

## 既知の制約

- `adb shell input keyevent` は実ジョイパッドのアナログ軸（`MotionEvent`）を完全再現しない。
- スナップショット計測（`meminfo`）は前景アプリ状態の影響を受けるため、単発値で断定しない。

## 追加推奨（今後）

1. 実ジョイパッド接続で `MotionEvent` を含む 30〜60 分連続計測（Perfetto）
2. `NORMAL` 固定・`MOUSE` 固定それぞれでフレーム時間（`dumpsys gfxinfo framestats`）比較
3. 主要アプリ（動画/ゲーム）併用時の `TOTAL` CPU と `com.ftvrcm` CPU の時系列比較

## 結論

現時点の実装・計測結果から、`ftvrcm` が入力イベント多発によって顕著なリソース逼迫や
重大なイベント横取りを引き起こす可能性は低い。
ただし、実ジョイパッドの軸入力まで含む最終評価は追加の長時間計測で補強するのが望ましい。
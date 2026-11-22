---
name: adb-app-evaluation
description: >-
  Evaluate whether an Android app or game is usable on Fire TV / Android TV
  both with a standard remote only and with ftvrcm enabled, by operating the UI
  via adb keyevents and inspecting screenshots, then recording the results in
  docs/diataxis/reference/app-compatibility.md. Use this skill when the user
  asks to test, evaluate, or check app/game compatibility over adb.
---

# ADB アプリ・ゲーム動作確認評価スキル

`adb` で接続された Fire TV / Android TV 実機上のアプリ・ゲームを実際に操作し、「リモコンのみ」と「`ftvrcm` 使用」それぞれの使用可能レベルを評価して [app-compatibility.md](../../../docs/diataxis/reference/app-compatibility.md) に記録します。

## 重要な原則

1. **直接タッチ注入（`adb shell input tap` 等）で評価しない**
   `ftvrcm` の評価を行うため、必ず `adb shell am broadcast -a com.ftvrcm.CMD ...` を使って `ftvrcm` 自身のカーソル移動・タッチ注入を介して対象アプリが反応するかを確認すること。
   > Note: `adb shell input keyevent` は Android の仕様上 `AccessibilityService.onKeyEvent` に届かないため、`ftvrcm` 側の操作には `com.ftvrcm.CMD` ブロードキャストを使用し、「リモコンのみ」の評価時には通常モードで `adb shell input keyevent` を使用する。
2. **スクリーンショット等で画面変化を確認しながら操作する**
   各操作の前後で `adb exec-out screencap -p`（または `uiautomator dump`）を取得して現在状態を把握し、盲目的に操作を連打しない。
3. **破壊的・不可逆な操作や環境への影響がある操作の制御**
   - **禁止（押さない）**：課金・購入・注文、アカウント削除、SNS 投稿、送金などの金銭的・不可逆・外部への影響が大きい操作は絶対に押さない。ログイン壁や課金壁に到達した場合は、そこまでの範囲で評価する。
   - **ゲームの場合**：上記の禁止事項（課金・アカウント削除等）や既存セーブの破壊を除き、ゲーム内の操作は自由に試してよい。
   - **アプリの場合（実行前にユーザーへ確認）**：実機の環境・データに影響を与える可能性がある操作（例：F-Droid 等でのアプリインストール / アンインストール / 更新、ファイル削除、設定初期化、ログアウト、権限変更など）を試す場合は、**実行する前に必ずユーザーへ確認を取ること**。
4. **テスト後の後片付け**
   評価終了後は `set_mode` で `NORMAL` モードに戻し、Shizuku 設定を変更した場合は元の設定（デフォルト `ON`）に戻しておく。
5. **3つの操作方法すべてをテストする**
   同じアプリ・ゲームについて、`リモコンのみ`、`ftvrcm`（Shizuku なし）、`ftvrcm+shizuku`（Shizuku あり）の 3 パターンで動作確認を行い、それぞれの行を記録する。

---

## 手順

### 1. 環境・バージョン情報の収集

以下の情報を取得する（複数端末がある場合は `-s <serial>` を付与）：

1. 接続端末の確認:
   ```bash
   adb devices
   ```
2. `ftvrcm` のコミットハッシュ / バージョン確認:
   ```bash
   git rev-parse --short HEAD
   ```
3. 対象アプリのバージョン取得（`<package>` は対象アプリのパッケージ名）:
   ```bash
   adb shell dumpsys package <package> | grep -E "versionName|versionCode" | head -n 2
   ```
4. `ftvrcm` の AccessibilityService 有効状態と Shizuku 状態の確認:
   ```bash
   adb shell settings get secure enabled_accessibility_services
   ```
   - `com.ftvrcm` が含まれていない場合はユーザーに有効化を案内するか、既存ドキュメントに従って確認する。
   - Shizuku の有効/無効は、テスト条件（`あり` / `なし`）として明確に記録する。必要なら `ftvrcm` の設定画面を開いてステータスバー（「アクセシビリティ: 動作中」「Shizuku: 起動中」等）をスクリーンショットで確認する。

### 2. `ftvrcm` 操作用 ADB コマンド（`com.ftvrcm.CMD`）

`ftvrcm` の AccessibilityService は ADB からの操作用に `com.ftvrcm.CMD` ブロードキャストを受け付けます（`RemoteControlAccessibilityService.kt`）。
※ `adb` に PATH が通っていない場合は `~/Android/Sdk/platform-tools/adb` を使用してください。

| 操作 | コマンド例（`adb shell am broadcast -a com.ftvrcm.CMD ...`） |
| :--- | :--- |
| モード設定（`MOUSE` / `NORMAL`） | `--es op set_mode --es mode MOUSE` |
| モードトグル | `--es op toggle_mode` |
| カーソル座標指定（左上基準） | `--es op set_cursor --ei x 960 --ei y 540` |
| カーソル相対移動 | `--es op move_cursor --ei dx 50 --ei dy 0` |
| タップ / ダブルタップ / 長押し | `--es op tap` / `--es op double_tap` / `--es op long_press` |
| 4方向スワイプ / スクロール | `--es op swipe_up` / `swipe_down` / `swipe_left` / `swipe_right` |
| ズーム（ピンチイン / アウト） | `--es op pinch_in` / `--es op pinch_out` |

### 3. 対象アプリの起動

```bash
adb shell monkey -p <package> -c android.intent.category.LAUNCHER 1
```

起動後、数秒待ってからスクリーンショットを取得し、初期画面を確認する。
（スクリーンショットは一時ディレクトリや `/tmp/ftvrcm_eval_*.png` などに保存し、`read_file` で視覚確認する）

```bash
adb exec-out screencap -p > /tmp/ftvrcm_eval_screen.png
```

### 4. 「リモコンのみ」の評価（通常操作モード）

1. `ftvrcm` が通常操作モード（カーソル非表示）であることを確認する（タッチ操作モードなら `adb shell input keyevent --longpress 82` で無効化）。
2. `DPAD_UP/DOWN/LEFT/RIGHT`（19/20/21/22）、`DPAD_CENTER`（23）、`BACK`（4）のみを使って以下を確認する：
   - フォーカス枠が見えるか、上下左右に移動できるか
   - 主要なボタン・メニューを決定キーで開けるか
   - コンテンツの閲覧・再生、またはゲームの開始〜基本操作ができるか
   - `BACK` で元の画面に戻れるか

### 5. 「`ftvrcm` 使用」の評価（タッチ操作モード）

1. `adb shell am broadcast -a com.ftvrcm.CMD --es op set_mode --es mode MOUSE` で `ftvrcm` のタッチ操作モードを有効化する。スクリーンショットでカーソル（輪っか表示）が出ていることを確認する。
2. `com.ftvrcm.CMD` 経由で以下を一通りテストする：
   - **カーソル移動＋タップ**：`set_cursor` / `move_cursor` で目的の UI 要素へカーソルを移動し、`tap` で画面遷移や反応があるか。
   - **スクロール / スワイプ**：リストや画面が `swipe_up` / `swipe_down` / `swipe_left` / `swipe_right` でスクロール・スワイプできるか。
   - **ピンチズーム**（該当アプリのみ）：`pinch_in` / `pinch_out` が機能するか。
   - **主要導線の完走**：アプリなら主要機能の実行、ゲームならタイトル→開始→基本プレイが一通り成立するか。
   - **操作テンポ・リアルタイム性の確認**：アクションゲームや時間制限・反射神経系のミニゲーム（離れたボタンを素早く交互に押す等）では、ADB 上でタップが通っても実際のリモコンカーソル移動では間に合わない場合がある。パススルーでの DPAD 操作で代替できる場合は `○`、DPAD も反応せず代替手段がない場合はプレイ成立の可否に応じて **`△`** または **`✕`** と判定し、その理由をメモに記載する。
   - **デフォルト設定でのキー干渉確認**：`BACK`（ズームイン）、`MENU`（モード切替 / パススルー）、`CH+/CH-`（スクロール）など、デフォルトのキー割り当てが対象アプリ側の操作と干渉して正常に使えないケースがないか確認する。

### 6. Shizuku あり / なし両条件の切り替えと再テスト

1. `ftvrcm` の設定画面を開き（`adb shell monkey -p com.ftvrcm -c android.intent.category.LAUNCHER 1`）、**「Shizukuを使用する（推奨）」** の ON / OFF を切り替えてもう一方の条件でもステップ 5 を実施する。
   - 特に `Shizuku: なし`（AccessibilityService 方式）では、スワイプ（`ACTION_SCROLL_*`）やピンチズーム（`GestureDescription` 等）の挙動・対応状況が変わるため、マップ移動やリストスクロール、ズームが機能するかを重点的に確認する。
2. テスト完了後は、元の Shizuku 設定（通常は ON）に戻す。

### 7. 判定と記録

[app-compatibility.md](../../../docs/diataxis/reference/app-compatibility.md) の「使用可能レベルの定義」（`◎` / `○` / `△` / `✕`）に従ってレベルを判定し、該当セクション（「アプリ」または「ゲーム」）のテーブルに結果を追記する。

- **操作方法ごとに行を分ける**：`リモコンのみ`（`ftvrcm ver.` は `―`）、`ftvrcm`、`ftvrcm+shizuku` の 3 行を記録し、**レベル** 列には記号のみ記載する。
- **メモ** 列の記載ルール：
  - デフォルト設定のままだとキー干渉などで正常に使えない場合は、**設定変更が必要である旨と具体的な対処方法**（例：「戻るキー干渉のためズームキー変更推奨」「パススルー併用が必要」等）を必ず記載する。
  - `Shizuku: なし` でスワイプやピンチが効かない等、方式による制限がある場合も簡潔に記載する。
  - 既存の行を更新する際、開発者が追記した感想やメモがある場合は消さずに残す（追記する）。

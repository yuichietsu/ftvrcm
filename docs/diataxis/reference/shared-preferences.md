# SharedPreferences スキーマ

ftvrcm の設定は SharedPreferences ファイル（`ftvrcm_settings`）に保存されます。

## 設定キー一覧

### 操作モード

| キー | 型 | デフォルト | 説明 |
|-----|----|-----------|----|
| `operation_mode` | String | `NORMAL` | 現在の操作モード（`NORMAL` \| `MOUSE`） |

### モード切り替え

| キー | 型 | デフォルト | 説明 |
|-----|----|-----------|----|
| `toggle_keycode` | String | `82`（MENU） | 有効化/無効化キーのキーコード。未知キーはスキャンコードを負数で保存（例: `-168`）。割り当てなしは `0` |
| `toggle_trigger` | String | `LONG_PRESS` | 切り替えの操作方法（`LONG_PRESS` \| `DOUBLE_TAP` \| `SINGLE_TAP`） |

> 注意: Fire TV では `KEYCODE_BACK`（4）長押しがシステム動作と競合し、AccessibilityService が無効化される場合があります。

### カーソル操作

| キー | 型 | デフォルト | 説明 |
|-----|----|-----------|----|
| `mouse_pointer_speed` | Int | `10` | 十字キー 1 回押下あたりのポインタ移動量（UI 設定範囲: 1〜50） |
| `mouse_cursor_start_position` | String | `center` | タッチ操作開始時のカーソル位置（`center` / `top_left` / `top` / `top_right` / `left` / `right` / `bottom_left` / `bottom` / `bottom_right` / `previous`） |
| `mouse_cursor_last_x` | Int | ― | 前回のカーソル X 座標 |
| `mouse_cursor_last_y` | Int | ― | 前回のカーソル Y 座標 |
| `touch_visual_feedback_enabled` | Boolean | `true` | タッチ操作フィードバック（アニメーション等）を有効化 |

### キー割り当て

| キー | 型 | デフォルト | 説明 |
|-----|----|-----------|----|
| `mouse_key_up` | String | `19` | カーソル上移動キー |
| `mouse_key_down` | String | `20` | カーソル下移動キー |
| `mouse_key_left` | String | `21` | カーソル左移動キー |
| `mouse_key_right` | String | `22` | カーソル右移動キー |
| `mouse_key_click` | String | `23` | タップ/ロングタップキー |
| `mouse_key_scroll_up` | String | `166` | 上スクロール/スワイプキー |
| `mouse_key_scroll_down` | String | `167` | 下スクロール/スワイプキー |
| `mouse_key_scroll_left` | String | `89` | 左スクロール/スワイプキー |
| `mouse_key_scroll_right` | String | `90` | 右スクロール/スワイプキー |
| `mouse_key_pinch_out` | String | `4`（BACK） | ズームイン（ピンチアウト）キー |
| `mouse_key_pinch_in` | String | `85`（MEDIA_PLAY_PAUSE） | ズームアウト（ピンチイン）キー |
| `mouse_key_cursor_dpad_toggle` | String | `82`（MENU） | リモコン操作パススルー切り替えキー |
| `key_mapping` | StringSet | ※初期値あり | キーコードとアクション ID のマッピング（形式: `{keyCode}:{actionId}`） |

### 動作チューニング（スワイプ/ズーム）

| キー | 型 | デフォルト | 説明 |
|-----|----|-----------|----|
| `mouse_swipe_distance_percent` | Int | `28` | スワイプ距離（画面短辺に対する%、5〜95） |
| `mouse_scroll_repeat_longpress` | Boolean | `true` | スクロール/スワイプキーの長押し連続実行を有効化 |
| `mouse_scroll_repeat_interval_ms` | Int | `120` | 長押し連続実行の間隔（ms、UI 設定範囲: 30〜500） |
| `mouse_pinch_distance_percent` | Int | `28` | ズーム量（画面短辺に対する%、5〜95） |

### エミュレーション方法（Shizuku 設定）

| キー | 型 | デフォルト | 説明 |
|-----|----|-----------|----|
| `use_shizuku` | Boolean | `true` | Shizuku を使用するかどうか（`true` で `SHIZUKU`、`false` で `ACCESSIBILITY_SERVICE`） |
| `emulation_method` | String | `SHIZUKU` | 内部保持用エミュレーション方法（`SHIZUKU` \| `ACCESSIBILITY_SERVICE`） |

### ジェスチャデバッグ（TouchTestActivity 用）

| キー | 型 | デフォルト | 説明 |
|-----|----|-----------|----|
| `last_gesture_type` | String | ― | 直近のジェスチャ種別 |
| `last_gesture_status` | String | ― | 直近のジェスチャ状態（`DISPATCHING` / `COMPLETED` / `REJECTED` 等） |
| `last_gesture_detail` | String | ― | 詳細（実行手段・エラー内容など） |
| `last_gesture_at_ms` | Long | ― | 記録時刻（`uptimeMillis`） |

---

## キーコード早見表（Fire TV Stick 対応キー）

| KeyCode | リモコンボタン |
|---------|-------------|
| `19` | 十字キー 上（DPAD_UP） |
| `20` | 十字キー 下（DPAD_DOWN） |
| `21` | 十字キー 左（DPAD_LEFT） |
| `22` | 十字キー 右（DPAD_RIGHT） |
| `23` | 決定（DPAD_CENTER） |
| `4` | 戻る（BACK） |
| `3` | ホーム（HOME） |
| `82` | メニュー（MENU） |
| `85` | 再生/一時停止（MEDIA_PLAY_PAUSE） |
| `89` | 巻き戻し（MEDIA_REWIND） |
| `90` | 早送り（MEDIA_FAST_FORWARD） |
| `166` | CH+（CHANNEL_UP） |
| `167` | CH-（CHANNEL_DOWN） |

未知のキー（`KEYCODE_UNKNOWN`）はスキャンコードを負数で保存します（例: `-168`）。割り当てなしは `0`。

---

## `key_mapping` のアクション ID

| ActionId | 操作 |
|----------|------|
| `mouse_up` | カーソル上移動 |
| `mouse_down` | カーソル下移動 |
| `mouse_left` | カーソル左移動 |
| `mouse_right` | カーソル右移動 |
| `mouse_click` | タップ（短押し）/ ロングタップ（長押し） |
| `mouse_scroll_up` | 上スワイプ（`SHIZUKU`）/ 上スクロール（`ACCESSIBILITY_SERVICE`） |
| `mouse_scroll_down` | 下スワイプ（`SHIZUKU`）/ 下スクロール（`ACCESSIBILITY_SERVICE`） |
| `mouse_scroll_left` | 左スワイプ（`SHIZUKU`）/ 左スクロール（`ACCESSIBILITY_SERVICE`） |
| `mouse_scroll_right` | 右スワイプ（`SHIZUKU`）/ 右スクロール（`ACCESSIBILITY_SERVICE`） |
| `mouse_pinch_in` | ズームアウト（ピンチイン） |
| `mouse_pinch_out` | ズームイン（ピンチアウト） |

> `mouse_key_cursor_dpad_toggle` は `key_mapping` には含まれず、個別設定として保持されます。

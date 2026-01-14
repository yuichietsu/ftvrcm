# SharedPreferences スキーマ

## 概要
ftvrcm の設定データは SharedPreferences で保存されます。本ドキュメントは、スキーマ定義とデータ形式を記載します。

## SharedPreferences ファイル

```
com.ftvrcm_preferences.xml
└── ftvrcm_settings
    ├── toggle_keycode
    ├── toggle_trigger
    ├── toggle_longpress
    ├── operation_mode
    ├── mouse_pointer_speed
    ├── emulation_method
    ├── proxy_host
    ├── proxy_port
    ├── proxy_token
    ├── mouse_key_up
    ├── mouse_key_down
    ├── mouse_key_left
    ├── mouse_key_right
    ├── mouse_key_click
    ├── mouse_key_scroll_up
    ├── mouse_key_scroll_down
    ├── mouse_key_scroll_left
    ├── mouse_key_scroll_right
    ├── mouse_key_pinch_in
    ├── mouse_key_pinch_out
    ├── mouse_swipe_distance_percent
    ├── mouse_swipe_double_scale
    ├── mouse_pinch_distance_percent
    ├── mouse_pinch_double_scale
    ├── mouse_scroll_repeat_longpress
    ├── mouse_scroll_repeat_interval_ms
    ├── mouse_key_cursor_dpad_toggle
    ├── mouse_cursor_start_position
    ├── touch_visual_feedback_enabled
    ├── key_mapping
    ├── last_gesture_type
    ├── last_gesture_status
    ├── last_gesture_detail
    ├── last_gesture_at_ms
    
```

## データスキーマ

### 1. 操作モード（operation_mode）

| 項目 | 値 |
|------|-----|
| **キー** | `operation_mode` |
| **型** | String |
| **値** | `NORMAL` \| `MOUSE` |
| **デフォルト** | `NORMAL` |
| **説明** | 現在の操作モード |

**例**：
```xml
<string name="operation_mode">MOUSE</string>
```

---

### 1.1 モード切り替えキー（toggle_keycode / toggle_trigger / toggle_longpress）

| 項目 | 値 |
|------|-----|
| **キー** | `toggle_keycode` / `toggle_trigger` / `toggle_longpress` |
| **型** | String / String / Boolean |
| **デフォルト** | `82`（`KEYCODE_MENU`） / `LONG_PRESS` / `true`（レガシー） |
| **説明** | 操作モード（通常/タッチ操作）を切り替えるキーと、切り替え操作（長押し/ダブルタップ/シングルタップ）を指定します。`toggle_longpress` は旧設定の互換用です。 |

**例**：
```xml
<string name="toggle_keycode">82</string>
<string name="toggle_trigger">LONG_PRESS</string>
```

補足：Fire TV（Fire OS）では `KEYCODE_BACK`（4）長押しがシステム動作と競合し、アクセシビリティが無効化される場合があります。
補足：未知のキーは `KEYCODE_UNKNOWN` を検出した場合、スキャンコードを負数で保存します（例：`-168`）。

---

### 2. マウス感度（mouse_sensitivity）

| 項目 | 値 |
|------|-----|
| **キー** | `mouse_sensitivity` |
| **型** | Integer |
| **範囲** | 1-20 |
| **デフォルト** | 10 |
| **説明** | マウス移動時のピクセル数（1ステップあたり） |

**例**：
```xml
<int name="mouse_sensitivity" value="10" />
```

---

### 3. ポインタ移動速度（mouse_pointer_speed）

| 項目 | 値 |
|------|-----|
| **キー** | `mouse_pointer_speed` |
| **型** | Integer |
| **デフォルト** | 10 |
| **説明** | リモコン十字キー1回押下あたりのポインタ移動量（体感的な移動の大きさ） |

**例**：
```xml
<int name="mouse_pointer_speed" value="10" />
```

---

### 3.1 タッチ操作フィードバック（touch_visual_feedback_enabled）

タッチ操作時の視覚フィードバック（タップ時の半透明表示、スワイプ軌跡、スクロール方向矢印）を有効化します。

| 項目 | 値 |
|------|-----|
| **キー** | `touch_visual_feedback_enabled` |
| **型** | Boolean |
| **デフォルト** | `true` |
| **説明** | タッチ操作のアニメーション/フィードバック表示を有効化 |

**例**：
```xml
<boolean name="touch_visual_feedback_enabled" value="true" />
```

---

### 3.0 スワイプ距離（mouse_swipe_distance_percent）

`emulation_method=PROXY` の場合に `input swipe` を生成する距離を調整します（画面短辺に対する割合）。

| 項目 | 値 |
|------|-----|
| **キー** | `mouse_swipe_distance_percent` |
| **型** | Integer |
| **範囲** | 5-95 |
| **デフォルト** | 28 |
| **説明** | スワイプ距離の割合（%） |

**例**：
```xml
<int name="mouse_swipe_distance_percent" value="28" />
```

---

### 3.0.1 スワイプダブルクリック倍率（mouse_swipe_double_scale）

スワイプキーをダブルクリックした際の距離倍率を指定します。

| 項目 | 値 |
|------|-----|
| **キー** | `mouse_swipe_double_scale` |
| **型** | String（Float） |
| **範囲** | 0.3-3.0 |
| **デフォルト** | 2.0 |
| **説明** | ダブルクリック時のスワイプ距離倍率（2倍/0.5倍など） |

**例**：
```xml
<string name="mouse_swipe_double_scale">2.0</string>
```

---

### 3.0.2 ピンチ量（mouse_pinch_distance_percent）

ピンチ操作の指の開き幅を画面短辺の割合で指定します。

| 項目 | 値 |
|------|-----|
| **キー** | `mouse_pinch_distance_percent` |
| **型** | Integer |
| **範囲** | 5-95 |
| **デフォルト** | 28 |
| **説明** | ピンチ開始/終了時の指の開き幅（%） |

**例**：
```xml
<int name="mouse_pinch_distance_percent" value="28" />
```

---

### 3.0.3 ピンチダブルクリック倍率（mouse_pinch_double_scale）

ピンチキーをダブルクリックした際のピンチ量倍率を指定します。

| 項目 | 値 |
|------|-----|
| **キー** | `mouse_pinch_double_scale` |
| **型** | String（Float） |
| **範囲** | 0.3-3.0 |
| **デフォルト** | 2.0 |
| **説明** | ダブルクリック時のピンチ量倍率（2倍/0.5倍など） |

**例**：
```xml
<string name="mouse_pinch_double_scale">2.0</string>
```

---

### 3.0.4 長押し連続実行（mouse_scroll_repeat_longpress / mouse_scroll_repeat_interval_ms）

上下左右の「スクロール/スワイプ」キーを長押ししたとき、一定間隔で連続実行するための設定です。

| 項目 | 値 |
|------|-----|
| **キー** | `mouse_scroll_repeat_longpress` / `mouse_scroll_repeat_interval_ms` |
| **型** | Boolean / Integer |
| **デフォルト** | `true` / 120 |
| **説明** | 長押しで連続実行するか、連続実行の間隔（ms） |

**例**：
```xml
<boolean name="mouse_scroll_repeat_longpress" value="true" />
<int name="mouse_scroll_repeat_interval_ms" value="120" />
```

---

### 3.1 エミュレーション方法（emulation_method）

| 項目 | 値 |
|------|-----|
| **キー** | `emulation_method` |
| **型** | String |
| **値** | `ACCESSIBILITY_SERVICE` \| `PROXY` |
| **デフォルト** | `ACCESSIBILITY_SERVICE` |
| **説明** | タップ/スクロール系の注入方法を選択します（`PROXY`の場合はPC上のプロキシ経由で`adb shell input`を使用） |

**例**：
```xml
<string name="emulation_method">PROXY</string>
```

---

### 3.2 プロキシ接続先（proxy_host / proxy_port / proxy_token）

`emulation_method=PROXY` の場合に、アプリが接続するPC上のプロキシ（HTTP）の接続先を指定します。

| 項目 | 値 |
|------|-----|
| **キー** | `proxy_host` / `proxy_port` / `proxy_token` |
| **型** | String / String / String |
| **デフォルト** | 空 / `8787` / 空 |
| **説明** | `proxy_token` は任意（推奨）。PC側プロキシでトークンを設定している場合に同じ値を入れます |

**例**：
```xml
<string name="proxy_host">192.168.11.127</string>
<string name="proxy_port">8787</string>
<string name="proxy_token">change-me</string>
```

### 3.3 （レガシー）アプリ内ADB直結について

過去バージョンではアプリ内ADBクライアントで `adbd` に直接接続する方式がありましたが、Fire OS 8 以降ではブロック/不安定になりやすいため撤去しました。
現在は `emulation_method=PROXY`（PC上プロキシ経由）を推奨します。

### 4. キーマッピング（key_mapping）

| 項目 | 値 |
|------|-----|
| **キー** | `key_mapping` |
| **型** | StringSet |
| **形式** | `{keyCode}:{actionId}` (カンマ区切り) |
| **デフォルト** | 空集合（初期化時に作成） |
| **説明** | リモコンキーとタッチ操作のマッピング |

**例**：
```xml
<set name="key_mapping">
  <string>19:mouse_up</string>
  <string>20:mouse_down</string>
  <string>21:mouse_left</string>
  <string>22:mouse_right</string>
  <string>23:mouse_click</string>
    <string>166:mouse_scroll_up</string>
    <string>167:mouse_scroll_down</string>
    <string>89:mouse_scroll_left</string>
    <string>90:mouse_scroll_right</string>
    <string>168:mouse_pinch_in</string>
    <string>169:mouse_pinch_out</string>
</set>
```

**キーコード一覧**（Fire TV Stick対応）：
| KeyCode | リモコンボタン |
|---------|---------------|
| 19 | 十字キー 上 |
| 20 | 十字キー 下 |
| 21 | 十字キー 左 |
| 22 | 十字キー 右 |
| 23 | 決定ボタン（KEYCODE_DPAD_CENTER） |
| 4 | 戻るボタン（KEYCODE_BACK） |
| 3 | ホームボタン（KEYCODE_HOME） |
| 82 | メニューボタン（KEYCODE_MENU） |
| 166 | チャンネル+（KEYCODE_CHANNEL_UP） |
| 167 | チャンネル-（KEYCODE_CHANNEL_DOWN） |
| 89 | 巻き戻し（KEYCODE_MEDIA_REWIND） |
| 90 | 早送り（KEYCODE_MEDIA_FAST_FORWARD） |

**タッチ操作 ID**：
| ActionId | 操作 |
|----------|------|
| `mouse_up` | ポインタ上移動 |
| `mouse_down` | ポインタ下移動 |
| `mouse_left` | ポインタ左移動 |
| `mouse_right` | ポインタ右移動 |
| `mouse_click` | タップ（短押し）/ロングタップ（長押し） |
| `mouse_scroll_up` | 上スクロール（`ACCESSIBILITY_SERVICE`）/ 上スワイプ（`PROXY`） |
| `mouse_scroll_down` | 下スクロール（`ACCESSIBILITY_SERVICE`）/ 下スワイプ（`PROXY`） |
| `mouse_scroll_left` | 左スクロール（`ACCESSIBILITY_SERVICE`）/ 左スワイプ（`PROXY`） |
| `mouse_scroll_right` | 右スクロール（`ACCESSIBILITY_SERVICE`）/ 右スワイプ（`PROXY`） |
| `mouse_pinch_in` | ピンチイン |
| `mouse_pinch_out` | ピンチアウト |

補足：`mouse_key_cursor_dpad_toggle` はキー割り当てに含まれず、個別設定として保持されます。
### 5. 最終ジェスチャ記録（last_gesture_*）

TouchTestActivity（動作確認画面）で「最後に注入したジェスチャがどう処理されたか（成功/キャンセル/フォールバック等）」を確認するためのデバッグ用途の記録です。

| 項目 | 値 |
|------|-----|
| **キー** | `last_gesture_type` / `last_gesture_status` / `last_gesture_detail` / `last_gesture_at_ms` |
| **型** | String / String / String / Long |
| **デフォルト** | 未設定（表示上は `-` 扱い） |
| **説明** | 直近のジェスチャ種別・状態・詳細（フォールバック有無など）・記録時刻（`uptimeMillis`） |

**例**：
```xml
<string name="last_gesture_type">swipe</string>
<string name="last_gesture_status">CANCELLED</string>
<string name="last_gesture_detail">fallback=SCROLL_VERTICAL ok</string>
<long name="last_gesture_at_ms" value="123456789" />
```

---

## デフォルト値初期化

アプリ初回起動時、以下のデフォルト値で初期化：

```kotlin
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("ftvrcm_settings", Context.MODE_PRIVATE)

    fun initializeDefaultsIfNeeded() {
        // operation_mode が無ければ未初期化とみなす
        if (prefs.contains("operation_mode")) return

        prefs.edit().apply {
            putString("operation_mode", "NORMAL")
            putString("toggle_keycode", "82")
            putString("toggle_trigger", "LONG_PRESS")
            putInt("mouse_pointer_speed", 10)
            putString("emulation_method", "ACCESSIBILITY_SERVICE")

            putString("mouse_key_up", "19")
            putString("mouse_key_down", "20")
            putString("mouse_key_left", "21")
            putString("mouse_key_right", "22")
            putString("mouse_key_click", "23")
            putString("mouse_key_scroll_up", "166")
            putString("mouse_key_scroll_down", "167")
            putString("mouse_key_scroll_left", "89")
            putString("mouse_key_scroll_right", "90")
            putString("mouse_key_pinch_in", "168")
            putString("mouse_key_pinch_out", "169")

            putInt("mouse_swipe_distance_percent", 28)
            putString("mouse_swipe_double_scale", "2.0")
            putInt("mouse_pinch_distance_percent", 28)
            putString("mouse_pinch_double_scale", "2.0")
            putBoolean("mouse_scroll_repeat_longpress", true)
            putInt("mouse_scroll_repeat_interval_ms", 120)

            putStringSet(
                "key_mapping",
                setOf(
                    "19:mouse_up",
                    "20:mouse_down",
                    "21:mouse_left",
                    "22:mouse_right",
                    "23:mouse_click",
                    "166:mouse_scroll_up",
                    "167:mouse_scroll_down",
                    "89:mouse_scroll_left",
                    "90:mouse_scroll_right",
                    "168:mouse_pinch_in",
                    "169:mouse_pinch_out",
                ),
            )
            apply()
        }
    }
}
```

---

## バックアップとリセット機能

### 設定エクスポート
ユーザーが設定をバックアップ可能：

```kotlin
fun exportSettings(): String {
    val allSettings = sharedPrefs.all
    return JSONObject(allSettings as Map<*, *>).toString()
}
```

### 設定インポート
バックアップから復元可能：

```kotlin
fun importSettings(jsonString: String) {
    val jsonObject = JSONObject(jsonString)
    sharedPrefs.edit().apply {
        jsonObject.keys().forEach { key ->
            when (val value = jsonObject.get(key)) {
                is String -> putString(key, value)
                is Int -> putInt(key, value)
                is Boolean -> putBoolean(key, value)
            }
        }
        apply()
    }
}
```

### 設定リセット
ユーザーが設定をリセット可能：

```kotlin
fun resetToDefaults() {
    sharedPrefs.edit().clear().apply()
    initializeDefaults()
}
```

---

## セキュリティ考慮事項

### 1. 暗号化

現在、SharedPreferences は暗号化なしで保存：
- **理由**：低いセキュリティリスク（ローカル設定のみ）
- **将来**：EncryptedSharedPreferences 導入を検討

### 2. パーミッション

SharedPreferences ファイルアクセス権限：
```xml
<!-- AndroidManifest.xml -->
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
```

---

## トラブルシューティング

### SharedPreferences データ破損時

```kotlin
fun repairData() {
    try {
        val data = sharedPrefs.all
        // データ検証
    } catch (e: Exception) {
        Log.e("ftvrcm", "SharedPreferences 破損", e)
        resetToDefaults()
    }
}
```

---

## まとめ

- **シンプルなスキーマ**：StringSet と基本型のみ使用
- **保守性**：デフォルト値明記、初期化ロジック統一
- **互換性**：開発中のためスキーマ互換を保証しない（必要に応じてリセット）

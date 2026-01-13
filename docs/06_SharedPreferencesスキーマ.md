# SharedPreferences スキーマ

## 概要
ftvrcm の設定データは SharedPreferences で保存されます。本ドキュメントは、スキーマ定義とデータ形式を記載します。

## SharedPreferences ファイル

```
com.ftvrcm_preferences.xml
└── ftvrcm_settings
    ├── toggle_keycode
    ├── toggle_longpress
    ├── operation_mode
    ├── mouse_pointer_speed
    ├── emulation_method
    ├── proxy_host
    ├── proxy_port
    ├── proxy_token
    ├── adb_host
    ├── adb_port
    ├── mouse_key_up
    ├── mouse_key_down
    ├── mouse_key_left
    ├── mouse_key_right
    ├── mouse_key_click
    ├── mouse_key_scroll_up
    ├── mouse_key_scroll_down
    ├── mouse_key_scroll_left
    ├── mouse_key_scroll_right
    ├── mouse_key_cursor_dpad_toggle
    ├── mouse_cursor_start_position
    ├── key_mapping
    ├── background_monitoring_enabled
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

### 1.1 モード切り替えキー（toggle_keycode / toggle_longpress）

| 項目 | 値 |
|------|-----|
| **キー** | `toggle_keycode` / `toggle_longpress` |
| **型** | String / Boolean |
| **デフォルト** | `85`（`KEYCODE_MEDIA_PLAY_PAUSE`） / `true` |
| **説明** | 操作モード（通常/タッチ操作）を切り替えるキーと、切り替えを長押しにするかどうか |

**例**：
```xml
<string name="toggle_keycode">85</string>
<boolean name="toggle_longpress" value="true" />
```

補足：Fire TV（Fire OS）では `KEYCODE_BACK`（4）長押しがシステム動作と競合し、アクセシビリティが無効化される場合があります。

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

### 3.3 （レガシー）ADB接続先（adb_host / adb_port）

過去バージョンではアプリ内ADBクライアントで `adbd` に直接接続していましたが、Fire OS 8 以降ではブロックされる場合があります。
互換性のため設定キーは残っていますが、現在の推奨は `PROXY` 方式です。

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
| `mouse_key_cursor_dpad_toggle` | 入力モード（カーソル/方向キー）切り替えキー（デフォルト: `MEDIA_PLAY_PAUSE`） |


### 5. バックグラウンド監視有効フラグ（background_monitoring_enabled）

| 項目 | 値 |
|------|-----|
| **キー** | `background_monitoring_enabled` |
| **型** | Boolean |
| **値** | `true` \| `false` |
| **デフォルト** | `true` |
| **説明** | バックグラウンドサービスでリモコンイベント監視するか |

**例**：
```xml
<boolean name="background_monitoring_enabled" value="true" />
```

---

### 6. 最終ジェスチャ記録（last_gesture_*）

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
            putInt("mouse_pointer_speed", 10)
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
                ),
            )
            putBoolean("background_monitoring_enabled", true)
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

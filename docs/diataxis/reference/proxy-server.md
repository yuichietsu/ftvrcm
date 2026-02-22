# プロキシサーバーリファレンス

`proxy-server/server.js` の API・環境変数・CLI 引数のリファレンスです。

## 起動コマンド

```bash
cd proxy-server
node server.js [options]
```

Node.js v18 以上が必要です。依存パッケージはありません（Node.js 標準モジュールのみ使用）。

## 環境変数

| 変数 | デフォルト | 説明 |
|-----|-----------|------|
| `FIRETV_SERIAL` | ― | ADB シリアル（例: `192.168.11.12:5555`） |
| `PROXY_TOKEN` | ― | 認証トークン（任意・推奨） |
| `HOST` | `0.0.0.0` | リッスンアドレス |
| `PORT` | `8787` | リッスンポート |

### ADB 制御パラメータ

| 変数 | デフォルト | 説明 |
|-----|-----------|------|
| `ADB_TIMEOUT_MS` | `8000` | `adb shell` 等の通常コマンドのタイムアウト（ms） |
| `ADB_CONNECT_TIMEOUT_MS` | `8000` | `adb connect` のタイムアウト（ms） |
| `ADB_CONNECT_COOLDOWN_MS` | `5000` | 自動 `adb connect` の最小試行間隔（ms） |
| `ADB_AUTH_FAIL_THRESHOLD` | `3` | 認証失敗がこの回数以上連続したら再認証候補とする |
| `ADB_AUTH_FAIL_WINDOW_MS` | `60000` | 連続判定の時間窓（ms） |
| `ADB_REAUTH_COOLDOWN_MS` | `600000` | 再認証フローの最小実行間隔（ms） |
| `ADB_REAUTH_MAX_PER_HOUR` | `3` | 1 時間あたりの再認証フロー最大回数（`0` で無効化） |
| `ADB_READY_CACHE_MS` | `1500` | ADB 状態が `device` のときに「新鮮」とみなすキャッシュ時間（ms） |
| `ADB_STATE_POLL_MS` | `0` | ADB 状態のバックグラウンド監視間隔（`0` で無効化） |

### ログパラメータ

| 変数 | 説明 |
|-----|------|
| `PROXY_DEBUG=1` | リクエスト概要を出力 |
| `PROXY_LOG_BODY=1` | 受信 JSON ボディを出力（トークンは `<redacted>`） |
| `PROXY_LOG_ADB=1` | 実行した `adb` コマンドと stdout/stderr を出力 |

## CLI 引数

環境変数と同じパラメータを `--` 付きのオプションで指定できます。

```
--adb-timeout-ms
--adb-connect-timeout-ms
--adb-connect-cooldown-ms
--adb-auth-fail-threshold
--adb-auth-fail-window-ms
--adb-reauth-cooldown-ms
--adb-reauth-max-per-hour
--adb-ready-cache-ms
--adb-state-poll-ms
--screenshot-dir <path>   スクリーンショット保存ディレクトリ（デフォルト: ./screenshots）
--debug / -d              (PROXY_DEBUG=1 相当)
--log-body                (PROXY_LOG_BODY=1 相当)
--log-adb                 (PROXY_LOG_ADB=1 相当)
```

## API エンドポイント

認証が設定されている場合、すべてのリクエストに `X-Auth-Token: <token>` ヘッダーが必要です。

### `GET /health`

ADB および接続状態を確認します。TCP 接続が切れている場合は `adb connect` を試行します。

- 成功: `200 OK`
- ADB 未接続/停止: 非 2xx

### `POST /tap`

タップを送出します。

```json
{ "x": 540, "y": 960, "serial": "optional" }
```

### `POST /grantAccessibility`

ADB で AccessibilityService を有効化します。

```json
{ "component": "com.ftvrcm/com.ftvrcm.service.RemoteControlAccessibilityService", "serial": "optional" }
```

### `POST /screenshot`

スクリーンショットを取得して保存します。保存先は `--screenshot-dir`（デフォルト: `./screenshots`）。

```json
{ "serial": "optional" }
```

## ADB の自動再接続フロー

- ADB が `device` 状態でない場合、バックグラウンドで `adb connect <serial>` を自動試行します
- `unauthorized` 等が連続した場合、条件を満たすと再認証フロー（`disconnect` → `kill-server` → `start-server` → `connect`）をベストエフォートで実行します
- 端末側での「USBデバッグを許可」はユーザー操作が必要です
- `ADB_STATE_POLL_MS=0`（デフォルト）の場合、バックグラウンド監視は行わず接続確認はリクエスト実行時のみです

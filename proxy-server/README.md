# ftvrcm-proxy-server

FireOS 8 でアプリから `adbd` へローカル接続がブロックされる環境向けの、最小プロキシサーバです。

構成：FireTVアプリ → (LAN) → PCのプロキシ → (adb) → FireTV

## 前提

- PCに `adb` が入っていること
- PCから FireTV に接続できること
  - 例: `adb connect 192.168.11.12:5555`

補足：本プロキシは、ADB状態をバックグラウンドで定期監視し、未接続の場合は `adb connect <serial>` を自動で試行します（ただし負荷を避けるため、試行間隔にクールダウンがあります）。

## 起動

```bash
cd proxy-server
export FIRETV_SERIAL="192.168.11.12:5555"
export PROXY_TOKEN="change-me" # 任意（推奨）
export HOST="0.0.0.0"
export PORT="8787"
node server.js
```

スクリーンショットの保存先は起動引数で変更できます。

```bash
node server.js --screenshot-dir ./screenshots
```

保存ファイル名は日付（`YYYYMMDD_HHMMSS.png`）です。

### ADBの自動接続/再認証（バックグラウンド）

- ADBが `device` 状態でない（未接続/オフライン等）場合、バックグラウンドで `adb connect <serial>` を自動で試行します（`serial` が `ip:port` の場合）。
- ADBは接続できているが `unauthorized`（認証未承認）等が連続する場合、一定条件を満たしたときだけ「再認証フロー」をバックグラウンドで実行します。
  - 再認証フローはベストエフォートで `adb disconnect <serial>` → `adb kill-server` → `adb start-server` → `adb connect <serial>` を行います。
  - 実際の認証（端末側での「USBデバッグを許可」など）はユーザー操作が必要です。
  - “できもしない認証”を連打して負荷をかけないよう、クールダウンと時間当たり上限があります。

#### 調整用パラメータ（環境変数）

- `ADB_TIMEOUT_MS`（デフォルト: `8000`）: `adb shell ...` 等の通常コマンドのタイムアウト
- `ADB_CONNECT_TIMEOUT_MS`（デフォルト: `8000`）: `adb connect` のタイムアウト
- `ADB_CONNECT_COOLDOWN_MS`（デフォルト: `5000`）: 自動 `adb connect` の最小試行間隔
- `ADB_AUTH_FAIL_THRESHOLD`（デフォルト: `3`）: 認証失敗がこの回数以上連続したら再認証候補
- `ADB_AUTH_FAIL_WINDOW_MS`（デフォルト: `60000`）: 連続判定の時間窓
- `ADB_REAUTH_COOLDOWN_MS`（デフォルト: `600000`）: 再認証フローの最小実行間隔
- `ADB_REAUTH_MAX_PER_HOUR`（デフォルト: `3`）: 1時間あたりの再認証フロー最大回数（`0`で無効化）
- `ADB_READY_CACHE_MS`（デフォルト: `1500`）: 直近の状態が `device` の場合に「新鮮」とみなすキャッシュ時間
- `ADB_STATE_POLL_MS`（デフォルト: `0`）: ADB状態のバックグラウンド監視間隔（`0`で無効化）

`ADB_STATE_POLL_MS=0` の場合はバックグラウンド監視を行わず、ADB接続確認や再認証のトリガは「実際のリクエスト実行時」のみになります（省リソース・ベストエフォート）。

#### 調整用パラメータ（CLI引数）

環境変数の代わりに、起動引数でも指定できます。

- `--adb-timeout-ms`
- `--adb-connect-timeout-ms`
- `--adb-connect-cooldown-ms`
- `--adb-auth-fail-threshold`
- `--adb-auth-fail-window-ms`
- `--adb-reauth-cooldown-ms`
- `--adb-reauth-max-per-hour`
- `--adb-ready-cache-ms`
- `--adb-state-poll-ms`

### デバッグログ

起動時ログに加えて、リクエスト/adb実行をログに出したい場合は以下を指定します。

- `--debug`（または `-d`）: リクエストの概要（メソッド/パス/ステータス/処理時間）を出力
- `--log-body`: 受信したJSONボディを出力（`X-Auth-Token` は常に `<redacted>`）
- `--log-adb`: 実行した `adb ...` コマンドと stdout/stderr を出力

環境変数でも指定できます。

- `PROXY_DEBUG=1`
- `PROXY_LOG_BODY=1`
- `PROXY_LOG_ADB=1`

例：

```bash
cd proxy-server
export FIRETV_SERIAL="192.168.11.12:5555"
export PROXY_TOKEN="change-me"
export HOST="0.0.0.0"
export PORT="8787"
node server.js --debug --log-adb
```

## API

- `GET /health`
- `POST /grantAccessibility` `{ component, serial? }`
- `POST /screenshot` `{ serial? }`
- `POST /tap` `{ x, y, serial? }`
- `POST /doubleTap` `{ x, y, serial? }`
- `POST /swipe` `{ x1, y1, x2, y2, durationMs?, serial? }`
- `POST /longPress` `{ x, y, durationMs?, serial? }`

入力系（`/tap` `/doubleTap` `/swipe` `/longPress`）は同時に1リクエストのみ処理します。処理中に新しい入力が来た場合は `409 busy` を返します（キューは作りません）。

認証（任意）：`PROXY_TOKEN` を設定した場合、`X-Auth-Token` ヘッダが必要です。

## セキュリティ注意

- `PROXY_TOKEN` を設定しない場合、LAN内の第三者が端末操作できてしまいます。必ず設定してください。
- 可能なら、PCのファイアウォールで接続元IPを制限してください。

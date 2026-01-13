# ftvrcm-proxy-server

FireOS 8 でアプリから `adbd` へローカル接続がブロックされる環境向けの、最小プロキシサーバです。

構成：FireTVアプリ → (LAN) → PCのプロキシ → (adb) → FireTV

## 前提

- PCに `adb` が入っていること
- PCから FireTV に接続できること
  - 例: `adb connect 192.168.11.12:5555`

## 起動

```bash
cd proxy-server
export FIRETV_SERIAL="192.168.11.12:5555"
export PROXY_TOKEN="change-me" # 任意（推奨）
export HOST="0.0.0.0"
export PORT="8787"
node server.js
```

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
- `POST /tap` `{ x, y, serial? }`
- `POST /swipe` `{ x1, y1, x2, y2, durationMs?, serial? }`
- `POST /longPress` `{ x, y, durationMs?, serial? }`

認証（任意）：`PROXY_TOKEN` を設定した場合、`X-Auth-Token` ヘッダが必要です。

## セキュリティ注意

- `PROXY_TOKEN` を設定しない場合、LAN内の第三者が端末操作できてしまいます。必ず設定してください。
- 可能なら、PCのファイアウォールで接続元IPを制限してください。

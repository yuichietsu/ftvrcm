# ftvrcm-proxy-server

FireOS 8 でアプリから `adbd` へのローカル接続がブロックされる環境向けの、最小プロキシサーバです。

構成：FireTV app → (LAN) → PC proxy → (adb) → FireTV

## 前提

- PC に `adb` がインストール済み
- PC から FireTV に接続できること（例: `adb connect 192.168.11.12:5555`）

## 簡単な起動例

```bash
cd proxy-server
export FIRETV_SERIAL="192.168.11.12:5555"
export PROXY_TOKEN="change-me"
node server.js
```

## メモ

- 依存パッケージなし（Node.js v18 以上、標準モジュールのみ）
- デフォルトポート: `8787`
- ADB 状態はバックグラウンドで監視され、未接続時に自動再接続を試みます（ただしクールダウン有）

## 詳細ドキュメント

完全な API・環境変数・パラメータについては、以下を参照してください：

- [プロキシサーバーのリファレンス](../docs/diataxis/reference/proxy-server.md) - API エンドポイント、環境変数、CLI 引数、ADB 制御設定
- [プロキシサーバーを起動する](../docs/diataxis/how-to/setup-proxy-server.md) - 段階的な起動ガイド・設定例・高度な調整

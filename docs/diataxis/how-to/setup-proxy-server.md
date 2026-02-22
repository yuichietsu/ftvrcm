# プロキシサーバーを起動する

`emulation_method=PROXY` を使用する場合、PC 上でプロキシサーバーを起動する必要があります。

## 前提条件

- Node.js v18 以上
- `adb` がインストールされ、Fire TV に接続できること

## 1. 依存関係なしで起動

`proxy-server` は依存パッケージなしで動作します（Node.js 標準モジュールのみ使用）。

```bash
cd proxy-server
export FIRETV_SERIAL="192.168.11.12:5555"
export PROXY_TOKEN="change-me"   # 任意（推奨）
export HOST="0.0.0.0"
export PORT="8787"
node server.js
```

## 2. アプリ側の設定

ftvrcm の設定画面で以下を設定します。

| 設定項目 | 値の例 |
|---------|--------|
| エミュレーション方法 | `PROXY` |
| プロキシホスト | `192.168.11.127`（PC の LAN IP） |
| プロキシポート | `8787` |
| プロキシトークン | `change-me`（PC 側と同じ値） |

設定後、設定画面の「プロキシ疎通テスト」ボタンで接続を確認してください（`/health` が `ok: true` で返れば正常です）。

## 3. スクリーンショット保存先の変更（オプション）

```bash
node server.js --screenshot-dir ./screenshots
```

## 4. デバッグログを有効化

```bash
node server.js --debug --log-adb
```

| オプション | 説明 |
|-----------|------|
| `--debug` | リクエスト概要（メソッド/パス/ステータス/処理時間）を出力 |
| `--log-body` | 受信 JSON ボディを出力（トークンは `<redacted>`） |
| `--log-adb` | 実行した `adb` コマンドと stdout/stderr を出力 |

環境変数でも設定できます: `PROXY_DEBUG=1`, `PROXY_LOG_BODY=1`, `PROXY_LOG_ADB=1`

## 5. ADB 再接続の設定

プロキシはバックグラウンドで ADB 状態を監視し、未接続の場合に自動再接続を試みます。
詳細なパラメータは [プロキシサーバーリファレンス](../reference/proxy-server.md) を参照してください。

## 注意

- Fire OS 8 ではアプリから端末内 `adbd` へのローカル接続がブロックされます。そのため PC 上のプロキシ経由で操作します。
- タッチ操作モードへ切り替える際にプロキシが疎通できない場合、切り替えがキャンセルされ通常操作のままになります。

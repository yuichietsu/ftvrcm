# トラブルシューティング

## 1. アクセシビリティ設定にサービスが表示されない（Fire OS）

Fire OS のバージョンや設定 UI によっては、AccessibilityService のトグルが見つからないことがあります。

ADB から有効化してください。詳細は [AccessibilityService を有効化する](enable-accessibility-service.md) を参照してください。

## 2. モード切り替えが機能しない

まず設定画面の「必要状態：アクセシビリティサービス」が **OFF** になっていないか確認してください。OFF の場合は ADB で再有効化してください。

**Fire OS での BACK 長押しの競合**

Fire TV（Fire OS）では BACK 長押しがシステム側で処理されるため、AccessibilityService が無効化される場合があります。
設定画面でモード切り替えキーを `MENU` など別のキーに変更することを推奨します。

また、Fire TV では `KeyEvent.isLongPress` が期待通りに発火しないことがあるため、長押し判定はアプリ側のタイマーで行っています。

## 3. ポインタは出るがタップが発生しない

- `AccessibilityService` 方式のタップは、ジェスチャ注入またはノードクリックで行います。
- Fire OS 側で特定アプリやシステム UI への注入が制限されている場合があります。その場合、システム UI へのクリックが制限されます。
- `PROXY` 方式の場合は後述の「プロキシ方式で操作できない」を参照してください。

補足：縦スワイプ（CH+/CH-）はジェスチャ注入が失敗した場合、スクロール操作（`ACTION_SCROLL_*`）を試行します。

## 4. 設定画面を開くとクラッシュする

`ListPreference` に `useSimpleSummaryProvider` を設定している状態でコード側から `preference.summary` を上書きすると例外になります。
最新のAPKをインストールしてください（`onBindViewHolder` では `summary` を直接更新しない修正を含みます）。

**症状**：`IllegalStateException: Cannot call this method while RecyclerView is computing a layout or scrolling`

最新の APK をインストールすることで解消します。

## 5. プロキシ方式でカーソルだけ動いて操作できない

### 5.1 接続先を確認する

設定画面の以下の項目が正しいか確認してください。

| 設定項目 | 確認内容 |
|---------|---------|
| `proxy_host` | PC の LAN IP（例: `192.168.11.127`） |
| `proxy_port` | プロキシ起動時の `PORT`（デフォルト `8787`） |
| `proxy_token` | PC 側の `PROXY_TOKEN` と一致しているか |

### 5.2 PC 側の状態を確認する

```bash
# PC から Fire TV へ ADB 接続できること
adb connect <FIRE_TV_IP>:5555
adb devices

# プロキシが起動していること（/health が ok: true を返すこと）
curl http://localhost:8787/health
```

### 5.3 `Cleartext HTTP traffic ... not permitted`

`AndroidManifest.xml` に `android:usesCleartextTraffic="true"` が含まれたビルドが必要です。
最新の APK をインストールして再試行してください。

### 5.4 `401 unauthorized`

PC 側の `PROXY_TOKEN` とアプリ側の `proxy_token` が一致していません。
どちらかを合わせてください。

### 5.5 `NetworkOnMainThreadException`

最新の APK をインストールしてください（PROXY 送信をバックグラウンドスレッドで実行する修正を含みます）。

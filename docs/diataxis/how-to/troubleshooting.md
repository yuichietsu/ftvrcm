# トラブルシューティング

## 1. アクセシビリティ設定にサービスが表示されない（Fire OS）

Fire OS のバージョンや設定 UI によっては、AccessibilityService のトグルが見つからないことがあります。

Shizuku 経由（**Shizukuの詳細設定** → **アクセシビリティを有効化**）または ADB から有効化してください。詳細は [AccessibilityService を有効化する](enable-accessibility-service.md) を参照してください。

## 2. モード切り替えが機能しない

まず設定画面下部のステータスバーで「**アクセシビリティ: 動作中**」になっているか確認してください。「アクセシビリティ: 停止中」の場合は Shizuku 経由または ADB で再有効化してください。

**Fire OS での BACK 長押しの競合**

Fire TV（Fire OS）では `BACK` 長押しがシステム側で処理されるため、AccessibilityService が無効化される場合があります。
デフォルトの「有効化/無効化キー」は `MENU` 長押しに設定されています。変更する場合も `BACK` 長押しは避けることを推奨します。

また、Fire TV では `KeyEvent.isLongPress` が期待通りに発火しないことがあるため、長押し判定はアプリ側のタイマーで行っています。

## 3. ポインタは出るがタップやスワイプが反応しない

### Shizuku 方式を使用している場合

1. 設定画面下部のステータスバーが「**Shizuku: 起動中**」になっているか確認してください
   - 「Shizuku: 未起動」の場合は Shizuku アプリからサービスを開始してください（[Shizuku をセットアップする](setup-shizuku.md)）
2. **Shizukuの設定** → **Shizukuの詳細設定** → **Shizuku権限の許可** が「既に許可されています」になっているか確認してください
3. **Shizukuの詳細設定** → **タッチ注入テスト** を実行し、「Shizuku接続テスト成功」となるか確認してください

### AccessibilityService 方式を使用している場合

- `AccessibilityService` 方式のタップ・スクロールは、ノードクリック（`ACTION_CLICK`）やノードスクロール（`ACTION_SCROLL_*`）で行います
- 対象アプリやビューによってはノード操作に反応しない場合や、Fire OS 側でシステム UI への注入が制限されている場合があります
- より確実に操作したい場合は、**Shizukuを使用する（推奨）** を ON にすることを推奨します

## 4. 設定画面を開くとクラッシュする

`ListPreference` に `useSimpleSummaryProvider` を設定している状態でコード側から `preference.summary` を上書きすると例外になります。
最新の APK をインストールしてください（`onBindViewHolder` では `summary` を直接更新しない修正を含みます）。

**症状**：`IllegalStateException: Cannot call this method while RecyclerView is computing a layout or scrolling`

最新の APK をインストールすることで解消します。

## 5. Shizuku 再起動後に動作しない

Fire TV を再起動すると Shizuku サービスは停止します。再起動後は Shizuku アプリまたは ADB から Shizuku サービスを再度開始してください。
なお、Shizuku を使用しない場合は **Shizukuの設定** → **Shizukuを使用する（推奨）** を OFF にすることで AccessibilityService 方式に切り替えられます。

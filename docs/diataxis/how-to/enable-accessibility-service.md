# AccessibilityService を有効化する

ftvrcm は AccessibilityService として動作します。インストール後に有効化が必要です。

## 方法 A: Shizuku 経由で有効化する（推奨・最も簡単）

Shizuku サービスが起動済みで ftvrcm に権限が付与されている場合、アプリ内からワンクリックで有効化できます。

1. **ftvrcm** の設定画面を開く
2. **Shizukuの設定** → **Shizukuの詳細設定** → **アクセシビリティを有効化** を選択する
3. 画面下部のステータスバーが「**アクセシビリティ: 動作中**」になったことを確認する

## 方法 B: 設定 UI から有効化する

1. Fire TV の **設定** → **ユーザーコントロール** → **アクセシビリティ** を開く
   - 機種・Fire OS バージョンによっては「ダウンロードしたサービス」として表示される場合があります
2. `ftvrcm` のトグルを **ON** にする
3. アプリの設定画面を開き、画面下部のステータスバーが「**アクセシビリティ: 動作中**」になっていることを確認する

## 方法 C: ADB から有効化する

設定 UI にサービスが表示されない場合（Fire OS の特定バージョン等）や Shizuku 未使用時は ADB から有効化できます。

```bash
# Fire TV に接続
adb connect <FIRE_TV_IP>:5555

# AccessibilityService を有効化
adb shell settings put secure enabled_accessibility_services \
  com.ftvrcm/com.ftvrcm.service.RemoteControlAccessibilityService
adb shell settings put secure accessibility_enabled 1

# 反映しない場合は再起動
adb reboot
```

有効化後、設定画面下部のステータスバーが「**アクセシビリティ: 動作中**」になることを確認してください。

## 無効化する

設定 UI から `ftvrcm` のトグルを **OFF** にするか、ADB から設定を削除してください。

```bash
adb shell settings delete secure enabled_accessibility_services
```

## 注意

- Fire TV（Fire OS）では **BACK 長押し** がシステム動作と競合し、AccessibilityService が無効化される場合があります。デフォルトの有効化/無効化キーは `MENU` 長押しになっており、変更する場合も `BACK` 長押しは避けることを推奨します。
- 拡張ストレージ環境でのアクセシビリティ認識の問題を防ぐため、`AndroidManifest.xml` で `android:installLocation="internalOnly"` が指定されています。
- 切り替えがまったく機能しない場合は、まず設定画面下部のステータスバーで「アクセシビリティ: 停止中」になっていないか確認してください。

# AccessibilityService を有効化する

ftvrcm は AccessibilityService として動作します。インストール後に手動で有効化が必要です。

## 方法 A: 設定 UI から有効化する

1. Fire TV の **設定** → **ユーザーコントロール** → **アクセシビリティ** を開く
   - 機種・Fire OS バージョンによっては「ダウンロードしたサービス」として表示される場合があります
2. `ftvrcm` のトグルを **ON** にする
3. アプリの設定画面で「必要状態：アクセシビリティサービス」が **ON** になっていることを確認する

## 方法 B: ADB から有効化する

設定 UI にサービスが表示されない場合（Fire OS の特定バージョン等）は ADB から有効化できます。

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

有効化後、設定画面の「現在のモード」が切り替わることを確認してください。

## 無効化する

設定 UI から `ftvrcm` のトグルを **OFF** にするか、ADB から設定を削除してください。

```bash
adb shell settings delete secure enabled_accessibility_services
```

## 注意

- Fire TV（Fire OS）では **BACK 長押し** がシステム動作と競合し、AccessibilityService が無効化される場合があります。その場合はモード切り替えキーを `MENU` など別のキーに変更することを推奨します。
- 切り替えがまったく機能しない場合は、まず設定画面の「必要状態：アクセシビリティサービス」が OFF になっていないか確認してください。

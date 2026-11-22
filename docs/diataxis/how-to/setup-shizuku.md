# Shizuku をセットアップする

ftvrcm はタッチ操作の注入方法として **Shizuku 方式**（デフォルト・推奨）と **AccessibilityService 方式** をサポートしています。
Shizuku を使用することで、0ms 遅延のタップ・長押し、自然なスワイプ、ネイティブ 2 本指ピンチ（ズーム）が利用可能になります。

## 前提条件

- Fire TV Stick（Android 9.0 / API 28 以上）
- Shizuku アプリ（`moe.shizuku.privileged.api`）が端末にインストールされていること

## 1. Shizuku サービスを起動する

1. Fire TV で **設定** → **マイ Fire TV** → **開発者オプション** → **ADB デバッグ** を ON にします
2. Shizuku アプリを起動し、ワイヤレスデバッグまたは PC からの ADB コマンドで Shizuku サービスを開始します

PC から ADB で起動する場合の例：

```bash
adb connect <FIRE_TV_IP>:5555
adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh
```

※ 具体的な起動コマンドは端末上の Shizuku アプリに表示される案内を参照してください。

## 2. ftvrcm で Shizuku を有効化・権限許可する

1. **ftvrcm** の設定画面を開きます
2. 画面下部のステータスバーで「**Shizuku: 起動中**」と表示されていることを確認します
3. **Shizukuの設定** → **Shizukuを使用する（推奨）** が ON になっていることを確認します
4. **Shizukuの詳細設定** を開きます
5. **Shizuku権限の許可** を選択し、許可ダイアログで許可します

## 3. アクセシビリティサービスを有効化する（任意・便利機能）

Shizuku 権限が許可されている状態であれば、**Shizukuの詳細設定** → **アクセシビリティを有効化** を選択するだけで、PC からの ADB コマンドなしにアプリ内から即座に AccessibilityService を有効化できます。

## 4. 動作確認（タッチ注入テスト）

**Shizukuの詳細設定** → **タッチ注入テスト** を選択すると、Shizuku 経由で `InputManager` への疎通が確認できます。
「Shizuku接続テスト成功」と表示されればセットアップ完了です。

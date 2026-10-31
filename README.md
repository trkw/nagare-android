# Nagare for Android

[Nagare](https://github.com/trkw/nagare) の Android 版です。Kotlin と Jetpack Compose のネイティブアプリです。

## できること

- サーバーURLとアプリ用トークンでログイン（トークンは Android Keystore の鍵で暗号化して保存）
- ドロワーでフォルダとフィードを選び、記事一覧から本文へ
- 未読のみ表示、あとで読む、既読・未読、引っ張って更新
- 翻訳（原文と切り替え）、全文取得、日本語の読み上げ（端末の TextToSpeech）
- エージェントに送る（メモつき）、元の記事を開く、共有
- 購読の追加。フィードのないページは「このページを見張る」。ブラウザの「共有」から Nagare を選ぶとURLが入った状態で開きます

## 構成

```
core   UIを持たない部分（APIクライアント、モデル）。Android SDKなしでビルド・テストできる
app    Compose のアプリ本体
```

minSdk 29（Android 10）、targetSdk 36 です。Android Gradle Plugin 9.4 と Gradle 9.6 を使います。

## ビルド

Android Studio でこのフォルダを開いて実行します。コマンドラインなら:

```sh
./gradlew :app:installDebug
./gradlew -PcoreOnly :core:test    # APIクライアントのテストだけ（Android SDK不要）
```

ログイン画面では、サーバーで作ったアプリ用トークンを入力します。

```sh
# trkw/nagare の apps/api で
bun run token:create --name android --kind app --remote
```

## 通知（FCM）

1. [Firebase コンソール](https://console.firebase.google.com/)でプロジェクトを作り、パッケージ名 `dev.trkw.nagare` の Android アプリを追加します。
2. ダウンロードした `google-services.json` を `app/` に置きます（git には入りません）。ファイルがなければ通知の項目が出ないだけで、アプリは普通にビルドできます。
3. サーバー側（trkw/nagare）には、プロジェクトの設定 → サービスアカウントで作った秘密鍵の JSON を登録します。

   ```sh
   # apps/api で
   bunx wrangler secret put FCM_SERVICE_ACCOUNT   # JSONの中身をそのまま貼る
   ```

アプリではドロワーの「このデバイスで通知を受け取る」で端末を登録し、フィードを選んでから「選択中のフィードの新着を通知する」で通知するフィードを選びます。

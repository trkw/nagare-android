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

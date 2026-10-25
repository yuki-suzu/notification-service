# Notification Service

システム内の各マイクロサービスから発行されたイベントを Kafka を通じて非同期で購読し、LINE WORKS
などの外部メッセージングプラットフォームへ配信する独立した通知ゲートウェイです。

## ⚙️ テックスタック

* **Language**: Java 25
* **Framework**: Spring Boot 3
* **Messaging**: Spring Kafka (Consumer, RetryableTopic, DLT)
* **HTTP Client**: Spring RestClient
* **Authentication**: JWT (Java JWT) - LINE WORKS Service Account 認証用

## 🚀 アプリケーションの仕様・責務

1. **イベント購読 (Event-Driven)**: Kafka トピックからイベント（JSON）を自動でデシリアライズして購読。
2. **LINE WORKS API 連携**: JWT を動的に生成・署名し、アクセストークンを取得。トークルームや個人 DM
   へメッセージを送信。トークン期限切れ時は自動リトライ。
3. **ドメインフォーマット**: 機械的な JSON データを、管理者が視認しやすい絵文字付きテキストや、従業員に配慮したリマインド文面へ整形。
4. **耐障害性とフォールトトレランス (DLT)**:
    - ネットワークエラー時は指数バックオフ（Exponential Backoff）による自動リトライ（最大 3 回）。
    - バリデーションエラーや上限オーバー時は DLT（Dead Letter Topic）へ即時隔離。
    - `DltErrorHandler` が Kafka ヘッダから原因を抽出し、システム管理者へ二次障害を防ぐ形で一次報を送信。

## 🌐 エンドポイント一覧 (REST API)

本サービスは基本的にイベント駆動（Kafka Consumer）で動作しますが、インフラの疎通確認および手動テスト用に以下の
API を提供しています。（ベースパス: `/api/v1`）

| メソッド | エンドポイント                     | 説明                           |
|:-----|:----------------------------|:-----------------------------|
| POST | `/notifications/test/alert` | 管理者チャンネル向けアラート通知の疎通確認テスト     |
| POST | `/notifications/test/error` | システム管理者（個人DM）向けエラー通知の疎通確認テスト |

*※ `message` パラメータを付与することで、任意のテスト文面を送信可能です。*

## 📥 Kafka トピック (Consumer)

以下のトピックを購読（Consume）し、対応するユースケースへディスパッチします。

| トピック名                           | 処理内容                                  | DLT ハンドリング |
|:--------------------------------|:--------------------------------------|:-----------|
| `notification-topic`            | システムエラーなどの汎用通知メッセージ配信                 | 対象         |
| `unstamped-alert-topic`         | 未打刻者の管理者向けサマリアラート配信                   | 対象         |
| `unstamped-direct-topic`        | 未打刻者本人向けの個別 DM 配信。全件終了後に結果レポートを管理者へ送信 | 対象         |
| `attendance-irregularity-topic` | 月次勤怠サマリ（予定休・当欠・半休・遅延）の管理者向けアラート配信     | 対象         |

### 🚨 エラーハンドリング仕様 (フォールバック)

* **メールアドレス未設定時**:
  `unstamped-direct-topic` において、送信元からダミーアドレス（`unknown@example.com` 等）が渡された場合、LINE
  WORKS のアカウント解決に失敗しますが、DLT
  には送らずスキップ処理として扱います。スキップされたアカウントは「配信結果レポート」の失敗リストに計上され、管理者へ報告されます。

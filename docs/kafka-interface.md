# Notification Service 非同期メッセージング（Kafka）インターフェース仕様書

本ドキュメントは、`attendance-management`（勤怠管理サービス）から `notification-service`（通知サービス）へ発行される
Kafka メッセージのインターフェース仕様を定義します。

---

## 1. トピック一覧概要

| トピック名 (論理名)                     | 送信元        | 受信側 Consumer                           | 主な用途                         | 送信頻度 / タイミング   |
|:--------------------------------|:-----------|:---------------------------------------|:-----------------------------|:---------------|
| `notification-topic`            | 各サービス      | `GeneralNotificationKafkaConsumer`     | 汎用通知（LINE WORKS トークルーム／個人DM） | システム例外発生時など随時  |
| `unstamped-alert-topic`         | attendance | `UnstampedAlertKafkaConsumer`          | 未打刻者検知・管理者トークルームアラート         | 始業時刻超過後のバッチ実行時 |
| `unstamped-direct-topic`        | attendance | `UnstampedDirectReminderKafkaConsumer` | 未打刻者本人向け LINE WORKS 個別DM一括配信 | 始業時刻超過後のバッチ実行時 |
| `attendance-irregularity-topic` | attendance | `AttendanceIrregularityKafkaConsumer`  | 月次勤怠サマリ（予定休・当欠・半休・遅延）の管理者通知  | 月次集計バッチ時       |

---

## 2. トピック詳細仕様

### ① 汎用通知要求 (`notification-topic`)

汎用的な通知メッセージを特定のチャンネルや個人宛てに送信します。システム例外発生時の緊急通報などにも利用されます。

* **送信側 Payload (JSON):**

```json
{
  "channel_type": "LINE_WORKS",
  "destination_type": "USER",
  "target_id": null,
  "message": "🚨 【バッチ処理エラー】\n・エラー内容: NullPointerException..."
}
```

* **フィールド定義:**
    * `channel_type` (String, 任意): 配信プラットフォーム。`LINE_WORKS`（デフォルト）
    * `destination_type` (String, 任意): `CHANNEL`（トークルーム）または `USER`（個人宛）
    * `target_id` (String, 任意): 宛先ID。`null` や省略時は環境変数（
      `app.line-works.alert-channel-id` / `app.line-works.system-manager-id`）設定の既定先へ送信されます。
    * `message` (String, 必須): 通知本文

---

### ② 未打刻者検知アラート (`unstamped-alert-topic`)

始業予定を過ぎても打刻のない従業員を、部門ごとにグルーピングして管理者トークルームへ通報します。

* **送信側 Payload (JSON):**

```json
{
  "target_date": "2026-09-22",
  "detected_at": "2026-09-22T09:15:00",
  "employees": [
    {
      "employee_number": "EMP001",
      "email": "taro.yamada@example.com",
      "department_name": "開発本部",
      "full_name": "山田 太郎",
      "scheduled_start_at": "09:00:00",
      "monthly_unstamped_count": 2
    }
  ]
}
```

* **フィールド定義:**
    * `target_date` (LocalDate, 必須): 判定対象日 (YYYY-MM-DD)。未来日は不可。
    * `detected_at` (LocalDateTime, 必須): 検知日時 (ISO-8601)
    * `employees` (Array, 必須 / 1件以上): 未打刻従業員リスト
        * `employee_number` (String, 必須): 社員番号
        * `email` (String, 必須): 社用メールアドレス
        * `department_name` (String, 必須): 所属部門名
        * `full_name` (String, 必須): 氏名
        * `scheduled_start_at` (LocalTime, 必須): 出勤予定時刻 (HH:mm:ss)
        * `monthly_unstamped_count` (Integer, 必須): 当月未打刻累積回数 (1以上)

---

### ③ 未打刻者本人向け個別DMリマインド (`unstamped-direct-topic`)

対象従業員へ個別に「打刻リマインド」を送信し、全件完了後に管理者トークルームへ配信結果レポートを通知します。送信元にて
`app.kafka.direct-reminder-enabled = false` に設定されている場合は、イベント発行自体がスキップされます。

* **送信側 Payload (JSON):**

```json
{
  "employees": [
    {
      "employee_number": "EMP001",
      "email": "taro.yamada@example.com",
      "full_name": "山田 太郎"
    },
    {
      "employee_number": "EMP002",
      "email": "unknown@example.com",
      "full_name": "佐藤 次郎"
    }
  ]
}
```

* **フィールド定義:**
    * `employees` (Array, 必須 / 1件以上): 対象従業員リスト
        * `employee_number` (String, 必須): 社員番号
        * `email` (String, 必須): 社用メールアドレス（LINE WORKS ID 導出キー）
        * `full_name` (String, 必須): 氏名（メッセージ宛名呼びかけ用）

> **💡 フォールバックおよびエラーハンドリング仕様:**
> 送信元の従業員マスタでメールアドレスが未設定（`null`）の場合、送信元システムでダミーアドレス（
`unknown@example.com`）が補完されて Publish されます（バリデーションを安全に通過させるため）。
> 受信側の `notification-service`
> は宛先解決時に送信エラーを検知しますが、処理自体は落とさず（DLTには退避せず）に送信をスキップし、管理者向け「配信結果レポート」の【🚨
> 送信失敗・スキップ】リストに計上して報告します。

---

### ④ 月次勤怠サマリ通知 (`attendance-irregularity-topic`)

月次集計で変動のあった従業員の勤怠実績（予定休・当欠・半休・遅延）をまとめ、管理者トークルームへサマリレポートを配信します。

* **送信側 Payload (JSON):**

```json
{
  "proc_month": "2026-09",
  "employees": [
    {
      "employee_number": "EMP001",
      "department_name": "開発本部",
      "full_name": "山田 太郎",
      "scheduled_holiday_count": 0,
      "unscheduled_holiday_count": 1,
      "half_holiday_count": 1,
      "delay_count": 2
    }
  ]
}
```

* **フィールド定義:**
    * `proc_month` (String, 必須): 処理対象月 (yyyy-MM 形式)
    * `employees` (Array, 必須 / 1件以上): サマリ対象従業員リスト
        * `employee_number` (String, 必須): 社員番号
        * `department_name` (String, 必須): 所属部門名
        * `full_name` (String, 必須): 氏名
        * `scheduled_holiday_count` (Integer, 必須): 当月予定休日数 (0以上)
        * `unscheduled_holiday_count` (Integer, 必須): 当月当日欠勤日数 (0以上)
        * `half_holiday_count` (Integer, 必須): 当月半日休暇日数（午前休・午後休の合算） (0以上)
        * `delay_count` (Integer, 必須): 当月遅延回数 (0以上)

---

## 3. リトライおよび障害設計（共通仕様）

Kafka 側の一時的な通信障害や、外部 API (LINE WORKS) のレートリミット等に備え、以下のフォールトトレランス設計が組み込まれています。

1. **指数バックオフ自動再試行:**
   インフラ通信瞬断等のエラー発生時は、Spring Kafka の `@RetryableTopic` 機能により自動再試行されます。
    * 初回待機時間: `1000ms`
    * 倍率 (Multiplier): `2.0`
    * 上限待機時間: `10000ms`
    * 最大試行回数: `3` 回（初回到着を含む）
2. **バリデーションエラーの即時隔離:**
   必須項目の欠落など、リトライしても解決しない JSON 形式違反（`MethodArgumentNotValidException`
   ）は、無駄なリトライを行わず即座に DLT（Dead Letter Topic: `<topic-name>-dlt`）へ隔離されます。
3. **システム管理者通報（DLTハンドラー）:**
   上限リトライオーバーまたは即時隔離によりメッセージが DLT へ退避された場合、`DltErrorHandler`
   が稼働します。Kafka メッセージの Header 内から **「根本原因の例外メッセージ」** と **「受信ペイロードの生データ」
   ** を抽出し、自動でシステム管理者の LINE WORKS 個人 DM 宛てに緊急一次報（アラート）を配信します。
   ※ここでの通知エラーは二次障害防止のため握りつぶされます。
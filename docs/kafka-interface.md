# Notification Service 非同期メッセージング（Kafka）インターフェース仕様書

本ドキュメントは、`attendance-management`（勤怠管理サービス）から `notification-service`（通知サービス）へ発行される Kafka
メッセージのインターフェース仕様を定義します。

---

## 1. トピック一覧概要

| トピック名 (論理名)                     | 送信元        | 受信側 Consumer                           | 主な用途                         | 送信頻度 / タイミング   |
|---------------------------------|------------|----------------------------------------|------------------------------|----------------|
| `notification-topic`            | 各サービス      | `GeneralNotificationKafkaConsumer`     | 汎用通知（LINE WORKS トークルーム／個人DM） | 随時             |
| `unstamped-alert-topic`         | attendance | `UnstampedAlertKafkaConsumer`          | 未打刻者検知・管理者トークルームアラート         | 始業時刻超過後のバッチ実行時 |
| `unstamped-direct-topic`        | attendance | `UnstampedDirectReminderKafkaConsumer` | 未打刻者本人向けLINE WORKS個別DM一括配信   | 始業時刻超過後のバッチ実行時 |
| `attendance-irregularity-topic` | attendance | `AttendanceIrregularityKafkaConsumer`  | 勤怠不良者（当欠・遅刻・早退）検知・管理者レポート    | 1日1回 日次集計バッチ時  |

---

## 2. トピック詳細仕様

### ① 汎用通知要求 (`notification-topic`)

汎用的な通知メッセージを特定のチャンネルや個人宛てに送信します。

* **送信側 Payload (JSON):**

```json
{
  "channel_type": "LINE_WORKS",
  "destination_type": "CHANNEL",
  "target_id": "ch-alert-room-01",
  "message": "システムメンテナンスが完了しました。"
}

```

* **フィールド定義:**
* `channel_type` (String, 任意): 配信プラットフォーム。`LINE_WORKS`（デフォルト）
* `destination_type` (String, 任意): `CHANNEL`（トークルーム）または `USER`（個人宛）
* `target_id` (String, 任意): 宛先ID。省略時は環境変数設定の既定先へ送信
* `message` (String, 必須): 通知本文

---

### ② 未打刻者検知アラート (`unstamped-alert-topic`)

始業予定を過ぎても打刻のない従業員を、部門ごとにグルーピングして管理者トークルームへ通報します。

* **送信条件:** 対象者が **1名以上** 存在する場合に発行
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
* `target_date` (LocalDate, 必須): 判定対象日 (YYYY-MM-DD)
* `detected_at` (LocalDateTime, 必須): 検知日時 (ISO-8601)
* `employees` (Array, 必須): 未打刻従業員リスト
* `employee_number` (String, 必須): 社員番号
* `email` (String, 必須): 社用メールアドレス
* `department_name` (String, 必須): 所属部門名
* `full_name` (String, 必須): 氏名
* `scheduled_start_at` (LocalTime, 必須): 出勤予定時刻 (HH:mm:ss)
* `monthly_unstamped_count` (Integer, 必須): 当月未打刻累積回数 (1以上)

---

### ③ 未打刻者本人向け個別DMリマインド (`unstamped-direct-topic`)

対象従業員へ個別に「打刻リマインド」を送信し、全件完了後に管理者トークルームへ配信結果レポートを通知します。

* **送信条件:** 対象者が **1名以上** 存在する場合に発行（0件時は送信スキップ）
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
      "email": "jiro.sato@example.com",
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

---

### ④ 勤怠不良者検知アラート (`attendance-irregularity-topic`)

日次集計で当欠・遅刻・早退の累積が規定値を超えた従業員をまとめ、管理者トークルームへ警告レポートを配信します。

* **送信条件:** 対象者が **1名以上** 存在する場合に発行（0件時は送信スキップ）
* **送信側 Payload (JSON):**

```json
{
  "target_date": "2026-09-22",
  "detected_at": "2026-09-22T19:00:00",
  "employees": [
    {
      "employee_number": "EMP001",
      "full_name": "山田 太郎",
      "department_name": "開発本部",
      "email": "taro.yamada@example.com",
      "same_day_absence_count": 1,
      "late_count": 3,
      "early_leaving_count": 0
    }
  ]
}

```

* **フィールド定義:**
* `target_date` (LocalDate, 必須): 判定対象日 (YYYY-MM-DD)
* `detected_at` (LocalDateTime, 必須): 検知日時 (ISO-8601)
* `employees` (Array, 必須 / 1件以上): 警告対象従業員リスト
* `employee_number` (String, 必須): 社員番号
* `full_name` (String, 必須): 氏名
* `department_name` (String, 必須): 所属部門名
* `email` (String, 必須): 社用メールアドレス
* `same_day_absence_count` (Integer, 必須): 当月当日欠勤回数 (0以上)
* `late_count` (Integer, 必須): 当月遅刻回数 (0以上)
* `early_leaving_count` (Integer, 必須): 当月早退回数 (0以上)

---

## 3. リトライおよび障害設計（共通仕様）

1. **指数バックオフ自動再試行:**
   ネットワーク瞬断等のエラー発生時は、最大 3 回（初回 1000ms、倍率 2.0、上限 10000ms）まで自動再試行されます。
2. **バリデーションエラーの即時隔離:**
   必須項目欠落などの JSON 形式違反（`MethodArgumentNotValidException`）は、無駄なリトライを行わず即座に DLT（Dead Letter
   Topic: `<topic-name>-dlt`）へ隔離されます。
3. **システム管理者通報（DLTハンドラー）:**
   DLT 退避時は、`DltErrorHandler` によりシステム管理者へ自動で緊急一次報（LINE WORKS 個人DM）が送信されます。

---

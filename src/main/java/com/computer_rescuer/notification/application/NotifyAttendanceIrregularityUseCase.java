package com.computer_rescuer.notification.application;

import com.computer_rescuer.notification.application.dto.AttendanceIrregularityAlertEvent;

/**
 * 勤怠不良者検知イベントを処理し、管理者トークルームへのアラート通知を実行するユースケース（入力ポート）。
 * <p>
 * 勤怠管理サービスから日次で配信された {@link AttendanceIrregularityAlertEvent} を受け付け、
 * 文面整形ドメインサービスによるメッセージ構築と、LINE WORKS 管理者チャンネルへの配送を規定します。
 * </p>
 */
public interface NotifyAttendanceIrregularityUseCase {

    /**
     * 勤怠不良者検知イベントを処理し、管理者トークルームへアラート通知を配信します。
     *
     * @param event 勤怠管理サービスから発行された勤怠不良検知イベントデータ
     * @throws IllegalStateException 送信ゲートウェイが未登録の場合
     * @throws RuntimeException      外部通知サービスとの通信に失敗した場合
     */
    void execute(AttendanceIrregularityAlertEvent event);
}

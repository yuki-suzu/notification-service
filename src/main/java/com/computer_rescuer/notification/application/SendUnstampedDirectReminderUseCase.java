package com.computer_rescuer.notification.application;

import com.computer_rescuer.notification.application.dto.UnstampedDirectReminderEvent;

/**
 * 未打刻者本人への個別ダイレクト通知を実行するユースケース（入力ポート）。
 * <p>
 * {@link UnstampedDirectReminderEvent} を受け付け、アカウント識別子の解決、
 * リマインド文面の組み立て、および送信ゲートウェイへのディスパッチを規定します。
 * </p>
 */
public interface SendUnstampedDirectReminderUseCase {

    /**
     * 未打刻者本人へのダイレクト通知を送信します。
     *
     * @param event 未打刻者個人の通知要求イベントデータ
     * @throws IllegalArgumentException アカウント解決に失敗した場合
     * @throws RuntimeException         外部通知サービスとの通信に失敗した場合
     */
    void execute(UnstampedDirectReminderEvent event);
}

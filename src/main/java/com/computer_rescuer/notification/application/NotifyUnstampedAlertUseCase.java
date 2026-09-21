package com.computer_rescuer.notification.application;

import com.computer_rescuer.notification.application.dto.UnstampedAlertEvent;

/**
 * 未打刻者検知イベントを処理し、管理者チャンネルへのアラート通知を実行するユースケース（入力ポート）。
 * <p>
 * 勤怠管理サービスから非同期に配信された {@link UnstampedAlertEvent} を受け付け、
 * ドメインフォーマッターによるメッセージ本文の組み立ておよび外部送信ゲートウェイへの連携を規定します。
 * </p>
 */
public interface NotifyUnstampedAlertUseCase {

    /**
     * 未打刻者検知イベントを処理し、整形されたアラートメッセージを配信します。
     *
     * @param event 勤怠管理サービスから発行された未打刻検知イベントデータ
     * @throws IllegalArgumentException イベントパラメータが不正な場合
     * @throws RuntimeException         外部通知サービスとの通信に失敗した場合
     */
    void execute(UnstampedAlertEvent event);
}

package com.computer_rescuer.notification.application;

import com.computer_rescuer.notification.application.dto.NotificationCommand;

/**
 * 通知配信ユースケースを表現する入力境界インターフェース。
 * <p>
 * プレゼンテーション層（Web APIコントローラー、Kafkaメッセージコンシューマー等）からの
 * 通知要求を受け付け、ドメインモデルへの変換および外部配信ゲートウェイへの連携を規定します。<br>
 * 呼び出し元に対して実装詳細を隠蔽し、型安全なコマンドオブジェクト（{@link NotificationCommand}）を通じた
 * 契約ベースの呼び出しを提供します。
 * </p>
 */
public interface SendNotificationUseCase {

    /**
     * 指定された通知要求コマンドを受け付け、適切な配信チャネルへ通知をディスパッチします。
     * <p>
     * 渡されたコマンドパラメータの検証、ドメインエンティティの再構築、
     * および宛先チャネルに対応する送信ゲートウェイの呼び出しを同期的に実行します。
     * </p>
     *
     * @param command 通知要求パラメータ（チャネル種別、宛先ID、メッセージ本文等）を保持する不変DTO
     * @throws IllegalArgumentException 未対応の通知チャネル種別が指定された場合
     * @throws RuntimeException         外部サービス（LINE WORKS等）との通信に失敗した場合
     */
    void send(NotificationCommand command);
}

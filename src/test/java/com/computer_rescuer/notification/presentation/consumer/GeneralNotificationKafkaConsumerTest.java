package com.computer_rescuer.notification.presentation.consumer;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.computer_rescuer.notification.application.SendNotificationUseCase;
import com.computer_rescuer.notification.application.dto.NotificationCommand;
import com.computer_rescuer.notification.domain.model.Notification;
import com.computer_rescuer.notification.domain.model.NotificationChannelType;
import com.computer_rescuer.notification.presentation.consumer.handler.DltErrorHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link GeneralNotificationKafkaConsumer} に対する実戦投入可能な単体テストクラス。
 * <p>
 * <b>テスト対象クラスの責務:</b><br>
 * 汎用通知要求トピック（{@code notification-topic}）からメッセージを購読し、 受信した {@link NotificationCommand} を
 * {@link SendNotificationUseCase} へディスパッチします。
 * </p>
 * <p>
 * <b>テスト方針:</b><br>
 * <ul>
 *   <li>受信コマンドが欠落なく UseCase へ引き渡されることの検証</li>
 *   <li>トピックメタデータ（Topic, Partition, Offset）の適切なログ受領検証</li>
 *   <li>UseCase 側で例外が発生した際の例外再スロー挙動の検証</li>
 * </ul>
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GeneralNotificationKafkaConsumer 単体テスト")
class GeneralNotificationKafkaConsumerTest {

  @Mock
  private SendNotificationUseCase sendNotificationUseCase;

  @Mock
  private DltErrorHandler dltErrorHandler;

  @InjectMocks
  private GeneralNotificationKafkaConsumer sut;

  @Nested
  @DisplayName("汎用通知メッセージ購読処理（consume）のテスト")
  class ConsumeTest {

    /**
     * 【テスト観点 / 責務】<br> Kafka トピックから汎用通知コマンドを受信した際、 ペイロードがそのまま
     * {@link SendNotificationUseCase#send(NotificationCommand)} へ委譲されることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - チャネル種別 LINE_WORKS、宛先種別 CHANNEL の有効な {@link NotificationCommand}
     * を生成。<br> - トピック名: "notification-topic"、パーティション: 0、オフセット: 50L。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - {@link SendNotificationUseCase#send(NotificationCommand)} が正確に 1
     * 回呼び出されること。
     * </p>
     */
    @Test
    @DisplayName("汎用通知コマンド受信時、UseCaseへ正しく委譲されること")
    public void consume_WhenValidCommandReceived_ShouldDelegateToUseCase() {
      // Arrange
      NotificationCommand command = new NotificationCommand(
          NotificationChannelType.LINE_WORKS,
          Notification.DestinationType.CHANNEL,
          "alert-room-01",
          "システム稼働テスト通知"
      );

      // Act
      sut.consume(command, "notification-topic", 0, 50L);

      // Assert
      verify(sendNotificationUseCase, times(1)).send(command);
    }

    /**
     * 【テスト観点 / 責務】<br> UseCase 側でチャネル不正や通信障害等の例外が発生した場合、 Spring Kafka
     * リトライ機構へ引き渡すため例外がそのまま再スローされることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - UseCase が {@link IllegalArgumentException} をスローするようにモック設定。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - 同一の例外が呼び出し元へ伝播すること。
     * </p>
     */
    @Test
    @DisplayName("UseCaseで例外が発生した場合、上位へ例外がそのまま伝播すること")
    public void consume_WhenUseCaseThrowsException_ShouldPropagateException() {
      // Arrange
      NotificationCommand command = new NotificationCommand(null, null, null, "テスト");
      doThrow(new IllegalArgumentException("未対応チャネル"))
          .when(sendNotificationUseCase).send(command);

      // Act & Assert
      assertThatThrownBy(() -> sut.consume(command, "notification-topic", 1, 10L))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("未対応チャネル");
    }
  }
}

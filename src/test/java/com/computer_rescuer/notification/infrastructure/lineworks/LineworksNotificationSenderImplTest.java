package com.computer_rescuer.notification.infrastructure.lineworks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.computer_rescuer.notification.domain.model.Notification;
import com.computer_rescuer.notification.domain.model.Notification.DestinationType;
import com.computer_rescuer.notification.domain.model.NotificationChannelType;
import com.computer_rescuer.notification.infrastructure.lineworks.client.LineworksMessageApi;
import com.computer_rescuer.notification.infrastructure.property.LineworksProperties;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link LineworksNotificationSenderImpl} に対する実戦投入可能な単体テストクラス。
 * <p>
 * <b>テスト対象クラスの責務:</b><br>
 * ドメイン層の送信ゲートウェイ（{@link
 * com.computer_rescuer.notification.domain.gateway.NotificationSender}）の実装として、 汎用的な
 * {@link Notification} モデルを受け付け、宛先種別（{@link DestinationType}）に応じた LINE WORKS API
 * （{@link LineworksMessageApi}）のエンドポイントへメッセージ送信をディスパッチします。<br>
 * <ul>
 *   <li>送信先チャネル種別（{@link NotificationChannelType#LINE_WORKS}）の識別子の提供</li>
 *   <li>{@link DestinationType#CHANNEL}（トークルーム）宛て通知のルーティングおよび宛先 ID 省略時のデフォルトアラートチャンネルへのフォールバック</li>
 *   <li>{@link DestinationType#USER}（個別DM）宛て通知のルーティングおよび宛先 ID 省略時のデフォルトシステム管理者へのフォールバック</li>
 * </ul>
 * </p>
 * <p>
 * <b>テスト方針:</b><br>
 * {@link LineworksMessageApi} をモック化し、各宛先種別および {@code targetId} の境界値（明示指定、null、空文字、空白文字）
 * における分岐を網羅検証します。全テストメソッドに厳格な JavaDoc を記述します。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LineworksNotificationSenderImpl 単体テスト")
class LineworksNotificationSenderImplTest {

  @Mock
  private LineworksMessageApi messageApi;

  private final LineworksProperties properties = new LineworksProperties(
      "https://api.worksmobile.com/r/dummy",
      "dummy-client-id",
      "dummy-client-secret",
      "dummy-service-account",
      "dummy-private-key",
      "dummy-bot-id",
      "default-alert-channel-999",
      "default-system-manager-888"
  );

  private LineworksNotificationSenderImpl sut;

  /**
   * 各テスト実行前の初期化処理。
   */
  @BeforeEach
  void setUp() {
    this.sut = new LineworksNotificationSenderImpl(messageApi, properties);
  }

  @Nested
  @DisplayName("チャネル識別子のテスト")
  class ChannelTypeTest {

    /**
     * 【テスト観点 / 責務】<br> 本実装がサポートする通知チャネル種別として、正確に {@link NotificationChannelType#LINE_WORKS}
     * を返却することを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - 特になし。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - 返却値が {@link NotificationChannelType#LINE_WORKS} と完全一致すること。
     * </p>
     */
    @Test
    @DisplayName("サポートするチャネル種別としてLINE_WORKSを返却すること")
    public void getChannelType_ShouldReturnLineWorks() {
      // Act
      NotificationChannelType channelType = sut.getChannelType();

      // Assert
      assertThat(channelType).isEqualTo(NotificationChannelType.LINE_WORKS);
    }
  }

  @Nested
  @DisplayName("CHANNEL（トークルーム）宛て送信のテスト")
  class SendToChannelTest {

    /**
     * 【テスト観点 / 責務】<br> 明示的なトークルーム ID（targetId）が指定されている場合、 その ID を宛先として
     * {@link LineworksMessageApi#sendChannelMessage(String, String)} が呼び出されることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - 宛先種別が {@link DestinationType#CHANNEL}。<br> - targetId に
     * "custom-room-123" を設定。<br> - 本文に "チャンネル向けメッセージ" を設定。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> -
     * {@code messageApi.sendChannelMessage("custom-room-123", "チャンネル向けメッセージ")} が呼び出されること。<br> -
     * ユーザー向け送信メソッド（{@code sendTextMessage}）は呼び出されないこと。
     * </p>
     */
    @Test
    @DisplayName("targetIdが明示指定されている場合、指定されたトークルームIDへ送信すること")
    public void send_WhenDestinationIsChannelWithTargetId_ShouldSendToSpecifiedChannel() {
      // Arrange
      Notification notification = new Notification(
          NotificationChannelType.LINE_WORKS,
          DestinationType.CHANNEL,
          "custom-room-123",
          "チャンネル向けメッセージ",
          LocalDateTime.now()
      );

      // Act
      sut.send(notification);

      // Assert
      verify(messageApi).sendChannelMessage("custom-room-123", "チャンネル向けメッセージ");
      verify(messageApi, never()).sendTextMessage(anyString(), anyString());
    }

    /**
     * 【テスト観点 / 責務】<br> トークルーム宛て通知において targetId が null の場合、 プロパティ設定の既定アラートチャンネル
     * ID（alertChannelId）へフォールバックして送信されることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - 宛先種別が {@link DestinationType#CHANNEL}。<br> - targetId が
     * null（{@link Notification#ofLineWorksAlert(String)} による生成など）。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> -
     * {@code messageApi.sendChannelMessage("default-alert-channel-999", ...)} が呼び出されること。
     * </p>
     */
    @Test
    @DisplayName("targetIdがnullの場合、デフォルトのアラートチャンネルIDへ送信すること")
    public void send_WhenDestinationIsChannelWithNullTargetId_ShouldFallbackToDefaultAlertChannel() {
      // Arrange: targetId が null の通知オブジェクト
      Notification notification = Notification.ofLineWorksAlert("管理者向けアラート通知");

      // Act
      sut.send(notification);

      // Assert
      verify(messageApi).sendChannelMessage("default-alert-channel-999", "管理者向けアラート通知");
      verify(messageApi, never()).sendTextMessage(anyString(), anyString());
    }

    /**
     * 【テスト観点 / 責務】<br> トークルーム宛て通知において targetId が空文字（""）または空白文字（"   "）の場合、 未指定と判断して既定アラートチャンネル ID
     * へフォールバックして送信されることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - targetId に空白文字 "   " を設定。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> -
     * {@code messageApi.sendChannelMessage("default-alert-channel-999", ...)} が呼び出されること。
     * </p>
     */
    @Test
    @DisplayName("targetIdが空白文字の場合、デフォルトのアラートチャンネルIDへ送信すること")
    public void send_WhenDestinationIsChannelWithBlankTargetId_ShouldFallbackToDefaultAlertChannel() {
      // Arrange: targetId が空白文字
      Notification notification = new Notification(
          NotificationChannelType.LINE_WORKS,
          DestinationType.CHANNEL,
          "   ",
          "空白IDアラート通知",
          LocalDateTime.now()
      );

      // Act
      sut.send(notification);

      // Assert
      verify(messageApi).sendChannelMessage("default-alert-channel-999", "空白IDアラート通知");
    }

    /**
     * 【テスト観点 / 責務】<br> チャンネル向け送信時に外部 API クライアントで例外が発生した場合、 例外が上位へそのまま伝播することを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - {@code messageApi.sendChannelMessage} が
     * {@link RuntimeException} をスロー。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - {@link RuntimeException} がスローされること。
     * </p>
     */
    @Test
    @DisplayName("チャンネル送信APIで例外が発生した場合、例外がそのまま伝播すること")
    public void send_WhenChannelApiThrowsException_ShouldPropagateException() {
      // Arrange
      Notification notification = Notification.ofLineWorksAlert("エラー発生テスト");
      doThrow(new RuntimeException("API 通信障害"))
          .when(messageApi).sendChannelMessage(anyString(), anyString());

      // Act & Assert
      assertThatThrownBy(() -> sut.send(notification))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("API 通信障害");
    }
  }

  @Nested
  @DisplayName("USER（個別DM）宛て送信のテスト")
  class SendToUserTest {

    /**
     * 【テスト観点 / 責務】<br> 明示的なユーザー ID（targetId）が指定されている場合、 その ID を宛先として
     * {@link LineworksMessageApi#sendTextMessage(String, String)} が呼び出されることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - 宛先種別が {@link DestinationType#USER}。<br> - targetId に
     * "target-user-001" を設定。<br> - 本文に "個別リマインドメッセージ" を設定。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> -
     * {@code messageApi.sendTextMessage("target-user-001", "個別リマインドメッセージ")} が呼び出されること。<br> -
     * チャンネル向け送信メソッド（{@code sendChannelMessage}）は呼び出されないこと。
     * </p>
     */
    @Test
    @DisplayName("targetIdが明示指定されている場合、指定されたユーザーIDへ個別DMを送信すること")
    public void send_WhenDestinationIsUserWithTargetId_ShouldSendToSpecifiedUser() {
      // Arrange
      Notification notification = new Notification(
          NotificationChannelType.LINE_WORKS,
          DestinationType.USER,
          "target-user-001",
          "個別リマインドメッセージ",
          LocalDateTime.now()
      );

      // Act
      sut.send(notification);

      // Assert
      verify(messageApi).sendTextMessage("target-user-001", "個別リマインドメッセージ");
      verify(messageApi, never()).sendChannelMessage(anyString(), anyString());
    }

    /**
     * 【テスト観点 / 責務】<br> ユーザー宛て通知において targetId が null の場合、 プロパティ設定の既定システム管理者
     * ID（systemManagerId）へフォールバックして送信されることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - 宛先種別が {@link DestinationType#USER}。<br> - targetId が
     * null（{@link Notification#ofLineWorksError(String)} による生成など）。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> -
     * {@code messageApi.sendTextMessage("default-system-manager-888", ...)} が呼び出されること。
     * </p>
     */
    @Test
    @DisplayName("targetIdがnullの場合、デフォルトのシステム管理者IDへ個別DMを送信すること")
    public void send_WhenDestinationIsUserWithNullTargetId_ShouldFallbackToDefaultSystemManager() {
      // Arrange: targetId が null の通知オブジェクト
      Notification notification = Notification.ofLineWorksError("システム障害通知");

      // Act
      sut.send(notification);

      // Assert
      verify(messageApi).sendTextMessage("default-system-manager-888", "システム障害通知");
      verify(messageApi, never()).sendChannelMessage(anyString(), anyString());
    }

    /**
     * 【テスト観点 / 責務】<br> ユーザー宛て通知において targetId が空文字（""）または空白文字（"   "）の場合、 未指定と判断して既定システム管理者 ID
     * へフォールバックして送信されることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - targetId に空文字 "" を設定。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> -
     * {@code messageApi.sendTextMessage("default-system-manager-888", ...)} が呼び出されること。
     * </p>
     */
    @Test
    @DisplayName("targetIdが空文字の場合、デフォルトのシステム管理者IDへ個別DMを送信すること")
    public void send_WhenDestinationIsUserWithEmptyTargetId_ShouldFallbackToDefaultSystemManager() {
      // Arrange: targetId が空文字
      Notification notification = new Notification(
          NotificationChannelType.LINE_WORKS,
          DestinationType.USER,
          "",
          "空文字ID通知",
          LocalDateTime.now()
      );

      // Act
      sut.send(notification);

      // Assert
      verify(messageApi).sendTextMessage("default-system-manager-888", "空文字ID通知");
    }

    /**
     * 【テスト観点 / 責務】<br> ユーザー向け個別DM送信時に外部 API クライアントで例外が発生した場合、 例外が上位へそのまま伝播することを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - {@code messageApi.sendTextMessage} が {@link RuntimeException}
     * をスロー。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - {@link RuntimeException} がスローされること。
     * </p>
     */
    @Test
    @DisplayName("ユーザー送信APIで例外が発生した場合、例外がそのまま伝播すること")
    public void send_WhenUserApiThrowsException_ShouldPropagateException() {
      // Arrange
      Notification notification = Notification.ofLineWorksError("DM障害テスト");
      doThrow(new RuntimeException("ユーザーが存在しません"))
          .when(messageApi).sendTextMessage(anyString(), anyString());

      // Act & Assert
      assertThatThrownBy(() -> sut.send(notification))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("ユーザーが存在しません");
    }
  }
}

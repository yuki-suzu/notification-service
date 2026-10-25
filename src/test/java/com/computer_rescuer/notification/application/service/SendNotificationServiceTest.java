package com.computer_rescuer.notification.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.computer_rescuer.notification.application.dto.NotificationCommand;
import com.computer_rescuer.notification.domain.gateway.NotificationSender;
import com.computer_rescuer.notification.domain.model.Notification;
import com.computer_rescuer.notification.domain.model.NotificationChannelType;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link SendNotificationService} に対する実戦投入可能な単体テストクラス。
 * <p>
 * <b>テスト対象クラスの責務:</b><br>
 * 汎用通知配信ユースケース（{@link com.computer_rescuer.notification.application.SendNotificationUseCase}）を実装し、
 * 受信した通知要求コマンド（{@link NotificationCommand}）をドメインモデル（{@link Notification}）へ変換した上で、
 * 指定された通知チャネル（LINE_WORKS、SLACK 等）に対応する送信ゲートウェイ（{@link NotificationSender}）へ正しくディスパッチします。
 * </p>
 * <p>
 * <b>テスト方針:</b><br>
 * <ul>
 *   <li>正常系: 指定チャネル（LINE WORKS、複数チャネル環境下の個別チャネル）への適切なメッセージ配信検証</li>
 *   <li>境界値: コマンドの未指定フィールド（channelType や destinationType が null）に対するドメインモデルのデフォルト値補完挙動の検証</li>
 *   <li>異常系: 未対応・未登録チャネルが指定された場合の {@link IllegalArgumentException} スロー検証</li>
 *   <li>異常系: 送信ゲートウェイが一切登録されていない状態での例外スロー検証</li>
 *   <li>異常系: 外部送信ゲートウェイでの例外発生時における呼び出し元への透過的な例外伝播検証</li>
 *   <li>抑制アノテーション（{@code @SuppressWarnings}）を排除した完全な型安全性の担保</li>
 *   <li>Java 21/25 仕様に従った {@code getFirst()} によるコレクションアクセスの徹底</li>
 *   <li>C1（分岐網羅）100% の達成</li>
 * </ul>
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SendNotificationService 単体テスト")
class SendNotificationServiceTest {

  @Mock
  private NotificationSender lineWorksSender;

  @Mock
  private NotificationSender slackSender;

  @Captor
  private ArgumentCaptor<Notification> notificationCaptor;

  @Nested
  @DisplayName("正常系: メッセージディスパッチのテスト")
  class NormalDispatchTest {

    /**
     * 【テスト観点 / 責務】<br> 全フィールドが明示的に指定された通知要求コマンドを受け付けた際、 LINE WORKS
     * 送信ゲートウェイへ正しい値を持った通知ドメインモデルがディスパッチされることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - LINE WORKS
     * 送信ゲートウェイ（{@code NotificationChannelType.LINE_WORKS}）を登録したサービスを構築。<br> - 送信先チャネル:
     * LINE_WORKS、宛先種別: USER、宛先ID: "user-abc-123"、本文: "システムメンテナンス通知" のコマンドを生成。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - LINE WORKS 送信ゲートウェイの
     * {@link NotificationSender#send(Notification)} が正確に 1 回呼び出されること。<br> - 送信された
     * {@link Notification} の各属性（チャネル、宛先種別、宛先ID、本文、要求日時）がコマンド値と一致すること。
     * </p>
     */
    @Test
    @DisplayName("全フィールド指定時、指定されたチャネルゲートウェイへ正確に通知モデルが渡されること")
    public void send_WhenAllFieldsSpecified_ShouldDispatchToCorrectSender() {
      // Arrange
      when(lineWorksSender.getChannelType()).thenReturn(NotificationChannelType.LINE_WORKS);
      SendNotificationService sut = new SendNotificationService(List.of(lineWorksSender));

      NotificationCommand command = new NotificationCommand(
          NotificationChannelType.LINE_WORKS,
          Notification.DestinationType.USER,
          "user-abc-123",
          "システムメンテナンス通知"
      );

      // Act
      sut.send(command);

      // Assert
      verify(lineWorksSender, times(1)).send(notificationCaptor.capture());
      Notification actual = notificationCaptor.getValue();

      assertThat(actual.channelType()).isEqualTo(NotificationChannelType.LINE_WORKS);
      assertThat(actual.destinationType()).isEqualTo(Notification.DestinationType.USER);
      assertThat(actual.targetId()).isEqualTo("user-abc-123");
      assertThat(actual.message()).isEqualTo("システムメンテナンス通知");
      assertThat(actual.requestedAt()).isNotNull();
    }

    /**
     * 【テスト観点 / 責務】<br> コマンドの {@code channelType} および {@code destinationType} が null の場合、
     * ドメインモデルの初期化仕様に基づき既定値（LINE_WORKS、CHANNEL）が自動補完されてディスパッチされることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - LINE WORKS 送信ゲートウェイを登録したサービスを構築。<br> -
     * {@code channelType = null}、{@code destinationType = null}、{@code targetId =
     * null}、本文のみ設定したコマンドを生成。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - LINE WORKS ゲートウェイが呼び出されること。<br> - 補完された {@link Notification} の
     * {@code channelType} が {@code LINE_WORKS} であること。<br> - 補完された {@code destinationType} が
     * {@code CHANNEL} であること。<br> - {@code targetId} が null であること。
     * </p>
     */
    @Test
    @DisplayName("チャネル種別や宛先種別がnullの場合、デフォルト値（LINE_WORKS/CHANNEL）が適用されて送信されること")
    public void send_WhenChannelAndDestinationTypeAreNull_ShouldApplyDefaultsAndDispatch() {
      // Arrange
      when(lineWorksSender.getChannelType()).thenReturn(NotificationChannelType.LINE_WORKS);
      SendNotificationService sut = new SendNotificationService(List.of(lineWorksSender));

      NotificationCommand command = new NotificationCommand(
          null,
          null,
          null,
          "デフォルト補完テストメッセージ"
      );

      // Act
      sut.send(command);

      // Assert
      verify(lineWorksSender, times(1)).send(notificationCaptor.capture());
      Notification actual = notificationCaptor.getValue();

      assertThat(actual.channelType()).isEqualTo(NotificationChannelType.LINE_WORKS);
      assertThat(actual.destinationType()).isEqualTo(Notification.DestinationType.CHANNEL);
      assertThat(actual.targetId()).isNull();
      assertThat(actual.message()).isEqualTo("デフォルト補完テストメッセージ");
    }

    /**
     * 【テスト観点 / 責務】<br> 複数の送信ゲートウェイ（LINE WORKS、SLACK）が登録されている環境下において、
     * 要求されたチャネルに対応するゲートウェイのみにディスパッチされ、他のゲートウェイには干渉しないことを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - LINE WORKS と SLACK の 2 つのゲートウェイを注入してサービスを初期化。<br> - 宛先チャネルを
     * {@code SLACK} としたコマンドを発行。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - SLACK 送信ゲートウェイの {@code send} のみが 1 回呼び出されること。<br> - LINE WORKS
     * 送信ゲートウェイの {@code send} は一度も呼び出されないこと。
     * </p>
     */
    @Test
    @DisplayName("複数ゲートウェイ存在時、指定されたチャネルのゲートウェイのみが呼び出されること")
    public void send_WhenMultipleSendersRegistered_ShouldDispatchOnlyToTargetSender() {
      // Arrange
      when(lineWorksSender.getChannelType()).thenReturn(NotificationChannelType.LINE_WORKS);
      when(slackSender.getChannelType()).thenReturn(NotificationChannelType.SLACK);
      SendNotificationService sut = new SendNotificationService(
          List.of(lineWorksSender, slackSender));

      NotificationCommand command = new NotificationCommand(
          NotificationChannelType.SLACK,
          Notification.DestinationType.CHANNEL,
          "general-channel-id",
          "Slack向け通知メッセージ"
      );

      // Act
      sut.send(command);

      // Assert
      verify(slackSender, times(1)).send(notificationCaptor.capture());
      verify(lineWorksSender, never()).send(any());

      List<Notification> capturedNotifications = notificationCaptor.getAllValues();
      Notification actual = capturedNotifications.getFirst();
      assertThat(actual.channelType()).isEqualTo(NotificationChannelType.SLACK);
      assertThat(actual.targetId()).isEqualTo("general-channel-id");
      assertThat(actual.message()).isEqualTo("Slack向け通知メッセージ");
    }
  }

  @Nested
  @DisplayName("異常系: バリデーションおよび例外ハンドリングのテスト")
  class ExceptionTest {

    /**
     * 【テスト観点 / 責務】<br> システムに未登録の通知チャネル（例: EMAIL）が要求された場合、 適切なエラーメッセージを伴う
     * {@link IllegalArgumentException} がスローされ、送信処理が中断されることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - LINE WORKS ゲートウェイのみを登録してサービスを構築。<br> - チャネル種別に {@code EMAIL}
     * を指定したコマンドを生成。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - {@link IllegalArgumentException} がスローされること。<br> -
     * 例外メッセージが「未対応の通知チャネル: EMAIL」であること。<br> - 登録済みゲートウェイへの {@code send} が呼び出されないこと。
     * </p>
     */
    @Test
    @DisplayName("未対応のチャネルが指定された場合、IllegalArgumentExceptionをスローすること")
    public void send_WhenUnsupportedChannelRequested_ShouldThrowIllegalArgumentException() {
      // Arrange
      when(lineWorksSender.getChannelType()).thenReturn(NotificationChannelType.LINE_WORKS);
      SendNotificationService sut = new SendNotificationService(List.of(lineWorksSender));

      NotificationCommand command = new NotificationCommand(
          NotificationChannelType.EMAIL,
          Notification.DestinationType.CHANNEL,
          "admin@example.com",
          "メール通知本文"
      );

      // Act & Assert
      assertThatThrownBy(() -> sut.send(command))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("未対応の通知チャネル: EMAIL");

      verify(lineWorksSender, never()).send(any());
    }

    /**
     * 【テスト観点 / 責務】<br> 送信ゲートウェイが 1 件も登録されていない状態で通知が要求された場合、 チャネル未解決として
     * {@link IllegalArgumentException} がスローされることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - 空のリストで {@link SendNotificationService} を初期化。<br> - LINE_WORKS
     * 宛ての通知コマンドを発行。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - {@link IllegalArgumentException} がスローされること。<br> -
     * メッセージが「未対応の通知チャネル: LINE_WORKS」であること。
     * </p>
     */
    @Test
    @DisplayName("ゲートウェイリストが空の場合、未対応チャネル例外をスローすること")
    public void send_WhenNoSendersRegistered_ShouldThrowIllegalArgumentException() {
      // Arrange
      SendNotificationService sut = new SendNotificationService(Collections.emptyList());

      NotificationCommand command = new NotificationCommand(
          NotificationChannelType.LINE_WORKS,
          Notification.DestinationType.CHANNEL,
          null,
          "テスト通知"
      );

      // Act & Assert
      assertThatThrownBy(() -> sut.send(command))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("未対応の通知チャネル: LINE_WORKS");
    }

    /**
     * 【テスト観点 / 責務】<br> 送信ゲートウェイ（外部API呼び出し等）で {@link RuntimeException} が発生した場合、
     * サービス層で握りつぶさずにそのまま上位呼び出し元へ例外を透過伝播することを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - LINE WORKS ゲートウェイの {@code send} 呼び出し時に
     * {@code RuntimeException("外部API通信エラー")} をスローするよう設定。<br> - 有効な通知コマンドを生成。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - ゲートウェイがスローした {@link RuntimeException} がそのまま再スローされること。<br> -
     * 例外メッセージが「外部API通信エラー」と一致すること。
     * </p>
     */
    @Test
    @DisplayName("送信ゲートウェイで例外が発生した場合、上位へ透過的に再スローされること")
    public void send_WhenSenderThrowsRuntimeException_ShouldPropagateException() {
      // Arrange
      when(lineWorksSender.getChannelType()).thenReturn(NotificationChannelType.LINE_WORKS);
      SendNotificationService sut = new SendNotificationService(List.of(lineWorksSender));

      NotificationCommand command = new NotificationCommand(
          NotificationChannelType.LINE_WORKS,
          Notification.DestinationType.USER,
          "user-001",
          "エラー発生テスト"
      );

      doThrow(new RuntimeException("外部API通信エラー"))
          .when(lineWorksSender).send(any());

      // Act & Assert
      assertThatThrownBy(() -> sut.send(command))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("外部API通信エラー");

      verify(lineWorksSender, times(1)).send(any());
    }
  }
}

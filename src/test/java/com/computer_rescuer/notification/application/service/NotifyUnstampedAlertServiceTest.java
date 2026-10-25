package com.computer_rescuer.notification.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.computer_rescuer.notification.application.dto.UnstampedAlertEvent;
import com.computer_rescuer.notification.application.dto.UnstampedAlertEvent.UnstampedEmployee;
import com.computer_rescuer.notification.domain.gateway.NotificationSender;
import com.computer_rescuer.notification.domain.model.Notification;
import com.computer_rescuer.notification.domain.model.Notification.DestinationType;
import com.computer_rescuer.notification.domain.model.NotificationChannelType;
import com.computer_rescuer.notification.domain.service.UnstampedAlertMessageFormatter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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
 * {@link NotifyUnstampedAlertService} に対する実戦投入可能な単体テストクラス。
 * <p>
 * <b>テスト対象クラスの責務:</b><br>
 * 勤怠管理サービスから受信した未打刻者検知イベント（{@link UnstampedAlertEvent}）を受け取り、
 * ドメインフォーマッター（{@link UnstampedAlertMessageFormatter}）を用いて管理者向け視認テキストを組み立て、 LINE WORKS
 * の管理者トークルーム（チャンネル）へアラート通知を配信するユースケースをオーケストレーションします。
 * </p>
 * <p>
 * <b>テスト方針:</b><br>
 * <ul>
 *   <li>入力境界値: 従業員リストが null または空の場合における早期リターン挙動の検証</li>
 *   <li>正常系: 未打刻者が存在する場合に、フォーマッターによる文面整形を経て LINE WORKS 管理者チャンネル宛てに通知が送信されることの検証</li>
 *   <li>異常系: LINE WORKS 送信ゲートウェイが未登録の場合の {@link IllegalStateException} スロー検証</li>
 *   <li>異常系: 外部送信ゲートウェイでの例外発生時における上位への透過的な例外伝播検証</li>
 *   <li>不要な警告抑制（{@code @SuppressWarnings}）を排除したクリーンな型安全設計</li>
 *   <li>コレクションアクセスにおける {@code getFirst()} の適用</li>
 *   <li>C1（分岐網羅）100% の達成</li>
 * </ul>
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("NotifyUnstampedAlertService 単体テスト")
class NotifyUnstampedAlertServiceTest {

  @Mock
  private NotificationSender lineWorksSender;

  @Mock
  private UnstampedAlertMessageFormatter unstampedAlertMessageFormatter;

  @Captor
  private ArgumentCaptor<Notification> notificationCaptor;

  @Nested
  @DisplayName("入力境界値チェックのテスト")
  class BoundaryCheckTest {

    /**
     * 【テスト観点 / 責務】<br> イベント内の従業員リスト（employees）が null の場合、通知処理（フォーマットおよび送信）を行わずに
     * 安全に早期リターンすることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - LINE WORKS 送信ゲートウェイを登録したサービスを構築。<br> - employees が null である
     * {@link UnstampedAlertEvent} を生成。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - 例外が発生せず正常終了すること。<br> -
     * フォーマッター（{@link UnstampedAlertMessageFormatter#format(LocalDate, List)}）が呼び出されないこと。<br> -
     * 送信ゲートウェイ（{@link NotificationSender#send(Notification)}）が呼び出されないこと。
     * </p>
     */
    @Test
    @DisplayName("employeesがnullの場合、文面整形および送信を行わず早期リターンすること")
    public void execute_WhenEmployeesIsNull_ShouldReturnImmediatelyWithoutSending() {
      // Arrange
      when(lineWorksSender.getChannelType()).thenReturn(NotificationChannelType.LINE_WORKS);
      NotifyUnstampedAlertService sut = new NotifyUnstampedAlertService(
          List.of(lineWorksSender),
          unstampedAlertMessageFormatter
      );

      UnstampedAlertEvent event = new UnstampedAlertEvent(
          LocalDate.of(2026, 9, 22),
          LocalDateTime.of(2026, 9, 22, 9, 15, 0),
          null
      );

      // Act
      sut.execute(event);

      // Assert
      verify(unstampedAlertMessageFormatter, never()).format(any(), any());
      verify(lineWorksSender, never()).send(any());
    }

    /**
     * 【テスト観点 / 責務】<br> イベント内の従業員リスト（employees）が空リスト（0件）の場合、通知処理を行わずに 安全に早期リターンすることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - LINE WORKS 送信ゲートウェイを登録したサービスを構築。<br> - employees が空リスト（0件）である
     * {@link UnstampedAlertEvent} を生成。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - 例外が発生せず正常終了すること。<br> - フォーマッターおよび送信ゲートウェイが一切呼び出されないこと。
     * </p>
     */
    @Test
    @DisplayName("employeesが空リストの場合、文面整形および送信を行わず早期リターンすること")
    public void execute_WhenEmployeesIsEmpty_ShouldReturnImmediatelyWithoutSending() {
      // Arrange
      when(lineWorksSender.getChannelType()).thenReturn(NotificationChannelType.LINE_WORKS);
      NotifyUnstampedAlertService sut = new NotifyUnstampedAlertService(
          List.of(lineWorksSender),
          unstampedAlertMessageFormatter
      );

      UnstampedAlertEvent event = new UnstampedAlertEvent(
          LocalDate.of(2026, 9, 22),
          LocalDateTime.of(2026, 9, 22, 9, 15, 0),
          Collections.emptyList()
      );

      // Act
      sut.execute(event);

      // Assert
      verify(unstampedAlertMessageFormatter, never()).format(any(), any());
      verify(lineWorksSender, never()).send(any());
    }
  }

  @Nested
  @DisplayName("正常系: 未打刻アラート通知のテスト")
  class NormalExecutionTest {

    /**
     * 【テスト観点 / 責務】<br> 未打刻対象者が 1 名以上存在する場合、ドメインフォーマッターを用いて文面が整形され、 LINE WORKS
     * の管理者チャンネル宛て（DestinationType.CHANNEL、targetId=null）で通知モデルが送信されることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - 未打刻従業員 1 名を含む {@link UnstampedAlertEvent} を生成。<br> -
     * フォーマッターが「⚠️ 【未打刻アラート】...」の整形テキストを返却するよう設定。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - フォーマッターが対象日および従業員リストを引数として 1 回呼び出されること。<br> - 送信ゲートウェイの
     * {@link NotificationSender#send(Notification)} が 1 回呼び出されること。<br> - 送信された通知オブジェクトのチャネルが
     * {@code LINE_WORKS}、宛先種別が {@code CHANNEL}、{@code targetId} が null であること。<br> -
     * 送信本文がフォーマッターの返却文字列と完全一致すること。
     * </p>
     */
    @Test
    @DisplayName("未打刻者が存在する場合、整形文面を生成して管理者チャンネル宛てに送信すること")
    public void execute_WhenEmployeesExist_ShouldFormatMessageAndSendToLineWorksAdminChannel() {
      // Arrange
      when(lineWorksSender.getChannelType()).thenReturn(NotificationChannelType.LINE_WORKS);
      NotifyUnstampedAlertService sut = new NotifyUnstampedAlertService(
          List.of(lineWorksSender),
          unstampedAlertMessageFormatter
      );

      LocalDate targetDate = LocalDate.of(2026, 9, 22);
      UnstampedEmployee employee = new UnstampedEmployee(
          "EMP001",
          "taro.yamada@example.com",
          "開発本部",
          "山田 太郎",
          LocalTime.of(9, 0),
          2
      );
      List<UnstampedEmployee> employees = List.of(employee);
      UnstampedAlertEvent event = new UnstampedAlertEvent(
          targetDate,
          LocalDateTime.of(2026, 9, 22, 9, 15, 0),
          employees
      );

      String expectedFormattedMessage = "⚠️ 【未打刻アラート】\n2026-09-22 の出勤打刻が確認できない従業員がいます。\n\n🏢 開発本部\n  ・山田 太郎 （予定: 09:00〜 / 今月2回目）";
      when(unstampedAlertMessageFormatter.format(targetDate, employees))
          .thenReturn(expectedFormattedMessage);

      // Act
      sut.execute(event);

      // Assert
      verify(unstampedAlertMessageFormatter, times(1)).format(targetDate, employees);

      verify(lineWorksSender, times(1)).send(notificationCaptor.capture());
      Notification actualNotification = notificationCaptor.getValue();

      assertThat(actualNotification.channelType()).isEqualTo(NotificationChannelType.LINE_WORKS);
      assertThat(actualNotification.destinationType()).isEqualTo(DestinationType.CHANNEL);
      assertThat(actualNotification.targetId()).isNull();
      assertThat(actualNotification.message()).isEqualTo(expectedFormattedMessage);
      assertThat(actualNotification.requestedAt()).isNotNull();
    }
  }

  @Nested
  @DisplayName("異常系: 例外ハンドリングのテスト")
  class ExceptionHandlingTest {

    /**
     * 【テスト観点 / 責務】<br> LINE WORKS 送信ゲートウェイがコンテキスト内に未登録（senderMap に不在）の場合に、 適切なエラーメッセージを伴う
     * {@link IllegalStateException} がスローされることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - 空の送信ゲートウェイリストで {@link NotifyUnstampedAlertService} を構築。<br> -
     * 未打刻者を含む有効なイベントを生成。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - {@link IllegalStateException} がスローされること。<br> - 例外メッセージが「LINE
     * WORKS 送信ゲートウェイが未登録です」と一致すること。
     * </p>
     */
    @Test
    @DisplayName("LINE WORKS送信ゲートウェイが未登録の場合、IllegalStateExceptionをスローすること")
    public void execute_WhenLineWorksSenderNotRegistered_ShouldThrowIllegalStateException() {
      // Arrange
      NotifyUnstampedAlertService sutWithoutSender = new NotifyUnstampedAlertService(
          Collections.emptyList(),
          unstampedAlertMessageFormatter
      );

      LocalDate targetDate = LocalDate.of(2026, 9, 22);
      UnstampedEmployee employee = new UnstampedEmployee(
          "EMP001",
          "taro.yamada@example.com",
          "営業部",
          "鈴木 一朗",
          LocalTime.of(9, 30),
          1
      );
      List<UnstampedEmployee> employees = List.of(employee);
      UnstampedAlertEvent event = new UnstampedAlertEvent(
          targetDate,
          LocalDateTime.of(2026, 9, 22, 9, 45, 0),
          employees
      );

      when(unstampedAlertMessageFormatter.format(targetDate, employees))
          .thenReturn("アラート本文");

      // Act & Assert
      assertThatThrownBy(() -> sutWithoutSender.execute(event))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("LINE WORKS 送信ゲートウェイが未登録です");
    }

    /**
     * 【テスト観点 / 責務】<br> 外部送信ゲートウェイ（{@link NotificationSender#send(Notification)}）で通信障害等の
     * {@link RuntimeException} が発生した場合、サービス層で握りつぶさずに上位（Kafka Consumer 等）へ 透過的に再スローされることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - 送信ゲートウェイ呼び出し時に {@code RuntimeException("LINE WORKS 接続タイムアウト")}
     * をスローするよう設定。<br> - 未打刻者を含む有効なイベントを生成。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - ゲートウェイがスローした {@link RuntimeException} がそのままスローされること。<br> -
     * 例外メッセージが「LINE WORKS 接続タイムアウト」であること。
     * </p>
     */
    @Test
    @DisplayName("外部送信ゲートウェイで通信障害が発生した場合、上位へ透過的に再スローされること")
    public void execute_WhenSenderThrowsException_ShouldPropagateException() {
      // Arrange
      when(lineWorksSender.getChannelType()).thenReturn(NotificationChannelType.LINE_WORKS);
      NotifyUnstampedAlertService sut = new NotifyUnstampedAlertService(
          List.of(lineWorksSender),
          unstampedAlertMessageFormatter
      );

      LocalDate targetDate = LocalDate.of(2026, 9, 22);
      UnstampedEmployee employee = new UnstampedEmployee(
          "EMP002",
          "jiro.sato@example.com",
          "総務部",
          "佐藤 次郎",
          LocalTime.of(9, 0),
          1
      );
      List<UnstampedEmployee> employees = List.of(employee);
      UnstampedAlertEvent event = new UnstampedAlertEvent(
          targetDate,
          LocalDateTime.of(2026, 9, 22, 9, 15, 0),
          employees
      );

      when(unstampedAlertMessageFormatter.format(targetDate, employees))
          .thenReturn("アラート文面");

      doThrow(new RuntimeException("LINE WORKS 接続タイムアウト"))
          .when(lineWorksSender).send(any());

      // Act & Assert
      assertThatThrownBy(() -> sut.execute(event))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("LINE WORKS 接続タイムアウト");

      verify(lineWorksSender, times(1)).send(any());
    }
  }
}

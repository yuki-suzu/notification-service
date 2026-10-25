package com.computer_rescuer.notification.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.computer_rescuer.notification.application.dto.AttendanceIrregularityEvent;
import com.computer_rescuer.notification.application.dto.AttendanceIrregularityEvent.EmployeeIrregularitySummary;
import com.computer_rescuer.notification.domain.gateway.NotificationSender;
import com.computer_rescuer.notification.domain.model.Notification;
import com.computer_rescuer.notification.domain.model.Notification.DestinationType;
import com.computer_rescuer.notification.domain.model.NotificationChannelType;
import com.computer_rescuer.notification.domain.service.AttendanceIrregularityMessageFormatter;
import java.time.LocalDateTime;
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
 * {@link NotifyAttendanceIrregularityService} に対する実戦投入可能な単体テストクラス。
 * <p>
 * <b>テスト対象クラスの責務:</b><br>
 * 勤怠管理サービスから非同期に発行された「月次勤怠サマリ（勤怠異常）イベント」（{@link AttendanceIrregularityEvent}）を受け取り、
 * ドメインフォーマッター（{@link AttendanceIrregularityMessageFormatter}）を用いて管理者向け視認テキストを整形した上で、 LINE WORKS
 * 送信ゲートウェイ（{@link NotificationSender}）を通じて管理者アラートチャンネルへ配信します。
 * </p>
 * <p>
 * <b>テスト方針:</b><br>
 * <ul>
 *   <li>入力値の境界値（従業員リストが null または空リスト）に対する安全な早期リターン挙動の検証</li>
 *   <li>LINE WORKS 送信ゲートウェイ未登録時における {@link IllegalStateException} スローの検証</li>
 *   <li>正常系における現在日時の引き渡し、フォーマッター呼び出し、および管理者チャンネル宛て通知モデルの属性検証</li>
 *   <li>外部送信ゲートウェイでの例外発生時における上位への透過的な例外伝播検証</li>
 *   <li>Java 21/25 仕様に従った {@code getFirst()} によるコレクションアクセスの徹底</li>
 *   <li>不要な警告抑制アノテーションの完全排除と C1（分岐網羅）100% の達成</li>
 * </ul>
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("NotifyAttendanceIrregularityService 単体テスト")
class NotifyAttendanceIrregularityServiceTest {

  @Mock
  private NotificationSender lineWorksSender;

  @Mock
  private AttendanceIrregularityMessageFormatter messageFormatter;

  @Captor
  private ArgumentCaptor<Notification> notificationCaptor;

  @Captor
  private ArgumentCaptor<LocalDateTime> receivedAtCaptor;

  @Nested
  @DisplayName("入力境界値および前提条件チェックのテスト")
  class PreconditionAndBoundaryTest {

    /**
     * 【テスト観点 / 責務】<br> イベント内の従業員リスト（employees）が null の場合、 メッセージ整形や送信処理を行わずに安全に早期リターンすることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - LINE WORKS 送信ゲートウェイを登録したサービスを構築。<br> - 対象月: "2026-09"、employees
     * が null である {@link AttendanceIrregularityEvent} を生成。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - 例外なく正常終了すること。<br> -
     * フォーマッター（{@link AttendanceIrregularityMessageFormatter#format(LocalDateTime,
     * List)}）が呼び出されないこと。<br> - 送信ゲートウェイ（{@link NotificationSender#send(Notification)}）が呼び出されないこと。
     * </p>
     */
    @Test
    @DisplayName("employeesがnullの場合、送信処理を行わず正常に早期リターンすること")
    public void execute_WhenEmployeesIsNull_ShouldReturnImmediatelyWithoutSending() {
      // Arrange
      when(lineWorksSender.getChannelType()).thenReturn(NotificationChannelType.LINE_WORKS);
      NotifyAttendanceIrregularityService sut = new NotifyAttendanceIrregularityService(
          List.of(lineWorksSender),
          messageFormatter
      );

      AttendanceIrregularityEvent event = new AttendanceIrregularityEvent("2026-09", null);

      // Act
      sut.execute(event);

      // Assert
      verify(messageFormatter, never()).format(any(), any());
      verify(lineWorksSender, never()).send(any());
    }

    /**
     * 【テスト観点 / 責務】<br> イベント内の従業員リスト（employees）が空リスト（0件）の場合、
     * 通知不要と判断してメッセージ整形や送信処理を行わずに早期リターンすることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - LINE WORKS 送信ゲートウェイを登録したサービスを構築。<br> - 対象月: "2026-09"、employees
     * が空リスト（{@link Collections#emptyList()}）であるイベントを生成。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - 例外なく正常終了すること。<br> - フォーマッターおよび送信ゲートウェイが一度も呼び出されないこと。
     * </p>
     */
    @Test
    @DisplayName("employeesが空リストの場合、送信処理を行わず正常に早期リターンすること")
    public void execute_WhenEmployeesIsEmpty_ShouldReturnImmediatelyWithoutSending() {
      // Arrange
      when(lineWorksSender.getChannelType()).thenReturn(NotificationChannelType.LINE_WORKS);
      NotifyAttendanceIrregularityService sut = new NotifyAttendanceIrregularityService(
          List.of(lineWorksSender),
          messageFormatter
      );

      AttendanceIrregularityEvent event = new AttendanceIrregularityEvent("2026-09",
          Collections.emptyList());

      // Act
      sut.execute(event);

      // Assert
      verify(messageFormatter, never()).format(any(), any());
      verify(lineWorksSender, never()).send(any());
    }

    /**
     * 【テスト観点 / 責務】<br> 対象従業員が存在するにもかかわらず、LINE WORKS 送信ゲートウェイが未登録の場合、 適切なエラーメッセージを伴う
     * {@link IllegalStateException} がスローされることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - 送信ゲートウェイリストを空（0件）にしてサービスを構築。<br> - 有効な従業員サマリを1件含むイベントを生成。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - {@link IllegalStateException} がスローされること。<br> - 例外メッセージが「LINE
     * WORKS 送信ゲートウェイが未登録です」と一致すること。<br> - フォーマッターが呼び出されないこと。
     * </p>
     */
    @Test
    @DisplayName("LINE WORKS送信ゲートウェイが未登録の場合、IllegalStateExceptionをスローすること")
    public void execute_WhenLineWorksSenderNotRegistered_ShouldThrowIllegalStateException() {
      // Arrange
      NotifyAttendanceIrregularityService sut = new NotifyAttendanceIrregularityService(
          Collections.emptyList(),
          messageFormatter
      );

      EmployeeIrregularitySummary summary = new EmployeeIrregularitySummary(
          "EMP001", "システム開発部", "山田 太郎", 0, 1, 0, 2
      );
      AttendanceIrregularityEvent event = new AttendanceIrregularityEvent("2026-09",
          List.of(summary));

      // Act & Assert
      assertThatThrownBy(() -> sut.execute(event))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("LINE WORKS 送信ゲートウェイが未登録です");

      verify(messageFormatter, never()).format(any(), any());
    }
  }

  @Nested
  @DisplayName("正常系: 月次勤怠サマリ通知配信のテスト")
  class NormalExecutionTest {

    /**
     * 【テスト観点 / 責務】<br> 正常なイベントを受信した際、受信時点の日時とともにドメインフォーマッターが呼び出され、
     * 管理者チャンネル宛て（DestinationType.CHANNEL、targetId = null）のアラート通知が構築されて LINE WORKS
     * ゲートウェイへ正常に送信されることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - LINE WORKS 送信ゲートウェイを登録したサービスを構築。<br> -
     * 2名の従業員サマリ（当欠、半休、遅延等）を含むイベントを生成。<br> - フォーマッターが整形テキスト「📢 【月次勤怠サマリ更新】...」を返却するよう設定。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - フォーマッターが現在日時に近い日時オブジェクトと従業員リストで 1 回呼び出されること。<br> - 送信ゲートウェイの
     * {@link NotificationSender#send(Notification)} が 1 回呼び出されること。<br> - 送信された {@link Notification}
     * の属性が以下の通りであること：
     *   <ul>
     *     <li>{@code channelType == NotificationChannelType.LINE_WORKS}</li>
     *     <li>{@code destinationType == DestinationType.CHANNEL}</li>
     *     <li>{@code targetId == null}（デフォルトのアラートチャンネル宛て）</li>
     *     <li>{@code message == "📢 【月次勤怠サマリ更新】..."}</li>
     *   </ul>
     * </p>
     */
    @Test
    @DisplayName("正常系: ドメインフォーマッターで本文整形後、管理者チャンネルへ正しく通知されること")
    public void execute_WhenEmployeesPresent_ShouldFormatAndSendAlertToChannel() {
      // Arrange
      when(lineWorksSender.getChannelType()).thenReturn(NotificationChannelType.LINE_WORKS);
      NotifyAttendanceIrregularityService sut = new NotifyAttendanceIrregularityService(
          List.of(lineWorksSender),
          messageFormatter
      );

      EmployeeIrregularitySummary emp1 = new EmployeeIrregularitySummary(
          "EMP001", "第1開発部", "山田 太郎", 8, 1, 1, 2
      );
      EmployeeIrregularitySummary emp2 = new EmployeeIrregularitySummary(
          "EMP002", "第2開発部", "佐藤 次郎", 8, 0, 0, 3
      );
      List<EmployeeIrregularitySummary> employees = List.of(emp1, emp2);
      AttendanceIrregularityEvent event = new AttendanceIrregularityEvent("2026-09", employees);

      String expectedFormattedMessage = """
          📢 【月次勤怠サマリ更新】 (2026-09-30 18:00時点)
          勤怠情報が更新されました
          
          🏢 第1開発部
            ・山田 太郎 当欠:1日 / 半休:1日 / 遅延:2日
          🏢 第2開発部
            ・佐藤 次郎 当欠:0日 / 半休:0日 / 遅延:3日""";

      when(messageFormatter.format(any(LocalDateTime.class), eq(employees)))
          .thenReturn(expectedFormattedMessage);

      LocalDateTime beforeExecution = LocalDateTime.now().minusSeconds(1);

      // Act
      sut.execute(event);

      LocalDateTime afterExecution = LocalDateTime.now().plusSeconds(1);

      // Assert
      // 1. フォーマッターの呼び出し検証
      verify(messageFormatter, times(1)).format(receivedAtCaptor.capture(), eq(employees));
      LocalDateTime capturedTime = receivedAtCaptor.getValue();
      assertThat(capturedTime).isAfterOrEqualTo(beforeExecution).isBeforeOrEqualTo(afterExecution);

      // 2. 送信ゲートウェイの呼び出し検証
      verify(lineWorksSender, times(1)).send(notificationCaptor.capture());
      Notification actualNotification = notificationCaptor.getAllValues().getFirst();

      assertThat(actualNotification.channelType()).isEqualTo(NotificationChannelType.LINE_WORKS);
      assertThat(actualNotification.destinationType()).isEqualTo(DestinationType.CHANNEL);
      assertThat(actualNotification.targetId()).isNull();
      assertThat(actualNotification.message()).isEqualTo(expectedFormattedMessage);
      assertThat(actualNotification.requestedAt()).isNotNull();
    }
  }

  @Nested
  @DisplayName("異常系: 外部通信例外ハンドリングのテスト")
  class ExceptionTest {

    /**
     * 【テスト観点 / 責務】<br> LINE WORKS 送信ゲートウェイで外部通信エラー（{@link RuntimeException}）が発生した場合、
     * サービス層で例外をもみ消さずに上位呼び出し元（Kafka Consumer 等）へそのまま透過伝播することを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - LINE WORKS 送信ゲートウェイを登録したサービスを構築。<br> - ゲートウェイの {@code send}
     * 呼び出し時に {@code RuntimeException("LINE WORKS API通信遮断")} をスローするよう設定。<br> - 有効なイベントを生成。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - スローされた {@link RuntimeException} がそのまま上位へ伝播すること。<br> -
     * 例外メッセージが「LINE WORKS API通信遮断」と一致すること。
     * </p>
     */
    @Test
    @DisplayName("送信ゲートウェイで例外が発生した場合、上位へ透過的に再スローされること")
    public void execute_WhenSenderThrowsException_ShouldPropagateException() {
      // Arrange
      when(lineWorksSender.getChannelType()).thenReturn(NotificationChannelType.LINE_WORKS);
      NotifyAttendanceIrregularityService sut = new NotifyAttendanceIrregularityService(
          List.of(lineWorksSender),
          messageFormatter
      );

      EmployeeIrregularitySummary summary = new EmployeeIrregularitySummary(
          "EMP001", "インフラ部", "鈴木 一朗", 8, 2, 0, 1
      );
      AttendanceIrregularityEvent event = new AttendanceIrregularityEvent("2026-09",
          List.of(summary));

      when(messageFormatter.format(any(LocalDateTime.class), any()))
          .thenReturn("メッセージ本文");

      doThrow(new RuntimeException("LINE WORKS API通信遮断"))
          .when(lineWorksSender).send(any());

      // Act & Assert
      assertThatThrownBy(() -> sut.execute(event))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("LINE WORKS API通信遮断");

      verify(lineWorksSender, times(1)).send(any());
    }
  }
}

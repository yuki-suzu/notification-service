package com.computer_rescuer.notification.presentation.consumer;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.computer_rescuer.notification.application.SendUnstampedDirectReminderUseCase;
import com.computer_rescuer.notification.application.dto.UnstampedDirectReminderEvent;
import com.computer_rescuer.notification.application.dto.UnstampedDirectReminderEvent.DirectReminderEmployee;
import com.computer_rescuer.notification.presentation.consumer.handler.DltErrorHandler;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link UnstampedDirectReminderKafkaConsumer} に対する実戦投入可能な単体テストクラス。
 * <p>
 * <b>テスト対象クラスの責務:</b><br>
 * 未打刻者本人向け個別ダイレクト通知トピック（{@code unstamped-direct-topic}）からメッセージを購読し、
 * 受信した個別通知要求イベント（{@link UnstampedDirectReminderEvent}）を {@link SendUnstampedDirectReminderUseCase}
 * へディスパッチします。
 * </p>
 * <p>
 * <b>テスト方針:</b><br>
 * <ul>
 *   <li>対象従業員リストが存在する場合の正常系委譲検証</li>
 *   <li>従業員リストが null の場合の三項演算子 null ガード分岐（C1カバレッジ）検証</li>
 *   <li>従業員リストが空コレクションの場合の境界値検証</li>
 *   <li>UseCase 側で例外が発生した際の例外再スロー検証</li>
 * </ul>
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UnstampedDirectReminderKafkaConsumer 単体テスト")
class UnstampedDirectReminderKafkaConsumerTest {

  @Mock
  private SendUnstampedDirectReminderUseCase sendUnstampedDirectReminderUseCase;

  @Mock
  private DltErrorHandler dltErrorHandler;

  @InjectMocks
  private UnstampedDirectReminderKafkaConsumer sut;

  @Nested
  @DisplayName("未打刻者個別DMメッセージ購読処理（consume）のテスト")
  class ConsumeTest {

    /**
     * 【テスト観点 / 責務】<br> 対象従業員を含む未打刻DM配信イベントを受信した際、
     * {@link SendUnstampedDirectReminderUseCase#execute(UnstampedDirectReminderEvent)}
     * へ正しくディスパッチされることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - 従業員 1 名を含む {@link UnstampedDirectReminderEvent} を生成。<br> -
     * トピック名: "unstamped-direct-topic"、パーティション: 0、オフセット: 10L。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> -
     * {@link SendUnstampedDirectReminderUseCase#execute(UnstampedDirectReminderEvent)} が正確に 1
     * 回呼び出されること。
     * </p>
     */
    @Test
    @DisplayName("対象従業員リストを含む正常イベント受信時、UseCaseへ正しく委譲されること")
    public void consume_WhenEmployeesPresent_ShouldDelegateToUseCase() {
      // Arrange
      DirectReminderEmployee employee = new DirectReminderEmployee(
          "EMP001", "taro@example.com", "山田 太郎"
      );
      UnstampedDirectReminderEvent event = new UnstampedDirectReminderEvent(List.of(employee));

      // Act
      sut.consume(event, "unstamped-direct-topic", 0, 10L);

      // Assert
      verify(sendUnstampedDirectReminderUseCase, times(1)).execute(event);
    }

    /**
     * 【テスト観点 / 責務】<br> イベント内の従業員リストが null の場合でも、三項演算子の null ガードにより 件数 0 として安全にログ出力され、UseCase
     * へイベントが渡されることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - 従業員リストが null の {@link UnstampedDirectReminderEvent} を生成。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> -
     * 例外なく正常終了し、{@code sendUnstampedDirectReminderUseCase.execute(event)} が呼び出されること。
     * </p>
     */
    @Test
    @DisplayName("従業員リストがnullの場合でもnull安全に件数処理され、UseCaseへ委譲されること")
    public void consume_WhenEmployeesIsNull_ShouldDelegateToUseCaseSafely() {
      // Arrange
      UnstampedDirectReminderEvent event = new UnstampedDirectReminderEvent(null);

      // Act
      sut.consume(event, "unstamped-direct-topic", 0, 20L);

      // Assert
      verify(sendUnstampedDirectReminderUseCase, times(1)).execute(event);
    }

    /**
     * 【テスト観点 / 責務】<br> 従業員リストが空コレクション（0件）のイベントを受信した際、 正常に UseCase へディスパッチされることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - 従業員リストが空の {@link UnstampedDirectReminderEvent} を生成。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - {@code sendUnstampedDirectReminderUseCase.execute(event)}
     * が呼び出されること。
     * </p>
     */
    @Test
    @DisplayName("従業員リストが空の場合、件数0として正常にUseCaseへ委譲されること")
    public void consume_WhenEmployeesIsEmpty_ShouldDelegateToUseCase() {
      // Arrange
      UnstampedDirectReminderEvent event = new UnstampedDirectReminderEvent(
          Collections.emptyList());

      // Act
      sut.consume(event, "unstamped-direct-topic", 1, 30L);

      // Assert
      verify(sendUnstampedDirectReminderUseCase, times(1)).execute(event);
    }

    /**
     * 【テスト観点 / 責務】<br> UseCase 側で例外が発生した場合に、Spring Kafka 側でリトライおよび DLT 退避を
     * 行わせるために例外がそのまま上位へ伝播することを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - UseCase が {@link RuntimeException} をスローするようにモック設定。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - メソッド呼び出し元へ同一の例外が伝播すること。
     * </p>
     */
    @Test
    @DisplayName("UseCaseで例外が発生した場合、上位へ例外がそのまま伝播すること")
    public void consume_WhenUseCaseThrowsException_ShouldPropagateException() {
      // Arrange
      UnstampedDirectReminderEvent event = new UnstampedDirectReminderEvent(
          Collections.emptyList());
      doThrow(new RuntimeException("未打刻個別DM配信エラー"))
          .when(sendUnstampedDirectReminderUseCase).execute(event);

      // Act & Assert
      assertThatThrownBy(() -> sut.consume(event, "unstamped-direct-topic", 0, 40L))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("未打刻個別DM配信エラー");
    }
  }
}

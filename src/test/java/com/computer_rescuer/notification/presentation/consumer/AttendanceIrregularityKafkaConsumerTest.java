package com.computer_rescuer.notification.presentation.consumer;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.computer_rescuer.notification.application.NotifyAttendanceIrregularityUseCase;
import com.computer_rescuer.notification.application.dto.AttendanceIrregularityEvent;
import com.computer_rescuer.notification.application.dto.AttendanceIrregularityEvent.EmployeeIrregularitySummary;
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
 * {@link AttendanceIrregularityKafkaConsumer} に対する実戦投入可能な単体テストクラス。
 * <p>
 * <b>テスト対象クラスの責務:</b><br>
 * 月次勤怠サマリ（勤怠異常）トピックからメッセージを非同期に購読（Consume）し、 受信した {@link AttendanceIrregularityEvent} を
 * {@link NotifyAttendanceIrregularityUseCase} へ委譲・ディスパッチします。
 * </p>
 * <p>
 * <b>テスト方針:</b><br>
 * <ul>
 *   <li>従業員リストが存在する場合の正常系ディスパッチ検証</li>
 *   <li>従業員リストが null の場合のログ件数算出分岐（C1カバレッジ）および正常委譲検証</li>
 *   <li>従業員リストが空コレクションの場合の境界値検証</li>
 *   <li>UseCase 側で例外が発生した場合の透過的な例外伝播検証</li>
 * </ul>
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AttendanceIrregularityKafkaConsumer 単体テスト")
class AttendanceIrregularityKafkaConsumerTest {

  @Mock
  private NotifyAttendanceIrregularityUseCase notifyAttendanceIrregularityUseCase;

  @InjectMocks
  private AttendanceIrregularityKafkaConsumer sut;

  @Nested
  @DisplayName("メッセージ購読処理（consume）のテスト")
  class ConsumeTest {

    /**
     * 【テスト観点 / 責務】<br> 正常な勤怠サマリエベント（従業員リストあり）を受信した際、 メタデータ（Topic, Partition, Offset）とともに UseCase
     * へ正しく委譲されることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - 処理対象月 "2026-09"、従業員 1 名を含む {@link AttendanceIrregularityEvent}
     * を生成。<br> - トピック名: "attendance-irregularity-topic"、パーティション: 0、オフセット: 100L。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> -
     * {@link NotifyAttendanceIrregularityUseCase#execute(AttendanceIrregularityEvent)}
     * が同一のイベントオブジェクトで正確に 1 回呼び出されること。
     * </p>
     */
    @Test
    @DisplayName("従業員リストを含む正常イベント受信時、UseCaseへ正しく委譲されること")
    public void consume_WhenEmployeesPresent_ShouldDelegateToUseCase() {
      // Arrange
      EmployeeIrregularitySummary summary = new EmployeeIrregularitySummary(
          "EMP001", "システム開発部", "山田 太郎", 0, 1, 0, 2
      );
      AttendanceIrregularityEvent event = new AttendanceIrregularityEvent("2026-09",
          List.of(summary));

      // Act
      sut.consume(event, "attendance-irregularity-topic", 0, 100L);

      // Assert
      verify(notifyAttendanceIrregularityUseCase, times(1)).execute(event);
    }

    /**
     * 【テスト観点 / 責務】<br> イベント内の従業員リストが null の場合でも、三項演算子の null ガードを通り、 NullPointerException を発生させることなく
     * UseCase へ委譲されることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - 従業員リストが null の {@link AttendanceIrregularityEvent} を生成。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> -
     * 例外なく正常終了し、{@code notifyAttendanceIrregularityUseCase.execute(event)} が呼び出されること。
     * </p>
     */
    @Test
    @DisplayName("従業員リストがnullの場合でもnull安全にログ件数を処理し、UseCaseへ委譲されること")
    public void consume_WhenEmployeesIsNull_ShouldDelegateToUseCaseSafely() {
      // Arrange
      AttendanceIrregularityEvent event = new AttendanceIrregularityEvent("2026-09", null);

      // Act
      sut.consume(event, "attendance-irregularity-topic", 1, 200L);

      // Assert
      verify(notifyAttendanceIrregularityUseCase, times(1)).execute(event);
    }

    /**
     * 【テスト観点 / 責務】<br> 従業員リストが空コレクション（0件）のイベントを受信した際、 正常に UseCase へ委譲されることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - 従業員リストが空の {@link AttendanceIrregularityEvent} を生成。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - {@code notifyAttendanceIrregularityUseCase.execute(event)}
     * が呼び出されること。
     * </p>
     */
    @Test
    @DisplayName("従業員リストが空の場合、件数0として正常にUseCaseへ委譲されること")
    public void consume_WhenEmployeesIsEmpty_ShouldDelegateToUseCase() {
      // Arrange
      AttendanceIrregularityEvent event = new AttendanceIrregularityEvent("2026-09",
          Collections.emptyList());

      // Act
      sut.consume(event, "attendance-irregularity-topic", 0, 300L);

      // Assert
      verify(notifyAttendanceIrregularityUseCase, times(1)).execute(event);
    }

    /**
     * 【テスト観点 / 責務】<br> UseCase の実行中に {@link RuntimeException} が発生した場合、 コンシューマー層で握りつぶされず、Spring
     * Kafka のリトライ／DLT 機構に委ねるために例外がそのまま再スローされることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - UseCase が呼び出された際に {@link RuntimeException} をスローするようにモック設定。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - メソッド呼び出し元へ同一の例外が伝播すること。
     * </p>
     */
    @Test
    @DisplayName("UseCaseで例外が発生した場合、上位（Spring Kafka）へ例外が伝播すること")
    public void consume_WhenUseCaseThrowsException_ShouldPropagateException() {
      // Arrange
      AttendanceIrregularityEvent event = new AttendanceIrregularityEvent("2026-09",
          Collections.emptyList());
      doThrow(new RuntimeException("月次勤怠集約処理エラー"))
          .when(notifyAttendanceIrregularityUseCase).execute(event);

      // Act & Assert
      assertThatThrownBy(() -> sut.consume(event, "attendance-irregularity-topic", 0, 400L))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("月次勤怠集約処理エラー");
    }
  }
}

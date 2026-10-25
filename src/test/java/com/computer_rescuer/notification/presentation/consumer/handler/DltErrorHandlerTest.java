package com.computer_rescuer.notification.presentation.consumer.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.computer_rescuer.notification.application.SendNotificationUseCase;
import com.computer_rescuer.notification.application.dto.NotificationCommand;
import com.computer_rescuer.notification.domain.model.Notification;
import com.computer_rescuer.notification.domain.model.NotificationChannelType;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.record.TimestampType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.KafkaHeaders;

/**
 * {@link DltErrorHandler} に対する実戦投入可能な単体テストクラス。
 * <p>
 * <b>テスト対象クラスの責務:</b><br>
 * Kafka のリトライ上限超過やデシリアライズ失敗により DLT（Dead Letter Topic）へ隔離された 障害レコードを集約処理し、Kafka
 * ヘッダーおよび受信生データから障害原因を特定して システム管理者の LINE WORKS 個人 DM 宛てに緊急一次報を通報します。<br>
 * 通報失敗時でもメッセージングパイプラインを巻き込んでクラッシュさせない完全なフェイルセーフ（二次障害防止）を備えます。
 * </p>
 * <p>
 * <b>テスト方針:</b><br>
 * <ul>
 *   <li>レコードが null の場合の早期リターン挙動の検証</li>
 *   <li>受信ペイロードの型（byte配列、通常オブジェクト、デシリアライズ失敗時の null）に応じた復元ロジックの検証</li>
 *   <li>例外メッセージヘッダー、FQCN ヘッダー、ヘッダー欠落時における原因抽出優先順位の検証</li>
 *   <li>LINE WORKS API の文字数制限を保護するための 300 文字切り詰め（境界値: 300文字未満、ちょうど300文字、300文字超）の検証</li>
 *   <li>LINE WORKS 連携障害時における二次障害防止（例外握りつぶし）の検証</li>
 *   <li>すべての条件分岐（C1）およびステートメント（C0）の 100% 網羅</li>
 * </ul>
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DltErrorHandler 単体テスト")
class DltErrorHandlerTest {

  @Mock
  private SendNotificationUseCase sendNotificationUseCase;

  @InjectMocks
  private DltErrorHandler sut;

  @Captor
  private ArgumentCaptor<NotificationCommand> commandCaptor;

  /**
   * テスト用の {@link ConsumerRecord} を生成するヘルパーメソッド。
   *
   * @param topic     トピック名
   * @param partition パーティション番号
   * @param offset    オフセット値
   * @param value     ペイロード
   * @param headers   Kafka レコードヘッダー
   * @return 構築された {@link ConsumerRecord}
   */
  private ConsumerRecord<Object, Object> createRecord(
      String topic,
      int partition,
      long offset,
      Object value,
      RecordHeaders headers
  ) {
    return new ConsumerRecord<>(
        topic,
        partition,
        offset,
        0L,
        TimestampType.NO_TIMESTAMP_TYPE,
        0,
        0,
        "test-key",
        value,
        headers,
        Optional.empty()
    );
  }

  @Nested
  @DisplayName("前提条件および null ガードのテスト")
  class PreconditionTest {

    /**
     * 【テスト観点 / 責務】<br> 渡された {@link ConsumerRecord} が null の場合、NullPointerException を発生させず
     * 安全に早期リターンし、通知処理を行わないことを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - 引数 record に null を指定。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - 例外なく正常終了すること。<br> -
     * {@link SendNotificationUseCase#send(NotificationCommand)} が呼び出されないこと。
     * </p>
     */
    @Test
    @DisplayName("recordがnullの場合、例外をスローせず即座にリターンすること")
    public void handleDlt_WhenRecordIsNull_ShouldReturnImmediately() {
      // Act
      sut.handleDlt(null);

      // Assert
      verify(sendNotificationUseCase, never()).send(any());
    }
  }

  @Nested
  @DisplayName("ペイロード復元と切り詰め境界値のテスト")
  class PayloadResolutionAndTruncationTest {

    /**
     * 【テスト観点 / 責務】<br> ペイロードがバイト配列（{@code byte[]}）であり、かつ 300 文字以下の短い文字列の場合、 UTF-8
     * 文字列として正しくデコードされ、切り詰め（省略記号）なしで管理者通知コマンドが構築されることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - ペイロード: JSON 文字列 "{\"message\":\"Short payload\"}" のバイト配列。<br> -
     * ヘッダー: {@link KafkaHeaders#EXCEPTION_MESSAGE} に "Validation failed" を設定。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - LINE WORKS のシステム管理者宛て個人 DM（USER 種別）通知コマンドが送信されること。<br> -
     * 通知本文にトピック名、オフセット、エラー内容、デコードされた生データが含まれること。<br> - 300 文字以下のため "...(略)" が付与されないこと。
     * </p>
     */
    @Test
    @DisplayName("byte配列のペイロードが正しく文字列復元され、300文字以内なら切り詰められないこと")
    public void handleDlt_WhenPayloadIsByteArrayAndUnderLimit_ShouldDecodeAndNotTruncate() {
      // Arrange
      String topic = "test-alert-topic";
      int partition = 1;
      long offset = 105L;
      String rawJson = "{\"message\":\"Short payload\"}";
      byte[] payloadBytes = rawJson.getBytes(StandardCharsets.UTF_8);

      RecordHeaders headers = new RecordHeaders();
      headers.add(new RecordHeader(KafkaHeaders.EXCEPTION_MESSAGE,
          "Validation failed".getBytes(StandardCharsets.UTF_8)));

      ConsumerRecord<Object, Object> record = createRecord(topic, partition, offset, payloadBytes,
          headers);

      // Act
      sut.handleDlt(record);

      // Assert
      verify(sendNotificationUseCase).send(commandCaptor.capture());
      NotificationCommand capturedCommand = commandCaptor.getValue();

      assertThat(capturedCommand.channelType()).isEqualTo(NotificationChannelType.LINE_WORKS);
      assertThat(capturedCommand.destinationType()).isEqualTo(Notification.DestinationType.USER);
      assertThat(capturedCommand.targetId()).isNull(); // null 指定でシステム管理者宛てデフォルト配送

      assertThat(capturedCommand.message())
          .contains("🚨 【Kafka DLT退避エラー】")
          .contains("・トピック: test-alert-topic")
          .contains("・オフセット: 105")
          .contains("・エラー内容: Validation failed")
          .contains("・受信データ: " + rawJson)
          .doesNotContain("...(略)");
    }

    /**
     * 【テスト観点 / 責務】<br> ペイロードがちょうど 300 文字（境界値）の場合、切り詰め（省略記号）が行われず、 完全な文字列のまま通知本文に埋め込まれることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - ペイロード: ちょうど 300 文字の文字列。<br> - ヘッダー:
     * {@link KafkaHeaders#EXCEPTION_MESSAGE} を設定。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - 受信データ部分に 300 文字全体が含まれ、末尾に "...(略)" が付与されないこと。
     * </p>
     */
    @Test
    @DisplayName("ペイロードがちょうど300文字の場合、切り詰められずに完全な文字列が出力されること")
    public void handleDlt_WhenPayloadIsExactly300Characters_ShouldNotTruncate() {
      // Arrange
      String exact300Chars = "A".repeat(300);
      RecordHeaders headers = new RecordHeaders();
      headers.add(new RecordHeader(KafkaHeaders.EXCEPTION_MESSAGE,
          "Error occurred".getBytes(StandardCharsets.UTF_8)));

      ConsumerRecord<Object, Object> record = createRecord("boundary-topic", 0, 10L, exact300Chars,
          headers);

      // Act
      sut.handleDlt(record);

      // Assert
      verify(sendNotificationUseCase).send(commandCaptor.capture());
      NotificationCommand capturedCommand = commandCaptor.getValue();

      assertThat(capturedCommand.message())
          .contains("・受信データ: " + exact300Chars)
          .doesNotContain("...(略)");
    }

    /**
     * 【テスト観点 / 責務】<br> ペイロードが 300 文字を超過（境界値: 301文字以上）している場合、 LINE WORKS のメッセージサイズ肥大化防止のため、先頭 300
     * 文字で切り詰められ末尾に "...(略)" が付与されることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - ペイロード: 350 文字の長い文字列（先頭300文字が 'X'、残り50文字が 'Y'）。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - 先頭 300 文字の 'X' と "...(略)" が含まれること。<br> - 301 文字目以降の 'Y'
     * が含まれないこと。
     * </p>
     */
    @Test
    @DisplayName("ペイロードが300文字を超える場合、先頭300文字に切り詰められて略称が付与されること")
    public void handleDlt_WhenPayloadExceeds300Characters_ShouldTruncateWithEllipsis() {
      // Arrange
      String longPayload = "X".repeat(300) + "Y".repeat(50); // 350文字
      RecordHeaders headers = new RecordHeaders();
      headers.add(new RecordHeader(KafkaHeaders.EXCEPTION_MESSAGE,
          "Overflow error".getBytes(StandardCharsets.UTF_8)));

      ConsumerRecord<Object, Object> record = createRecord("long-topic", 0, 20L, longPayload,
          headers);

      // Act
      sut.handleDlt(record);

      // Assert
      verify(sendNotificationUseCase).send(commandCaptor.capture());
      NotificationCommand capturedCommand = commandCaptor.getValue();

      String expectedTruncated = "X".repeat(300) + "...(略)";
      assertThat(capturedCommand.message())
          .contains("・受信データ: " + expectedTruncated)
          .doesNotContain("Y");
    }

    /**
     * 【テスト観点 / 責務】<br> デシリアライズ失敗等によりレコードの value（ペイロード）が null の場合、 代替メッセージ "(null / デシリアライズ失敗生データ)"
     * が安全に復元されることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - レコードの value に null を設定。<br> - デシリアライズ失敗時に Kafka
     * から付与される例外関連ヘッダーを設定。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - 受信データとして "(null / デシリアライズ失敗生データ)" が出力されること。
     * </p>
     */
    @Test
    @DisplayName("valueがnullの場合、デシリアライズ失敗用代替テキストが出力されること")
    public void handleDlt_WhenPayloadValueIsNull_ShouldUseDeserializationFailureFallback() {
      // Arrange
      RecordHeaders headers = new RecordHeaders();
      // デシリアライズ失敗時に Spring Kafka が付与するヘッダーを模擬
      headers.add(new RecordHeader("springDeserializerExceptionValue",
          "raw-bytes".getBytes(StandardCharsets.UTF_8)));
      headers.add(new RecordHeader(KafkaHeaders.EXCEPTION_MESSAGE,
          "Deserialization error".getBytes(StandardCharsets.UTF_8)));

      ConsumerRecord<Object, Object> record = createRecord("null-value-topic", 2, 30L, null,
          headers);

      // Act
      sut.handleDlt(record);

      // Assert
      verify(sendNotificationUseCase).send(commandCaptor.capture());
      NotificationCommand capturedCommand = commandCaptor.getValue();

      assertThat(capturedCommand.message())
          .contains("・受信データ: (null / デシリアライズ失敗生データ)");
    }
  }

  @Nested
  @DisplayName("例外原因ヘッダー抽出の優先順位テスト")
  class ExceptionHeaderExtractionTest {

    /**
     * 【テスト観点 / 責務】<br> KafkaHeaders.EXCEPTION_MESSAGE が存在せず、KafkaHeaders.EXCEPTION_FQCN のみが存在する場合、
     * 例外クラス名（FQCN）が原因として採用されることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - ヘッダーに
     * {@link KafkaHeaders#EXCEPTION_FQCN}（"java.lang.IllegalArgumentException"）のみを設定。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - 通知メッセージの「エラー内容」に完全修飾クラス名が出力されること。
     * </p>
     */
    @Test
    @DisplayName("EXCEPTION_MESSAGEがなくEXCEPTION_FQCNがある場合、FQCNをエラー内容として採用すること")
    public void handleDlt_WhenOnlyExceptionFqcnHeaderPresent_ShouldExtractFqcn() {
      // Arrange
      RecordHeaders headers = new RecordHeaders();
      headers.add(new RecordHeader(
          KafkaHeaders.EXCEPTION_FQCN,
          "java.lang.IllegalArgumentException".getBytes(StandardCharsets.UTF_8)
      ));

      ConsumerRecord<Object, Object> record = createRecord("fqcn-topic", 0, 1L, "payload", headers);

      // Act
      sut.handleDlt(record);

      // Assert
      verify(sendNotificationUseCase).send(commandCaptor.capture());
      NotificationCommand capturedCommand = commandCaptor.getValue();

      assertThat(capturedCommand.message())
          .contains("・エラー内容: java.lang.IllegalArgumentException");
    }

    /**
     * 【テスト観点 / 責務】<br> ヘッダーに例外関連の情報が一切存在しない場合、 「原因特定不能（例外ヘッダーなし）」のフォールバック文言が設定されることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - 例外ヘッダーを含まない空の RecordHeaders を設定。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - 通知メッセージの「エラー内容」に「原因特定不能（例外ヘッダーなし）」が出力されること。
     * </p>
     */
    @Test
    @DisplayName("例外ヘッダーが存在しない場合、原因特定不能のフォールバック文言を出力すること")
    public void handleDlt_WhenNoExceptionHeadersPresent_ShouldFallbackToUnableToIdentify() {
      // Arrange: 例外ヘッダーなし
      RecordHeaders headers = new RecordHeaders();
      ConsumerRecord<Object, Object> record = createRecord("empty-header-topic", 0, 2L, "payload",
          headers);

      // Act
      sut.handleDlt(record);

      // Assert
      verify(sendNotificationUseCase).send(commandCaptor.capture());
      NotificationCommand capturedCommand = commandCaptor.getValue();

      assertThat(capturedCommand.message())
          .contains("・エラー内容: 原因特定不能（例外ヘッダーなし）");
    }

    /**
     * 【テスト観点 / 責務】<br> {@link ConsumerRecord#headers()} 自体が null を返却する極限状態において、
     * NullPointerException を起こさず「障害情報ヘッダーなし」として安全に処理されることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - {@link ConsumerRecord} のモックを作成し、{@code headers()} で null を返却。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - 「・エラー内容: 障害情報ヘッダーなし」が出力されること。
     * </p>
     */
    @Test
    @DisplayName("headersがnullの場合、障害情報ヘッダーなしのフォールバック文言を出力すること")
    public void handleDlt_WhenHeadersIsNull_ShouldFallbackToNoHeadersMessage() {
      // Arrange
      @SuppressWarnings("unchecked")
      ConsumerRecord<Object, Object> recordMock = mock(ConsumerRecord.class);
      when(recordMock.topic()).thenReturn("null-header-topic");
      when(recordMock.partition()).thenReturn(0);
      when(recordMock.offset()).thenReturn(99L);
      when(recordMock.value()).thenReturn("dummy-val");
      when(recordMock.headers()).thenReturn(null);

      // Act
      sut.handleDlt(recordMock);

      // Assert
      verify(sendNotificationUseCase).send(commandCaptor.capture());
      NotificationCommand capturedCommand = commandCaptor.getValue();

      assertThat(capturedCommand.message())
          .contains("・エラー内容: 障害情報ヘッダーなし");
    }
  }

  @Nested
  @DisplayName("耐障害性および二次障害防止ガードのテスト")
  class FaultToleranceTest {

    /**
     * 【テスト観点 / 責務】<br> LINE WORKS への DLT 障害通知送信中に外部例外（RuntimeException 等）が発生した場合、
     * 【二次障害防止ガード】により例外が安全にキャッチされ、ハンドラー外へ例外が一切漏洩しないことを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - {@code sendNotificationUseCase.send(any())} が
     * {@link RuntimeException} をスローするように設定。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - {@code assertThatCode(...).doesNotThrowAnyException()}
     * により、完全に正常終了すること。
     * </p>
     */
    @Test
    @DisplayName("通知送信中に外部例外が発生しても二次障害防止ガードにより安全に握りつぶされること")
    public void handleDlt_WhenNotificationSendingThrowsException_ShouldCatchSafelyWithoutThrowing() {
      // Arrange
      RecordHeaders headers = new RecordHeaders();
      headers.add(new RecordHeader(KafkaHeaders.EXCEPTION_MESSAGE,
          "DB Connection Timeout".getBytes(StandardCharsets.UTF_8)));
      ConsumerRecord<Object, Object> record = createRecord("critical-topic", 0, 500L,
          "important payload", headers);

      // LINE WORKS 通信失敗を模擬
      doThrow(new RuntimeException("LINE WORKS API is completely down"))
          .when(sendNotificationUseCase).send(any());

      // Act & Assert
      assertThatCode(() -> sut.handleDlt(record))
          .doesNotThrowAnyException();

      verify(sendNotificationUseCase).send(any());
    }
  }
}

package com.computer_rescuer.notification.application.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.computer_rescuer.notification.domain.model.Notification;
import com.computer_rescuer.notification.domain.model.NotificationChannelType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link NotificationCommand} に対する実戦投入可能な単体テストクラス。
 * <p>
 * <b>テスト対象クラスの責務:</b><br>
 * Kafka トピックから受信した汎用通知要求メッセージを JSON からデシリアライズして保持し、
 * アプリケーション層で扱う通知ドメインモデル（{@link Notification}）へ安全に変換（{@link
 * NotificationCommand#toDomainModel()}）します。<br>
 * チャネル種別や宛先種別が未指定（null）の場合に、安全な既定値（LINE_WORKS、CHANNEL）を補完するファクトリ責務を有します。
 * </p>
 * <p>
 * <b>テスト方針:</b><br>
 * <ul>
 *   <li>全フィールド明示指定時におけるドメインモデルへの直接マッピング検証</li>
 *   <li>{@code channelType} が null の場合の既定値フォールバック（LINE_WORKS）検証</li>
 *   <li>{@code destinationType} が null の場合の既定値フォールバック（CHANNEL）検証</li>
 *   <li>全フィールドが null の極限境界値における安全なオブジェクト生成検証</li>
 *   <li>現在日時（{@code requestedAt}）の正確な生成・設定検証</li>
 *   <li>Jackson {@link ObjectMapper} を用いた JSON（snake_case）デシリアライズ契約の検証</li>
 *   <li>C1（分岐網羅）100% の達成</li>
 * </ul>
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationCommand 単体テスト")
class NotificationCommandTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Nested
  @DisplayName("ドメインモデル変換ロジック（toDomainModel）のテスト")
  class ToDomainModelTest {

    /**
     * 【テスト観点 / 責務】<br> 全てのパラメータが明示的に指定された場合、値が欠落・改変されることなく そのまま {@link Notification}
     * ドメインモデルへマッピングされることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - channelType: SLACK<br> - destinationType: USER<br> - targetId:
     * "U12345678"<br> - message: "緊急メンテナンスのお知らせ"
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - 変換後の {@link Notification} の各属性がコマンドの値と完全一致すること。<br> -
     * {@code requestedAt} に現在日時（実行前後の範囲内）が自動設定されていること。
     * </p>
     */
    @Test
    @DisplayName("全フィールド指定時、指定値がそのままドメインモデルに反映されること")
    public void toDomainModel_WhenAllFieldsProvided_ShouldMapDirectly() {
      // Arrange
      NotificationCommand command = new NotificationCommand(
          NotificationChannelType.SLACK,
          Notification.DestinationType.USER,
          "U12345678",
          "緊急メンテナンスのお知らせ"
      );

      LocalDateTime beforeExecution = LocalDateTime.now();

      // Act
      Notification actual = command.toDomainModel();

      LocalDateTime afterExecution = LocalDateTime.now();

      // Assert
      assertThat(actual.channelType()).isEqualTo(NotificationChannelType.SLACK);
      assertThat(actual.destinationType()).isEqualTo(Notification.DestinationType.USER);
      assertThat(actual.targetId()).isEqualTo("U12345678");
      assertThat(actual.message()).isEqualTo("緊急メンテナンスのお知らせ");

      assertThat(actual.requestedAt())
          .isNotNull()
          .isAfterOrEqualTo(beforeExecution)
          .isBeforeOrEqualTo(afterExecution);
    }

    /**
     * 【テスト観点 / 責務】<br> {@code channelType} が null の場合、デフォルト値として
     * {@link NotificationChannelType#LINE_WORKS} が 自動補完され、他の指定済みフィールドは維持されることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - channelType: null<br> - destinationType: CHANNEL<br> -
     * targetId: "room-999"<br> - message: "チャンネル宛て通知"
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - {@code actual.channelType()} が
     * {@link NotificationChannelType#LINE_WORKS} となること。<br> - 他の属性（destinationType, targetId,
     * message）が保持されていること。
     * </p>
     */
    @Test
    @DisplayName("channelTypeがnullの場合、デフォルト値としてLINE_WORKSが補完されること")
    public void toDomainModel_WhenChannelTypeIsNull_ShouldDefaultToLineWorks() {
      // Arrange
      NotificationCommand command = new NotificationCommand(
          null,
          Notification.DestinationType.CHANNEL,
          "room-999",
          "チャンネル宛て通知"
      );

      // Act
      Notification actual = command.toDomainModel();

      // Assert
      assertThat(actual.channelType()).isEqualTo(NotificationChannelType.LINE_WORKS);
      assertThat(actual.destinationType()).isEqualTo(Notification.DestinationType.CHANNEL);
      assertThat(actual.targetId()).isEqualTo("room-999");
      assertThat(actual.message()).isEqualTo("チャンネル宛て通知");
    }

    /**
     * 【テスト観点 / 責務】<br> {@code destinationType} が null の場合、デフォルト値として
     * {@link Notification.DestinationType#CHANNEL} が 自動補完され、他の指定済みフィールドは維持されることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - channelType: LINE_WORKS<br> - destinationType: null<br> -
     * targetId: "target-user"<br> - message: "宛先種別nullテスト"
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - {@code actual.destinationType()} が
     * {@link Notification.DestinationType#CHANNEL} となること。<br> - 他の属性が正しく保持されていること。
     * </p>
     */
    @Test
    @DisplayName("destinationTypeがnullの場合、デフォルト値としてCHANNELが補完されること")
    public void toDomainModel_WhenDestinationTypeIsNull_ShouldDefaultToChannel() {
      // Arrange
      NotificationCommand command = new NotificationCommand(
          NotificationChannelType.LINE_WORKS,
          null,
          "target-user",
          "宛先種別nullテスト"
      );

      // Act
      Notification actual = command.toDomainModel();

      // Assert
      assertThat(actual.channelType()).isEqualTo(NotificationChannelType.LINE_WORKS);
      assertThat(actual.destinationType()).isEqualTo(Notification.DestinationType.CHANNEL);
      assertThat(actual.targetId()).isEqualTo("target-user");
      assertThat(actual.message()).isEqualTo("宛先種別nullテスト");
    }

    /**
     * 【テスト観点 / 責務】<br> 全ての引数が null の極限境界値において、{@link NullPointerException} を発生させずに安全にドメインモデルが生成され、
     * デフォルト値（LINE_WORKS、CHANNEL、現在時刻）が適用されることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - すべてのフィールドが null の {@link NotificationCommand} を生成。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - channelType: LINE_WORKS<br> - destinationType: CHANNEL<br> -
     * targetId: null<br> - message: null<br> - requestedAt: 実行前後の現在日時が設定されていること。
     * </p>
     */
    @Test
    @DisplayName("全フィールドがnullの場合でも安全にデフォルト補完されてドメインモデルが生成されること")
    public void toDomainModel_WhenAllFieldsAreNull_ShouldDefaultAllApplicableFields() {
      // Arrange
      NotificationCommand command = new NotificationCommand(null, null, null, null);

      LocalDateTime beforeExecution = LocalDateTime.now();

      // Act
      Notification actual = command.toDomainModel();

      LocalDateTime afterExecution = LocalDateTime.now();

      // Assert
      assertThat(actual.channelType()).isEqualTo(NotificationChannelType.LINE_WORKS);
      assertThat(actual.destinationType()).isEqualTo(Notification.DestinationType.CHANNEL);
      assertThat(actual.targetId()).isNull();
      assertThat(actual.message()).isNull();

      assertThat(actual.requestedAt())
          .isNotNull()
          .isAfterOrEqualTo(beforeExecution)
          .isBeforeOrEqualTo(afterExecution);
    }
  }

  @Nested
  @DisplayName("JSON デシリアライズ契約（@JsonProperty）のテスト")
  class JsonDeserializationTest {

    /**
     * 【テスト観点 / 責務】<br> Kafka トピックから受信する snake_case 形式の JSON ペイロードが、 {@link NotificationCommand}
     * の対応するレコードコンポーネントへ正しくデシリアライズされることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - {@code "channel_type": "LINE_WORKS"}<br> -
     * {@code "destination_type": "USER"}<br> - {@code "target_id": "emp-001"}<br> -
     * {@code "message": "打刻漏れアラート"} を含む JSON 文字列。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - 各プロパティが正しくパースされ、型安全にマッピングされること。
     * </p>
     */
    @Test
    @DisplayName("snake_caseのJSON文字列から全フィールドが正しくデシリアライズされること")
    public void deserialize_FromJsonWithSnakeCase_ShouldPopulateAllFields() throws Exception {
      // Arrange
      String json = """
          {
            "channel_type": "LINE_WORKS",
            "destination_type": "USER",
            "target_id": "emp-001",
            "message": "打刻漏れアラート"
          }
          """;

      // Act
      NotificationCommand actual = objectMapper.readValue(json, NotificationCommand.class);

      // Assert
      assertThat(actual.channelType()).isEqualTo(NotificationChannelType.LINE_WORKS);
      assertThat(actual.destinationType()).isEqualTo(Notification.DestinationType.USER);
      assertThat(actual.targetId()).isEqualTo("emp-001");
      assertThat(actual.message()).isEqualTo("打刻漏れアラート");
    }

    /**
     * 【テスト観点 / 責務】<br> 任意フィールド（channel_type, destination_type, target_id）が省略された JSON を受信した場合でも、
     * エラーとならずに null としてデシリアライズされ、その後の {@link NotificationCommand#toDomainModel()} で
     * 正常にデフォルト値補完が行えることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - message のみを含む最小構成の JSON 文字列。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - デシリアライズ直後の省略項目が null であること。<br> - {@code toDomainModel()} 実行時に
     * LINE_WORKS / CHANNEL が補完されること。
     * </p>
     */
    @Test
    @DisplayName("最小限のJSONからデシリアライズ後、toDomainModelで安全にデフォルト補完されること")
    public void deserialize_FromMinimalJson_ShouldLeaveOptionalFieldsNullAndDefaultSuccessfully()
        throws Exception {
      // Arrange
      String json = """
          {
            "message": "最小ペイロード"
          }
          """;

      // Act
      NotificationCommand command = objectMapper.readValue(json, NotificationCommand.class);
      Notification domainModel = command.toDomainModel();

      // Assert
      assertThat(command.channelType()).isNull();
      assertThat(command.destinationType()).isNull();
      assertThat(command.targetId()).isNull();
      assertThat(command.message()).isEqualTo("最小ペイロード");

      // ドメインモデル変換後の検証
      assertThat(domainModel.channelType()).isEqualTo(NotificationChannelType.LINE_WORKS);
      assertThat(domainModel.destinationType()).isEqualTo(Notification.DestinationType.CHANNEL);
      assertThat(domainModel.targetId()).isNull();
      assertThat(domainModel.message()).isEqualTo("最小ペイロード");
    }
  }
}

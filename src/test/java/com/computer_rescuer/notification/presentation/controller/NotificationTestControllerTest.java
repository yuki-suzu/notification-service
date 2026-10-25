package com.computer_rescuer.notification.presentation.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.computer_rescuer.notification.application.SendNotificationUseCase;
import com.computer_rescuer.notification.application.dto.NotificationCommand;
import com.computer_rescuer.notification.domain.model.Notification;
import com.computer_rescuer.notification.domain.model.NotificationChannelType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * {@link NotificationTestController} に対する実戦投入可能な単体テストクラス。
 * <p>
 * <b>テスト対象クラスの責務:</b><br>
 * LINE WORKS 通知の疎通確認および手動テスト用 REST エンドポイント（{@code /api/v1/notifications/test/*}）を提供し、
 * クライアントからのリクエストパラメータを検証・解釈して管理者チャンネルまたはシステム管理者宛ての {@link NotificationCommand}
 * を構築し、{@link SendNotificationUseCase} へディスパッチします。
 * </p>
 * <p>
 * <b>テスト方針:</b><br>
 * <ul>
 *   <li>Spring Context を起動しない（{@code @SpringBootTest} を排除）純粋な単体テストとして設計</li>
 *   <li>{@link MockMvcBuilders#standaloneSetup(Object...)} による超高速な HTTP 契約（ルーティング、ステータスコード、レスポンス本文）の検証</li>
 *   <li>{@code @RequestParam} の明示的指定時および未指定時の既定値（{@code defaultValue}）補完挙動の検証</li>
 *   <li>ディスパッチされる {@link NotificationCommand} のチャネル種別、宛先種別、宛先ID、メッセージの正確性検証</li>
 *   <li>ユースケース層で例外が発生した場合の透過的伝播検証</li>
 *   <li>コントローラーメソッドの直接呼び出し（POJOユニットテスト）による網羅性の担保</li>
 *   <li>C1（分岐網羅）100% の達成</li>
 * </ul>
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationTestController 単体テスト")
class NotificationTestControllerTest {

  @Mock
  private SendNotificationUseCase sendNotificationUseCase;

  @InjectMocks
  private NotificationTestController sut;

  @Captor
  private ArgumentCaptor<NotificationCommand> commandCaptor;

  private MockMvc mockMvc;

  /**
   * 各テスト実行前の共通初期化処理。
   * <p>
   * Spring コンテキストを起動せず、テスト対象コントローラーのみを組み込んだスタンドアロン MockMvc を構築します。
   * </p>
   */
  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(sut).build();
  }

  @Nested
  @DisplayName("管理者チャンネル向けアラート通知テスト (/alert) のテスト")
  class TestAlertEndpointTest {

    /**
     * 【テスト観点 / 責務】<br> {@code /alert} エンドポイントに対してメッセージパラメータを指定して POST リクエストを送信した際、 HTTP 200 OK
     * が返却され、指定メッセージを含んだ CHANNEL 宛て通知コマンドが UseCase へ発行されることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - リクエストパラメータ {@code message = "手動アラートテストです"} を付与した POST
     * リクエストを準備。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - レスポンスステータスが 200 OK であること。<br> -
     * レスポンス本文が「アラートチャンネルへ送信完了じゃ！」であること。<br> - UseCase へ渡されたコマンドの {@code channelType} が
     * LINE_WORKS、{@code destinationType} が CHANNEL であること。<br> - コマンドの {@code targetId} が
     * null、{@code message} が指定文字列と一致すること。
     * </p>
     */
    @Test
    @DisplayName("メッセージ指定時、200 OKを返却し指定本文のCHANNEL宛て通知コマンドを発行すること")
    public void testAlert_WithMessageParam_ShouldReturnOkAndDispatchChannelCommand()
        throws Exception {
      // Arrange
      String customMessage = "手動アラートテストです";

      // Act & Assert
      mockMvc.perform(post("/api/v1/notifications/test/alert")
              .param("message", customMessage))
          .andExpect(status().isOk())
          .andExpect(content().string("アラートチャンネルへ送信完了じゃ！"));

      verify(sendNotificationUseCase, times(1)).send(commandCaptor.capture());
      NotificationCommand captured = commandCaptor.getValue();

      assertThat(captured.channelType()).isEqualTo(NotificationChannelType.LINE_WORKS);
      assertThat(captured.destinationType()).isEqualTo(Notification.DestinationType.CHANNEL);
      assertThat(captured.targetId()).isNull();
      assertThat(captured.message()).isEqualTo(customMessage);
    }

    /**
     * 【テスト観点 / 責務】<br> {@code /alert} エンドポイント呼び出し時にメッセージパラメータを省略した場合、 デフォルト値（"🤖
     * [NotificationService] テスト通知ですじゃ！"）が適用されて UseCase へ発行されることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - パラメータなしで {@code /api/v1/notifications/test/alert} へ POST
     * リクエストを送信。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - レスポンスステータスが 200 OK であること。<br> - コマンドのメッセージ本文に定型文「🤖
     * [NotificationService] テスト通知ですじゃ！」が補完されていること。
     * </p>
     */
    @Test
    @DisplayName("メッセージ省略時、既定の定型文メッセージでCHANNEL宛て通知コマンドを発行すること")
    public void testAlert_WithoutMessageParam_ShouldApplyDefaultMessage() throws Exception {
      // Act & Assert
      mockMvc.perform(post("/api/v1/notifications/test/alert"))
          .andExpect(status().isOk())
          .andExpect(content().string("アラートチャンネルへ送信完了じゃ！"));

      verify(sendNotificationUseCase, times(1)).send(commandCaptor.capture());
      NotificationCommand captured = commandCaptor.getValue();

      assertThat(captured.channelType()).isEqualTo(NotificationChannelType.LINE_WORKS);
      assertThat(captured.destinationType()).isEqualTo(Notification.DestinationType.CHANNEL);
      assertThat(captured.targetId()).isNull();
      assertThat(captured.message()).isEqualTo("🤖 [NotificationService] テスト通知ですじゃ！");
    }

    /**
     * 【テスト観点 / 責務】<br> コントローラーの {@code testAlert} メソッドを直接呼び出した場合でも、 正しい {@link ResponseEntity}
     * が返却され、UseCase が適切に呼び出されることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - メソッド引数に直接 "直接テスト" を渡す。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - 返却された ResponseEntity のステータスが 200 OK、本文が期待文字列と一致すること。
     * </p>
     */
    @Test
    @DisplayName("Javaメソッド直接呼び出し時、正常なResponseEntityを返却すること")
    public void testAlert_DirectMethodCall_ShouldReturnResponseEntityOk() {
      // Act
      ResponseEntity<String> response = sut.testAlert("直接テスト");

      // Assert
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isEqualTo("アラートチャンネルへ送信完了じゃ！");

      verify(sendNotificationUseCase, times(1)).send(commandCaptor.capture());
      assertThat(commandCaptor.getValue().message()).isEqualTo("直接テスト");
    }

    /**
     * 【テスト観点 / 責務】<br> UseCase 側で外部通信障害等の {@link RuntimeException} が発生した場合、
     * コントローラー内で不当に握りつぶされず、例外が上位へ伝播することを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - {@code sendNotificationUseCase.send} が {@link RuntimeException}
     * をスローするように設定。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - コントローラーメソッド呼び出しにより同一の例外が再スローされること。
     * </p>
     */
    @Test
    @DisplayName("UseCaseで例外が発生した場合、例外がそのまま伝播すること")
    public void testAlert_WhenUseCaseThrowsException_ShouldPropagateException() {
      // Arrange
      doThrow(new RuntimeException("LINE WORKS ゲートウェイ障害"))
          .when(sendNotificationUseCase).send(any());

      // Act & Assert
      assertThatThrownBy(() -> sut.testAlert("障害テスト"))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("LINE WORKS ゲートウェイ障害");
    }
  }

  @Nested
  @DisplayName("システム管理者向け障害通知テスト (/error) のテスト")
  class TestErrorEndpointTest {

    /**
     * 【テスト観点 / 責務】<br> {@code /error} エンドポイントに対してメッセージを指定して POST リクエストを送信した際、 HTTP 200 OK
     * が返却され、USER（個人DM）宛ての通知コマンドが UseCase へ発行されることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - リクエストパラメータ {@code message = "手動障害通知テストです"} を付与。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - レスポンスステータスが 200 OK であること。<br> -
     * レスポンス本文が「システム管理者へ送信完了じゃ！」であること。<br> - UseCase へ渡されたコマンドの {@code destinationType} が USER
     * であること。<br> - コマンドの {@code message} が指定文字列と一致すること。
     * </p>
     */
    @Test
    @DisplayName("メッセージ指定時、200 OKを返却し指定本文のUSER宛て通知コマンドを発行すること")
    public void testError_WithMessageParam_ShouldReturnOkAndDispatchUserCommand() throws Exception {
      // Arrange
      String customMessage = "手動障害通知テストです";

      // Act & Assert
      mockMvc.perform(post("/api/v1/notifications/test/error")
              .param("message", customMessage))
          .andExpect(status().isOk())
          .andExpect(content().string("システム管理者へ送信完了じゃ！"));

      verify(sendNotificationUseCase, times(1)).send(commandCaptor.capture());
      NotificationCommand captured = commandCaptor.getValue();

      assertThat(captured.channelType()).isEqualTo(NotificationChannelType.LINE_WORKS);
      assertThat(captured.destinationType()).isEqualTo(Notification.DestinationType.USER);
      assertThat(captured.targetId()).isNull();
      assertThat(captured.message()).isEqualTo(customMessage);
    }

    /**
     * 【テスト観点 / 責務】<br> {@code /error} エンドポイント呼び出し時にメッセージパラメータを省略した場合、 既定値（"🚨 [NotificationService]
     * テスト障害通知ですじゃ！"）が適用されて UseCase へ発行されることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - パラメータなしで {@code /api/v1/notifications/test/error} へ POST
     * リクエストを送信。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - レスポンスステータスが 200 OK であること。<br> - コマンドのメッセージ本文に定型文「🚨
     * [NotificationService] テスト障害通知ですじゃ！」が補完されていること。
     * </p>
     */
    @Test
    @DisplayName("メッセージ省略時、既定の定型文メッセージでUSER宛て通知コマンドを発行すること")
    public void testError_WithoutMessageParam_ShouldApplyDefaultMessage() throws Exception {
      // Act & Assert
      mockMvc.perform(post("/api/v1/notifications/test/error"))
          .andExpect(status().isOk())
          .andExpect(content().string("システム管理者へ送信完了じゃ！"));

      verify(sendNotificationUseCase, times(1)).send(commandCaptor.capture());
      NotificationCommand captured = commandCaptor.getValue();

      assertThat(captured.channelType()).isEqualTo(NotificationChannelType.LINE_WORKS);
      assertThat(captured.destinationType()).isEqualTo(Notification.DestinationType.USER);
      assertThat(captured.targetId()).isNull();
      assertThat(captured.message()).isEqualTo("🚨 [NotificationService] テスト障害通知ですじゃ！");
    }

    /**
     * 【テスト観点 / 責務】<br> コントローラーの {@code testError} メソッドを直接呼び出した場合でも、 正しい {@link ResponseEntity}
     * が返却され、USER 宛てに UseCase が適切に呼び出されることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - メソッド引数に直接 "直接エラーテスト" を渡す。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - 返却された ResponseEntity のステータスが 200 OK、本文が期待文字列と一致すること。
     * </p>
     */
    @Test
    @DisplayName("Javaメソッド直接呼び出し時、正常なResponseEntityを返却すること")
    public void testError_DirectMethodCall_ShouldReturnResponseEntityOk() {
      // Act
      ResponseEntity<String> response = sut.testError("直接エラーテスト");

      // Assert
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody()).isEqualTo("システム管理者へ送信完了じゃ！");

      verify(sendNotificationUseCase, times(1)).send(commandCaptor.capture());
      assertThat(commandCaptor.getValue().destinationType()).isEqualTo(
          Notification.DestinationType.USER);
      assertThat(commandCaptor.getValue().message()).isEqualTo("直接エラーテスト");
    }

    /**
     * 【テスト観点 / 責務】<br> UseCase 側で例外が発生した場合に、そのまま上位へ透過的に再スローされることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - {@code sendNotificationUseCase.send} が
     * {@link IllegalArgumentException} をスローするように設定。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - メソッド呼び出し元へ同一の例外がスローされること。
     * </p>
     */
    @Test
    @DisplayName("UseCaseで例外が発生した場合、例外がそのまま伝播すること")
    public void testError_WhenUseCaseThrowsException_ShouldPropagateException() {
      // Arrange
      doThrow(new IllegalArgumentException("未対応チャネルエラー"))
          .when(sendNotificationUseCase).send(any());

      // Act & Assert
      assertThatThrownBy(() -> sut.testError("障害テスト"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("未対応チャネルエラー");
    }
  }
}

package com.computer_rescuer.notification.infrastructure.lineworks.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.computer_rescuer.notification.infrastructure.lineworks.model.LineworksTextMessageRequest;
import com.computer_rescuer.notification.infrastructure.property.LineworksProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link LineworksMessageApi} に対する実戦投入可能な単体テストクラス。
 * <p>
 * <b>テスト対象クラスの責務:</b><br>
 * LINE WORKS Message API (API 2.0) 呼び出しに特化した専用アダプターとして機能します。<br>
 * <ul>
 *   <li>ユーザー宛て個人 DM 送信用エンドポイント（{@code /bots/{botId}/users/{userId}/messages}）の動的構築</li>
 *   <li>トークルーム（チャンネル）送信用エンドポイント（{@code /bots/{botId}/channels/{channelId}/messages}）の動的構築</li>
 *   <li>プレーンテキストメッセージから型安全な JSON リクエストモデル（{@link LineworksTextMessageRequest}）へのカプセル化</li>
 *   <li>共通通信基盤（{@link LineworksCoreHttpClient}）への適切な引数（URI、リクエストボディ、API識別名）による委譲</li>
 * </ul>
 * </p>
 * <p>
 * <b>テスト方針:</b><br>
 * 通信基盤である {@link LineworksCoreHttpClient} をモック化し、
 * 正しい URL パス、ネストされた JSON モデルの構造、および API 識別名が渡されているかを {@link ArgumentCaptor} を用いて厳密に検証します。<br>
 * また、通信基盤層で例外が発生した際に呼び出し元へ安全に透過伝播されることを確認します。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LineworksMessageApi 単体テスト")
class LineworksMessageApiTest {

  @Mock
  private LineworksCoreHttpClient coreClient;

  @Captor
  private ArgumentCaptor<String> pathCaptor;

  @Captor
  private ArgumentCaptor<LineworksTextMessageRequest> requestBodyCaptor;

  @Captor
  private ArgumentCaptor<String> apiNameCaptor;

  private LineworksMessageApi sut;

  private final LineworksProperties properties = new LineworksProperties(
      "https://api.worksmobile.com/r/dummy",
      "dummy-client-id",
      "dummy-client-secret",
      "dummy-service-account",
      "dummy-private-key",
      "bot-12345",
      "channel-default",
      "manager-default"
  );

  /**
   * 各テスト実行前の共通初期化処理。
   */
  @BeforeEach
  void setUp() {
    sut = new LineworksMessageApi(coreClient, properties);
  }

  @Nested
  @DisplayName("ユーザー宛てメッセージ送信（sendTextMessage）のテスト")
  class SendTextMessageTest {

    /**
     * 【テスト観点 / 責務】<br> 指定されたユーザー ID とメッセージ本文から正しい URL パスおよびテキストリクエストモデルが生成され、
     * {@link LineworksCoreHttpClient#post(String, Object, String)} へ委譲されることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - botId: "bot-12345"<br> - userId: "user-abc-999"<br> - message:
     * "打刻リマインドです。"
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - URL パスが {@code "/bots/bot-12345/users/user-abc-999/messages"}
     * であること。<br> - リクエストボディの {@code content.type()} が {@code "text"}、{@code content.text()}
     * がメッセージ本文と一致すること。<br> - API 識別名が {@code "テキストメッセージ送信"} として渡されること。
     * </p>
     */
    @Test
    @DisplayName("ユーザーIDとメッセージから正しいパスとリクエストボディを構築してPOST委譲すること")
    public void sendTextMessage_WhenValidInput_ShouldBuildCorrectPathAndBody() {
      // Arrange
      String userId = "user-abc-999";
      String message = "打刻リマインドです。";

      // Act
      sut.sendTextMessage(userId, message);

      // Assert
      verify(coreClient, times(1)).post(
          pathCaptor.capture(),
          requestBodyCaptor.capture(),
          apiNameCaptor.capture()
      );

      assertThat(pathCaptor.getValue()).isEqualTo("/bots/bot-12345/users/user-abc-999/messages");
      assertThat(apiNameCaptor.getValue()).isEqualTo("テキストメッセージ送信");

      LineworksTextMessageRequest actualRequest = requestBodyCaptor.getValue();
      assertThat(actualRequest).isNotNull();
      assertThat(actualRequest.content()).isNotNull();
      assertThat(actualRequest.content().type()).isEqualTo("text");
      assertThat(actualRequest.content().text()).isEqualTo("打刻リマインドです。");
    }

    /**
     * 【テスト観点 / 責務】<br> メッセージ本文が空文字または null の境界値であっても、例外を発生させずに 型安全にリクエストモデルを構築して委譲できることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - message: null
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - リクエストモデルの {@code content.text()} が null の状態で構築され、委譲されること。
     * </p>
     */
    @Test
    @DisplayName("メッセージ本文がnullの場合でも安全にリクエストボディが構築されること")
    public void sendTextMessage_WhenMessageIsNull_ShouldBuildRequestWithNullText() {
      // Arrange
      String userId = "user-001";

      // Act
      sut.sendTextMessage(userId, null);

      // Assert
      verify(coreClient, times(1)).post(
          eq("/bots/bot-12345/users/user-001/messages"),
          requestBodyCaptor.capture(),
          eq("テキストメッセージ送信")
      );

      LineworksTextMessageRequest actualRequest = requestBodyCaptor.getValue();
      assertThat(actualRequest.content().type()).isEqualTo("text");
      assertThat(actualRequest.content().text()).isNull();
    }

    /**
     * 【テスト観点 / 責務】<br> 通信基盤層（{@link LineworksCoreHttpClient}）で例外が発生した場合、
     * メソッド内で例外を握りつぶさずにそのまま呼び出し元へ再スローすることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - {@code coreClient.post} が {@link RuntimeException}（"LINE WORKS
     * API の通信エラー"）をスローするように設定。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - {@link RuntimeException} がそのままスローされること。<br> -
     * エラーメッセージが保持されていること。
     * </p>
     */
    @Test
    @DisplayName("通信基盤で例外が発生した場合、上位へ透過的に再スローされること")
    public void sendTextMessage_WhenCoreClientThrowsException_ShouldPropagateException() {
      // Arrange
      doThrow(new RuntimeException("LINE WORKS API の通信エラー"))
          .when(coreClient)
          .post(anyString(), any(), anyString());

      // Act & Assert
      assertThatThrownBy(() -> sut.sendTextMessage("user-001", "テスト"))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("LINE WORKS API の通信エラー");

      verify(coreClient, times(1)).post(anyString(), any(), anyString());
    }
  }

  @Nested
  @DisplayName("トークルーム宛てメッセージ送信（sendChannelMessage）のテスト")
  class SendChannelMessageTest {

    /**
     * 【テスト観点 / 責務】<br> 指定されたトークルーム（チャンネル）ID とメッセージ本文から正しい URL パスおよびテキストリクエストモデルが生成され、
     * {@link LineworksCoreHttpClient#post(String, Object, String)} へ委譲されることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - botId: "bot-12345"<br> - channelId: "ch-alert-room-888"<br> -
     * message: "📢 【管理者レポート】未打刻DM配信が完了しました。"
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - URL パスが
     * {@code "/bots/bot-12345/channels/ch-alert-room-888/messages"} であること。<br> - リクエストボディの
     * {@code content.type()} が {@code "text"}、{@code content.text()} がメッセージ本文と一致すること。<br> - API
     * 識別名が {@code "トークルームへのメッセージ送信"} として渡されること。
     * </p>
     */
    @Test
    @DisplayName("チャンネルIDとメッセージから正しいパスとリクエストボディを構築してPOST委譲すること")
    public void sendChannelMessage_WhenValidInput_ShouldBuildCorrectPathAndBody() {
      // Arrange
      String channelId = "ch-alert-room-888";
      String message = "📢 【管理者レポート】未打刻DM配信が完了しました。";

      // Act
      sut.sendChannelMessage(channelId, message);

      // Assert
      verify(coreClient, times(1)).post(
          pathCaptor.capture(),
          requestBodyCaptor.capture(),
          apiNameCaptor.capture()
      );

      assertThat(pathCaptor.getValue()).isEqualTo(
          "/bots/bot-12345/channels/ch-alert-room-888/messages");
      assertThat(apiNameCaptor.getValue()).isEqualTo("トークルームへのメッセージ送信");

      LineworksTextMessageRequest actualRequest = requestBodyCaptor.getValue();
      assertThat(actualRequest).isNotNull();
      assertThat(actualRequest.content()).isNotNull();
      assertThat(actualRequest.content().type()).isEqualTo("text");
      assertThat(actualRequest.content().text()).isEqualTo(
          "📢 【管理者レポート】未打刻DM配信が完了しました。");
    }

    /**
     * 【テスト観点 / 責務】<br> 改行文字や特殊文字を含む複数行の長文メッセージであっても、改変されることなく そのままリクエストボディに格納されて委譲されることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - 改行を含む複数行のアラート文面。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - 改行文字を含んだ文字列が完全に維持されてリクエストモデルへ設定されること。
     * </p>
     */
    @Test
    @DisplayName("改行を含む複数行メッセージでも本文が正確に維持されて委譲されること")
    public void sendChannelMessage_WhenMultilineMessage_ShouldPreserveExactText() {
      // Arrange
      String channelId = "ch-test";
      String multilineMessage = "1行目：警告\n2行目：打刻漏れ一覧\n  ・山田 太郎";

      // Act
      sut.sendChannelMessage(channelId, multilineMessage);

      // Assert
      verify(coreClient, times(1)).post(
          eq("/bots/bot-12345/channels/ch-test/messages"),
          requestBodyCaptor.capture(),
          eq("トークルームへのメッセージ送信")
      );

      assertThat(requestBodyCaptor.getValue().content().text()).isEqualTo(multilineMessage);
    }

    /**
     * 【テスト観点 / 責務】<br> トークルーム送信処理中に通信基盤層で例外が発生した場合、 メソッド内で握りつぶさずに呼び出し元へ例外を再スローすることを確認します。
     * <p>
     * 【事前条件（Given / Arrange）】<br> - {@code coreClient.post} が {@link IllegalStateException} をスロー。
     * </p>
     * <p>
     * 【期待結果（Then / Assert）】<br> - {@link IllegalStateException} がそのまま上位へ伝播すること。
     * </p>
     */
    @Test
    @DisplayName("チャンネル送信で通信例外が発生した場合、上位へ透過的に再スローされること")
    public void sendChannelMessage_WhenCoreClientThrowsException_ShouldPropagateException() {
      // Arrange
      doThrow(new IllegalStateException("ネットワーク切断"))
          .when(coreClient)
          .post(anyString(), any(), anyString());

      // Act & Assert
      assertThatThrownBy(() -> sut.sendChannelMessage("ch-001", "テスト"))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("ネットワーク切断");

      verify(coreClient, times(1)).post(anyString(), any(), anyString());
    }
  }
}

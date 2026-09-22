package com.computer_rescuer.notification.infrastructure.lineworks.client;

import com.computer_rescuer.notification.infrastructure.lineworks.model.LineworksTextMessageRequest;
import com.computer_rescuer.notification.infrastructure.property.LineworksProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * LINE WORKS の Message API (API 2.0) 呼び出しに特化した専用クライアント（Adapter）。
 * <p>
 * このクラスは、Application 層（ユースケースや他の Adapter）から呼び出され、 「指定されたユーザーにメッセージを送る」というビジネスの要求を、 LINE WORKS API
 * の具体的な HTTP リクエスト（エンドポイントの組み立て、JSON 変換）に翻訳します。<br> 認証トークンの管理や HTTP 通信の異常系ハンドリングは
 * {@link LineworksCoreHttpClient} に委譲しているため、 本クラスは「メッセージ仕様の組み立て」に専念します。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LineworksMessageApi {

  /**
   * 共通の HTTP 通信・トークン管理基盤
   */
  private final LineworksCoreHttpClient coreClient;
  /**
   * LINE WORKS の設定値（Bot ID 等）を保持するプロパティ
   */
  private final LineworksProperties properties;

  /**
   * 指定したユーザーに対して、プレーンテキストのメッセージを送信します。
   * <p>
   * 内部で {@link LineworksCoreHttpClient} を呼び出すため、アクセストークンの付与や、 トークン失効時の自動リトライは透過的（自動的）に行われます。
   * 呼び出し元はトークンの状態を気にする必要はありません。
   * </p>
   *
   * @param userId  送信先の対象ユーザー ID（LINE WORKS 内部で一意に割り振られた ID）
   * @param message 送信するテキストメッセージの内容
   * @throws RuntimeException 通信エラーや LINE WORKS 側でのバリデーションエラーが発生した場合
   */
  public void sendTextMessage(String userId, String message) {
    // API 2.0 の仕様に基づくメッセージ送信エンドポイントの組み立て
    // 例: /bots/12345/users/user-abc-123/messages
    String path = String.format("/bots/%s/users/%s/messages", properties.botId(), userId);

    // 送信用の型安全な JSON モデル（Record）を生成
    LineworksTextMessageRequest requestBody = LineworksTextMessageRequest.ofText(message);

    log.debug("ユーザー '{}' に対する LINE WORKS メッセージ送信処理を開始します。", userId);

    // トークン管理機能を持つ CoreClient を経由して POST リクエストを実行
    coreClient.post(path, requestBody, "テキストメッセージ送信");

    log.info("✅ LINE WORKS メッセージの送信に成功しました。宛先ユーザーID: {}", userId);
  }

  /**
   * 指定したトークルーム（チャンネル）に対して、テキストメッセージを送信します。
   * <p>
   * 管理者向けのサマリーレポートや、チーム全体へのシステム通知などに使用します。
   * </p>
   *
   * @param channelId 送信先のトークルームID（Channel ID）
   * @param message   送信するテキストメッセージの内容
   */
  public void sendChannelMessage(String channelId, String message) {
    // API 2.0 のチャンネル（トークルーム）向け送信エンドポイント
    // 例: /bots/12345/channels/ch-abc-123/messages
    String path = String.format("/bots/%s/channels/%s/messages", properties.botId(), channelId);

    LineworksTextMessageRequest requestBody = LineworksTextMessageRequest.ofText(message);

    log.debug("トークルーム '{}' に対する LINE WORKS メッセージ送信処理を開始します。", channelId);

    // 既存のCoreClientをそのまま使い回す！
    coreClient.post(path, requestBody, "トークルームへのメッセージ送信");

    log.info("✅ LINE WORKS トークルームへの送信に成功しました。Channel ID: {}", channelId);
  }
}

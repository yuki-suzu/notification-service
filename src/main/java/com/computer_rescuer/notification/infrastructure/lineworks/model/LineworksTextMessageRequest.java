package com.computer_rescuer.notification.infrastructure.lineworks.model;

/**
 * LINE WORKS の Message API (API 2.0) に対して送信する「テキストメッセージ」のリクエストボディを表現する Record。
 * <p>
 * LINE WORKS の API 仕様上、単純な文字列ではなく、メッセージのタイプ（text, image, button_template 等）を 示す {@code type}
 * フィールドを含んだネスト構造（JSON）を要求されるため、このクラスでその構造をカプセル化します。
 * </p>
 *
 * @param content 送信するメッセージの具体的な種別と内容を保持するオブジェクト
 */
public record LineworksTextMessageRequest(Content content) {

  /**
   * 最も基本的なテキストメッセージのリクエストオブジェクトを生成するファクトリメソッド。
   * <p>
   * 呼び出し元が LINE WORKS 固有のデータ構造（Content 等）を意識することなく、 送りたい文字列を渡すだけで適切なリクエストボディが組み上がるように設計されています。
   * </p>
   *
   * @param text 送信したいプレーンテキストのメッセージ
   * @return 構築済みのテキストメッセージリクエストオブジェクト
   */
  public static LineworksTextMessageRequest ofText(String text) {
    return new LineworksTextMessageRequest(new Content("text", text));
  }

  /**
   * LINE WORKS メッセージのコンテンツ部分を表現する内部 Record。
   *
   * @param type メッセージの種別。テキストの場合は常に "text" となります。
   * @param text 実際にユーザーの画面に表示される文字列
   */
  public record Content(String type, String text) {

  }
}

package com.computer_rescuer.notification.infrastructure.lineworks.support;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * LINE WORKS のアカウント識別子（ユーザーID）を解決するサポートクラス。
 * <p>
 * 社内運用ルール（LINE WORKS のアカウント ID が社用メールアドレスの末尾 {@code .com} を除いた文字列となっている仕様）を カプセル化し、標準的なメールアドレスから
 * LINE WORKS API 用の宛先識別子への変換を担当します。<br> 将来的に Slack や他のチャットツールが増加した場合でも、各ツールのインフラパッケージ配下に 同様の
 * Resolver / Support を配置することで、プラットフォーム固有の変換ルールを隔離できます。
 * </p>
 */
@Component
public class LineworksAccountResolver {

  /**
   * 社用メールアドレスから LINE WORKS の宛先ユーザー ID を導出します。
   * <p>
   * 入力されたメールアドレスの末尾が {@code .com} で終わる場合、その拡張子部分を除去した文字列を返却します。 入力値が空文字または null
   * の場合は、空文字を返却して安全性を担保します。
   * </p>
   *
   * @param email 変換元の社用メールアドレス（例: "suzuki.taro@example.com"）
   * @return 導出された LINE WORKS ユーザー ID（例: "suzuki.taro@example"）。email が空の場合は空文字列
   */
  public String resolveUserIdFromEmail(String email) {
    if (!StringUtils.hasText(email)) {
      return "";
    }
    // 末尾の .com を安全に除去（大文字小文字を許容）
    return email.replaceFirst("(?i)\\.com$", "");
  }
}

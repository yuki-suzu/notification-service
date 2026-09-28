package com.computer_rescuer.notification.infrastructure.lineworks.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

/**
 * LINE WORKS の Directory API（API 2.0）を利用してユーザー情報の取得・存在確認を行う API クライアント。
 * <p>
 * {@link LineworksCoreHttpClient} を経由して通信を行い、ユーザー ID に基づくプロファイル情報の解決を担当します。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LineworksUserApi {

  private final LineworksCoreHttpClient coreClient;

  /**
   * LINE WORKS のユーザー ID を指定してユーザープロファイルを取得します。
   * <p>
   * 該当するユーザーが存在しない場合（404 Not Found）は空の {@link Optional} を返却します。
   * </p>
   *
   * @param userId 検索対象の LINE WORKS ユーザー ID
   * @return 取得されたユーザー情報の {@link Optional}。存在しない場合は空
   */
  public Optional<LineworksUserResponse> fetchUser(String userId) {
    String path = String.format("/users/%s", userId);
    log.debug("LINE WORKS ユーザー存在確認 API 呼び出し: userId={}", userId);

    try {
      LineworksUserResponse response = coreClient.get(path, LineworksUserResponse.class,
          "ユーザー情報取得");
      return Optional.ofNullable(response);

    } catch (HttpClientErrorException.NotFound e) {
      log.warn("⚠️ LINE WORKS 上にユーザーが存在しません（404 Not Found）: userId={}", userId);
      return Optional.empty();

    } catch (Exception e) {
      log.error("❌ LINE WORKS ユーザー取得エラー: userId={}, エラー={}", userId, e.getMessage());
      return Optional.empty();
    }
  }

  /**
   * LINE WORKS User API のレスポンスをマッピングするレコード。
   *
   * @param userId   ユーザー ID
   * @param userName 姓名オブジェクト
   * @param nickname ニックネーム
   */
  public record LineworksUserResponse(
      @JsonProperty("userId") String userId,
      @JsonProperty("userName") UserName userName,
      @JsonProperty("nickname") String nickname
  ) {

    /**
     * 通知本文の宛名として最適な表示名を取得します。
     * <p>
     * ニックネームが設定されている場合はニックネームを、未設定時は「姓 名」を返却します。
     * </p>
     *
     * @return 解決された表示名
     */
    public String resolveDisplayName() {
      if (nickname != null && !nickname.isBlank()) {
        return nickname;
      }
      if (userName != null) {
        String last = userName.lastName() != null ? userName.lastName() : "";
        String first = userName.firstName() != null ? userName.firstName() : "";
        return (last + " " + first).trim();
      }
      return "";
    }
  }

  /**
   * 姓名オブジェクト。
   *
   * @param lastName  姓
   * @param firstName 名
   */
  public record UserName(
      @JsonProperty("lastName") String lastName,
      @JsonProperty("firstName") String firstName
  ) {

  }
}

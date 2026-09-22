package com.computer_rescuer.notification.infrastructure.lineworks.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * LINE WORKS のアクセストークンをオンメモリでキャッシュ・管理するマネージャー。
 * <p>
 * トークン取得 API への無駄なリクエスト（レートリミット超過）を防ぐため、 有効なトークンを保持し、必要な場合（初回起動時や期限切れ時）のみ再取得を行います。<br>
 * スレッドセーフに動作するよう、メソッドレベルでの同期（synchronized）を行っています。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LineworksTokenManager {

  private final LineworksAuthApi authApi;

  /**
   * キャッシュされたアクセストークン
   */
  private String cachedToken = null;

  /**
   * キャッシュされたトークンを取得します。存在しない場合は新規取得を行います。
   *
   * @return 有効なアクセストークン
   */
  public synchronized String getToken() {
    if (cachedToken == null) {
      log.info("LINE WORKS トークンが存在しないため、新規取得します。");
      // bot (メッセージ送信) と directory.read (ユーザー一覧取得) の権限を要求
      cachedToken = authApi.fetchAccessToken("bot,directory.read");
    }
    return cachedToken;
  }

  /**
   * トークンの有効期限が切れた（401 エラー等が発生した）場合に呼び出され、 キャッシュを破棄した上で新しいトークンを再取得します。
   *
   * @return 新しく取得されたアクセストークン
   */
  public synchronized String refreshToken() {
    log.info("LINE WORKS トークンの有効期限が切れたと判断し、強制的に再取得します。");
    cachedToken = null;
    return getToken();
  }
}

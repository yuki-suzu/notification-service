package com.computer_rescuer.notification.application.service;

import com.computer_rescuer.notification.application.SendUnstampedDirectReminderUseCase;
import com.computer_rescuer.notification.application.dto.UnstampedDirectReminderEvent;
import com.computer_rescuer.notification.application.dto.UnstampedDirectReminderEvent.DirectReminderEmployee;
import com.computer_rescuer.notification.domain.gateway.NotificationSender;
import com.computer_rescuer.notification.domain.model.Notification;
import com.computer_rescuer.notification.domain.model.Notification.DestinationType;
import com.computer_rescuer.notification.domain.model.NotificationChannelType;
import com.computer_rescuer.notification.domain.service.DirectReminderReportFormatter;
import com.computer_rescuer.notification.domain.service.DirectReminderReportFormatter.FailureItem;
import com.computer_rescuer.notification.domain.service.UnstampedDirectReminderMessageFormatter;
import com.computer_rescuer.notification.infrastructure.lineworks.client.LineworksUserApi;
import com.computer_rescuer.notification.infrastructure.lineworks.client.LineworksUserApi.LineworksUserResponse;
import com.computer_rescuer.notification.infrastructure.lineworks.support.LineworksAccountResolver;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 勤怠未打刻者本人への個別ダイレクト通知ユースケースを処理するアプリケーションサービス。
 * <p>
 * {@link SendUnstampedDirectReminderUseCase} 入力インターフェースを実装し、
 * 勤怠管理サービスから非同期メッセージング経由で受信した未打刻従業員リストに対して、 LINE WORKS
 * によるダイレクトメッセージ（DM）の送信パイプラインをオーケストレーションします。<br> 本サービスは以下の責務を担います：
 * <ul>
 *   <li><b>代理送信モード判定:</b> イベント内に特定の代理識別用アドレス（{@code proxy@...}）が含まれる場合、
 *       本人向けDM無効設定（テスト・肩慣らし運用）と判断し、全対象者のリマインドをシステム管理者へ代理送信します。</li>
 *   <li><b>アカウント存在確認 & 表示名解決:</b> 通常モード時は、社用メールアドレスから LINE WORKS アカウント ID を導出し、
 *       さらに LINE WORKS Directory API（{@link LineworksUserApi}）を呼び出してアカウントの実在性を確認するとともに、
 *       最新の表示名（ニックネーム等）を動的に解決してメッセージ宛名へ反映します。</li>
 *   <li><b>障害フォールバック & レポート集約:</b> アカウント解決不能時はシステム管理者へエラー一次報を送信しつつ、
 *       全件完了後に管理者トークルームへ配信結果レポート（成功・失敗内訳）を一括通知します。</li>
 * </ul>
 * </p>
 */
@Slf4j
@Service
public class SendUnstampedDirectReminderService implements SendUnstampedDirectReminderUseCase {

  /**
   * 通知チャネル種別（LINE_WORKS等）をキーとする送信ゲートウェイのインデックスマップ。
   */
  private final Map<NotificationChannelType, NotificationSender> senderMap;

  /**
   * 社用メールアドレスから LINE WORKS ユーザー ID を導出する文字列解決サポート。
   */
  private final LineworksAccountResolver lineworksAccountResolver;

  /**
   * 未打刻者本人向けの個別リマインド文面を整形するドメインサービス。
   */
  private final UnstampedDirectReminderMessageFormatter reminderMessageFormatter;

  /**
   * 未打刻DM配信結果（成功・失敗・代理送信注記）から管理者トークルーム向けサマリー文面を構築するドメインサービス。
   */
  private final DirectReminderReportFormatter reportFormatter;

  /**
   * LINE WORKS API 2.0 の Directory API を通じてユーザー実在確認およびプロファイル取得を行う API クライアント。
   */
  private final LineworksUserApi lineworksUserApi;

  /**
   * 必要なドメインサービス、インフラサポート、APIクライアントを注入してサービスを初期化します。
   *
   * @param senders                  登録されている全送信ゲートウェイのリスト（LINE WORKS 実装等）
   * @param lineworksAccountResolver 社用メールアドレスから候補アカウントIDを導出するリゾルバー
   * @param reminderMessageFormatter 本人向けリマインドメッセージを整形するドメインフォーマッター
   * @param reportFormatter          管理者向けサマリーレポートを整形するドメインフォーマッター
   * @param lineworksUserApi         LINE WORKS ユーザー存在確認および表示名取得を担当するクライアント
   */
  public SendUnstampedDirectReminderService(
      List<NotificationSender> senders,
      LineworksAccountResolver lineworksAccountResolver,
      UnstampedDirectReminderMessageFormatter reminderMessageFormatter,
      DirectReminderReportFormatter reportFormatter,
      LineworksUserApi lineworksUserApi
  ) {
    this.senderMap = senders.stream()
        .collect(Collectors.toMap(NotificationSender::getChannelType, Function.identity()));
    this.lineworksAccountResolver = lineworksAccountResolver;
    this.reminderMessageFormatter = reminderMessageFormatter;
    this.reportFormatter = reportFormatter;
    this.lineworksUserApi = lineworksUserApi;
  }

  /**
   * 未打刻者本人へのダイレクト通知要求イベントを受け付け、個別DM配信および管理者サマリーレポート通知を実行します。
   * <p>
   * イベント内の全従業員を対象として事前に代理送信モードを評価し、 従業員ごとに通知パラメータを決定して送信処理を行います。<br>
   * 処理完了後、二次障害防止ガードのもとで管理者トークルームへ全件の結果レポートを配信します。
   * </p>
   *
   * @param event 勤怠管理サービスから発行された未打刻者ダイレクト通知イベントオブジェクト（1件以上の従業員リストを含む）
   * @throws IllegalStateException LINE WORKS 送信ゲートウェイがコンテキスト内に未登録の場合
   */
  @Override
  public void execute(UnstampedDirectReminderEvent event) {
    if (event.employees() == null || event.employees().isEmpty()) {
      log.info("ℹ️ 個別DM通知の対象者はいませんでした。処理を終了します。");
      return;
    }

    NotificationSender sender = senderMap.get(NotificationChannelType.LINE_WORKS);
    if (sender == null) {
      log.error("❌ LINE WORKS 送信ゲートウェイが見つかりません。");
      throw new IllegalStateException("LINE WORKS 送信ゲートウェイが未登録です");
    }

    // 💡 リスト内のいずれかに proxy メールが含まれている場合は、イベント全体を管理者代理送信モードとして判定
    final boolean isProxy = event.employees().stream()
        .anyMatch(emp -> StringUtils.startsWithIgnoreCase(emp.email(), "proxy"));

    log.info("▶ [未打刻DM一括配信] 配信対象: {} 名 (代理送信モード: {})",
        event.employees().size(), isProxy);

    List<DirectReminderEmployee> successItems = new ArrayList<>();
    List<FailureItem> failureItems = new ArrayList<>();

    for (DirectReminderEmployee emp : event.employees()) {
      sendIndividual(emp, sender, successItems, failureItems, isProxy);
    }

    log.info("✅ [未打刻DM一括配信完了] 正常送信: {} 件 / 失敗・警告: {} 件",
        successItems.size(), failureItems.size());

    // 管理者チャンネルへサマリーレポートを配送
    notifyAdminReport(successItems, failureItems, sender, isProxy);
  }

  /**
   * 個別従業員に対する通知計画（宛先ID、メッセージ本文、成否種別）を解決し、外部ゲートウェイを通じた送信と結果リストへの集約を実行します。
   * <p>
   * 送信計画の立案（{@link #resolveDispatchPlan(DirectReminderEmployee, boolean)}）と
   * 実際の送信処理を分離することで、単一責任の原則を担保しテスト容易性を向上させています。<br>
   * 送信ゲートウェイ起因の例外が発生した場合は安全に捕捉し、後続の従業員処理を中断させることなく失敗リストへ計上します。
   * </p>
   *
   * @param emp          通知対象となる個別の従業員データ（社員番号、メールアドレス、氏名）
   * @param sender       メッセージ送信を担当するドメインゲートウェイ
   * @param successItems 正常に通知が完了した従業員を蓄積するリスト
   * @param failureItems アカウント導出不可や通信失敗等により警告・失敗となった情報を蓄積するリスト
   * @param isProxy      管理者代理送信モードとして動作させる場合は {@code true}、通常送信の場合は {@code false}
   */
  private void sendIndividual(
      DirectReminderEmployee emp,
      NotificationSender sender,
      List<DirectReminderEmployee> successItems,
      List<FailureItem> failureItems,
      boolean isProxy
  ) {
    try {
      // 1. 宛先、本文、成否判定を含む送信計画（DispatchPlan）の立案
      DispatchPlan plan = resolveDispatchPlan(emp, isProxy);

      // 2. 外部通知モデルの再構築および送信ゲートウェイへのディスパッチ（1箇所に集約）
      Notification notification = new Notification(
          NotificationChannelType.LINE_WORKS,
          DestinationType.USER,
          plan.targetId(),
          plan.message(),
          LocalDateTime.now()
      );
      sender.send(notification);
      log.info(plan.successLog());

      // 3. 実行結果に応じたハコ詰め（成功／失敗リストへの振り分け）
      if (plan.isSuccess()) {
        successItems.add(emp);
      } else {
        failureItems.add(new FailureItem(emp, plan.failureReason()));
      }

    } catch (Exception e) {
      log.error("❌ 送信例外: 社員番号: {}, 氏名: {}, 原因: {}",
          emp.employeeNumber(), emp.fullName(), e.getMessage(), e);
      failureItems.add(new FailureItem(emp, "送信エラー: " + e.getMessage()));
    }
  }

  /**
   * 従業員の所属・メール状態および動作モードに基づいて、最適な送信計画（{@link DispatchPlan}）を解決します。
   *
   * @param emp         通知対象の従業員データ
   * @param isProxy     代理送信モードであるか否か
   * @param baseMessage フォーマッターによって生成された基本リマインド文面
   * @return 構築された送信計画不変レコード
   */
  private DispatchPlan resolveDispatchPlan(DirectReminderEmployee emp, boolean isProxy) {
    String baseMessage = reminderMessageFormatter.format(emp);

    if (isProxy) {
      return createProxyPlan(emp, baseMessage);
    }
    return createDirectPlan(emp, baseMessage);
  }

  /**
   * 管理者代理送信モードにおける送信計画を生成します。
   * <p>
   * 宛先ユーザーID（targetId）に {@code null} を指定することで、 送信ゲートウェイ側で環境変数設定のシステム管理者（個人DM）へ配送されるように構成します。
   * レポート上の集計は正常送信（成功）として扱われます。
   * </p>
   *
   * @param emp         通知対象の従業員データ
   * @param baseMessage 生成済みの基本リマインド文面
   * @return 管理者代理配信用の {@link DispatchPlan}
   */
  private DispatchPlan createProxyPlan(DirectReminderEmployee emp, String baseMessage) {
    return new DispatchPlan(
        null, // targetId = null でシステム管理者へ配送
        baseMessage,
        true,
        null,
        String.format("  └ 🛡️ 管理者へ代理送信成功: %s さん分", emp.fullName())
    );
  }

  /**
   * 通常モードにおける送信計画を生成します。
   * <p>
   * 社用メールアドレスから候補IDを算出した後、LINE WORKS Directory API を呼び出して実在性を確認します。<br>
   * <ul>
   *   <li><b>実在確認成功:</b> LINE WORKS 上の最新表示名（またはニックネーム）を宛名に差し替えた文面を構築し、
   *       本人への送信成功計画を返却します。</li>
   *   <li><b>実在確認失敗 / メール不正:</b> 警告メッセージを付与した代理通報文面を構築し、
   *       宛先をシステム管理者に倒した上で、レポート上は「失敗・スキップ」として記録する計画を返却します。</li>
   * </ul>
   * </p>
   *
   * @param emp         通知対象の従業員データ
   * @param baseMessage 生成済みの基本リマインド文面
   * @return 本人宛て送信またはアカウント未解決フォールバック用の {@link DispatchPlan}
   */
  private DispatchPlan createDirectPlan(DirectReminderEmployee emp, String baseMessage) {
    // ステップ 1: メールアドレスから candidateId を導出（純粋な文字列処理）
    String candidateId = lineworksAccountResolver.resolveUserIdFromEmail(emp.email());

    // ステップ 2: LINE WORKS API で存在確認＆プロファイル取得（外部通信）
    Optional<LineworksUserResponse> userOpt = StringUtils.hasText(candidateId)
        ? lineworksUserApi.fetchUser(candidateId)
        : Optional.empty();

    if (userOpt.isPresent()) {
      LineworksUserResponse user = userOpt.get();

      // LINE WORKS の表示名（ニックネーム等）が存在すれば優先し、未設定時は勤怠マスタ氏名を採用
      String displayName = StringUtils.hasText(user.resolveDisplayName())
          ? user.resolveDisplayName()
          : emp.fullName();

      DirectReminderEmployee displayEmp = new DirectReminderEmployee(
          emp.employeeNumber(), emp.email(), displayName
      );

      return new DispatchPlan(
          user.userId(),
          reminderMessageFormatter.format(displayEmp),
          true,
          null,
          String.format("  └ ✉️ 本人送信成功: %s さん (ID: %s)", displayName, user.userId())
      );
    }

    // アカウント逆引きまたは存在チェックに失敗した場合（設定ミス等の異常系）
    log.warn(
        "⚠️ LINE WORKS アカウント解決不可のため、管理者へ代理通知します: 社員番号: {}, 氏名: {}",
        emp.employeeNumber(), emp.fullName());

    String fallbackMessage = "📢 【管理者代理通知 / 未打刻リマインド】\n"
        + "※対象者のLINE WORKSアカウントが解決できないため、管理者に代理配信されました。\n\n"
        + baseMessage;

    return new DispatchPlan(
        null, // 代理先としてシステム管理者宛て
        fallbackMessage,
        false, // レポート上は失敗として計上
        "アカウント解決不可のため管理者へ代理送信",
        String.format("  └ 🛡️ 管理者へエラー代理通知完了: %s さん分", emp.fullName())
    );
  }

  /**
   * 全件の個別配信結果をドメインフォーマッターで整形し、管理者向け共有トークルームへ配信結果レポートを送信します。
   * <p>
   * 管理者トークルームへのレポート送信自体が失敗した場合でも、 業務トランザクション全体をロールバックさせたりエラーを上位へ伝播させたりしないよう、 例外を安全に捕捉して二次障害を防止します。
   * </p>
   *
   * @param successItems 正常に通知が完了した従業員リスト
   * @param failureItems 送信失敗またはスキップされた障害明細リスト
   * @param sender       メッセージ送信を担当するドメインゲートウェイ
   * @param isProxy      代理送信モードとして実行されたか否かのフラグ（レポート末尾の注記制御用）
   */
  private void notifyAdminReport(
      List<DirectReminderEmployee> successItems,
      List<FailureItem> failureItems,
      NotificationSender sender,
      boolean isProxy
  ) {
    try {
      String reportMessage = reportFormatter.format(successItems, failureItems, isProxy);
      sender.send(Notification.ofLineWorksAlert(reportMessage));
      log.info("📢 管理者トークルームへ配信結果レポートを送信しました。");
    } catch (Exception e) {
      log.error("⚠️ [二次障害防止] 管理者への配信結果レポート送信に失敗しました。", e);
    }
  }

  /**
   * 個別通知における配信パラメータ（宛先、本文、成否種別、ログ情報）をカプセル化する内部不変レコード。
   * <p>
   * 分岐処理内で決定された複数の出力パラメータをひとまとめに保持し、 送信処理および結果リストへの振り分け処理へ引き渡すためのキャリアオブジェクトとして機能します。
   * </p>
   *
   * @param targetId      送信先の LINE WORKS ユーザー ID（{@code null} の場合は既定のシステム管理者宛て）
   * @param message       送信対象となる整形済みの通知本文メッセージ
   * @param isSuccess     配信成功（成功リストへ追加）として扱うか否か
   * @param failureReason 配信失敗または警告となった場合の理由（成功時は {@code null}）
   * @param successLog    送信完了時にサーバーログへ出力する成功ログ文字列
   */
  private record DispatchPlan(
      String targetId,
      String message,
      boolean isSuccess,
      String failureReason,
      String successLog
  ) {

  }
}

//package com.computer_rescuer.notification.infrastructure.config;
//
//import com.fasterxml.jackson.databind.DeserializationFeature;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.fasterxml.jackson.databind.PropertyNamingStrategies;
//import com.fasterxml.jackson.databind.SerializationFeature;
//import com.fasterxml.jackson.databind.json.JsonMapper;
//import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.context.annotation.Primary;
//
/// **
// * Jackson（JSON シリアライザ / デシリアライザ）の Bean 構成クラス。
// * <p>
// * アプリケーション全体（Springwolf UI、Kafka メッセージシリアライズ、REST 通信等）で
// * 利用される共通の {@link ObjectMapper} をカスタマイズして DI コンテナへ登録します。<br>
// * Spring 7 で非推奨となった {@code Jackson2ObjectMapperBuilder} には依存せず、
// * Jackson 公式推奨のイミュータブルビルダー（{@link JsonMapper#builder()}）を用いて型安全に構築します。
// * </p>
// * <p>
// * <b>【アーキテクチャ上の留意事項・暫定対応の経緯】</b><br>
// * 本クラスは、Spring Boot 4.0.5（Spring 7）環境下において、Spring Boot 3 世代向けライブラリである
// * Springwolf（v1.7.0）が初期化される際、Spring Boot 本体の {@code JacksonAutoConfiguration} より前に
// * フライングで {@link ObjectMapper} のインジェクションを要求してしまう起動順序の競合を回避するために明示定義されています。
// * </p>
// */
//@Configuration
//public class JacksonConfig {
//
//    /*
//     * TODO: 【Springwolf バージョンアップ時の見直し検討】
//     * -----------------------------------------------------------------------------------------
//     * [発生理由]
//     * Springwolf (v1.7.0) 導入時、SpringwolfWebConfiguration#publishingPayloadCreator が
//     * Spring Boot 4 の自動構成 (JacksonAutoConfiguration) 完了前に ObjectMapper を要求し、
//     * UnsatisfiedDependencyException が発生するため、本明示的 Bean 定義により初期化順序を調停している。
//     *
//     * [見直し・撤廃条件]
//     * 将来的に Springwolf が Spring Boot 4 / Spring 7 正式対応版へバージョンアップされた際、
//     * 本クラスを削除（またはコメントアウト）してもコンテキスト初期化が正常に通るか再検証すること。
//     * Spring Boot 本体の自動構成と application.yaml の spring.jackson 設定のみで充足する場合は、
//     * 車輪の再発明を防ぐため本クラスの廃止・削除を検討すること。
//     * -----------------------------------------------------------------------------------------
//     */
//
//    /**
//     * アプリケーション共通のプライマリ {@link ObjectMapper} Bean を生成・構成します。
//     * <p>
//     * Jackson 公式のモダンビルダー {@link JsonMapper#builder()} を用い、以下のポリシーを一元適用します：<br>
//     * 1. {@link JavaTimeModule} の登録と {@code WRITE_DATES_AS_TIMESTAMPS} の無効化（日時を ISO-8601 文字列で統一）<br>
//     * 2. {@link PropertyNamingStrategies#SNAKE_CASE} 適用（Kafka メッセージおよび JSON のキーをスネークケースに統一）<br>
//     * 3. {@code FAIL_ON_UNKNOWN_PROPERTIES} 無効化（未知の JSON プロパティが存在してもエラーにせず安全に無視）
//     * </p>
//     *
//     * @return 共通ポリシーが適用された {@link ObjectMapper} インスタンス
//     */
//    @Bean
//    @Primary
//    public ObjectMapper objectMapper() {
//        return JsonMapper.builder()
//                // Java 8+ 日時型 (LocalDate, LocalDateTime等) のシリアライズ対応
//                .addModule(new JavaTimeModule())
//                // 日時を [2026, 9, 22] などの数値配列ではなく ISO-8601 文字列 ("2026-09-22") に統一
//                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
//                // JSON プロパティ名を snake_case に統一
//                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
//                // 未知のプロパティが存在してもエラーにせず安全に無視
//                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
//                .build();
//    }
//}

# ==========================================
# 1. ビルドステージ（Java 25 / ARM64対応）
# ==========================================
FROM eclipse-temurin:25-jdk AS builder
WORKDIR /workspace/app

# Gradle Wrapperと設定ファイルを先にコピー（依存解決キャッシュの最適化）
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle gradle.properties* ./

# gradlewに実行権限を付与
RUN chmod +x gradlew

# ソースコード一式をコピー（jOOQのDDL解析に必要なSQL群を含む）
COPY src src

# テストをスキップしてSpring Bootの実行可能JARを生成
RUN ./gradlew bootJar -x test -x e2eTest --no-daemon

# -plain.jar を除外して app.jar として確定
RUN cp $(ls build/libs/*.jar | grep -v plain) app.jar

# ==========================================
# 2. 実行用軽量ステージ（JRE 25）
# ==========================================
FROM eclipse-temurin:25-jre
WORKDIR /app

# compose.yaml のヘルスチェック実行に必要な curl を導入
# ついでに New Relic Agent をダウンロード！
RUN apt-get update && apt-get install -y --no-install-recommends curl unzip \
    && rm -rf /var/lib/apt/lists/*

# 最新の New Relic Java Agent を公式から取得して配置
# （Java 25 対応の最新 8.x 系を取得）
RUN mkdir -p /app/newrelic \
    && curl -O https://download.newrelic.com/newrelic/java-agent/newrelic-agent/current/newrelic-java.zip \
    && unzip newrelic-java.zip -d /app \
    && rm newrelic-java.zip

# OpenTelemetry Java Agent をダウンロード
RUN mkdir -p /app/otel \
    && curl -L -o /app/otel/opentelemetry-javaagent.jar \
       https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-javaagent.jar

# タイムゾーンを東京に設定
ENV TZ=Asia/Tokyo

# マウント用ディレクトリを事前に作成
RUN mkdir -p /app/keys/lineworks

# ビルド成果物のみをコピー
COPY --from=builder /workspace/app/app.jar app.jar

# compose.yaml のコンテナ側ポートに合わせて 8081 を公開
EXPOSE 8081

# アプリケーション起動
ENTRYPOINT ["java", "-jar", "app.jar"]

# Đóng gói bằng Maven Wrapper, chỉ đưa JAR và Java 17 JRE vào image chạy.
FROM eclipse-temurin:17-jdk-jammy AS builder
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw
COPY src src
RUN ./mvnw --batch-mode -DskipTests package

# Ứng dụng chạy bằng UID không đặc quyền; dữ liệu PostgreSQL nằm ở volume riêng.
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=builder /workspace/target/stockflow-*.jar /app/stockflow.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-XX:+UseSerialGC", "-Xmx350m", "-jar", "/app/stockflow.jar"]

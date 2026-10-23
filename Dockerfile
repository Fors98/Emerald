FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY backend/pom.xml ./pom.xml
RUN mvn -B dependency:go-offline
COPY backend/src ./src
RUN mvn -B verify
FROM eclipse-temurin:21-jre-jammy
RUN groupadd -g 10001 emerald && useradd -u 10001 -g emerald -s /usr/sbin/nologin emerald && mkdir -p /app/media && chown -R emerald:emerald /app
WORKDIR /app
COPY --from=build --chown=emerald:emerald /build/target/emerald-backend-1.0.0.jar /app/app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java","-XX:MaxRAMPercentage=70","-jar","/app/app.jar"]

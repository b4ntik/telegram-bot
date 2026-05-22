# Этап 1: Сборка (используем образ с Maven и JDK)
FROM maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /app

# Копируем pom.xml и скачиваем зависимости
COPY pom.xml .
RUN mvn dependency:go-offline

# Копируем исходники и собираем JAR
COPY src ./src
RUN mvn clean package -DskipTests

# Этап 2: Запуск (только JRE, без Maven)
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Копируем собранный JAR из этапа сборки
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
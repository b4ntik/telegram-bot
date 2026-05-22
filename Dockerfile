# Этап 1: Сборка
FROM maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /app

# Копируем pom.xml и скачиваем зависимости
COPY pom.xml .
RUN mvn dependency:go-offline

# Копируем исходники и собираем JAR (в target/)
COPY src ./src
RUN mvn clean package -DskipTests

# Этап 2: Запуск
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Копируем JAR из target/ (куда Maven кладёт по умолчанию)
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
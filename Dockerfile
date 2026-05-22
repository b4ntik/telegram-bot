# Этап 1: Сборка
FROM maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /app

# Копируем pom.xml и скачиваем зависимости
COPY pom.xml .
RUN mvn dependency:go-offline

# Копируем исходники и собираем JAR (в target/)
COPY src ./src
RUN mvn clean package -DskipTests

# Отладка: посмотрим, где и какой JAR создался
RUN ls -la target/

# Этап 2: Запуск
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Копируем JAR из target/ (куда Maven кладёт по умолчанию)
COPY --from=build /app/target/*.jar app.jar

# Проверяем, что JAR скопировался и имеет правильный манифест
RUN ls -la && jar tf app.jar | grep -q "META-INF/MANIFEST.MF"

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
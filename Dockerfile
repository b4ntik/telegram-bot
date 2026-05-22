FROM maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn clean package -DskipTests
RUN ls -la target/  # Отладка: покажет, какой JAR создался

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
RUN ls -la  # Отладка: проверит, что файл скопировался
RUN jar tf app.jar | grep -i main-class  # Проверит наличие Main-Class в манифесте

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
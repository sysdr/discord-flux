FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /app

COPY pom.xml ./
COPY .mvn ./.mvn
COPY mvnw ./
RUN ./mvnw dependency:go-offline -B

COPY src ./src
RUN ./mvnw clean package -DskipTests -B

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache docker-cli
WORKDIR /app
COPY --from=builder /app/target/loadbalancer-1.0.0.jar ./
CMD ["java", "-jar", "loadbalancer-1.0.0.jar"]

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

COPY build/libs/medisection-3d-0.0.1-SNAPSHOT.jar app.jar

ENV SPRING_PROFILES_ACTIVE=dev
ENV JWT_SECRET_KEY=medisection-demo-secret-key-change-me-1234567890
ENV JWT_EXPIRATION_TIME=3600000

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

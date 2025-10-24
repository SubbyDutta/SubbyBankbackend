
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jdk-alpine
WORKDIR /app

# Copy the built jar
COPY --from=build /app/target/*.jar app.jar

# Expose backend port
EXPOSE 8080

# Environment variables (Railway will inject real ones)
ENV PORT=8080
ENV DATABASE_URL=""
ENV POSTGRES_USER=""
ENV POSTGRES_PASSWORD=""
ENV JWT_SECRET=""
ENV RAZORPAY_KEY_ID=""
ENV RAZORPAY_SECRET=""
ENV FRAUD_ML_URL=""
ENV LOAN_CHECK_URL=""

ENTRYPOINT ["java", "-jar", "app.jar"]

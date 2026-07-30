# Stage 1: Build common module
FROM maven:3.9-eclipse-temurin-21 AS common-builder
WORKDIR /app
COPY . .
RUN mvn clean install -pl jari-common -am -DskipTests

# Stage 2: Build discovery service
FROM maven:3.9-eclipse-temurin-21 AS discovery
WORKDIR /app
COPY . .
RUN mvn clean package -pl jari-discovery -am -DskipTests
ENTRYPOINT ["java", "-jar", "jari-discovery/target/jari-discovery-0.0.1-SNAPSHOT.jar"]

# Stage 3: Build gateway
FROM maven:3.9-eclipse-temurin-21 AS gateway
WORKDIR /app
COPY . .
RUN mvn clean package -pl jari-gateway -am -DskipTests
ENTRYPOINT ["java", "-jar", "jari-gateway/target/jari-gateway-0.0.1-SNAPSHOT.jar"]

# Stage 4: Build user-service
FROM maven:3.9-eclipse-temurin-21 AS user-service
WORKDIR /app
COPY . .
RUN mvn clean package -pl jari-user-service -am -DskipTests
ENTRYPOINT ["java", "-jar", "jari-user-service/target/jari-user-service-0.0.1-SNAPSHOT.jar"]

# Stage 6: Build project-service
FROM maven:3.9-eclipse-temurin-21 AS project-service
WORKDIR /app
COPY . .
RUN mvn clean package -pl jari-project-service -am -DskipTests
ENTRYPOINT ["java", "-jar", "jari-project-service/target/jari-project-service-0.0.1-SNAPSHOT.jar"]

# Stage 7: Build task-service
FROM maven:3.9-eclipse-temurin-21 AS task-service
WORKDIR /app
COPY . .
RUN mvn clean package -pl jari-task-service -am -DskipTests
ENTRYPOINT ["java", "-jar", "jari-task-service/target/jari-task-service-0.0.1-SNAPSHOT.jar"]

# Stage 8: Build notification-service
FROM maven:3.9-eclipse-temurin-21 AS notification-service
WORKDIR /app
COPY . .
RUN mvn clean package -pl jari-notification-service -am -DskipTests
ENTRYPOINT ["java", "-jar", "jari-notification-service/target/jari-notification-service-0.0.1-SNAPSHOT.jar"]

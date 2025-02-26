FROM eclipse-temurin:17-jdk-focal

# Install required system dependencies for nd4j
RUN apt-get update && apt-get install -y \
    libgomp1 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy the entire project
COPY . .

# Build the application
RUN chmod +x mvnw && \
    ./mvnw package -DskipTests

CMD ["java", "-jar", "target/ai-content-platform-0.0.1-SNAPSHOT.jar"]

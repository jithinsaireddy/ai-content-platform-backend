FROM eclipse-temurin:17-jdk-focal

# Install required system dependencies for nd4j
RUN apt-get update && apt-get install -y \
    libgomp1 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy only the POM file first
COPY pom.xml mvnw ./
COPY .mvn .mvn

# Download dependencies
RUN chmod +x mvnw && \
    ./mvnw dependency:go-offline

# Copy the rest of the project
COPY . .

# Build the application
RUN ./mvnw clean package -DskipTests

CMD ["java", "-jar", "target/app.jar"]

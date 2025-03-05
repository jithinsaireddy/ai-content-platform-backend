#!/bin/bash

# chmod +x aws_setup.sh  // Make the script executable
# ./aws_setup.sh [--fresh] [--artifact]  // Run the script, optionally with --fresh to force new build and --artifact to use JAR from artifact directory


# Function to check if a command exists
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# Function to print in color
print_color() {
    local color=$1
    local text=$2
    case $color in
        "red") echo -e "\033[0;31m$text\033[0m" ;;
        "green") echo -e "\033[0;32m$text\033[0m" ;;
        "yellow") echo -e "\033[0;33m$text\033[0m" ;;
        "blue") echo -e "\033[0;34m$text\033[0m" ;;
    esac
}

# Parse command line arguments
FRESH_BUILD=false
USE_ARTIFACT=false
while [[ "$#" -gt 0 ]]; do
    case $1 in
        --fresh) FRESH_BUILD=true ;;
        --artifact) USE_ARTIFACT=true ;;
        *) echo "Unknown parameter: $1"; exit 1 ;;
    esac
    shift
done

echo "Starting AWS environment setup..."

# Check and install git if not present
if ! command_exists git; then
    echo "Git not found. Installing git..."
    sudo yum install -y git
else
    echo "Git is already installed"
fi

# Check and install Java if not present
if ! command_exists java; then
    print_color "yellow" "Java not found. Installing Java..."
    sudo yum install -y java-17-amazon-corretto-devel
else
    print_color "green" "Java is already installed"
fi

# Set JAVA_HOME if not already set
if [ -z "$JAVA_HOME" ]; then
    # Find Java installation path
    JAVA_PATH=$(dirname $(dirname $(readlink -f $(which java))))
    echo "export JAVA_HOME=$JAVA_PATH" >> ~/.bashrc
    echo "export PATH=\$PATH:\$JAVA_HOME/bin" >> ~/.bashrc
    source ~/.bashrc
    print_color "green" "JAVA_HOME has been set to $JAVA_PATH"
else
    print_color "green" "JAVA_HOME is already set to $JAVA_HOME"
fi

# Generate SSH key if it doesn't exist
SSH_KEY_PATH="$HOME/.ssh/id_ed25519"
if [ ! -f "$SSH_KEY_PATH" ]; then
    echo "Generating new SSH key..."
    ssh-keygen -t ed25519 -C "jithin@example.com" -f "$SSH_KEY_PATH" -N ""
else
    echo "SSH key already exists"
fi

# Install Docker if not present
if ! command_exists docker; then
    echo "Installing Docker..."
    sudo yum update -y
    sudo yum install -y docker
    sudo service docker start
    sudo usermod -a -G docker ec2-user
    sudo systemctl enable docker
    
    # Install Docker Compose
    echo "Installing Docker Compose..."
    sudo curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
    sudo chmod +x /usr/local/bin/docker-compose
    
    echo "Docker installation complete. Please log out and log back in for group changes to take effect."
    echo "Run 'sudo reboot' to reboot the system."
else
    echo "Docker is already installed"
fi

# Display public SSH key
echo -e "\nYour public SSH key is:"
cat "$HOME/.ssh/id_ed25519.pub"

# Prompt for GitHub SSH setup confirmation
read -p "Have you added the SSH key to your GitHub account? (y/n) " github_confirmed

if [ "$github_confirmed" = "y" ]; then
    # Get current directory name
    CURRENT_DIR=$(basename "$PWD")
    REPO_NAME="ai-content-platform-backend"

    # Check if we're already in the correct repository directory
    if [ "$CURRENT_DIR" = "$REPO_NAME" ]; then
        echo "Already in the repository directory. Pulling latest changes..."
        git pull origin main
    else
        echo "Cloning repository..."
        git clone git@github.com:jithinsaireddy/ai-content-platform-backend.git
        cd ai-content-platform-backend
    fi

    # Check if we need to build the JAR
    if [ "$FRESH_BUILD" = true ]; then
        print_color "yellow" "Fresh build requested. Building new JAR..."
        ./mvnw clean package -DskipTests
        if [ $? -ne 0 ]; then
            print_color "red" "Failed to build JAR"
            exit 1
        fi
        # Copy successful build to artifact directory
        mkdir -p artifact
        cp target/app.jar artifact/app.jar
        print_color "green" "JAR copied to artifact directory for future use"
    else
        if [ "$USE_ARTIFACT" = true ]; then
            if [ -f "artifact/app.jar" ]; then
                print_color "green" "Using JAR from artifact directory..."
                mkdir -p target
                cp artifact/app.jar target/app.jar
            else
                print_color "red" "No JAR found in artifact directory"
                exit 1
            fi
        else
            # Check target directory first
            if [ -f "target/app.jar" ]; then
                print_color "green" "Found JAR in target directory, using it..."
            else
                print_color "yellow" "No existing JAR found. Building new one..."
                ./mvnw clean package -DskipTests
                if [ $? -ne 0 ]; then
                    print_color "red" "Failed to build JAR"
                    exit 1
                fi
            fi
        fi
    fi

    # Remove existing containers and images if doing a fresh build
    if [ "$FRESH_BUILD" = true ]; then
        print_color "yellow" "Removing existing containers and images..."
        docker-compose down
        docker rmi $(docker images -q ai-content-platform-backend_app) 2>/dev/null || true
    fi

    # Start the application
    print_color "blue" "Starting application..."
    print_color "yellow" "Stopping any existing containers..."
    docker-compose down
    
    if [ "$FRESH_BUILD" = true ]; then
        docker-compose build --no-cache
        docker-compose up
    else
        docker-compose up
    fi

    print_color "green" "Setup complete! Application is running."
else
    echo "Please add your SSH key to GitHub before proceeding."
    echo "Visit https://github.com/settings/ssh/new to add your SSH key."
fi

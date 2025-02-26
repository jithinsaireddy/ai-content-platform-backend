#!/bin/bash

# chmod +x aws_setup.sh  // Make the script executable
# ./aws_setup.sh  // Run the script


# Function to check if a command exists
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

echo "Starting AWS environment setup..."

# Check and install git if not present
if ! command_exists git; then
    echo "Git not found. Installing git..."
    sudo yum install -y git
else
    echo "Git is already installed"
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
    # Check if repository already exists
    if [ -d "ai-content-platform-backend" ]; then
        echo "Repository already exists. Pulling latest changes..."
        cd ai-content-platform-backend
        git pull origin main
    else
        echo "Cloning repository..."
        git clone git@github.com:jithinsaireddy/ai-content-platform-backend.git
        cd ai-content-platform-backend
    fi

    # Build and start Docker containers
    echo "Building and starting Docker containers..."
    docker-compose build
    docker-compose up -d
else
    echo "Please add your SSH key to GitHub before proceeding."
    echo "Visit https://github.com/settings/ssh/new to add your SSH key."
fi

#!/bin/bash

# Database Configuration
export SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/ai_content_platform"
export SPRING_DATASOURCE_USERNAME="postgres"
export SPRING_DATASOURCE_PASSWORD="postgres"

# JWT Configuration
export JWT_SECRET="8970B76B1C5A0F4E3D2C1B8A7F6E5D4C3B2A1908F7E6D5C4B3A2918F7E6D5C4B3A2918F7E6D5C4B3A2918F7E6D5C4B3A2918F7E6D5C4B3A2918F7E6D5C4B3A2918"

# API Keys
export OPENAI_API_KEY="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"
export SERPAPI_API_KEY="119b891a70487aad264aa1425c67dd633cdd2795265168014d0778615a857ffd"

# Optional API Keys (currently using dummy values)
export TWITTER_API_KEY="dummy-key"
export GOOGLE_TRENDS_API_KEY="dummy-key"
export REDDIT_API_KEY="dummy-key"

echo "Environment variables set successfully!"

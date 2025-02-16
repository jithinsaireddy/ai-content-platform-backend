#!/bin/bash

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m'

# Base URL
BASE_URL="http://localhost:8080"
TOKEN=""

echo "Starting API Tests..."

# Function to print test results
test_result() {
    if [ $1 -eq 0 ]; then
        echo -e "${GREEN}✓ $2${NC}"
    else
        echo -e "${RED}✗ $2${NC}"
    fi
}

# 1. Authentication Tests
echo -e "\n=== Authentication Tests ==="

# Register new user
echo "Testing user registration..."
REGISTER_RESPONSE=$(curl -s -X POST "${BASE_URL}/api/auth/register" \
    -H "Content-Type: application/json" \
    -d '{
        "username": "testuser",
        "email": "test@example.com",
        "password": "password123"
    }')
test_result $? "User Registration"

# Login
echo "Testing user login..."
LOGIN_RESPONSE=$(curl -s -X POST "${BASE_URL}/api/auth/login" \
    -H "Content-Type: application/json" \
    -d '{
        "usernameOrEmail": "testuser",
        "password": "password123"
    }')
TOKEN=$(echo $LOGIN_RESPONSE | grep -o '"accessToken":"[^"]*' | cut -d'"' -f4)
test_result $? "User Login"

# 2. Content Generation Tests
echo -e "\n=== Content Generation Tests ==="

# Generate multiple content items for trend analysis
echo "Generating test content for trend analysis..."
for topic in "AI" "Machine Learning" "Data Science"; do
    curl -s -X POST "${BASE_URL}/api/content/generate" \
        -H "Authorization: Bearer $TOKEN" \
        -H "Content-Type: application/json" \
        -d "{
            \"contentType\": \"text\",
            \"topic\": \"$topic\",
            \"keywords\": \"artificial intelligence, technology\"
        }"
    sleep 1
done

# Generate content
echo "Testing content generation..."
CONTENT_RESPONSE=$(curl -s -X POST "${BASE_URL}/api/content/generate" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{
        "contentType": "text",
        "topic": "AI Technology",
        "keywords": ["artificial intelligence", "machine learning"]
    }')
CONTENT_ID=$(echo $CONTENT_RESPONSE | grep -o '"id":[0-9]*' | cut -d':' -f2)
test_result $? "Content Generation"

# Validate content
echo "Testing content validation..."
curl -s -X POST "${BASE_URL}/api/content/validate" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{
        "content": "This is a test content for validation."
    }'
test_result $? "Content Validation"

# 3. Trend Analysis Tests
echo -e "\n=== Trend Analysis Tests ==="

# Get trending topics
echo "Testing trending topics retrieval..."
curl -s -X GET "${BASE_URL}/api/trends" \
    -H "Authorization: Bearer $TOKEN"
test_result $? "Get Trending Topics"

# Get trend insights
echo "Testing trend insights retrieval..."
curl -s -X GET "${BASE_URL}/api/trends/insights" \
    -H "Authorization: Bearer $TOKEN"
test_result $? "Get Trend Insights"

# Get trend sentiment
echo "Testing trend sentiment analysis..."
curl -s -X GET "${BASE_URL}/api/trends/sentiment/AI" \
    -H "Authorization: Bearer $TOKEN"
test_result $? "Get Trend Sentiment"

# Test trend prediction
echo "Testing trend prediction..."
curl -s -X GET "${BASE_URL}/api/trends/predict?topic=AI&timeframe=7d" \
    -H "Authorization: Bearer $TOKEN"
test_result $? "Get Trend Prediction"

# Test trend correlation
echo "Testing trend correlation..."
curl -s -X GET "${BASE_URL}/api/trends/correlate?topics=AI,MachineLearning" \
    -H "Authorization: Bearer $TOKEN"
test_result $? "Get Trend Correlation"

# 4. Content Strategy Tests
echo -e "\n=== Content Strategy Tests ==="

# Get content strategy
echo "Testing content strategy retrieval..."
curl -s -X GET "${BASE_URL}/api/strategy" \
    -H "Authorization: Bearer $TOKEN"
test_result $? "Get Content Strategy"

# Test content optimization
echo "Testing content optimization..."
curl -s -X POST "${BASE_URL}/api/content/optimize" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{
        "contentId": 1,
        "optimizationGoal": "engagement",
        "targetMetrics": ["readability", "seo"]
    }'
test_result $? "Content Optimization"

# Test content performance metrics
echo "Testing content performance metrics..."
curl -s -X GET "${BASE_URL}/api/content/1/metrics" \
    -H "Authorization: Bearer $TOKEN"
test_result $? "Get Content Performance Metrics"

# 5. Competitor Analysis Tests
echo -e "\n=== Competitor Analysis Tests ==="

# Analyze competitors
echo "Testing competitor analysis..."
curl -s -X POST "${BASE_URL}/api/competitors/analyze?industry=Technology" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '["Competitor1", "Competitor2"]'
test_result $? "Competitor Analysis"

# Get competitive advantage
echo "Testing competitive advantage analysis..."
curl -s -X GET "${BASE_URL}/api/competitors/competitive-advantage/Technology" \
    -H "Authorization: Bearer $TOKEN"
test_result $? "Get Competitive Advantage"

# Test error handling - Invalid auth
echo "Testing invalid authentication..."
curl -s -X GET "${BASE_URL}/api/trends" \
    -H "Authorization: Bearer invalid_token"
test_result $? "Invalid Authentication Test"

# Test error handling - Invalid request
echo "Testing invalid request..."
curl -s -X POST "${BASE_URL}/api/content/generate" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{
        "invalid": "data"
    }'
test_result $? "Invalid Request Test"

# 6. Content Localization Tests
echo -e "\n=== Content Localization Tests ==="

# Localize content
echo "Testing content localization..."
curl -s -X POST "${BASE_URL}/api/localization/localize" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{
        "content": {"id": 1, "contentBody": "Test content"},
        "targetRegions": ["US", "UK"]
    }'
test_result $? "Content Localization"

# Get regional performance
echo "Testing regional performance analysis..."
curl -s -X GET "${BASE_URL}/api/localization/performance/1?regions=US,UK" \
    -H "Authorization: Bearer $TOKEN"
test_result $? "Get Regional Performance"

# 7. User Preferences Tests
echo -e "\n=== User Preferences Tests ==="

# Update writing style
echo "Testing writing style update..."
curl -s -X POST "${BASE_URL}/api/users/writing-style" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{
        "writingSample": "This is my preferred writing style."
    }'
test_result $? "Update Writing Style"

# 8. Analytics Tests
echo -e "\n=== Analytics Tests ==="

# Get user stats
echo "Testing user statistics retrieval..."
curl -s -X GET "${BASE_URL}/api/analytics/user-stats" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json"
test_result $? "Get User Statistics"

echo -e "\nAPI Tests Completed!"

#!/bin/bash

# Create the project directory
mkdir -p /Users/jithinpothireddy/CascadeProjects/ai-content-platform/src

# Navigate to the project directory
cd /Users/jithinpothireddy/CascadeProjects/ai-content-platform

# Initialize a new React project
npx create-react-app src

# Navigate into the src directory
cd src

# Install necessary dependencies
npm install @react-three/drei @react-three/fiber @react-spring/three @emotion/react @emotion/styled \
  @mui/material axios framer-motion react-router-dom three gsap react-spring react-particles \
  @react-spring/web @types/three

# Create necessary directories
mkdir -p src/components/3d
mkdir -p src/components/layout
mkdir -p src/components/auth
mkdir -p src/components/content
mkdir -p src/components/analytics
mkdir -p src/assets/models
mkdir -p src/assets/textures
mkdir -p src/styles
mkdir -p src/services
mkdir -p src/context

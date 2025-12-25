#!/bin/bash

echo "Starting Invoice Service with Java..."
echo ""

# Set API Key
export ARK_API_KEY=de7e4292-2a15-4f5c-ae7c-2553c555fea8

# Check if JAR exists
if [ ! -f "target/invoice-service-1.0.0.jar" ]; then
    echo "JAR file not found. Compiling first..."
    echo ""
    mvn clean package -DskipTests
    
    if [ $? -ne 0 ]; then
        echo "Build failed!"
        exit 1
    fi
fi

echo ""
echo "Starting application with Java..."
java -jar target/invoice-service-1.0.0.jar


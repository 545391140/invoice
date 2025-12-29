#!/bin/bash

echo "Starting Invoice Service..."
echo ""
echo "Please make sure ARK_API_KEY environment variable is set!"
echo ""

if [ -z "$ARK_API_KEY" ]; then
    echo "ERROR: ARK_API_KEY environment variable is not set!"
    echo "Please set it before running:"
    echo "  export ARK_API_KEY=your_api_key_here"
    exit 1
fi

echo "Building project..."
mvn clean package -DskipTests

if [ $? -ne 0 ]; then
    echo "Build failed!"
    exit 1
fi

echo ""
echo "Starting application..."
java -jar target/invoice-service-1.0.0.jar








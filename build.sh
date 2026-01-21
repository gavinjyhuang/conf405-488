#!/bin/bash

# Build script for Conf405/488 ImageJ Plugin

echo "Building Conf405/488 ImageJ Plugin..."

# Check if ImageJ is installed
IMAGEJ_JAR=""

# Common ImageJ locations
IMAGEJ_LOCATIONS=(
    "/Applications/ImageJ.app/Contents/Java/ij.jar"
    "/Applications/Fiji.app/jars/ij.jar"
    "$HOME/ImageJ/ij.jar"
    "$HOME/Downloads/ImageJ/ij.jar"
    "$HOME/Downloads/ImageJ 2/ij.jar"
    "C:/Program Files/ImageJ/ij.jar"
    "/usr/local/ImageJ/ij.jar"
)

for loc in "${IMAGEJ_LOCATIONS[@]}"; do
    if [ -f "$loc" ]; then
        IMAGEJ_JAR="$loc"
        break
    fi
done

if [ -z "$IMAGEJ_JAR" ]; then
    echo "Error: Could not find ij.jar (ImageJ)"
    echo "Please specify the path to ij.jar:"
    echo "  javac -cp /path/to/ij.jar Conf405_488.java"
    exit 1
fi

echo "Found ImageJ at: $IMAGEJ_JAR"

# Compile
javac -cp "$IMAGEJ_JAR" Conf405_488.java

if [ $? -eq 0 ]; then
    echo "Build successful!"
    echo ""
    echo "To install:"
    echo "  1. Copy all Conf405_488*.class files to your ImageJ plugins folder"
    echo "  2. Restart ImageJ"
    echo ""
    ls -lh Conf405_488*.class
else
    echo "Build failed!"
    exit 1
fi

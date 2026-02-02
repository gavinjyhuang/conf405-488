#!/bin/bash

# Build script for Conf405/488 ImageJ Plugin (.class only)

echo "Building Conf405/488 ImageJ Plugin (.class only)..."

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

# Clean previous classes
rm -f Conf405_488*.class

# Compile targeting Java 8 for Fiji/ImageJ compatibility
if javac --help 2>/dev/null | grep -q -- "--release"; then
    # Prefer modern flag to ensure correct bootclasspath and bytecode (major 52)
    javac --release 8 -cp "$IMAGEJ_JAR" Conf405_488.java
else
    # Fallback for older javac versions
    javac -source 1.8 -target 1.8 -cp "$IMAGEJ_JAR" Conf405_488.java
fi

if [ $? -eq 0 ]; then
    echo "Class compilation successful!"
    echo "Generated class files:"
    ls -1 Conf405_488*.class
else
    echo "Build failed!"
    exit 1
fi

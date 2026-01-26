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
    echo "Packaging JAR..."

    # Create distribution folder
    DIST_DIR="dist"
    mkdir -p "$DIST_DIR"

    # Package class files into a JAR for ImageJ
    JAR_NAME="Conf405_488.jar"
    jar cf "$DIST_DIR/$JAR_NAME" Conf405_488.class Conf405_488\$1.class Conf405_488\$2.class

    if [ $? -eq 0 ]; then
        echo "JAR created: $DIST_DIR/$JAR_NAME"
        echo ""
        echo "To install (recommended):"
        echo "  1. Copy $DIST_DIR/$JAR_NAME to your ImageJ plugins folder"
        echo "  2. Restart ImageJ"
        echo ""
        echo "Alternative install (legacy):"
        echo "  - Copy all Conf405_488*.class files to your ImageJ plugins folder"
        echo ""
        ls -lh "$DIST_DIR/$JAR_NAME"
    else
        echo "JAR packaging failed!"
        exit 1
    fi
else
    echo "Build failed!"
    exit 1
fi

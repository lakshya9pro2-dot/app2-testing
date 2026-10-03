#!/bin/bash
# setup.sh — Run this ONCE after cloning to bootstrap the project.
# Downloads gradle-wrapper.jar and initializes git if needed.

set -e

echo "=== LiteWeb Extractor Setup ==="

# 1. Download gradle-wrapper.jar (required for ./gradlew to work)
WRAPPER_JAR="gradle/wrapper/gradle-wrapper.jar"
if [ ! -f "$WRAPPER_JAR" ]; then
  echo "Downloading gradle-wrapper.jar..."
  curl -fsSL -o "$WRAPPER_JAR" \
    "https://services.gradle.org/distributions/gradle-8.4-bin.zip" || true

  # Alternative: use gradle's own bootstrap
  if command -v gradle &>/dev/null; then
    gradle wrapper --gradle-version 8.4
    echo "gradle-wrapper.jar downloaded via 'gradle wrapper'"
  else
    echo ""
    echo "  Could not auto-download gradle-wrapper.jar."
    echo "  Option A: Install Gradle (brew install gradle / apt install gradle)"
    echo "            then run: gradle wrapper --gradle-version 8.4"
    echo "  Option B: Download manually from:"
    echo "            https://services.gradle.org/distributions/gradle-8.4-bin.zip"
    echo "            then run: gradle wrapper --gradle-version 8.4"
    echo "  Option C: Open the project in Android Studio — it handles this automatically."
    echo ""
  fi
else
  echo "gradle-wrapper.jar already present."
fi

# 2. Make gradlew executable
chmod +x gradlew
echo "gradlew is executable."

# 3. Initialize git if not already
if [ ! -d ".git" ]; then
  git init
  git add .
  git commit -m "Initial commit: LiteWeb Extractor"
  echo "Git repository initialized."
else
  echo "Git repository already exists."
fi

echo ""
echo "=== Setup complete! ==="
echo ""
echo "Next steps:"
echo "  1. Create a GitHub repo:  https://github.com/new"
echo "  2. Push:  git remote add origin https://github.com/YOUR_USERNAME/LiteWebExtractor.git"
echo "            git push -u origin main"
echo "  3. GitHub Actions will build the APK automatically."
echo "  4. Download APK from: Actions tab → latest run → Artifacts"
echo ""

set -eux

# Create dummy API key if there is none
if [ ! -f "apiKeys.properties" ]; then
  echo 'OPENAI_API_KEY="dummy"' > apiKeys.properties
fi

# Define variables
ANDROID_SDK_URL="https://dl.google.com/android/repository/commandlinetools-mac-8512546_latest.zip" # Update this for other OS
ANDROID_SDK_HOME_DIR="$HOME/android-sdk"
ANDROID_SDK_TOOLS_DIR="$ANDROID_SDK_HOME_DIR/cmdline-tools"
ANDROID_HOME="$ANDROID_SDK_HOME_DIR"
GRADLEW="./gradlew"

# Step 1: Download Android SDK Command Line Tools
if [ ! -d "$ANDROID_SDK_HOME_DIR" ]; then
  echo "Downloading Android SDK Command Line Tools..."
  mkdir -p "$ANDROID_SDK_TOOLS_DIR"
  curl -o commandlinetools.zip "$ANDROID_SDK_URL"
  unzip -q commandlinetools.zip -d "$ANDROID_SDK_TOOLS_DIR"
  mv "$ANDROID_SDK_TOOLS_DIR/cmdline-tools" "$ANDROID_SDK_TOOLS_DIR/latest"
  rm -f commandlinetools.zip
  echo "Android SDK Command Line Tools installed."
fi

# Step 2: Set Environment Variables
echo "Setting up environment variables..."
export ANDROID_HOME=$ANDROID_HOME
export ANDROID_SDK_ROOT=$ANDROID_HOME
export PATH="$ANDROID_HOME/emulator:$ANDROID_HOME/platform-tools:$ANDROID_SDK_TOOLS_DIR/latest/bin:$PATH"

# Step 3: Accept Licenses
echo "Accepting Android SDK licenses..."
yes | sdkmanager --licenses

# Step 4: Install Required SDK Platforms and Tools
echo "Installing required SDK platforms and tools..."
sdkmanager "platform-tools" "build-tools;34.0.0" "platforms;android-34"

# Step 5: Build App
echo "Building the app..."
if [ -f "$GRADLEW" ]; then
  # Clean the project
  echo "Cleaning the project..."
  $GRADLEW clean

  # Build the app (Debug variant)
  echo "Building the Debug APK..."
  $GRADLEW assembleDebug

  echo "Build complete! Your APK can be found in app/build/outputs/apk/debug/"
else
  echo "Error: Could not find the Gradle wrapper script (gradlew). Please ensure you are in the correct project directory."
  exit 1
fi

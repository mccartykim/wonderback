# Development shell configuration
{ pkgs, androidSdk, gradle, pythonEnv }:

pkgs.mkShell {
  buildInputs = [
    androidSdk
    pkgs.jdk17
    gradle
    pythonEnv
    pkgs.git
    pkgs.curl
    pkgs.jq
    pkgs.go
  ];

  shellHook = ''
    export ANDROID_HOME="${androidSdk}/libexec/android-sdk"
    export ANDROID_SDK_ROOT="$ANDROID_HOME"
    export ANDROID_SDK="$ANDROID_HOME"
    export ANDROID_NDK_ROOT="$ANDROID_HOME/ndk/21.4.7075529"
    export ANDROID_AVD_HOME="$HOME/.wonderback/avd"
    export JAVA_HOME="${pkgs.jdk17}"
    export PATH="$ANDROID_HOME/platform-tools:$PATH"

    # Create AVD directory if it doesn't exist
    mkdir -p "$ANDROID_AVD_HOME"

    # Gradle needs local.properties to find the SDK
    if [ ! -f local.properties ]; then
      echo "sdk.dir=$ANDROID_HOME" > local.properties
      echo "Created local.properties with sdk.dir=$ANDROID_HOME"
    fi

    echo ""
    echo "TalkBack Agent Development Environment"
    echo "======================================="
    echo "Android SDK: $ANDROID_HOME"
    echo "Java:        $(java -version 2>&1 | head -1)"
    echo "Gradle:      $(gradle --version 2>/dev/null | grep '^Gradle' || echo 'available')"
    echo "Python:      $(python3 --version)"
    echo ""
    echo "Workflows:"
    echo "  nix run .#demo                 🎬 GESTURE DEMO (wow your coworkers!)"
    echo "  nix run .#gesture-demo         🎯 Gesture demo only (needs setup first)"
    echo ""
    echo "Individual commands (usually not needed):"
    echo "  nix run .#build                Build TalkBack APK"
    echo "  nix run .#build-sudoku         Build Sudoku test app"
    echo "  nix run .#create-avd           Create Android Virtual Device"
    echo "  nix run .#start-emulator       Start emulator with GUI"
    echo "  nix run .#server               Start analysis server"
    echo "  nix run .#test-server          Run Python server tests"
    echo "  nix run .#setup-adb            ADB reverse port forwarding"
    echo "  bd --no-db list                Check beads task status"
  '';
}

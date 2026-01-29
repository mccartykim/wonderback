# Build Sudoku test app
{ pkgs, androidSdk, gradle }:

pkgs.writeShellApplication {
  name = "talkback-build-sudoku";
  runtimeInputs = [ androidSdk pkgs.jdk17 gradle pkgs.coreutils ];
  text = ''
    REPO="''${REPO_ROOT:-$(git rev-parse --show-toplevel 2>/dev/null || pwd)}"
    cd "$REPO"

    export ANDROID_HOME="${androidSdk}/libexec/android-sdk"
    export ANDROID_SDK_ROOT="$ANDROID_HOME"
    export ANDROID_SDK="$ANDROID_HOME"
    export JAVA_HOME="${pkgs.jdk17}"

    echo "sdk.dir=$ANDROID_HOME" > local.properties

    echo "Building Sudoku Test App"
    echo "========================"
    gradle :sudoku-test-app:assembleDebug --no-daemon --warning-mode all
    echo ""
    echo "APK: sudoku-test-app/build/outputs/apk/debug/sudoku-test-app-debug.apk"
  '';
}

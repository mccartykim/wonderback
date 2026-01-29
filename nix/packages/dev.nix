# Full workflow: build Android + run server tests
{ pkgs, androidSdk, gradle, pythonEnv }:

pkgs.writeShellApplication {
  name = "talkback-agent-dev";
  runtimeInputs = [ androidSdk pkgs.jdk17 gradle pythonEnv pkgs.git pkgs.coreutils pkgs.findutils pkgs.gnused ];
  text = ''
    REPO="''${REPO_ROOT:-$(git rev-parse --show-toplevel 2>/dev/null || pwd)}"
    cd "$REPO"

    export ANDROID_HOME="${androidSdk}/libexec/android-sdk"
    export ANDROID_SDK_ROOT="$ANDROID_HOME"
    export ANDROID_SDK="$ANDROID_HOME"
    export JAVA_HOME="${pkgs.jdk17}"

    echo "sdk.dir=$ANDROID_HOME" > local.properties

    echo "=== [1/2] Android Build ==="
    gradle assemblePhoneDebug --no-daemon --warning-mode all

    echo ""
    echo "=== [2/2] Python Server Tests ==="
    cd "$REPO/server"
    python -m pytest test_server.py -v

    echo ""
    echo "All done. APKs:"
    cd "$REPO"
    find . -name "*.apk" -newer local.properties
  '';
}

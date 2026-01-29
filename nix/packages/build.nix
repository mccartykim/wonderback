# Build TalkBack APK
{ pkgs, androidSdk, gradle }:

pkgs.writeShellApplication {
  name = "talkback-agent-build";
  runtimeInputs = [ androidSdk pkgs.jdk17 gradle pkgs.coreutils pkgs.findutils pkgs.gnused ];
  text = ''
    REPO="''${REPO_ROOT:-$(git rev-parse --show-toplevel 2>/dev/null || pwd)}"
    cd "$REPO"

    export ANDROID_HOME="${androidSdk}/libexec/android-sdk"
    export ANDROID_SDK_ROOT="$ANDROID_HOME"
    export ANDROID_SDK="$ANDROID_HOME"
    export JAVA_HOME="${pkgs.jdk17}"

    echo "sdk.dir=$ANDROID_HOME" > local.properties

    echo "TalkBack Agent Build"
    echo "===================="
    echo "Java:    $(java -version 2>&1 | head -1)"
    echo "Gradle:  $(gradle --version | grep '^Gradle')"
    echo "SDK:     $ANDROID_HOME"
    echo ""

    MODE="''${1:---full}"
    case "$MODE" in
      --check)
        echo "=== Compile check (no APK) ==="
        gradle compilePhoneDebugKotlin compilePhoneDebugJavaWithJavac \
          --no-daemon --warning-mode all
        ;;
      --full|*)
        echo "=== assemblePhoneDebug ==="
        gradle assemblePhoneDebug --no-daemon --warning-mode all
        echo ""
        echo "APKs:"
        find . -name "*.apk" -newer local.properties
        ;;
    esac
  '';
}

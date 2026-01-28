{
  description = "TalkBack Agent - Accessibility analysis via macOS LLM inference";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
    android-nixpkgs = {
      url = "github:nickcao/nix-android";
      inputs.nixpkgs.follows = "nixpkgs";
    };
  };

  outputs = { self, nixpkgs, flake-utils, android-nixpkgs, ... }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = import nixpkgs { inherit system; config.allowUnfree = true; };

        # Android SDK configuration
        androidSdk = android-nixpkgs.packages.${system}.androidsdk (sdkPkgs: with sdkPkgs; [
          build-tools-34-0-0
          cmdline-tools-latest
          platform-tools
          platforms-android-34
          ndk-21-4-7075529
        ]);

        # Python environment for the analysis server
        pythonEnv = pkgs.python312.withPackages (ps: with ps; [
          fastapi
          uvicorn
          pydantic
          pyyaml
          pytest
          httpx        # needed by TestClient
          websockets   # needed by FastAPI WebSocket
          anyio        # async test support
          pytest-asyncio
          # ollama and zeroconf: install via pip in venv if needed
          # (ollama has binary deps, zeroconf needs ifaddr)
        ]);

      in {
        devShells.default = pkgs.mkShell {
          name = "talkback-agent";

          buildInputs = with pkgs; [
            # Android build
            androidSdk
            jdk17
            gradle

            # Python server
            pythonEnv
            python312Packages.pip

            # Development tools
            git
            curl
            jq

            # Beads (task management)
            go
          ];

          shellHook = ''
            export ANDROID_HOME="${androidSdk}/libexec/android-sdk"
            export ANDROID_SDK_ROOT="$ANDROID_HOME"
            export ANDROID_NDK_ROOT="$ANDROID_HOME/ndk/21.4.7075529"
            export JAVA_HOME="${pkgs.jdk17}"
            export PATH="$ANDROID_HOME/platform-tools:$PATH"

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
            echo "  ./build.sh                     Full Android build + Python tests"
            echo "  ./build.sh --android-only      Just Gradle build"
            echo "  ./build.sh --server-only       Just Python tests"
            echo "  cd server && python main.py    Start analysis server"
            echo "  adb reverse tcp:8080 tcp:8080  Forward port for ADB connection"
            echo "  bd --no-db list                Check beads task status"
          '';
        };

        # Server package — run the analysis server
        packages.server = pkgs.writeShellApplication {
          name = "talkback-agent-server";
          runtimeInputs = [ pythonEnv ];
          text = ''
            # Find server dir relative to script invocation or use REPO_ROOT
            SERVER_DIR="''${REPO_ROOT:-$(git rev-parse --show-toplevel 2>/dev/null || pwd)}/server"
            if [ ! -f "$SERVER_DIR/main.py" ]; then
              echo "Error: Cannot find server/main.py. Run from the repo root or set REPO_ROOT."
              exit 1
            fi
            cd "$SERVER_DIR"

            # Install pip-only deps if needed
            if ! python -c "import ollama" 2>/dev/null; then
              echo "Installing ollama + zeroconf..."
              pip install --quiet ollama zeroconf 2>/dev/null || true
            fi

            exec python main.py "$@"
          '';
        };

        # Run Python server tests
        packages.test-server = pkgs.writeShellApplication {
          name = "talkback-agent-test-server";
          runtimeInputs = [ pythonEnv ];
          text = ''
            SERVER_DIR="''${REPO_ROOT:-$(git rev-parse --show-toplevel 2>/dev/null || pwd)}/server"
            cd "$SERVER_DIR"
            exec python -m pytest test_server.py -v "$@"
          '';
        };

        # Quick ADB setup for device connection
        packages.setup-adb = pkgs.writeShellApplication {
          name = "talkback-agent-setup";
          runtimeInputs = with pkgs; [ androidSdk ];
          text = ''
            PORT="''${1:-8080}"
            echo "Setting up ADB reverse port forwarding on port $PORT..."
            adb reverse "tcp:$PORT" "tcp:$PORT"
            echo "Done! Android device will connect to localhost:$PORT"
            echo "which tunnels to this machine's port $PORT over USB."
          '';
        };

        # Full dev build: compile Android + run Python tests
        packages.dev-build = pkgs.writeShellApplication {
          name = "talkback-agent-dev-build";
          runtimeInputs = with pkgs; [ androidSdk jdk17 gradle pythonEnv git ];
          text = ''
            REPO="''${REPO_ROOT:-$(git rev-parse --show-toplevel 2>/dev/null || pwd)}"
            cd "$REPO"

            export ANDROID_HOME="${androidSdk}/libexec/android-sdk"
            export ANDROID_SDK_ROOT="$ANDROID_HOME"
            export JAVA_HOME="${pkgs.jdk17}"

            # Ensure local.properties
            echo "sdk.dir=$ANDROID_HOME" > local.properties

            echo "=== Android Build ==="
            echo "Java: $(java -version 2>&1 | head -1)"
            echo "SDK:  $ANDROID_HOME"

            MODE="''${1:---full}"
            case "$MODE" in
              --check)
                gradle compilePhoneDebugKotlin compilePhoneDebugJavaWithJavac --no-daemon --warning-mode all
                ;;
              --full|*)
                gradle assemblePhoneDebug --no-daemon --warning-mode all
                echo ""
                echo "APKs:"
                find . -name "*.apk" -newer local.properties 2>/dev/null || echo "(none found)"
                ;;
            esac

            echo ""
            echo "=== Python Tests ==="
            cd "$REPO/server"
            python -m pytest test_server.py -v

            echo ""
            echo "All done."
          '';
        };
      }
    );
}

{
  description = "TalkBack Agent - Accessibility analysis via macOS LLM inference";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils, ... }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = import nixpkgs {
          inherit system;
          config = {
            allowUnfree = true;
            android_sdk.accept_license = true;
          };
        };

        # Android SDK via nixpkgs built-in androidenv.
        # composeAndroidPackages lets us pick exactly what we need.
        androidComposition = pkgs.androidenv.composeAndroidPackages {
          buildToolsVersions = [ "34.0.0" ];
          platformVersions = [ "34" ];
          includeNDK = true;
          ndkVersions = [ "21.4.7075529" ];
          includeEmulator = true;  # Enable for TalkBack agent testing
          includeSystemImages = true;  # Enable for AVD support
          systemImageTypes = [ "google_apis" "google_apis_playstore" ];  # google_apis for userdebug (root access)
          abiVersions = [ "x86_64" "arm64-v8a" ];  # x86_64 for Intel/Linux, arm64-v8a for M1/M2 Macs
          includeSources = false;
        };

        androidSdk = androidComposition.androidsdk;

        # Gradle 8.14.3 — upgraded from 7.6.6 (no longer receives security updates).
        # AGP 7.2.2 requires Gradle 7.3.3+; gradle_8 is 8.14.3.
        gradle = pkgs.gradle_8;

        # Python environment for the analysis server
        pythonEnv = pkgs.python312.withPackages (ps: with ps; [
          fastapi
          uvicorn
          pydantic
          pyyaml
          pytest
          httpx
          websockets
          anyio
          pytest-asyncio
        ]);

      in {
        devShells.default = pkgs.mkShell {
          name = "talkback-agent";

          buildInputs = [
            androidSdk
            pkgs.jdk17
            gradle
            pythonEnv
            pkgs.python312Packages.pip
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
            echo "  nix run .#build                Build Android APK"
            echo "  nix run .#build -- --check     Compile check only (faster)"
            echo "  nix run .#test-server          Run Python server tests"
            echo "  nix run .#server               Start analysis server"
            echo "  nix run .#setup-adb            ADB reverse port forwarding"
            echo "  nix run .#start-emulator       Start emulator with GUI"
            echo "  nix run .#demo                 Demo: TalkBack agent with GUI"
            echo "  bd --no-db list                Check beads task status"
          '';
        };

        packages = {
          # Wrap build.sh with Nix-provided JDK + Android SDK.
          # This replaces build.sh's wget-gradle-from-internet approach
          # with a hermetic Nix-provided toolchain.
          build = pkgs.writeShellApplication {
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
          };

          # Run the analysis server
          server = pkgs.writeShellApplication {
            name = "talkback-agent-server";
            runtimeInputs = [ pythonEnv pkgs.git ];
            text = ''
              SERVER_DIR="''${REPO_ROOT:-$(git rev-parse --show-toplevel 2>/dev/null || pwd)}/server"
              if [ ! -f "$SERVER_DIR/main.py" ]; then
                echo "Error: Cannot find server/main.py. Run from the repo root or set REPO_ROOT."
                exit 1
              fi
              cd "$SERVER_DIR"

              # Install pip-only deps if needed (ollama has native bindings)
              if ! python -c "import ollama" 2>/dev/null; then
                echo "Installing ollama + zeroconf via pip..."
                pip install --quiet ollama zeroconf 2>/dev/null || true
              fi

              exec python main.py "$@"
            '';
          };

          # Run Python server tests
          test-server = pkgs.writeShellApplication {
            name = "talkback-agent-test-server";
            runtimeInputs = [ pythonEnv pkgs.git ];
            text = ''
              SERVER_DIR="''${REPO_ROOT:-$(git rev-parse --show-toplevel 2>/dev/null || pwd)}/server"
              cd "$SERVER_DIR"
              exec python -m pytest test_server.py -v "$@"
            '';
          };

          # ADB reverse port forwarding setup
          setup-adb = pkgs.writeShellApplication {
            name = "talkback-agent-setup";
            runtimeInputs = [ androidSdk ];
            text = ''
              PORT="''${1:-8080}"
              echo "Setting up ADB reverse port forwarding on port $PORT..."
              adb reverse "tcp:$PORT" "tcp:$PORT"
              echo "Done! Device connects to localhost:$PORT -> this machine's port $PORT over USB."
            '';
          };

          # Full workflow: build Android + run server tests
          dev = pkgs.writeShellApplication {
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
          };

          # Start emulator with GUI for demo viewing
          start-emulator = pkgs.writeShellApplication {
            name = "talkback-start-emulator-gui";
            runtimeInputs = [ androidSdk pkgs.coreutils ];
            text = ''
              REPO="''${REPO_ROOT:-$(git rev-parse --show-toplevel 2>/dev/null || pwd)}"
              cd "$REPO"

              export ANDROID_HOME="${androidSdk}/libexec/android-sdk"

              echo "╔═══════════════════════════════════════════════════════╗"
              echo "║   STARTING ANDROID EMULATOR WITH GUI                 ║"
              echo "╚═══════════════════════════════════════════════════════╝"
              echo ""

              AVD_NAME="talkback_test"
              if ! "$ANDROID_HOME/emulator/emulator" -list-avds 2>/dev/null | grep -q "^$AVD_NAME$"; then
                echo "❌ AVD '$AVD_NAME' not found!"
                echo ""
                echo "Available AVDs:"
                "$ANDROID_HOME/emulator/emulator" -list-avds 2>/dev/null || echo "  (none)"
                echo ""
                echo "To create the AVD, see SETUP.md"
                exit 1
              fi

              if adb devices | grep -q "emulator"; then
                echo "⚠️  Emulator already running!"
                echo ""
                adb devices
                echo ""
                echo "To stop it first: adb emu kill"
                exit 0
              fi

              echo "Starting emulator '$AVD_NAME' with GUI and audio..."
              echo ""
              echo "Features enabled:"
              echo "  ✓ GUI window (not headless)"
              echo "  ✓ Audio output (hear TalkBack speaking!)"
              echo "  ✓ Root access (-writable-system)"
              echo "  ✓ Permissive SELinux (for accessibility)"
              echo ""

              "$ANDROID_HOME/emulator/emulator" \
                -avd "$AVD_NAME" \
                -writable-system \
                -selinux permissive \
                -no-boot-anim \
                -gpu swiftshader_indirect \
                &

              EMULATOR_PID=$!
              echo "Emulator starting (PID: $EMULATOR_PID)..."
              echo "Waiting for boot..."

              timeout=60
              count=0
              until adb shell getprop sys.boot_completed 2>/dev/null | grep -q "1"; do
                if [ $count -ge $timeout ]; then
                  echo "❌ Timeout waiting for emulator"
                  exit 1
                fi
                echo -n "."
                sleep 1
                count=$((count + 1))
              done
              echo ""

              echo "✅ Emulator is ready!"
              adb root
              sleep 2
              echo ""
              echo "Next: nix run .#demo"
            '';
          };

          # Run TalkBack tester agent demo with GUI and delays
          demo = pkgs.writeShellApplication {
            name = "talkback-demo";
            runtimeInputs = [ androidSdk pythonEnv pkgs.coreutils ];
            text = ''
              REPO="''${REPO_ROOT:-$(git rev-parse --show-toplevel 2>/dev/null || pwd)}"
              cd "$REPO"

              export ANDROID_HOME="${androidSdk}/libexec/android-sdk"

              DELAY="''${1:-2.0}"

              echo "╔═══════════════════════════════════════════════════════╗"
              echo "║    TALKBACK TESTER AGENT - DEMO MODE                 ║"
              echo "╚═══════════════════════════════════════════════════════╝"
              echo ""
              echo "Demo with ''${DELAY}s delays for viewing"
              echo ""
              echo "Watch the GUI window to see:"
              echo "  - Agent tapping cells"
              echo "  - Number picker dialogs"
              echo "  - TalkBack announcements"
              echo ""

              if ! adb devices | grep -q "emulator"; then
                echo "❌ No emulator detected!"
                echo ""
                echo "Start emulator first: nix run .#start-emulator"
                exit 1
              fi

              echo "✓ Emulator detected"
              echo ""

              echo "🎮 Launching Sudoku app..."
              adb shell am force-stop com.wonderback.sudoku.debug 2>/dev/null || true
              sleep 1
              adb shell am start -n com.wonderback.sudoku.debug/com.wonderback.sudoku.MainActivity
              sleep 2
              echo ""

              echo "🤖 Starting Tester Agent with ''${DELAY}s demo delays..."
              echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
              echo ""

              cd "$REPO/agents"
              python3 tester_agent.py --debug-delay "$DELAY"

              EXIT_CODE=$?
              echo ""
              echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

              if [ $EXIT_CODE -eq 0 ]; then
                echo "✅ Demo complete! Agent solved the puzzle!"
              else
                echo "ℹ️  Demo complete! (Expected MVP behavior)"
              fi

              echo ""
              echo "To run again: nix run .#demo [delay-seconds]"
              echo "Examples:"
              echo "  nix run .#demo 3.0    # Slower (better for presenting)"
              echo "  nix run .#demo 1.0    # Faster"
            '';
          };
        };
      }
    );
}

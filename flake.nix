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
          # ollama and zeroconf may need to be installed via pip
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

            echo "TalkBack Agent Development Environment"
            echo "======================================="
            echo "Android SDK: $ANDROID_HOME"
            echo "Java:        $(java -version 2>&1 | head -1)"
            echo "Gradle:      $(gradle --version 2>/dev/null | grep '^Gradle' || echo 'available')"
            echo "Python:      $(python3 --version)"
            echo ""
            echo "Commands:"
            echo "  gradle assemblePhoneDebug    Build TalkBack APK"
            echo "  cd server && python main.py  Start analysis server"
            echo "  adb reverse tcp:8080 tcp:8080  Forward port for ADB connection"
            echo "  bd status                    Check beads task status"
          '';
        };

        # Server package
        packages.server = pkgs.writeShellApplication {
          name = "talkback-agent-server";
          runtimeInputs = [ pythonEnv ];
          text = ''
            cd ${self}/server
            exec python main.py "$@"
          '';
        };

        # Quick setup script
        packages.setup-adb = pkgs.writeShellApplication {
          name = "talkback-agent-setup";
          runtimeInputs = [ androidSdk ];
          text = ''
            echo "Setting up ADB reverse port forwarding..."
            adb reverse tcp:8080 tcp:8080
            echo "Done! Android device will connect to localhost:8080"
            echo "which tunnels to this machine's port 8080 over USB."
          '';
        };
      }
    );
}

# Create Android Virtual Device
{ pkgs, androidSdk }:

pkgs.writeShellApplication {
  name = "talkback-create-avd";
  runtimeInputs = [ androidSdk pkgs.coreutils ];
  text = ''
    export ANDROID_HOME="${androidSdk}/libexec/android-sdk"
    export ANDROID_AVD_HOME="$HOME/.wonderback/avd"
    mkdir -p "$ANDROID_AVD_HOME"

    AVD_NAME="talkback_test"

    if "$ANDROID_HOME/emulator/emulator" -list-avds 2>/dev/null | grep -q "^$AVD_NAME$"; then
      echo "✓ AVD '$AVD_NAME' already exists"
      exit 0
    fi

    echo "Creating AVD: $AVD_NAME"
    echo ""

    if [[ "$(uname -m)" == "arm64" ]]; then
      SYSTEM_IMAGE="system-images;android-34;google_apis;arm64-v8a"
      DEVICE="pixel_6"
      echo "Detected: Apple Silicon (M1/M2)"
    else
      SYSTEM_IMAGE="system-images;android-34;google_apis;x86_64"
      DEVICE="pixel_5"
      echo "Detected: Intel/AMD x86_64"
    fi

    echo "System Image: $SYSTEM_IMAGE"
    echo "Device: $DEVICE"
    echo ""

    echo "no" | "$ANDROID_HOME/cmdline-tools/19.0/bin/avdmanager" \
      create avd -n "$AVD_NAME" -k "$SYSTEM_IMAGE" -d "$DEVICE" --force

    echo ""
    echo "✅ AVD created successfully at: $ANDROID_AVD_HOME/$AVD_NAME.avd/"
  '';
}

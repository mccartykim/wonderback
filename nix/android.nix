# Android SDK configuration for TalkBack Agent
{ pkgs }:

let
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

in {
  androidSdk = androidComposition.androidsdk;
  
  # Gradle 8.14.3 — upgraded from 7.6.6 (no longer receives security updates).
  # AGP 7.2.2 requires Gradle 7.3.3+; gradle_8 is 8.14.3.
  gradle = pkgs.gradle_8;
}

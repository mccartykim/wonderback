# ADB reverse port forwarding setup
{ pkgs, androidSdk }:

pkgs.writeShellApplication {
  name = "talkback-agent-setup";
  runtimeInputs = [ androidSdk ];
  text = ''
    PORT="''${1:-8080}"
    echo "Setting up ADB reverse port forwarding on port $PORT..."
    adb reverse "tcp:$PORT" "tcp:$PORT"
    echo "Done! Device connects to localhost:$PORT -> this machine's port $PORT over USB."
  '';
}

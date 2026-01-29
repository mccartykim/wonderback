# Run Python server tests
{ pkgs, pythonEnv }:

pkgs.writeShellApplication {
  name = "talkback-agent-test-server";
  runtimeInputs = [ pythonEnv pkgs.git ];
  text = ''
    SERVER_DIR="''${REPO_ROOT:-$(git rev-parse --show-toplevel 2>/dev/null || pwd)}/server"
    cd "$SERVER_DIR"
    exec python -m pytest test_server.py -v "$@"
  '';
}

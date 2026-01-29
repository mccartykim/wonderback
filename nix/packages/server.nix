# Run the analysis server
{ pkgs, pythonEnv }:

pkgs.writeShellApplication {
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
}

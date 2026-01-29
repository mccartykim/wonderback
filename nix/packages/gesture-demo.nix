# Gesture-based demo using server API (wonderback-89)
{ pkgs, pythonEnv }:

pkgs.writeShellApplication {
  name = "talkback-gesture-demo";
  runtimeInputs = [ pythonEnv pkgs.coreutils ];
  text = ''
    REPO="''${REPO_ROOT:-$(git rev-parse --show-toplevel 2>/dev/null || pwd)}"
    cd "$REPO/agents"
    
    DELAY="''${1:-1.5}"
    MODE="''${2:-sudoku}"
    
    exec python3 gesture_demo.py --delay "$DELAY" --mode "$MODE"
  '';
}

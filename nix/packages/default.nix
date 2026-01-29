# All package definitions for TalkBack Agent
{ pkgs, androidSdk, gradle, pythonEnv }:

{
  # Import individual package modules
  build = import ./build.nix { inherit pkgs androidSdk gradle; };
  build-sudoku = import ./build-sudoku.nix { inherit pkgs androidSdk gradle; };
  create-avd = import ./create-avd.nix { inherit pkgs androidSdk; };
  server = import ./server.nix { inherit pkgs pythonEnv; };
  test-server = import ./test-server.nix { inherit pkgs pythonEnv; };
  setup-adb = import ./setup-adb.nix { inherit pkgs androidSdk; };
  dev = import ./dev.nix { inherit pkgs androidSdk gradle pythonEnv; };
  start-emulator = import ./start-emulator.nix { inherit pkgs androidSdk; };
  gesture-demo = import ./gesture-demo.nix { inherit pkgs pythonEnv; };
  enable-talkback-ui = import ./enable-talkback-ui.nix { inherit pkgs androidSdk; };
  demo = import ./demo.nix { inherit pkgs androidSdk gradle pythonEnv; };
}

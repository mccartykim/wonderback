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

        # Import modular configuration
        android = import ./nix/android.nix { inherit pkgs; };
        pythonEnv = import ./nix/python.nix { inherit pkgs; };
        
        # Extract from android module
        androidSdk = android.androidSdk;
        gradle = android.gradle;

        # Import all packages
        packages = import ./nix/packages { inherit pkgs androidSdk gradle pythonEnv; };

      in {
        # Development shell
        devShells.default = import ./nix/shell.nix {
          inherit pkgs androidSdk gradle pythonEnv;
        };

        # All packages
        packages = packages // {
          # Default package is the demo
          default = packages.demo;
        };
      }
    );
}

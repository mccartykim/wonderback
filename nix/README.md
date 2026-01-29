# Nix Module Organization

This directory contains modularized Nix configuration for the TalkBack Agent project.

## Structure

```
nix/
├── README.md           # This file
├── android.nix         # Android SDK and Gradle configuration
├── python.nix          # Python environment with dependencies
├── shell.nix           # Development shell (nix develop)
└── packages/           # All package definitions
    ├── default.nix     # Package index (imports all packages)
    ├── build.nix       # Build TalkBack APK
    ├── build-sudoku.nix # Build Sudoku test app
    ├── create-avd.nix  # Create Android Virtual Device
    ├── server.nix      # Analysis server
    ├── test-server.nix # Server tests
    ├── setup-adb.nix   # ADB port forwarding
    ├── dev.nix         # Full dev workflow
    ├── start-emulator.nix # Start emulator with GUI
    ├── gesture-demo.nix   # Gesture demo script
    └── demo.nix        # Complete one-command demo
```

## Why Modularized?

**Before:** 640+ line monolithic `flake.nix`  
**After:** ~50 line main flake + small focused modules

### Benefits

1. **Easier to Navigate** - Each file has one clear purpose
2. **Easier to Modify** - Change one package without touching others
3. **Better Git Diffs** - Changes are isolated to relevant files
4. **Reusable** - Modules can be imported by other projects
5. **Testable** - Each module can be tested independently

## Module Responsibilities

### `android.nix`
- Android SDK composition
- Gradle version
- NDK, emulator, system images

### `python.nix`
- Python 3.12 environment
- FastAPI, pytest, requests, etc.
- All Python dependencies

### `shell.nix`
- Development shell configuration
- Environment variables (ANDROID_HOME, etc.)
- Shell hook with helpful commands

### `packages/*.nix`
- Each file defines one package
- Self-contained with explicit dependencies
- Imported by `packages/default.nix`

## Adding a New Package

1. Create `nix/packages/my-package.nix`:
```nix
{ pkgs, androidSdk, ... }:

pkgs.writeShellApplication {
  name = "my-package";
  runtimeInputs = [ androidSdk ];
  text = ''
    echo "Hello from my package"
  '';
}
```

2. Add to `nix/packages/default.nix`:
```nix
{
  # ...
  my-package = import ./my-package.nix { inherit pkgs androidSdk; };
}
```

3. Use it:
```bash
nix run .#my-package
```

## Modifying Existing Packages

Just edit the relevant file in `nix/packages/`. Changes are automatically picked up by the main flake.

## Main Flake

The root `flake.nix` is now just:
- Inputs (nixpkgs, flake-utils)
- Module imports
- Glue code

All the actual logic lives in these modules.

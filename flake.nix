{
  description = "Koma development shell";

  inputs = {
    nixpkgs.url = "github:nixos/nixpkgs/nixos-unstable";
    treefmt-nix = {
      url = "github:numtide/treefmt-nix";
      inputs.nixpkgs.follows = "nixpkgs";
    };
    git-hooks = {
      url = "github:cachix/git-hooks.nix";
      inputs.nixpkgs.follows = "nixpkgs";
    };
    bun-flake = {
      url = "github:antoine-bouteiller/bun-flake";
      inputs.nixpkgs.follows = "nixpkgs";
    };
  };

  outputs = {
    self,
    nixpkgs,
    treefmt-nix,
    git-hooks,
    bun-flake,
  }: let
    forAllSystems = nixpkgs.lib.genAttrs ["aarch64-darwin" "x86_64-linux"];
    treefmt = forAllSystems (system: treefmt-nix.lib.evalModule nixpkgs.legacyPackages.${system} ./treefmt.nix);
  in {
    formatter = forAllSystems (system: treefmt.${system}.config.build.wrapper);

    checks = forAllSystems (system: {
      formatting = treefmt.${system}.config.build.check self;
    });

    devShells = forAllSystems (
      system: let
        pkgs = nixpkgs.legacyPackages.${system};
        preCommit = git-hooks.lib.${system}.run {
          src = ./.;
          # Exact refuses symlinks among app sources but skips dot directories.
          configPath = ".pre-commit/config.yaml";
          hooks = {
            treefmt = {
              enable = true;
              package = treefmt.${system}.config.build.wrapper;
            };
            gitleaks.enable = true;
          };
        };
      in {
        # NoCC: Exact's Rust builds link with the platform toolchain (Xcode's clang, the NDK's), not a Nix cc wrapper.
        default = pkgs.mkShellNoCC {
          packages =
            preCommit.enabledPackages
            ++ [
              bun-flake.packages.${system}.default
              # Exact installs its pinned stable and nightly toolchains through rustup (rust-toolchain.toml).
              pkgs.rustup
              pkgs.jdk25
            ];
          shellHook = ''
            mkdir -p .pre-commit
            ${preCommit.shellHook}
            export EXACT="''${EXACT:-$PWD/../exact}"
            # `cargo install`ed tools Exact's setup adds (wasm-bindgen).
            export PATH="$PATH:''${CARGO_HOME:-$HOME/.cargo}/bin"
            export ANDROID_HOME="''${ANDROID_HOME:-$HOME/Library/Android/sdk}"
          '';
        };
      }
    );
  };
}

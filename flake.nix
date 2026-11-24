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
  };

  outputs = {
    self,
    nixpkgs,
    treefmt-nix,
    git-hooks,
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
          configPath = ".pre-commit/config.yaml";
          hooks = {
            treefmt = {
              enable = true;
              package = treefmt.${system}.config.build.wrapper;
            };
            gitleaks.enable = true;
            conventional-commit = {
              enable = true;
              name = "conventional-commit";
              entry = "${pkgs.lib.getExe pkgs.nushell} tools/commit-msg.nu";
              language = "system";
              stages = ["commit-msg"];
            };
          };
        };
      in {
        default = pkgs.mkShellNoCC {
          packages =
            preCommit.enabledPackages
            ++ [
              pkgs.jdk21
              # tools/release.nu
              pkgs.nushell
              pkgs.gh
            ];
          shellHook = ''
            # CI commits nothing.
            if [ -z "''${CI:-}" ]; then
              mkdir -p .pre-commit
              ${preCommit.shellHook}
            fi
            export ANDROID_HOME="''${ANDROID_HOME:-$HOME/Library/Android/sdk}"
          '';
        };
      }
    );
  };
}

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
      url = "github:antoine-bouteiller/bun-flake/v1.4.5";
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
          configPath = ".pre-commit/config.yaml";
          hooks = {
            treefmt = {
              enable = true;
              package = treefmt.${system}.config.build.wrapper;
            };
            gitleaks.enable = true;
            commitlint = {
              enable = true;
              name = "commitlint";
              entry = "bunx commitlint --edit";
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
              bun-flake.packages.${system}.default
              pkgs.jdk21
              # semantic-release and commitlint run on Node.
              pkgs.nodejs_26
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

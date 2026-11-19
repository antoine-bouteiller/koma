{
  projectRootFile = "flake.nix";

  programs = {
    alejandra.enable = true;
    deadnix.enable = true;
    statix.enable = true;
    oxfmt.enable = true;
  };

  settings.global.excludes = [
    # Written by `exact new` and `bun exact.mjs update`.
    "exact.mjs"
    "AGENTS.md"
    "CLAUDE.md"
    ".vscode/**"
    # Hand-wrapped prose; oxfmt would pad every table.
    "*.md"
  ];
}

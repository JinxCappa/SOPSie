{
  description = "SOPSie JetBrains Plugin Development";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = nixpkgs.legacyPackages.${system};
      in {
        devShells.default = pkgs.mkShell {
          buildInputs = with pkgs; [
            jdk21
            gradle
            sops
            age
          ];

          shellHook = ''
            export JAVA_HOME=${pkgs.jdk21}
            echo "SOPSie JetBrains Plugin Dev Environment"
            java -version 2>&1 | head -1
            gradle --version 2>/dev/null | grep -E "^Gradle" || true
          '';
        };
      });
}

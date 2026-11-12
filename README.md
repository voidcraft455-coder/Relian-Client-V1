# Relian-Client-V1

Lunar-inspired Minecraft Java launcher and client project with original Relian branding.

## In-game client runtime

The separate `relian-client-fabric/` module is an early in-game runtime targeting Minecraft Java 1.21.1 on Fabric. It includes a rounded HUD editor and six client-side HUD modules: FPS, coordinates, keystrokes, armor durability, ping, and held-item durability. See [the runtime README](relian-client-fabric/README.md) for build instructions and limitations.

The runtime is not yet connected to a complete Minecraft installation and launch pipeline. The custom branded startup/loading overlay is not implemented yet.

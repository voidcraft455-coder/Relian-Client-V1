# Relian Client Fabric runtime

Relian's first in-game utility HUD runtime targets Minecraft Java **1.21.1 + Fabric** and Java 21.

## HUD Studio

Press **Right Shift** to open HUD Studio. You can enable or disable modules, select a module, adjust its color and scale, and drag its preview position. Settings persist in `config/relian-hud.json`.

## Included utility modules

- FPS counter
- Coordinates
- Keystrokes
- Armor status and durability
- Ping display
- Held-item durability
- Active potion effects and remaining duration
- Facing direction
- Biome name
- Movement speed
- Local clock
- Dimension
- Target block
- Health
- Hunger and saturation
- World time and day count
- Server address and player count
- Session timer

Each module has enable/disable state, screen position, scale, and color.

## Build

Use Java 21 and run `gradle build` in this directory. The mod JAR is produced in `build/libs/`.

## Installation

The Windows launcher workflow builds this runtime and bundles it inside the Windows installer. When a compatible Fabric 1.21.1 instance is created, the launcher installs the HUD JAR into its `mods` directory. Existing compatible instances can use **Install Relian HUD** on the Instances page.

## Cosmetic wardrobe status

The Windows launcher includes local asset libraries for skin, cape, wings, and hat PNG files. Assets are validated and stored on the user's computer. This Fabric runtime does not yet render custom capes, wings, or hats on player models; importing an asset does not equip it or make it visible to other players.

## Current limits

This runtime is a first version-specific adapter, not a universal mod. It targets Fabric 1.21.1 only. CI builds the JAR, and the launcher bundles it, but a live Minecraft 1.21.1 session is still required to verify the HUD editor and every module visually.

Relian focuses on utility HUDs and legitimate client features. It does not include automated attacks, auto-aim, fast-crystal automation, hidden-player tracking, or anti-cheat bypasses.
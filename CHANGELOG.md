# Changelog

Versioned releases are Git tags in this project, with no separate folder per version.

## Unreleased

- Add a Health Fill damage ghost: hold lost health for 0.3 seconds, then catch up
  with exponentially increasing speed, using the same custom artwork/transforms
  at reduced opacity. Starting speed is reduced to one third of the previous
  pace and capped at 3.2x that starting speed (about 2.76 seconds to catch up).
- Preview the health ghost with HUD Studio's health slider; stamina is unaffected.

- Add independent HUD artwork-layer transforms, visibility, opacity and styling,
  alongside whole-piece selection; preserve live fills, parent-relative dragging,
  undo/redo and existing HUD layouts.

- Remove the old translucent background behind custom health/stamina artwork;
  add replaceable backgrounds and artwork layers for all normal HUD text areas.
- Add HUD Studio for linked artwork uploads, placement, scale, rotation, opacity,
  text styling/visibility, undo/redo, and saved resolution-relative HUD layouts.
- Share the live HUD renderer between the game and editor, retaining functional
  resource fills/cooldowns and allowing built-in artwork to be disabled per piece.

- Add a UI Studio fill bucket with connected-region filling, paint opacity,
  selected-layer boundaries, moved-layer support, and undo/redo.

- Center UI Studio's canvas and add middle-mouse panning, cursor-anchored wheel
  zoom, and a Reset View action to recover artwork moved out of sight.

- Add UI Studio with brush/eraser, shape/line/text tools, advanced colors,
  image import/export, layered projects, and undo/redo.
- Assign transparent artwork to health/stamina frames and fills, class badge,
  ability panel, or crosshair; restore defaults without changing live HUD values.
- Add layered drawing, tool gesture, PNG, and runtime HUD replacement checks.

- Add a particle color editor with RGB/HSV controls, hex input, live preview,
  color swatches, session history, and Apply/Cancel behavior.

- Organize game, networking, UI, particle code, editor tools, tests, and docs
  into folders; keep the two application launchers at the project root.
- Add shared build/check scripts, keep generated files under build, and start
  particle file dialogs in particle-effects for new designs.

- Add Particle Studio, a standalone desktop effect editor with image imports,
  six emission presets, fourteen sliders, and an orbitable 3D preview.
- Save settings and transparent sprites in portable .pfx files; assign effects
  to game events or restore defaults without changing gameplay values.
- Load local event assignments at game startup, respecting particle quality
  settings, with depth-sorted image particles in the game renderer.
- Add portable asset, emission, editor UI, and game sprite rendering checks.

- Replace forward melee cones with full 3D boxes that follow movement, height,
  and turning, with collision against enemy body dimensions.
- Allow every Gauntlet ability in the air while preserving damage and timings.
- Carry Pummel targets vertically and release them to fall from their current
  height; keep Shockwave's radial area with vertical collision bounds.
- Render debug hitboxes and airborne impact effects at their actual height.
- Synchronize airborne Shockwaves over LAN (protocol 4; update every device).
- Add collision and airborne combat checks, including shared LAN behavior.

## 0.1.0 - 2026-10-23

Initial Git checkpoint of the existing game, including:

- Camera-relative movement, sprint stamina, double jump, and ground pound.
- Animated class selection and the playable Gauntlet moveset.
- Punch at 23 base damage with a 1.7-second cooldown; damage streak gains of
  0.05x per hit, capped at 1.35x, with a 6.5-second refreshable timer.
- LAN lobbies for up to eight players, shared combat, and training dummies.
- World-space particle effects and per-device quality settings.
- Developer console, separate health/stamina bars, and the F11 debug HUD.
- Automated gameplay, console, camera, particle, and lobby checks.
- Class reference, development handoff, and prepared Google Doc corrections.

This checkpoint preserves the current project; setting up Git does not change gameplay.

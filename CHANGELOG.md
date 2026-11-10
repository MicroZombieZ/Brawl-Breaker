# Changelog

Versioned releases are Git tags in this project, with no separate folder per version.

## Unreleased

- Apply Map Studio's depth/clipping fix to gameplay through a shared rasterizer;
  fix scenery/player/target overlap and depth-test transparent particles and
  shockwaves, including image particles crossing the camera boundary.

- Rework Map Studio navigation with screen-relative panning, cursor-centered
  zoom, selection/map framing, view presets, and visible camera controls.

- Fix disappearing Map Studio faces near the camera and incorrect object/outline
  overlap with near-plane clipping and per-pixel depth testing; align click
  selection with the frontmost surface.

- Make Shift-click add/toggle viewport selections in every Map Studio tool,
  preserving the existing selection and avoiding accidental transforms.

- Add Map Studio rectangle selection, Shift-add/toggle, list multi-selection,
  group clipboard/delete operations, and transforms with group cancellation.
- Add Map Studio Undo/Redo (Ctrl+Z/Ctrl+Y) with one history step per group drag.

- Add Map Studio keyboard shortcuts, independent object/spawn copy and paste,
  and selection handles for moving, yaw rotation, dimensions, and uniform scale.
  Include selection focus, precision dragging, cancellation, and typing guards.

- Add Map Studio (MapEditor.bat): 3D shape/ground editing, object colors and
  transforms, anchored/loose physics, collision flags, team spawns, targets,
  reusable assets, full map saving/loading, and a non-destructive physics preview.
- Automatically include valid saved maps in random Practice/LAN match selection.
  LAN protocol 5 transfers host-selected maps and moving scenery to all clients.
- Share map collision/platforms and loose-object physics between solo and LAN;
  use Team 1 spawn markers for all players, including late joins and respawns.

- Replace pause-menu Quit with Main Menu, leaving the session and unloading its
  map; add Quit to the opening menu.

- Hide the arena behind an opaque menu background until match entry; fade through
  black while loading Practice or LAN player/map state, and unload on disconnect.
- Store the shared practice map template separately in src/server-storage;
  LAN waiting rooms no longer instantiate active arena targets.

- Add an opening menu with the Cube Movement title, Host and Join LAN setup,
  disabled Quick Match, and direct entry into the test arena through Practice.

- Add a Health Fill damage ghost using the same custom artwork/transforms at
  reduced opacity: hold for 0.15 seconds, then sweep at exponentially increasing
  speed with a 0.5-second ramp and 3.2x speed cap. A faster starting speed keeps
  the complete effect to about 0.70 seconds after the latest damage.
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

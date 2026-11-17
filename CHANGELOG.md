# Changelog

Versioned releases are Git tags in this project, with no separate folder per version.

## Unreleased

- Replace empty lobby connection errors with readable details and persistent diagnostic logs; report server failures, reject occupied host ports, and clean up failed hosting attempts.

- Implement Shield in solo and LAN: short M1 Shield-Bash, limited-turn Charge,
  windup Swing, and Protect/Assault modes. Add frontal-only Protect damage
  reduction, mode-specific movement, riot-shield visuals, HUD/debug support,
  and Shield regression checks. LAN protocol 6 synchronizes mode state;
  document exact initial tuning while preserving Gauntlet stats.

- Update the planned Shield design: M1 Shield-Bash (23.5 damage / 0.45s),
  Charge (25 damage / 40s, 1s windup), Swing (35 damage / 50s, 1.3s windup),
  and Protect/Assault switching (25s). Keep unspecified design values TBD.

- Make camera obstruction follow rendered map triangles and their edges rather
  than object bounding boxes, including passable visible objects. Preserve camera
  clearance while removing false obstruction around rotated/curved/sloped shapes;
  leave player movement collision unchanged.

- Replace axis-separated object-box collision with continuous convex-mesh player
  collision using every face and edge of rotated boxes, spheres, cylinders and
  wedges. Add overlap recovery and surface sliding, including wedge slopes.
  Preserve vertical corrections through knockback/grabs and LAN ability movement;
  add per-face/rotation, diagonal sweep and overlap regression checks.

- Add multi-effect particle timelines with named steps, per-step settings, relative
  start delays, add/duplicate/import/reorder/remove, and sequence playback/looping.
  Save complete sequences in .pfx and assign them to gameplay events with bounded,
  cancellable delayed emission; preserve legacy single-effect loading.

- Add Reload Game to main/pause menus when launched through Run.bat. End the
  session, rebuild, and relaunch with fresh code/assets; offer retry on build errors.

- Add saved X/Y/Z particle origin offsets with Reset origin and a moving emitter
  marker. Apply offsets in emitter-facing coordinates in preview/gameplay.
  Add a show/hide wireframe training dummy for scale reference in Particle Studio.

- Add Practice and LAN host map pickers with saved .map files, built-in Practice,
  and Random. Refresh newly added maps, report invalid explicit selections, and
  preserve equal-chance random selection and host map transfer to guests.

Changes since **v0.1.0**, reviewed against Git history through `0e806c8` on
**2026-11-16**. Related follow-up fixes and tuning are summarized by their final
behavior below; these changes have not yet been assigned a new release version.

### Combat and airborne hitboxes

- Replace forward melee cones with full 3D attack boxes that follow player
  movement, height, and turning, and collide with enemy body dimensions.
- Allow every Gauntlet ability in the air while preserving damage and timings.
- Carry Pummel targets vertically and release them to fall from their current
  height. Preserve Shockwave's radial area with vertical collision bounds.
- Render debug hitboxes and airborne impact effects at their actual height.
- Synchronize airborne Shockwaves over LAN, introduced in protocol 4.
- Add collision and airborne combat checks, including shared LAN behavior.

### Particle Studio and custom particle shapes

- Add Particle Studio (`ParticleEditor.bat`) with image imports, six emission
  presets, playback controls, and an orbitable 3D preview.
- Expand from the original 14 sliders to 25: control aggressiveness, count,
  repeat interval, speed, spawn range, spread, heading, elevation, gravity,
  size, lifetime, growth, spin, opacity, speed/size/lifetime randomness,
  emission duration, fade-in/out, wind on three axes, air resistance, and
  debris bounce. Add a ground-plane collision toggle.
- Save settings and transparent sprites in portable `.pfx` files; assign them
  to game events or restore defaults. Load assignments at game startup and
  respect per-device particle quality settings. Preserve older effect defaults.
- Add an advanced color editor with RGB/HSV controls, hex input, live preview,
  swatches, recent colors, and Apply/Cancel behavior.
- Add a **Custom shape** tab inside Particle Studio with triangulated OBJ
  import/export, vertex-coordinate editing, axis scaling and rotation,
  centering/normalization, and undo/redo.
- Add Cube, Sphere, Cylinder, Cone, Trapezoid prism, and Pyramid starters.
- Add visible vertex handles: left-click and drag individual vertices in the
  screen plane, highlight the corresponding coordinate row, and prevent
  selection through hidden faces. Each drag is one undo step; Escape cancels it.
- Support right/middle-button or empty-space orbiting, wheel zoom, and framing
  in the shape workspace.
- Add **Custom mesh** and a reusable **Shape preset** picker on the main
  **Particle effects** tab, with Import preset, Save shape preset, and Refresh.
  Store reusable OBJ presets in `particle-effects/shapes`; selecting a preset
  preserves emission settings, while selecting a built-in shape disables the mesh.
- Embed custom meshes in saved effects and game assignments, so selected shapes
  do not depend on external OBJ files. Use effect color, size, spin, and physics.
- Share Map Studio's depth/clipping renderer for custom shapes in the shape tab,
  animated particle preview, and game. Limit shapes to 512 vertices and 256
  triangles, with an 8,192-triangle budget across live/delayed mesh particles.
- Add checks for portable assets, legacy compatibility, custom forces and fades,
  emission, presets, color editing, OBJ validation, vertex dragging, undo/redo,
  mesh budgets, and preview/game rendering.

### UI Studio

- Add UI Studio with brush/eraser, shape, line, and text tools; advanced color
  selection; image import/export; layered drawing projects; and undo/redo.
- Assign transparent artwork to health/stamina frames and fills, the class badge,
  ability panel, or crosshair. Restore defaults without changing live HUD values.
- Center the canvas and add middle-mouse panning, cursor-anchored wheel zoom,
  and Reset View to recover artwork moved out of sight.
- Add a connected-area fill bucket respecting paint opacity, selected-layer
  boundaries, moved layers, and undo/redo.
- Add layered drawing, tool gesture, PNG, and runtime HUD replacement checks.

### HUD Studio and health feedback

- Add HUD Studio for linked artwork uploads, placement, scale, rotation, opacity,
  text styling/visibility, undo/redo, and resolution-relative saved layouts.
- Support editing whole HUD pieces or individual artwork layers independently,
  including frames and fills, with parent-relative dragging and live resource fills.
- Remove the old translucent background behind custom health/stamina artwork.
  Add replaceable backgrounds and artwork layers for normal HUD text areas, and
  allow built-in artwork to be disabled per piece.
- Share the live HUD renderer between the game and editor, retaining functional
  resource fills and cooldown displays and compatibility with existing layouts.
- Add a damage ghost to **Health Fill** using its artwork and transforms at
  reduced opacity. After follow-up tuning, it holds for 0.15 seconds, then moves
  with exponentially increasing speed over a 0.5-second ramp, capped at 3.2x
  its starting speed. The complete effect lasts about 0.70 seconds after the
  latest damage. Stamina is unaffected.
- Preview the health ghost through HUD Studio's health slider.

### Main menu and match loading

- Add an opening menu with the Cube Movement title, Host, Join, Practice,
  disabled Quick Match, and Quit.
- Replace pause-menu Quit with Main Menu, leaving the session and unloading its map.
- Hide the arena behind an opaque menu background until match entry. Fade through
  black while loading Practice or LAN player/map state, and unload on disconnect.
- Store the shared practice-map template separately in `src/server-storage`;
  LAN waiting rooms no longer instantiate active arena targets.

### Map Studio, maps, and shared physics

- Add Map Studio (`MapEditor.bat`) with 3D shape/ground editing, object colors
  and transforms, anchored/loose physics, collision flags, team spawns, targets,
  reusable assets, full map saving/loading, and a non-destructive physics preview.
- Automatically include valid saved maps in randomized Practice/LAN map selection.
  LAN protocol 5 transfers the host-selected map and moving scenery to clients.
- Share map collision, platforms, and loose-object physics between solo and LAN.
  Use Team 1 spawn markers for all players, including late joins and respawns.
- Add keyboard shortcuts, independent object/spawn copy and paste, and selection
  handles for movement, yaw rotation, dimensions, and uniform scale. Include
  selection focus, precision dragging, cancellation, and typing guards.
- Add rectangle selection, Shift-add/toggle, list multi-selection, group clipboard
  and delete operations, and group transforms with cancellation.
- Make Shift-click toggle viewport selections in every tool without accidentally
  starting a transform or clearing the existing selection.
- Add Undo/Redo (Ctrl+Z/Ctrl+Y), with one history step per group drag.
- Rework camera navigation with screen-relative panning, cursor-centered zoom,
  selection/map framing, view presets, and visible camera controls.

### Rendering fixes

- Fix disappearing Map Studio faces near the camera and incorrect object/outline
  overlap through near-plane clipping and per-pixel depth testing. Align click
  selection with the frontmost surface.
- Apply the same fix to gameplay through a shared rasterizer, correcting scenery,
  player, and target overlap. Depth-test and blend transparent particles and
  shockwaves, including image particles crossing the camera boundary.
- Keep rendering fixes separate from player collision physics.

### Project organization and verification

- Organize game, network, UI, particle code, editor tools, tests, and documentation
  into dedicated folders, with application launchers at the project root.
- Add shared build/check scripts and keep generated output under `build`.
  Start particle Open/Save dialogs in `particle-effects` for new designs.
- Extend automated coverage for maps, editing tools, camera navigation, depth
  rendering, UI/HUD assets, and particles alongside the gameplay and LAN checks.
  
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

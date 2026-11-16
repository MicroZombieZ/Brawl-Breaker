# Cube movement

A minimal Java 3D demo using Swing and perspective projection. No dependencies.

See [CLASS_STATS.txt](docs/CLASS_STATS.txt) for class stats, ability details, passives,
and planned class designs. Keep it updated alongside gameplay changes.

Double-click `Run.bat` to build and launch the game. For terminal use, run
`scripts\Build.bat`, then `java -cp build/classes CubeMovement` from this folder.
Requires JDK 11 or newer (JDK 24 is installed on this computer).

The opening menu offers **Host**, **Join**, **Quick Match** (disabled for now),
and **Practice** beneath the Cube Movement title. Host and Join open the LAN
setup for your name, host IP, and port; the host starts the match once everyone
has joined. Back returns to the opening menu before connecting. Practice opens a map list; select a saved map, **Practice (built-in)**, or
**Random**, then click **Start Practice**. Escape pauses during play; F2 opens
the existing LAN lobby controls.
The pause menu's **Main Menu** button leaves the session and unloads the map
(hosting players also close their lobby). **Quit** is on the opening menu.

Startup and waiting rooms use an opaque, soft blue background, with no arena
rendering. Practice and LAN match entry fade out for 0.3 seconds, load the map
and player state at black, then fade in for 0.4 seconds. Controls stay blocked
throughout the transition. Leaving/disconnecting returns to lobby setup with
the map unloaded. Map templates live in `src/server-storage/MapStorage.java`;
Practice and the LAN host can select from `.map` files in `server-storage/maps`
or the built-in practice arena. **Random** gives each valid saved map and the
built-in arena an equal chance. **Refresh maps** discovers newly saved files;
missing or invalid explicit selections show an error without starting a match.
The lobby map picker is controlled by the host. This is local game data, not
cloud storage. The host sends the selected map and loose-object positions to
guests, including late joiners; guests don't need matching custom map files.
All devices need this updated game build (LAN protocol 5).

## Map Studio

Launch **`MapEditor.bat`**. The 3D viewport supports middle/right-drag to orbit,
Shift + middle/right-drag to pan in screen directions, cursor-centered wheel
zoom, and left-click to select. **F / 6** frames the complete selection and
sets the orbit center; **Home / Frame all** fits the entire map. Perspective,
Top, Front, and Right buttons change viewing direction without losing the
current orbit center. Orbiting cannot flip the camera upside down. Camera
controls are displayed above the viewport; navigation doesn't edit the map.
New/Open automatically frames the map. Left-drag in Move mode moves the
selected object or spawn along X/Z; use the axis handles or inspector for precise X/Y/Z,
dimensions, yaw, color, anchoring, player collision, and starting velocities.

Map Studio shortcuts: **1 Select**, **2 Move**, **3 Rotate**, **4 Dimensions**,
**5 Uniform Scale**, **6 / F Focus selection**, **Delete**, **Ctrl+C / Ctrl+V**
copy/paste, **Ctrl+X** cut, **Ctrl+D** duplicate, **Ctrl+S** save,
**Ctrl+Shift+S** save as, and **Ctrl+O** open. Text fields keep their normal
typing/editing shortcuts. Copy/paste supports objects, team spawns, and targets.

Select a model to reveal the current tool's handles. Move uses red X, green Y,
and blue Z axes. Dimensions changes local width/height/depth, following the
object's yaw. Rotate uses a green Y-rotation ring (drag left/right); the map
format currently supports yaw rotation only. Uniform Scale uses the white
center handle (drag right/up to enlarge). Hold **Shift** for fine adjustment;
**Escape** cancels a drag. The inspector updates when you release the mouse.
Spawn markers support move handles. Handles are hidden during physics preview.
The Map Studio viewport clips faces at the camera boundary and uses per-pixel
depth for surfaces and selection outlines. Clicking selects the nearest visible
surface. Manipulation handles remain an overlay so they stay accessible.
The game uses the same depth renderer for ground, scenery, players, and targets.
Particles and shockwaves depth-test against solid geometry and blend per pixel;
image particles clip correctly at the camera boundary. HUD and debug overlays
remain screen overlays.

In **Select (1)** mode, left-drag a selection rectangle to select objects and
spawn/target markers whose projected shapes overlap it (including obscured
items). Drag in either direction. Hold **Shift** to add; **Shift-click** toggles
an item. An empty box/click clears the selection; Escape cancels an unfinished
box. Shift-click also adds/toggles objects in Move, Rotate, Dimensions, and Scale
without starting a transform. For precision transforms, press Shift during a
handle drag. Shift-clicking empty space keeps the selection.
The list also supports Ctrl/Shift multi-selection. Copy, cut, paste,
duplicate, delete, and movement apply to all selected items. Rotation/resize/
scale apply to each selected object's own transform; markers only move.
The inspector and Save asset operate on the first selected item.
**Ctrl+Z** undoes and **Ctrl+Y / Ctrl+Shift+Z** redoes; toolbar buttons are also
available. History includes creation, deletion, paste, inspector changes, and
group transforms. A complete drag is one undo step. Up to 100 steps are kept;
editing after Undo clears Redo. New/Open clears history; saved files are only
updated when you save again. Selection and camera navigation do not add steps.

- Create boxes, spheres, cylinders, and wedges. Duplicate or delete objects.
- Select **Map / ground** for the map name, rectangular/elliptical plane,
  width/depth (4–200 units), and ground color.
- Add team spawn markers (teams 1–8) and training targets. Green markers are
  Team 1; purple markers are other teams. Everyone currently spawns on Team 1,
  cycling through its markers; combat/friendly fire rules are unchanged.
- **Save asset** stores the selected object's shape, dimensions, color, physics,
  and collision settings as a reusable `.asset`. **Insert asset** copies it
  into the map. Assets are embedded in saved maps, so deleting an asset file
  doesn't break a map that already uses it.
- **Test physics** previews a separate copy. **Stop** or editing restores the
  authored layout. Anchored objects remain fixed; loose objects fall with
  gravity, bounce, settle, and collide with the ground and solid object bounds.
- **New**, **Built-in Practice**, and **Open map** create/edit entire maps.
  **Save / Save As** writes to `server-storage/maps`; valid saved maps enter
  the map lists and Random pool automatically; reopen the picker or use Refresh maps.
  The built-in practice arena stays available; editing it creates a saved map.

Maps support 128 objects, 64 spawn markers, and 64 targets. Saving validates
finite coordinates, dimensions, and at least one unobstructed Team 1 spawn.
Invalid files are skipped by random selection. Save replaces files atomically.
Unsaved changes prompt before New/Open/closing. The editor saves authored
positions, never the temporary physics-preview positions.

Collision uses conservative axis-aligned bounding boxes for all shapes,
including rotated objects, spheres, cylinders, and wedges. Platforms support
landing and standing; wedges are decorative wedge meshes with box collision,
not walkable ramps. Loose physics has linear gravity/bounce, without rotation,
mass, player pushing, or a full rigid-body solver. Solo and LAN host share
the collision/physics code. Saved map edits take effect in the next match.

## Project folders

| Folder | Contents |
| --- | --- |
| `src/game` | Game window, movement, camera, and combat |
| `src/network` | LAN simulation and connections |
| `src/ui` | Developer console and lobby interface |
| `src/particles` | Shared particle simulation, effect files, sprites, and events |
| `tools/particle-editor` | Particle Studio application source |
| `tools/ui-editor` | UI Studio painting and layered drawing application |
| `tools/hud-editor` | HUD Studio placement, scaling, and linked artwork application |
| `tools/map-editor` | Map Studio 3D authoring application |
| `src/server-storage` | Map templates, format validation, and random map selection |
| `server-storage/maps`, `server-storage/assets` | Saved maps and reusable object assets |
| `particle-effects` | Saved particle designs and game event assignments |
| `ui-assets` | Layered UI projects, PNG exports, and assigned HUD artwork |
| `tests` | Automated checks |
| `docs` | Class stats, development handoff, and Google Doc update notes |
| `scripts` | Shared build and check commands |
| `build` | Generated classes and compiler lists; ignored by Git |

Keep the whole project together when copying it to another device. The game
and editor launchers stay at the top level and use the correct working folder,
including when launched from elsewhere. Build outputs stay out of the source
folders. Java classes retain their existing names and default package.

## Controls and movement

- WASD or arrow keys move the cube along the plane.
- Hold Shift while moving to sprint (7 units per second).
- The bottom-left stamina bar drains only while sprinting and actually moving.
- A full bar lasts 5 seconds of sprinting. Walking or standing recovers 15% per second.
- At empty stamina, you walk until stamina recovers to 25%, then sprinting becomes available again.
- In solo play, pausing or opening the class menu freezes stamina. In a LAN match, menus stop your inputs while the shared simulation continues.
- Press Space to jump. Release and press again after landing for another jump.
- Press Space again in the air for a second jump. Press Left Control while airborne to ground pound back to the floor.
- You can move and sprint in the air; the camera follows the jump.
- The third-person camera follows the cube at a desired distance of 8 units.
- Orbit through 360 degrees horizontally and look almost straight up or down.
- The camera stays above the ground and outside the player cube, adjusting its position when blocked.
- Move the mouse to orbit the camera around the cube; no clicking required.
- Scroll the mouse wheel to zoom in and out. Scrolling fully inward switches to first person; scrolling back out switches to third person. Zooming out is limited to 20 units.
- Horizontal mouse movement rotates the view; vertical movement changes its angle.
- During play, the cursor is hidden and recentered for continuous camera rotation.
- Esc opens the pause menu and unlocks the mouse. Click Resume or press Esc to play again.
- The menu also includes Quit. Switching to another window automatically pauses and releases the mouse.
- Movement follows the camera direction, so W / Up always moves away from you.
- The cube smoothly turns left and right around its upright Y axis, trailing the camera rotation slightly even while standing still.
- Press R to open the class menu and unlock the mouse. Choose Gauntlet, Mage, Ranger, or Rogue to preview its name, then click Equip to apply it and return to play.
- The slanted class sidebar slides diagonally down and left along its edges. Selecting a class slides its title up on the right, with the Equip button following shortly afterward.
- The equipped class appears in the top-right corner. Gauntlet is playable; the other classes remain placeholders.
- Press R or Esc, or click Close, to leave the class menu without equipping a new class.
- Close the window to exit.

Movement uses elapsed time and a walking speed of 4 units per second. Diagonal
movement is normalized, and the cube stays within the plane edges. The orbiting
camera renders the cube and grid in perspective through Java2D. Its vertical
angle allows -89 to 89 degrees; ground collision keeps the camera above the plane. Geometry behind the
camera is clipped so the grid renders correctly when viewed up close.

Solid scenery can be registered in `cameraObstacles` using world-space boxes
(`minX, minY, minZ, maxX, maxY, maxZ`). The camera pulls closer when a box blocks
its view and returns to its desired distance when clear. Training dummies do
not block the camera or ordinary player movement.

## Gauntlet training

Equip Gauntlet from the R menu to gain Reinforced (+15 HP, for 115 total) and its moveset.
Four stationary training dummies have 100 HP, take damage and knockback, and respawn at their original positions four seconds after defeat. They do not attack or walk. Blue dummies show their slow percentage and remaining duration.

| Input | Move | Base damage | Cooldown |
| --- | --- | --- | --- |
| Left mouse | Punch | 23 | 1.7 seconds |
| Q | Pummel | 30 across three hits | 45 seconds |
| E | Shockwave | 10 in the inner ring | 25 seconds |
| F | Hay-maker | 45 plus knockback | 50 seconds |

Directional attacks use full 3D boxes that follow the player's position, height, and current body facing, including as it turns toward the camera. Punch uses a 2-wide, 2-high, 2-deep box; Pummel uses a 1.8-wide, 2-high, 1.8-deep box. Each extends forward from the player's center, with vertical bounds from 0.5 units below the feet to 1.5 units above. They collide with the actual enemy body, including its height and rotation. Punch still checks once at 0.12 seconds, and Pummel attempts its grab at 0.20 seconds.

All abilities work in the air. Pummel carries its target as you move and turn in all three dimensions, then releases it at its current height to fall naturally. Shockwave keeps its surrounding area: targets within 2.5 units receive damage and a 70% slow; targets within 5 units receive a 40% slow for 3 seconds. Targets must also overlap the 2-unit vertical attack bounds. Its rings and particles appear at the impact height.

Hay-maker sweeps a 1.9-wide, 2-high, 0.95-deep box along its launch, traveling up to 3.6 units over 0.3 seconds and steering with your turning. Normal movement works alongside the launch. You can walk, sprint, jump, and ground pound during abilities and recovery. Attack durations, damage, and cooldowns are unchanged. Recovery prevents starting another attack; missed Pummel and Hay-maker attempts have 1.1 and 1.2 seconds of recovery respectively. Solo and LAN use the same collision logic; LAN snapshots synchronize attack facing and Shockwave height. Copy the updated folder to every device before connecting.

Kill-Streak increases damage for subsequent attacks by 0.05x per damaging hit, from 1x up to 1.35x (seven hits to reach the cap). Each damaging hit also refreshes a 6.5-second timer. Individual Pummel hits and each damaged Shockwave target grant their own stacks; damage is calculated using the multiplier at the start of the attack. After 6.5 seconds without a hit, the multiplier returns to 1x. Death and leaving Gauntlet also clear the streak. Pausing and the class menu freeze combat timers. Switching classes cannot bypass attack recovery or cooldowns.

The HUD shows HP, speed in world units per second, maximum speed, defense, damage multiplier and its timer, recovery time, and cooldowns. Gauntlet has 0% base defense; its durability passive increases HP. Visuals use colored cubes, animated gauntlets, damage flashes, floating damage numbers, shockwave rings, and world-space particles. Training dummies do not attack. LAN matches support combat between players; damage reduction remains unimplemented.

## Particle effects

Particles occupy 3D world positions and render through the camera with the player and NPC geometry. They move, fade, and expire; cube debris and shards fall and bounce. These effects are cosmetic and use the existing damage, movement, and cooldown rules.

| Action or status | Effect |
| --- | --- |
| Walk / sprint | Ground-contact dust, more frequent at sprinting speed |
| Jump / double jump | Takeoff dust; airborne ring and shards for the second jump |
| Land / ground pound | Landing dust scaled by impact speed; heavier dust, bouncing debris, and a ring for ground pounds |
| Punch | Gold fist trail; directional sparks and shards only when a hit connects |
| Pummel | Grab flash, slam dust and debris, then an impact burst for each confirmed punch |
| Shockwave | Expanding dust ring and scattered cube debris |
| Hay-maker | Air trails during the launch, with a larger spark/debris burst on a successful hit |
| Knockback | Short directional wisps and sliding dust |
| Kill-Streak | Subtle gold gauntlet glow and sparks that strengthen toward 1.35x; drifting sparks when the streak ends |
| Slowed | Sparse blue wisps around the affected entity |
| Defeat / respawn | Colored cube fragments; inward-gathering sparks followed by a delayed outward pulse |

Open **Esc > Particles** to choose **OFF**, **LOW** (160 particles), **MEDIUM** (400, default), or **HIGH** (800). Quality changes emission density and the maximum live count; lowering the setting immediately trims existing effects. In the console, use `particles low`, `particles medium`, `particles high`, `particles off`, or `particles clear`. Detailed `stats on` includes the current count and quality. A full `reset` restores medium quality.

Solo particle motion freezes with pause and follows `timescale`. LAN particles continue while your controls are paused, matching the ongoing shared match. Confirmed gameplay events carry sequence IDs so repeated snapshots do not replay bursts; joining a match does not replay its old effects. Each device chooses its own quality. Copy the updated game folder to every device because the network snapshot format now includes particle events.

## Particle Studio

On the **Particle effects** tab, **Shape preset** selects reusable custom meshes
without opening the shape editor. **Import preset** copies and normalizes a
triangulated OBJ into `particle-effects/shapes`; **Save shape preset** saves the
current edited mesh under a reusable name. Use **Refresh** after placing OBJ files
in that folder yourself. The main **Shape** selector shows **Custom mesh** when
one is active; choosing a built-in shape disables the custom mesh. Presets change
only geometry, preserving emission settings. Selected meshes remain embedded in
saved effects, so gameplay does not depend on the preset folder.

The **Custom shape** tab lives inside Particle Studio. Import a triangulated
Wavefront OBJ (geometry only, up to 512 vertices / 256 triangles / 256 KB), or
choose **Cube, Sphere, Cylinder, Cone, Trapezoid, or Pyramid** and click
**Create shape** (this replaces the current shape and can be undone). Imports are centered and normalized to one unit across
their longest dimension. Edit individual vertex X/Y/Z cells, apply axis scales
and rotations, or center/normalize again. **Undo/Redo** (Ctrl+Z/Ctrl+Y) cover shape
edits. Left-click a visible vertex handle to select it, then drag to move that vertex
in the screen plane. Orbit to adjust it along another direction, or use its
highlighted X/Y/Z row for exact coordinates. Hidden vertices cannot be picked
through faces. Each drag is one undo step; Escape cancels it. Right/middle-drag
or drag empty space to orbit, scroll to zoom, and use **Frame shape** to fit the view.
Export the edited geometry as OBJ or save the effect: its shape is embedded in
`.pfx`, including game assignments. **Use built-in shape** removes the mesh.
Custom shapes override the sprite and built-in shape, use the effect color,
size and spin, and render with the same depth-buffer and near-plane clipping
as Map Studio in both the shape tab and animated preview/game. OBJ materials,
textures, and animation are not imported; triangulate polygons before exporting
from your modeling software. A shared 8,192-triangle budget trims the oldest
mesh particles in addition to the normal quality count cap. Shapes are cosmetic;
the ground bounce uses the existing particle-size approximation.

Advanced customization includes speed/size/lifetime randomness (zero removes
variation), emission duration (0-5 seconds), fade-in/out as fractions of each
particle's lifetime, air resistance (0-10 per second), and world-axis X/Y/Z wind
acceleration (-20 to 20 units/s?). Ground collision can be disabled; debris bounce
strength is adjustable from 0 to 1 (cubes/shards, up to two bounces). Ground
collision uses the Y=0 plane, not map objects. Sparkle adds its existing birth
stagger to emission duration. Repeat interval can overlap unfinished emissions.
These settings are saved in `.pfx` files and used by game event assignments.
Older effects keep their original defaults. Images retain their original colors.


Double-click **ParticleEditor.bat** to open the separate desktop editor, or run
`scripts\Build.bat` and then `java -cp build/classes ParticleEditor` from the
project root. It uses the game's particle
simulation and requires JDK 11 or newer.

1. Choose **Sparkle**, **Dust Explosion**, **Burst**, **Cone**, **Ring**, or
   **Fountain**. Choose a built-in shape or **Import image**. Transparent PNGs
   preserve transparency; JPEGs and the first frame of GIFs also work. Images
   must be at most 2048 x 2048 pixels and under 16 MB. Imported images keep their
   own colors and aspect ratio; **Edit color** affects built-in shapes.
   The color editor provides RGB/HSV controls, a live swatch, `#RRGGBB` hex
   input, and recent/palette colors. Edits preview on existing and newly emitted
   built-in particles. **Apply** keeps the color; **Cancel** restores it. Recent
   colors last for the editor session; the chosen color saves in the effect file.
   Use the separate Opacity slider for transparency.
2. Adjust the sliders. Aggressiveness scales particle count and speed; count
   sets particles per burst; repeat interval controls looping. Speed, spawn
   range (emitter radius), cone spread (half-angle), heading, elevation, gravity,
   size, lifetime, growth, spin, and opacity control the effect. Heading is
   relative to the gameplay event's facing. Ring and Dust Explosion emit
   radially; elevation sets their lift, and Dust's spread adds vertical scatter.
   Sparkles stagger emission and twinkle. Particle sizes, lifetimes, and speeds
   have randomized variation. Changes apply to newly emitted particles.
3. Drag in the preview to orbit; scroll to zoom. The gold line shows emission
   direction. Use **Emit burst**, **Restart**, **Loop emission**, and **Pause**
   to inspect the effect. The preview emits one unit above the floor. It uses
   HIGH quality with an 800-particle cap; game quality settings still apply.
4. **Save** creates one portable `.pfx` file containing settings and the uploaded
   image. File dialogs start in `particle-effects` for a new design. **Open
   effect** restores it for further editing. Changing presets
   resets parameters while retaining the imported image; **Remove image**
   returns to built-in shapes. Unsaved edits prompt you before closing, opening,
   or creating another effect.
5. To use it in the game, select a **Game event** and click **Assign effect**.
   This writes `particle-effects/<EVENT>.pfx`. Restart the game to load it.
   **Restore default** removes that event's assignment. Assigning does not save
   your separate working file. Assigned effects replace that event's particle
   burst; ongoing trails/status particles and separate Shockwave rings remain.

Available events are TAKEOFF, DOUBLE_JUMP, LAND, GROUND_POUND, SWING, GRAB, SLAM,
HIT, SHOCKWAVE, DEFEAT, and RESPAWN. HIT applies to all confirmed attack impacts;
the editor currently assigns by event rather than by class or individual move.
Copy the `particle-effects` folder with the game to share assignments. In LAN,
assignments are local cosmetic assets; they are not transferred automatically.
Damage, collision, cooldowns, and the network format are unchanged. Invalid
assigned files log a warning and fall back to the built-in effect for that event.

## UI Studio

Double-click **UIEditor.bat** to open the UI painting application. For terminal
use, build with `scripts\Build.bat`, then run `java -cp build/classes UIEditor`.

- **Drawing:** brush, eraser, fill bucket, rectangle, ellipse, line, text, eyedropper, and
  Move Layer tools. Drag to paint or draw shapes; hold Shift for squares and
  circles. Choose filled or outlined shapes, brush/outline width, paint opacity,
  and zoom (25-400%). Hold the middle mouse button and drag anywhere in the
  workspace to pan the canvas. Scroll up/down to zoom in/out around the cursor;
  the zoom slider uses the workspace center. **Reset view** fits and centers the
  canvas. New/Open also reset the view. Panning/zooming do not alter artwork or
  undo history. The size control also sets text size (minimum 8 pixels).
  Select **FILL** and click to fill a connected area of matching color on the
  selected layer. It respects paint opacity and shape boundaries on that layer;
  other layers are not used as boundaries. Each fill is one undo step.
- **Color:** Edit color opens the advanced RGB/HSV picker with hex input and
  recent colors. Paint opacity controls transparency. The checkerboard shows
  transparent pixels; it is not exported.
- **Layers:** Add, Copy, Delete, Rename, Up/Down, visibility, and opacity operate
  on the selected layer. The top list entry draws on top. Move Layer drags its
  contents; Clear Layer erases its pixels. Hidden layers are protected from
  drawing. Documents support 1-16 layers and 16-1024 pixels per canvas axis.
- **History:** Undo/Redo covers complete strokes, shapes, image imports, and
  layer changes. Use Ctrl+Z, Ctrl+Y, or Ctrl+Shift+Z. History keeps up to 20 states
  and may trim older states for memory. Undo history is local to the session.
- **Files:** Save/Open `.uidraw` projects to preserve individual layers, names,
  positions, visibility, and opacity. Import PNG/JPEG/GIF (first frame) as a new
  layer, fitted to the canvas without stretching or enlarging. Images must be
  at most 2048 x 2048 and under 16 MB. Export PNG flattens visible layers with
  transparency. Ctrl+S saves the project; exporting or assigning does not save
  the editable project. New/Open/Close offer to save unsaved work.

To replace existing game UI, choose a slot under **Replace Game UI**. **New
canvas for slot** offers the slot's display size. **Assign artwork** writes
the visible composition to `ui-assets/<SLOT>.png`. **Open assigned artwork**
imports that PNG as a new layer for further editing; use the `.uidraw` project
to recover the original separate layers. **Restore game default** removes the
assignment. Restart the game after assigning or restoring artwork.

| Slot | Display size | Behavior |
| --- | --- | --- |
| `HEALTH_FRAME`, `STAMINA_FRAME` | 250 x 18 | Artwork overlays the bar; keep the middle transparent |
| `HEALTH_FILL`, `STAMINA_FILL` | 250 x 18 | Full-size artwork clips from left to right to the current resource value |
| `HEALTH_BACKGROUND`, `STAMINA_BACKGROUND` | 250 x 18 | Optional background behind custom fills/frames |
| `HEALTH_LABEL`, `STAMINA_LABEL` | 250 x 25 | Artwork behind the live resource label |
| `CLASS_BADGE` | 240 x 38 | Background behind the live class name |
| `CLASS_LABEL` | 240 x 38 | Optional artwork for the class text area |
| `ABILITY_PANEL` | 240 x 142 | Background behind live ability names and cooldowns |
| `ABILITY_LABELS` | 240 x 142 | Optional artwork for the ability text area |
| `CROSSHAIR` | 24 x 24 | Replaces the center cross during play |
| `LAN_STATUS` | 600 x 22 | Artwork for the live LAN status area |
| `DEBUG_INFO` | 650 x 48 | Artwork for the F11 debug information area |

Images scale to each slot. HP/stamina values and cooldowns remain live; use
HUD Studio below to position pieces or hide/style their text. A bar with custom
frame or fill artwork no longer has the old translucent background behind it.
Add an explicit background image if wanted. Unassigned/invalid slots use the
existing UI unless built-in artwork is disabled for that piece. Copy `ui-assets`
with the game to share skins; LAN does not transfer artwork automatically.

## HUD Studio

Double-click **HUDEditor.bat**, or build with `scripts\Build.bat` and run
`java -cp build/classes HUDEditor`. This is the placement editor; UI Studio
remains the painting/layering editor. They share the same `ui-assets` artwork.

1. Select a linked piece: **HEALTH**, **STAMINA**, **CLASS**, **ABILITIES**,
   **CROSSHAIR**, **LAN**, or **DEBUG**. Its outline appears in the preview.
2. Choose an **Artwork layer**, then **Upload image** to replace it with a
   PNG/JPEG/GIF (first frame). The image limit is 2048 x 2048 pixels / 16 MB;
   transparent PNGs work best. For health, the frame, fill, background, and label
   are separate images but stay linked as one moving HUD piece. Removing an
   image is staged until Save & Apply.
3. **Edit target** selects **Whole HUD piece** or **Selected artwork layer**.
   Choosing an artwork layer switches to individual editing. Move, scale, rotate,
   hide, or change opacity for just the frame, fill, background, or label. Layer
   X/Y values are offsets inside the parent piece; dragging follows its rotation
   and scale. Switch back to Whole HUD piece to transform everything together,
   preserving individual adjustments. The outline shows the current selection.
   Drag the selection in the preview or edit its X/Y values. Adjust width/height scale
   (0.1-5x), optionally locked together, rotation (-180 to 180 degrees), and
   opacity. Rotation is around the scaled piece's center. Bar fills still clip
   to the live resource value before being scaled and rotated.
4. Choose whether to show the piece, show live text, or use built-in artwork for
   empty slots. Disable built-in artwork to remove default backgrounds, fills,
   borders, panels, and crosshair strokes for that piece. Text has separate
   color and size controls; hide live text if your artwork already contains it.
   Layer text/default-art settings apply within the whole-piece settings. Parent
   and layer opacity/font size multiply; a layer color overrides the parent color.
5. **Save & Apply** writes `ui-assets/hud-layout.properties` and only changed
   artwork files. Restart the game to load them. Existing PNG assignments from
   UI Studio continue to work. Saving clears the editor's undo/redo history.

The preview uses the same HUD renderer as the game. Health/stamina sliders and
LAN/debug toggles let you inspect live states without changing gameplay.
Health Fill leaves a 40%-opacity ghost of lost health for 0.15 seconds after damage,
then accelerates exponentially over 0.5 seconds, capped at 3.2 times its starting
speed. It starts at 90% of the lost-health gap per second and finishes catching
up in about 0.55 seconds (about 0.70 seconds including the hold).
More damage restarts the hold; healing,
respawning, maximum-HP changes, and arena reset clear the trail. The ghost follows
the fill's custom artwork, transparency, visibility, and layer/piece transforms.
It applies only to Health Fill. Lower the preview health slider to inspect it.
In solo play the animation pauses with gameplay and follows timescale; in LAN
it continues while local menus are open.
The game still uses its real values, LAN status, and F11 debug toggle. Middle-drag
pans the preview; scrolling zooms it around the cursor. These preview controls
do not change the actual HUD scale. **Reset view** recovers the preview;
**Reset selection** restores the selected piece or layer's default position/style
while keeping uploaded images. Resetting the whole piece keeps its individual
layer adjustments; reset those layers separately if needed.

Undo/redo supports up to 40 placement/style/image edits, including whole drags
(Ctrl+Z / Ctrl+Y / Ctrl+Shift+Z). Ctrl+S saves and applies. **Export layout** saves
a placement/style backup; **Open layout** loads one with the current artwork.
Copy its PNG files as well when sharing. **Reload from game** reads the current
on-disk layout and images. Closing, opening, or reloading prompts for unsaved
changes. HUD positions use a 1000 x 700 reference frame and adapt to window
dimensions; scale remains relative to each piece's normal size. Layer offsets
stay relative to that piece. Layout format 2 saves both levels; existing format 1
layouts open with unchanged whole-piece placement and default layer transforms.
HUD Studio edits
the player HUD; pause/class/lobby menus and world-space NPC labels stay in their
existing interfaces. Gameplay values and network messages are unchanged.

## LAN lobbies

Up to eight players can share an arena on the same Wi-Fi or wired local network. Everyone needs the same updated game folder and JDK 11 or newer. Each device runs `Run.bat`; no additional libraries or account are required. This uses a direct connection to the host, with no internet matchmaking or automatic lobby discovery.

1. On the host device, press **F2**, or open **Esc > LAN Lobby**.
2. Enter your player name, leave the port at **24560**, and click **Host Lobby**.
3. Share the local IP shown in the lobby with your friends. If several addresses appear, use the one on the shared Wi-Fi/wired network.
4. On each other device, open the lobby, enter a name, the host IP, and the same port, then click **Join Lobby**. For testing two copies on one computer, use `127.0.0.1` as the host IP.
5. The roster shows everyone connected. The host clicks **Start Match**. Players can also join after a match has started.
6. Press **R** and equip **Gauntlet** to use its moves. Other players appear as colored cubes with names and HP. Attacks can hit other players or the shared training dummies.
7. Press **F2** to view the roster or **Leave Lobby**. Leaving restores a fresh solo arena. If the host leaves or closes the game, all guests disconnect and return to the lobby screen.

The host runs movement, stamina, jumping, combat, cooldowns, damage, grabs, knockback, slows, and respawns. Knocked-out players respawn after three seconds at full class HP with their damage streak cleared. Dummies respawn after four seconds. Shockwave slows reduce other players' movement speed. Menus, window switching, and the console stop your movement inputs but do not pause the match or make you invulnerable. A paused player can still be hit or pushed. Gameplay-changing dev commands are unavailable during a lobby session; `help`, `clear`, `hitboxes`, `stats`, and `particles` remain available.

If Windows asks about Java network access, allow it on your **private** network. The host must accept inbound TCP on the selected port (24560 by default). If a connection fails, check the host IP and port, that both devices are on the same network, and that the network permits device-to-device connections; guest Wi-Fi and some school networks isolate devices. The host computer must keep the game running. A different port can be selected if the default is already in use.

Network checks run real TCP connections on this machine. A physical second-device Wi-Fi test is still needed to verify your router and firewall configuration.

## Developer console

Press backtick (the key above Tab) to open the console. Solo gameplay pauses and the cursor unlocks; in a LAN match, only your controls pause. Enter runs a command; Up and Down recall command history. Backtick or Esc closes it. Opening from the pause or class menu returns to that menu; opening during play resumes play when closed. Commands are local to this running game and do not execute shell commands. Dev settings last until `reset` or restarting the game. Gameplay-changing commands require leaving the LAN lobby first.

| Command | Effect |
| --- | --- |
| `help` | Show all commands and usage examples |
| `clear` | Clear console output |
| `heal` | Restore maximum player HP |
| `hp <amount>` | Set HP between 0 and the current class maximum; 0 defeats the player |
| `stamina <0..100>` | Set stamina; a refill to at least 25 clears exhaustion |
| `god on/off` | Toggle immunity to incoming damage; does not restore HP |
| `cooldowns reset` | Set all ability cooldowns to zero |
| `cooldowns on/off` | Toggle ability cooldowns; turning off clears existing cooldowns |
| `streak <1..1.35>` | Set damage multiplier and give it a fresh 6.5-second timer; 1 clears it |
| `streak reset` | Clear multiplier and timer |
| `damage <amount>` | Deal 0–100000 damage to yourself; respects god mode |
| `tp <x> <z>` | Teleport to ground level within -10..10 on each axis, clamped to fit the player on the plane; cancel the current attack |
| `speed <0..10>` | Set walking and sprinting speed multiplier; 1 is normal |
| `dummy spawn` | Spawn a dummy two units ahead, clamped to the plane |
| `dummy reset` | Restore the original four dummies using the current dummy max HP |
| `dummy clear` | Remove all dummies and release an active grab |
| `dummy hp <1..100000>` | Set max HP for existing and future dummies; refill existing dummies immediately |
| `hitboxes on/off` | Show 3D melee boxes, Shockwave vertical bounds, and player/dummy hurtboxes |
| `stats on/off` | Toggle detailed position, velocity, speed/time settings, dev flags, and combat state |
| `particles off/low/medium/high` | Set local particle quality and its live-particle limit |
| `particles clear` | Clear local particles and visual tracking |
| `timescale <0..4>` | Scale movement, physics, combat, effects, and timers; 1 is normal, 0 freezes the simulation; camera and menus remain responsive |
| `reset` | Restore player, class selection, arena, camera, and all dev settings to startup defaults; keep the console open |

Cooldown commands do not remove attack animations or recovery. Core HP, stamina, and combat HUD remain visible when detailed stats are off. Customized dummy max HP persists through dummy respawns and `dummy reset`; a full `reset` restores it to 100.

Examples: `cooldowns off`, `dummy hp 500`, `tp 0 -2`, `speed 1.5`, `timescale 0.5`.

Run `scripts\Check.bat` from the project root to build and run all twelve checks.
To run one check, first use `scripts\Build.bat tests`, then, for example,
`java -cp "build/test-classes;build/classes" ParticleEditorCheck`. All check
sources live in `tests`.

## Versions and restore points

This folder is a local Git repository. `VERSION.txt` records the current release;
`CHANGELOG.md` describes the changes. The initial release is tagged `v0.1.0`.
History stays inside the hidden `.git` folder, so each version uses the same
project folder. Keep `.git` when moving or backing up the development project.
Compiled `.class` files are ignored and rebuilt by `Run.bat`.

Useful commands from this folder:

```text
git status                  Show changes since the last checkpoint
git diff                    Review changes before saving
git log --oneline --decorate Show saved checkpoints and version tags
git tag --list               List named versions
```

To save completed work, run `git add -A`, then `git commit -m "Describe the change"`.
To undo a saved change while retaining history, use `git revert <commit-id>`.
Milestone releases get version tags, such as `v0.2.0` for a feature release and
`v0.2.1` for a fix. Local commits work without a GitHub account or internet access.
No remote repository is configured by this setup.

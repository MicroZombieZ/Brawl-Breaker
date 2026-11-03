# UI artwork

Launch UIEditor.bat at the project root to paint layered designs. Save editable
projects as .uidraw and export transparent PNG images. Assign artwork to a game
slot to write a PNG here named HEALTH_FRAME, HEALTH_FILL, STAMINA_FRAME,
STAMINA_FILL, CLASS_BADGE, ABILITY_PANEL, CROSSHAIR, or another supported artwork
layer (backgrounds and live text areas are replaceable too). Restart the game to load
assignments; restore defaults from the editor. Copy this folder to share skins.

Health and stamina fills clip to live values. Frames overlay those fills; leave
their middle transparent. Class/cooldown text stays dynamic over the artwork.
Assignments are cosmetic and local to each device; they do not change gameplay.

Launch HUDEditor.bat to select linked HUD pieces, upload artwork layers, and
move/scale/rotate them with a shared live game preview. Save & Apply writes
hud-layout.properties here and updates only changed images. Copy this layout
and the PNG files together to share your HUD. Layout backups do not embed images.
Use Edit target to choose Whole HUD piece or Selected artwork layer. Individual
frame/fill/background/label transforms are saved inside the parent piece; moving
the whole piece preserves them. Older layouts open with default layer transforms.
Custom bar frames/fills no longer have the old translucent background behind
them; assign a background explicitly if wanted. Built-in artwork and live text
can each be hidden through HUD Studio.

@echo off
setlocal
cd /d "%~dp0.."
call scripts\Build.bat tests
if errorlevel 1 exit /b 1
for %%T in (WorldRenderCheck MapCheck HUDCheck UIEditorCheck ParticleEditorCheck HitboxCheck ParticleCheck LobbyCheck ConsoleCheck CombatCheck ShieldCheck MovementCheck StaminaCheck CameraCheck RotationCheck) do (
    echo Running %%T...
    java -Dcamera.maps=build/test-empty-map-pool -cp "build\test-classes;build\classes" %%T
    if errorlevel 1 exit /b 1
)
echo All checks passed.
exit /b 0

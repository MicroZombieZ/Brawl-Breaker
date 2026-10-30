@echo off
setlocal
cd /d "%~dp0"
javac --release 11 ParticleEditor.java ParticleEffect.java ParticleSprites.java ParticleSystem.java GameEffect.java CombatSystem.java
if errorlevel 1 (
    echo Compilation failed. Install JDK 11 or newer and try again.
    pause
    exit /b 1
)
java ParticleEditor
if errorlevel 1 pause
endlocal

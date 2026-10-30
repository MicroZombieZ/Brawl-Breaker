@echo off
setlocal
cd /d "%~dp0"
call scripts\Build.bat
if errorlevel 1 (
    pause
    exit /b 1
)
java -cp "build\classes" ParticleEditor
set "APP_EXIT=%errorlevel%"
if not "%APP_EXIT%"=="0" pause
exit /b %APP_EXIT%

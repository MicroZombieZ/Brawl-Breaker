@echo off
setlocal
cd /d "%~dp0"

:reload
call scripts\Build.bat
if errorlevel 1 goto build_failed

java -Dcamera.launcher=true -cp "build\classes" CubeMovement
set "APP_EXIT=%errorlevel%"
if "%APP_EXIT%"=="42" goto reload
if not "%APP_EXIT%"=="0" pause
exit /b %APP_EXIT%

:build_failed
echo Build failed. Fix the errors above, then retry.
choice /c RQ /n /m "[R] Rebuild and retry, [Q] Quit: "
if errorlevel 2 exit /b 1
if errorlevel 1 goto reload
exit /b 1

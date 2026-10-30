@echo off
setlocal
cd /d "%~dp0.."
where javac >nul 2>nul
if errorlevel 1 (
    echo JDK 11 or newer is required. Make sure javac is on PATH.
    exit /b 1
)
if not exist "build\classes" mkdir "build\classes"
if errorlevel 1 exit /b 1
> "build\sources.txt" (
    for /d %%D in ("src\*") do for %%F in ("%%D\*.java") do if exist "%%F" echo "src/%%~nxD/%%~nxF"
    for /d %%D in ("tools\*") do for %%F in ("%%D\*.java") do if exist "%%F" echo "tools/%%~nxD/%%~nxF"
)
javac --release 11 -d "build\classes" @build\sources.txt
if errorlevel 1 exit /b 1
if /i "%~1"=="tests" (
    if not exist "build\test-classes" mkdir "build\test-classes"
    if errorlevel 1 exit /b 1
    > "build\test-sources.txt" (for %%F in ("tests\*.java") do echo "tests/%%~nxF")
    javac --release 11 -cp "build\classes" -d "build\test-classes" @build\test-sources.txt
    if errorlevel 1 exit /b 1
)
exit /b 0

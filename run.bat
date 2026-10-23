@echo off
setlocal
cd /d "%~dp0"

set "LOCAL_JDK=%~dp0.jdk"

if defined JAVA_HOME if exist "%JAVA_HOME%\bin\javac.exe" goto run
set "JAVA_HOME="

call :findLocal
if defined JAVA_HOME goto run

echo JDK 25 was not found. Downloading Temurin JDK 25 into .jdk\ ...
powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; $ProgressPreference='SilentlyContinue'; New-Item -ItemType Directory -Force '%LOCAL_JDK%' | Out-Null; Invoke-WebRequest 'https://api.adoptium.net/v3/binary/latest/25/ga/windows/x64/jdk/hotspot/normal/eclipse' -OutFile '%LOCAL_JDK%\jdk.zip'; Expand-Archive '%LOCAL_JDK%\jdk.zip' '%LOCAL_JDK%' -Force; Remove-Item '%LOCAL_JDK%\jdk.zip'"
if errorlevel 1 goto downloadFailed

call :findLocal
if not defined JAVA_HOME goto downloadFailed

:run
echo Using JDK: %JAVA_HOME%
set "PATH=%JAVA_HOME%\bin;%PATH%"
call "%~dp0gradlew.bat" runClient %*
if errorlevel 1 (
    echo.
    echo runClient failed. See the output above.
    pause
    exit /b 1
)
exit /b 0

:findLocal
for /d %%D in ("%LOCAL_JDK%\jdk-25*") do (
    if exist "%%D\bin\javac.exe" set "JAVA_HOME=%%D"
)
exit /b 0

:downloadFailed
echo.
echo Could not download the JDK. Install JDK 25 and set JAVA_HOME, then run this again.
pause
exit /b 1

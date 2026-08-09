@echo off
setlocal enabledelayedexpansion
title Auto Compiler

echo Looking for a JDK 25 or higher...

set "FOUND_JDK="
set "FOUND_VER="

:: 1) Check if JAVA_HOME already points at a JDK 25+
if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\javac.exe" (
        call :CheckJdk "%JAVA_HOME%"
        if defined FOUND_JDK goto :JdkFound
    )
)

:: 2) Check if a JDK already on the PATH is 25+
where javac >nul 2>nul
if not errorlevel 1 (
    for /f "delims=" %%J in ('where javac') do (
        for %%P in ("%%~dpJ..") do (
            call :CheckJdk "%%~fP"
            if defined FOUND_JDK goto :JdkFound
        )
    )
)

:: 3) Search the common JDK installation directories
set "SEARCH_PATHS="%ProgramFiles%\Java" "%ProgramFiles(x86)%\Java" "%UserProfile%\.jdks" "C:\Java" "%ProgramFiles%\Eclipse Adoptium" "%ProgramFiles(x86)%\Eclipse Adoptium" "%LocalAppData%\Programs\Eclipse Adoptium" "%ProgramFiles%\Temurin" "%ProgramFiles%\AdoptOpenJDK" "%ProgramFiles%\Microsoft" "%ProgramFiles%\Amazon Corretto" "%ProgramFiles%\Zulu" "%ProgramFiles%\BellSoft""

for %%D in (%SEARCH_PATHS%) do (
    if exist %%D (
        for /f "delims=" %%F in ('dir /b /ad %%D 2^>nul') do (
            if exist "%%~D\%%F\bin\javac.exe" (
                call :CheckJdk "%%~D\%%F"
                if defined FOUND_JDK goto :JdkFound
            )
        )
    )
)

echo [ERROR] No JDK version 25 or higher was found on this system.
echo Please install JDK 25+ to build this project.
echo You can find JDKs at oracle.com/java/technologies/javase/jdk25-archive-downloads.html.
echo JDK 25 is recommended for this project.
echo.
pause
exit /b 1

:CheckJdk
set "CANDIDATE=%~1"
if not exist "%CANDIDATE%\bin\javac.exe" goto :eof

set "JAVA_VER="
set "MAJOR_VER="

:: Preferred: read the version from the JDK release file
for /f "tokens=2 delims==" %%V in ('type "%CANDIDATE%\release" 2^>nul ^| findstr /b /i "JAVA_VERSION="') do set "JAVA_VER=%%V"
if defined JAVA_VER (
    set "JAVA_VER=!JAVA_VER:"=!"
    for /f "tokens=1 delims=." %%M in ("!JAVA_VER!") do set "MAJOR_VER=%%M"
)

:: Fallback: derive the version from the folder name (e.g. jdk-25.0.2, temurin-25+36)
if not defined MAJOR_VER (
    for %%P in ("%CANDIDATE%") do set "CAND_LAST=%%~nxP"
    for /f "tokens=2 delims=-_" %%B in ("!CAND_LAST!") do set "VER_NUM=%%B"
    if not defined VER_NUM set "VER_NUM=!CAND_LAST!"
    for /f "tokens=1 delims=+" %%P in ("!VER_NUM!") do set "VER_NUM=%%P"
    for /f "tokens=1 delims=abcdefghijklmnopqrstuvwxyz-.+_" %%M in ("!VER_NUM!") do set "MAJOR_VER=%%M"
)

:: Make sure the major version is a real number before comparing
set /a "TESTNUM=!MAJOR_VER!" 2>nul
if errorlevel 1 goto :eof

if !MAJOR_VER! geq 25 (
    set "FOUND_JDK=%CANDIDATE%"
    set "FOUND_VER=!MAJOR_VER!"
)
goto :eof

:JdkFound
echo [SUCCESS] Found compatible JDK !FOUND_VER! at:
echo !FOUND_JDK!
echo.
set "JAVA_HOME=%FOUND_JDK%"
set "PATH=%FOUND_JDK%\bin;%PATH%"
goto :RunGradle

:RunGradle
echo ==========================================
echo    Running Gradle Build...
echo ==========================================
echo.
call gradlew.bat build
set "BUILD_RESULT=%errorlevel%"
if not "%BUILD_RESULT%"=="0" (
    echo.
    echo [ERROR] Gradle build failed with exit code %BUILD_RESULT%.
)
echo.
pause
exit /b %BUILD_RESULT%

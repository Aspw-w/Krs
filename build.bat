@echo off
setlocal enabledelayedexpansion
title Auto Compiler.

echo Looking for a JDK 25 or higher.

:: Define search paths (Common JDK installation areas)
set "SEARCH_PATHS="%ProgramFiles%\Java" "%ProgramFiles(x86)%\Java" "%UserProfile%\.jdks" "C:\Java""

:: Loop through directories to find a valid JDK
for %%D in (%SEARCH_PATHS%) do (
    if exist %%D (
        for /f "delims=" %%F in ('dir /b /ad %%D 2^>nul') do (
            set "FOLDER_NAME=%%F"
            
            :: Extract potential version number from folder name
            for /f "tokens=2 delims=-_" %%V in ("!FOLDER_NAME!") do set "VER_NUM=%%V"
            if "!VER_NUM!"=="" (
                for /f "tokens=1 delims=abcdefghijklmnopqrstuvwxyz-_ " %%V in ("!FOLDER_NAME!") do set "VER_NUM=%%V"
            )
            
            :: Clean version string to get the major version number
            for /f "tokens=1 delims=." %%M in ("!VER_NUM!") do set "MAJOR_VER=%%M"
            
            :: Check if version is 25 or higher
            if not "!MAJOR_VER!"=="" (
                if !MAJOR_VER! geq 25 (
                    if exist "%%~D\!FOLDER_NAME!\bin\javac.exe" (
                        set "FOUND_JDK=%%~D\!FOLDER_NAME!"
                        set "FOUND_VER=!MAJOR_VER!"
                        goto :JdkFound
                    )
                )
            )
        )
    )
)

:: Alternative Check: Is the currently active system JDK already 25+?
where javac >nul 2>nul
if %errorlevel% equ 0 (
    for /f "tokens=3" %%I in ('java -version 2^>^&1 ^| findstr /i "version"') do (
        for /f "tokens=1 delims=._-^"" %%M in ("%%I") do set "SYS_VER=%%M"
    )
    if !SYS_VER! geq 25 (
        echo [SUCCESS] Current system JDK is already version !SYS_VER!.
        goto :RunGradle
    )
)

echo [ERROR] No JDK version 25 or higher was found on this system.
echo Please install JDK 25+ to build this project.
echo.
pause
exit /b

:JdkFound
echo [SUCCESS] Found compatible JDK !FOUND_VER! at:
echo !FOUND_JDK!
echo.

:: Set the JAVA_HOME environment variable for this session
endlocal & (
    set "JAVA_HOME=%FOUND_JDK%"
    set "PATH=%FOUND_JDK%\bin;%PATH%"
)

:RunGradle
echo ==========================================
echo    Running Gradle Build...
echo ==========================================
echo.
call gradlew.bat build
pause

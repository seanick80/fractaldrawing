@echo off
REM Build and optionally run the Drawing App
setlocal enabledelayedexpansion

set BASEDIR=%~dp0
set SRC=%BASEDIR%src
set OUT=%BASEDIR%out
set LIB=%BASEDIR%lib

REM Build classpath from all JARs in lib\
set CP=
for %%j in ("%LIB%\*.jar") do (
    if "!CP!"=="" (set CP=%%j) else (set CP=!CP!;%%j)
)

if not exist "%OUT%" mkdir "%OUT%"

echo Compiling...
dir /s /b "%SRC%\*.java" > "%OUT%\sources.txt"
if "!CP!"=="" (
    javac -d "%OUT%" @"%OUT%\sources.txt"
) else (
    javac -d "%OUT%" -cp "!CP!" @"%OUT%\sources.txt"
)
if errorlevel 1 (
    echo Build failed.
    exit /b 1
)

echo Build successful.

if "%1"=="run" (
    echo Starting Drawing App...
    java -cp "%OUT%;!CP!" com.seanick80.drawingapp.DrawingApp --gradient-dir "%BASEDIR%data\gradients" --location-dir "%BASEDIR%data\locations"
)

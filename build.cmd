@echo off
REM Build and optionally run the Drawing App
setlocal

set BASEDIR=%~dp0
set SRC=%BASEDIR%src
set OUT=%BASEDIR%out

if not exist "%OUT%" mkdir "%OUT%"

echo Compiling...
dir /s /b "%SRC%\*.java" > "%OUT%\sources.txt"
javac -d "%OUT%" @"%OUT%\sources.txt"
if errorlevel 1 (
    echo Build failed.
    exit /b 1
)

echo Build successful.

if "%1"=="run" (
    echo Starting Drawing App...
    java -cp "%OUT%" com.seanick80.drawingapp.DrawingApp
)

@echo off
REM Build and run fractal regression tests
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

echo Running tests...
java -ea -cp "%OUT%" com.seanick80.drawingapp.fractal.FractalRenderTest

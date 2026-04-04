@echo off
setlocal enabledelayedexpansion

set BASEDIR=%~dp0
set OUT=%BASEDIR%out
set LIB=%BASEDIR%lib

if not exist "%OUT%\com" (
    echo No build found, building first...
    call "%BASEDIR%build.cmd"
)

REM Build classpath from all JARs in lib\
set CP=
for %%j in ("%LIB%\*.jar") do (
    if "!CP!"=="" (set CP=%%j) else (set CP=!CP!;%%j)
)

java -cp "%OUT%;!CP!" com.seanick80.drawingapp.DrawingApp --gradient-dir "%BASEDIR%data\gradients" --location-dir "%BASEDIR%data\locations"

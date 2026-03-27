@echo off
setlocal

set BASEDIR=%~dp0
set OUT=%BASEDIR%out

if not exist "%OUT%\com" (
    echo No build found, building first...
    call "%BASEDIR%build.cmd"
)

java -cp "%OUT%" com.seanick80.drawingapp.DrawingApp --gradient-dir "%BASEDIR%data\gradients" --location-dir "%BASEDIR%data\locations"

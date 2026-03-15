@echo off
setlocal

set BASEDIR=%~dp0
set OUT=%BASEDIR%out
set GRADIENT_DIR=%BASEDIR%src\com\seanick80\drawingapp\gradient\defaults

java -cp "%OUT%" com.seanick80.drawingapp.DrawingApp --gradient-dir "%GRADIENT_DIR%"

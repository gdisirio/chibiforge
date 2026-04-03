@echo off
setlocal

set "DIR=%~dp0"

if defined LOCALAPPDATA (
    set "CACHE_ROOT=%LOCALAPPDATA%\ChibiForge"
) else (
    set "CACHE_ROOT=%USERPROFILE%\AppData\Local\ChibiForge"
)

set "CACHE_DIR=%CACHE_ROOT%\openjfx"
set "LOG_FILE=%CACHE_ROOT%\ui.log"

if not exist "%CACHE_ROOT%" mkdir "%CACHE_ROOT%"
if not exist "%CACHE_DIR%" mkdir "%CACHE_DIR%"

start "" javaw -Djavafx.cachedir="%CACHE_DIR%" -cp "%DIR%chibiforge-ui.jar;%DIR%chibiforge.jar" org.chibios.chibiforge.ui.ChibiForgeLauncher %* >> "%LOG_FILE%" 2>&1


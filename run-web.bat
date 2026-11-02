@echo off
setlocal
title IT Incident Tracking System (Web Server)
set "ROOT=%~dp0"
cd /d "%ROOT%"
if exist "%ROOT%.env" (
  for /f "usebackq eol=# tokens=1,* delims==" %%A in ("%ROOT%.env") do set "%%A=%%B"
)
echo ====================================================================
echo    Starting IT Incident Tracking System (Browser Mode)
echo ====================================================================

set CP=incident tracking\dist\incident_tracking.jar;incident tracking\lib\mysql-connector-j-9.2.0.jar;incident tracking\build\classes

java -cp "%CP%" incident.tracking.IncidentTracking

pause

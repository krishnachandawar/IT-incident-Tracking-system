@echo off
setlocal
title IT Incident Tracking System (Console)
if exist ".env" (
  for /f "usebackq eol=# tokens=1,* delims==" %%A in (".env") do set "%%A=%%B"
)
echo ====================================================================
echo    Starting IT Incident Tracking System (Console Mode)
echo ====================================================================

set CP=incident tracking\dist\incident_tracking.jar;incident tracking\lib\mysql-connector-j-9.2.0.jar;incident tracking\build\classes

java -cp "%CP%" incident.tracking.IncidentTracking --console

pause

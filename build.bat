@echo off
setlocal
echo ====================================================================
echo    Compiling IT Incident Tracking System...
echo ====================================================================

if not exist "incident tracking\build\classes" mkdir "incident tracking\build\classes"
if not exist "incident tracking\dist" mkdir "incident tracking\dist"

powershell -NoProfile -Command "javac -cp 'incident tracking/lib/mysql-connector-j-9.2.0.jar' -d 'incident tracking/build/classes' (Get-ChildItem -Path 'incident tracking/src' -Filter '*.java' -Recurse | Select-Object -ExpandProperty FullName)"

jar --create --file "incident tracking\dist\incident_tracking.jar" --main-class "incident.tracking.IncidentTracking" -C "incident tracking\build\classes" .

echo Build complete! Target: incident tracking\dist\incident_tracking.jar

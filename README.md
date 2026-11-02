# IT Incident Tracking System

A full-lifecycle IT Incident Tracking System backed by MySQL. Now supports both **Interactive Web Browser Mode** and **Console Mode**.

## Quick Start (Browser Mode)

1. **Set up Database in MySQL Workbench**:
   - Open MySQL Workbench and run [`incident_tracking_workbench_setup.sql`](incident_tracking_workbench_setup.sql).
2. **Launch Application in Browser**:
   - Double-click [`run-web.bat`](run-web.bat) or run `java -cp "incident tracking\dist\incident_tracking.jar;incident tracking\lib\mysql-connector-j-9.2.0.jar" incident.tracking.IncidentTracking`.
   - Your browser will automatically open `http://localhost:8080/`.

See [WORKBENCH_AND_BROWSER_SETUP.md](WORKBENCH_AND_BROWSER_SETUP.md) for full setup instructions, seed logins, JDBC configurations, and query examples.
See [DOCUMENTATION.md](DOCUMENTATION.md) for installation, MySQL setup, role-specific usage, troubleshooting, and the incident workflow diagram.

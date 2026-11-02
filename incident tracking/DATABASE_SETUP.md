# MySQL setup (Workbench)

For the full click-by-click Workbench guide, see [WORKBENCH_CONNECTION.md](WORKBENCH_CONNECTION.md).

## What you need

- MySQL Server 8.0+ listening on `localhost:3306`
- MySQL Workbench
- Connector/J already in this repo: `lib/mysql-connector-j-9.2.0.jar`

Local JDBC account (created by the Workbench script):

- Database: `incident_tracking`
- User: `incident_user`
- Password: `Incident@123`

## Run the schema

1. In Workbench, connect as **root**.
2. Open and execute `database inserts/incident_tracking_workbench_setup.sql`.
3. Add a second Workbench connection as `incident_user` / `Incident@123` with default schema `incident_tracking`.

`db.properties` in this folder already matches that account. The Java portal and `access.MysqlConnectionTest` read it automatically.

## Prove the driver works

```powershell
Set-Location "d:\Tracking system\incident tracking"
.\scripts\test-mysql-connection.ps1
```

## Sample application logins (after the script)

| Username | Role | App password |
| --- | --- | --- |
| alice | USER | Password1234! |
| bob_tech | TECHNICIAN | Password1234! |
| carol_mgr | MANAGER | Password1234! |

These are application accounts in `app_users`, not MySQL server logins.

## Older restricted-role script

`database inserts/incident_tracking_setup.sql` and `db/db.sql` create four MySQL roles with **random** passwords. Prefer the Workbench script for local development. If you already used the older script, keep those generated passwords in environment variables as described in `DOCUMENTATION.md`.

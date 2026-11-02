# IT Incident Tracking System

> A Java and MySQL incident-management application with a browser dashboard, console mode, role-based access, approval workflows, ticket history, and audit logging.

[![Java](https://img.shields.io/badge/Java-8%2B-orange)](#technology-stack)
[![Database](https://img.shields.io/badge/MySQL-8.0%2B-blue)](#database-and-persistence)
[![Interface](https://img.shields.io/badge/UI-Browser%20%2B%20Console-16a085)](#running-the-application)

## Contents

- [Project at a glance](#project-at-a-glance)
- [Implemented functionality](#implemented-functionality)
- [Roles and permissions](#roles-and-permissions)
- [Technology stack](#technology-stack)
- [Project structure](#project-structure)
- [Requirements](#requirements)
- [Database and persistence](#database-and-persistence)
- [Configuration](#configuration)
- [Installation](#installation)
- [Running the application](#running-the-application)
- [Using the browser dashboard](#using-the-browser-dashboard)
- [API reference](#api-reference)
- [Incident lifecycle](#incident-lifecycle)
- [AI usage in this project](#ai-usage-in-this-project)
- [Troubleshooting](#troubleshooting)
- [Security and deployment notes](#security-and-deployment-notes)

## Project at a glance

This system records IT incidents from submission through resolution. A requester creates a ticket, a technician can claim and update it, and a manager can review, reassign, resolve, close, or reopen it. Each important action is persisted in MySQL so the application history remains available after a restart.

The project is intentionally simple to operate locally:

```text
Browser or console
        |
        v
Java HTTP server and service layer
        |
        v
MySQL Server (incident_tracking)
```

The web page is generated and served by the Java application. There is no separate frontend server or backend framework to configure.

## Implemented functionality

<details>
<summary><strong>Authentication and accounts</strong></summary>

- Login, logout, and session detection.
- Password hashing with a per-user salt.
- Public registration with validation for username, password, employee ID, department, and designation.
- Registration choices for `USER`, `TECHNICIAN`, and `MANAGER`.
- USER accounts are approved immediately.
- TECHNICIAN and MANAGER accounts are created as `PENDING`.
- The ADMIN account (the main manager) can approve or reject pending registrations.
- Pending and rejected accounts cannot authenticate.
- Duplicate username and employee ID detection.

</details>

<details>
<summary><strong>Incident and ticket management</strong></summary>

- Create an incident with issue type, description, department, designation, and requester details.
- Generate a unique token ID for every ticket.
- Calculate and store a priority level and priority score.
- List a user's own tickets and retrieve ticket history.
- Filter open tickets by issue type.
- Claim an unassigned ticket as a technician.
- Update status, estimated resolution time, and technician notes.
- Reassign tickets to another technician.
- Resolve, close, and reopen tickets according to role permissions.
- Track reissued tickets and the user or administrator who closed them.

</details>

<details>
<summary><strong>History, monitoring, and administration</strong></summary>

- Store ticket updates in `ticket_updates`.
- Store requester activity in `user_activity_log`.
- Store technician activity in `technician_activity_log`.
- Store manager activity in `manager_activity_log`.
- Store administrative actions in `admin_audit_log`.
- View all repository incidents as a manager or administrator.
- View manager audit activity.
- View technician directory entries.
- View user accounts, roles, and approval state.
- Promote or change roles through the administrative role-management endpoint.
- View administrative statistics and database status.

</details>

## Roles and permissions

| Role | Main purpose | Approval | Typical capabilities |
|---|---|---:|---|
| `USER` | Report incidents | Immediate | Create tickets, view own tickets and history |
| `TECHNICIAN` | Work on incidents | ADMIN approval | View work queue, claim tickets, add updates, change technical status |
| `MANAGER` | Manage operations | ADMIN approval | View all incidents, review activity, reassign, resolve, close, reopen |
| `ADMIN` | Main manager and system administrator | Seeded account | Approve registrations, manage users, inspect all tickets, audit administration |

> A MANAGER can manage operational tickets. Only `ADMIN` is the main manager for approving new technician and manager registrations.

## Technology stack

| Area | Implementation |
|---|---|
| Language | Java |
| Web server | JDK `com.sun.net.httpserver.HttpServer` |
| Browser UI | Embedded HTML, CSS, and JavaScript rendered by `WebUiRenderer` |
| Database | MySQL 8.0 or later |
| Database access | JDBC with MySQL Connector/J 9.2.0 |
| Build | Project `build.bat` |
| Configuration | `db.properties` and optional root `.env` |
| Modes | Browser mode and console mode |

## Project structure

```text
.
├── README.md
├── .env.example
├── .gitignore
├── build.bat
├── run-web.bat
├── run-console.bat
├── incident_tracking_v2_migration.sql
└── incident tracking/
    ├── db.properties
    ├── database inserts/
    │   └── incident_tracking_workbench_setup.sql
    ├── lib/
    │   └── mysql-connector-j-9.2.0.jar
    ├── scripts/
    │   └── test-mysql-connection.ps1
    ├── src/access/
    │   ├── AuthenticationService.java
    │   ├── DatabaseConnections.java
    │   ├── MysqlConnectionTest.java
    │   └── Role.java
    └── src/web/
        ├── IncidentService.java
        ├── WebServer.java
        └── WebUiRenderer.java
```

## Requirements

- Windows with PowerShell and Java available on `PATH`.
- JDK 8 or later.
- MySQL Server 8.0 or later running locally.
- MySQL Workbench is recommended for executing and inspecting SQL.
- Port `3306` available for MySQL.
- Port `8080` available for the application, unless changed with `PORT`.

Check Java:

```powershell
java -version
javac -version
```

## Database and persistence

### Fresh database setup

1. Start MySQL Server.
2. Open MySQL Workbench and connect as an administrative MySQL user.
3. Open [`incident_tracking_workbench_setup.sql`](incident%20tracking/database%20inserts/incident_tracking_workbench_setup.sql).
4. Execute the complete script.
5. Confirm that the `incident_tracking` schema and its tables appear.
6. Confirm that the Java JDBC account can connect.

The setup script creates or prepares:

- Database `incident_tracking`.
- Application user table `app_users`.
- Ticket table `tickets`.
- Ticket history table `ticket_updates`.
- User, technician, manager, and administrator activity tables.
- Technician directory data.
- Approval columns: `approval_status`, `approved_by`, and `approved_at`.
- The local JDBC user `incident_user`.
- Sample application accounts and sample tickets.

### Existing database migration

For a database created before the approval workflow was added, run [`incident_tracking_v2_migration.sql`](incident_tracking_v2_migration.sql) as a database administrator. Review the statements before rerunning a migration that has already been applied.

### Verify the connection

```powershell
Set-Location "D:\Tracking system\incident tracking"
.\scripts\test-mysql-connection.ps1
```

A healthy result reports a successful JDBC connection, the MySQL version, and the available tables.

### Workbench connection values

| Field | Local value |
|---|---|
| Host | `127.0.0.1` |
| Port | `3306` |
| Schema | `incident_tracking` |
| Username | `incident_user` |
| Password | `Incident@123` |

These are local development defaults. Change them for any shared, hosted, or production environment.

## Configuration

Copy [`.env.example`](.env.example) to `.env` in the repository root:

```powershell
Copy-Item .env.example .env
```

Example:

```properties
INCIDENT_DB_URL=jdbc:mysql://localhost:3306/incident_tracking?serverTimezone=UTC&allowPublicKeyRetrieval=true&useSSL=false
INCIDENT_DB_USER=incident_user
INCIDENT_DB_PASSWORD=Incident@123
PORT=8080
```

The launch scripts load `.env` automatically. `.env` is ignored by Git and must not be committed.

Configuration precedence is:

1. Environment variables loaded from `.env`.
2. Values in `incident tracking/db.properties`.
3. Application defaults, including port `8080`.

## Installation

<details>
<summary><strong>Complete first-time setup</strong></summary>

1. Clone or open the repository.
2. Install JDK and MySQL.
3. Start MySQL Server.
4. Run the Workbench setup script.
5. Copy `.env.example` to `.env`.
6. Adjust local database values if necessary.
7. Test JDBC connectivity.
8. Build the project.
9. Start browser mode.

Commands:

```powershell
Set-Location "D:\Tracking system"
Copy-Item .env.example .env
.\incident tracking\scripts\test-mysql-connection.ps1
.\build.bat
.\run-web.bat
```

</details>

## Running the application

### Browser mode

```powershell
.\run-web.bat
```

Open [http://localhost:8080/](http://localhost:8080/) if the browser does not open automatically.

### Console mode

```powershell
.\run-console.bat
```

### Direct Java launch

```powershell
java -cp "incident tracking\dist\incident_tracking.jar;incident tracking\lib\mysql-connector-j-9.2.0.jar" incident.tracking.IncidentTracking
```

Use `--console` at the end for console mode.

### Build after source changes

Stop any running Java server before rebuilding on Windows because the JAR may be locked:

```powershell
.\build.bat
```

## Using the browser dashboard

1. **Register or log in.** Select USER, TECHNICIAN, or MANAGER when registering.
2. **Wait for approval when required.** Technician and manager accounts show as pending until ADMIN approval.
3. **Create an incident.** Enter the issue type and a clear description.
4. **Track the token.** Use the generated token ID to locate the ticket and its history.
5. **Work the queue.** Technicians claim and update tickets.
6. **Review operations.** Managers inspect all incidents, reassign work, and review audit history.
7. **Approve registrations.** ADMIN uses the registration approvals panel to approve or reject pending accounts.
8. **Inspect persistence.** Refresh MySQL Workbench and query the tables while the app is running.

### Seed application accounts

These are application accounts, not MySQL accounts:

| Username | Role | Password |
|---|---|---|
| `alice` | USER | `Password1234!` |
| `bob_tech` | TECHNICIAN | `Password1234!` |
| `carol_mgr` | MANAGER | `Password1234!` |
| `admin` | ADMIN | `Password1234!` |

Change seeded credentials before using the application beyond local development.

## API reference

All JSON endpoints are served from `http://localhost:8080`. Login creates a session cookie used by protected endpoints.

| Method | Endpoint | Purpose |
|---|---|---|
| `GET` | `/api/status` | Check application and database status |
| `GET` | `/api/session` | Check the current login session |
| `POST` | `/api/login` | Authenticate a user |
| `POST` | `/api/register` | Register a USER, TECHNICIAN, or MANAGER |
| `POST` | `/api/logout` | End the current session |
| `GET` | `/api/tickets` | Retrieve tickets allowed for the current role |
| `POST` | `/api/tickets/create` | Create an incident |
| `POST` | `/api/tickets/claim` | Claim a ticket as a technician |
| `POST` | `/api/tickets/update` | Add a ticket update |
| `POST` | `/api/tickets/reassign` | Reassign a ticket |
| `GET` | `/api/ticket-updates` | Retrieve ticket update history |
| `GET` | `/api/activity` | Retrieve activity for the current view |
| `GET` | `/api/technicians` | Retrieve technician directory |
| `GET` | `/api/priority-score` | Calculate priority information |
| `GET` | `/api/admin/stats` | Retrieve administrator statistics |
| `GET` | `/api/admin/users` | Retrieve user accounts |
| `GET` | `/api/admin/registrations` | Retrieve pending registrations |
| `POST` | `/api/admin/registrations/decision` | Approve or reject a registration |
| `POST` | `/api/admin/users/role` | Change a user's role |
| `GET` | `/api/admin/tickets/all` | Retrieve all incidents |
| `POST` | `/api/admin/tickets/close` | Close an incident |
| `POST` | `/api/admin/tickets/reopen` | Reopen an incident |

Example status request:

```powershell
curl.exe http://localhost:8080/api/status
```

Expected response includes:

```json
{
  "database_connected": true,
  "jdbc_url": "jdbc:mysql://localhost:3306/incident_tracking..."
}
```

## Incident lifecycle

```text
New
  |
  v
Open -> Claimed -> In progress -> Resolved -> Closed
  |                                      ^
  +-------------- Reopened --------------+
```

Each transition is written to the relevant history and activity tables. The exact action available in the UI is controlled by the signed-in role and session.

## AI usage in this project

AI was used as a development assistant, not as a replacement for human ownership of the system.

| Area | How AI was used | Human responsibility |
|---|---|---|
| Code navigation | Located related Java services, SQL scripts, launch scripts, and UI code. | Confirmed the correct files and intended behavior. |
| Feature development | Helped implement browser UI flows, persistence paths, audit history, and registration approval. | Reviewed requirements, integrated changes, and made product decisions. |
| Debugging | Helped identify stale packaged output, backend parameter mismatches, Java syntax errors, and class-brace errors. | Ran the application and verified the diagnosis. |
| Database work | Helped align schema scripts and migration statements with the Java queries. | Executed SQL locally and checked the live MySQL database. |
| Testing | Helped create repeatable checks for login, registration, approval, rejection, ticket operations, and API responses. | Reviewed results and confirmed expected outcomes. |
| Documentation | Helped consolidate setup, usage, API, architecture, and troubleshooting information into this README. | Approved the content and remains responsible for keeping it accurate. |

AI-generated suggestions must still be reviewed for security, correctness, privacy, licensing, and compatibility with the actual database and runtime environment.

## Troubleshooting

<details>
<summary><strong>Login says “Failed to fetch”</strong></summary>

The Java server is usually not running or the browser is using the wrong port.

1. Run `.\run-web.bat`.
2. Open `http://localhost:8080/`.
3. Check `http://localhost:8080/api/status`.
4. Confirm `PORT` in `.env`.

</details>

<details>
<summary><strong>Database connection fails</strong></summary>

- Confirm MySQL Server is running in Windows Services.
- Confirm port `3306` is available.
- Confirm the schema setup script was executed.
- Check `INCIDENT_DB_URL`, `INCIDENT_DB_USER`, and `INCIDENT_DB_PASSWORD`.
- Run `incident tracking\scripts\test-mysql-connection.ps1`.

</details>

<details>
<summary><strong>Access denied for incident_user</strong></summary>

Run the Workbench setup script as a MySQL administrative user. The application password `Password1234!` is not the same as the MySQL password `Incident@123`.

</details>

<details>
<summary><strong>Build cannot replace the JAR</strong></summary>

Stop the running Java process, then run `.\build.bat` again. Windows locks a JAR while the web server is using it.

</details>

<details>
<summary><strong>Technician or manager cannot log in after registration</strong></summary>

That account is expected to remain blocked while its approval state is `PENDING`. Log in as `admin`, open the registration approvals panel, and approve it.

</details>

## Security and deployment notes

- Never commit `.env`, passwords, or private database credentials.
- Replace all sample passwords before sharing or deploying the application.
- Use TLS, a reverse proxy, secure cookies, and a production secret store for deployment.
- Restrict MySQL access to the application host.
- Review role permissions before exposing the application outside localhost.
- Treat user-entered incident descriptions and audit data as potentially sensitive.
- The supplied credentials and `useSSL=false` JDBC setting are for local development only.

## Current project status

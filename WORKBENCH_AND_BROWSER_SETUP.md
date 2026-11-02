# MySQL Workbench & Browser Tracking System Setup Guide

This guide details how to set up the **IT Incident Tracking System** with **MySQL Workbench**, connect via **JDBC**, and run the application in the **Browser** while preserving the core system design, data model, role hierarchy, and queue priority algorithms.

---

## 1. Repository & System Analysis Summary

The repository implements an IT Incident Tracking and Resolution platform:
- **Roles**:
  - `USER`: Submits incidents, tracks token status, views update history, reviews user activity log.
  - `TECHNICIAN`: Views open incident queue prioritized by severity + designation rank, claims tickets (`Open` &rarr; `Allocated`), reports progress (`In Progress`, `Waiting`, `Pending Manager Review`) with remaining ETA minutes and user-facing notes, views technician activity log.
  - `MANAGER`: Reviews unresolved tickets, updates status, approves resolutions (`Resolved` with ETA `0` from `Pending Manager Review`), reassigns tickets to registered technicians from `technician_directory`, inspects organization-wide audit history.
- **Incident Lifecycle**:
  ```text
  Open ──► Allocated ──► In Progress ──► Pending Manager Review ──► Resolved
               ▲              │                     ▲
               │              ▼                     │
               └────────── Waiting ─────────────────┘
  ```
- **Priority Scoring Formula**:
  $$\text{Priority Score} = (\text{Severity} \times 60) + (\text{Designation Rank} \times 40)$$
  - **Severity**: Security (5), Data Loss (4), Backup & Recovery (4), Hardware (3), Network/Database (2), Other (1).
  - **Designation Rank**: SDE1 (5), SDE2 (4), SDE3 (3), SDE4 (2), SDE5 (1), Other (0).
- **Dual Mode**:
  - **Browser Mode (Default)**: Opens interactive Web UI in your default browser at `http://localhost:8080/`.
  - **Console Mode**: Preserves the original CLI menus (`--console`).

---

## 2. Setting Up MySQL Workbench

### Step 2.1: Open MySQL Workbench & Connect to MySQL Server
1. Launch **MySQL Workbench**.
2. Under **MySQL Connections**, click on your local connection (typically **Local instance MySQL80** connecting to `localhost:3306`).
3. Enter your MySQL root password if prompted.

### Step 2.2: Execute the Setup Script
1. In MySQL Workbench, click **File &rarr; Open SQL Script...** (or press `Ctrl + Shift + O`).
2. Select the setup script located in the repository root:
   ```text
   D:\Tracking system\incident_tracking_workbench_setup.sql
   ```
   *(Also available at `incident tracking\database inserts\incident_tracking_workbench_setup.sql`)*
3. Click the **Lightning Bolt (Execute)** button on the toolbar (or press `Ctrl + Shift + Enter`).
4. In the bottom **Output** panel, verify that all statements executed successfully with green checkmarks.

### What the Script Sets Up:
- Database: `incident_tracking` (utf8mb4)
- Tables: `app_users`, `tickets`, `ticket_updates`, `user_activity_log`, `technician_activity_log`, `manager_activity_log`
- View: `technician_directory`
- Dedicated Service User: `'incident_user'@'localhost'` with password `'Incident@123'` (with full permissions on `incident_tracking.*`).
- Seed Accounts (Password for all three is `Password1234!`):
  - **Alice (User)**: username `alice`, Employee ID `EMP101`, Department `Engineering`, Designation `SDE2`
  - **Bob (Technician)**: username `bob_tech`, Employee ID `TECH201`, Department `IT Support`, Designation `Senior Technician`
  - **Carol (Manager)**: username `carol_mgr`, Employee ID `MGR301`, Department `IT Operations`, Designation `IT Manager`
- Seed Incidents: Sample tickets with updates and activity logs to test all priority levels and statuses.

---

## 3. JDBC Configuration

The application uses standard JDBC via MySQL Connector/J (`mysql-connector-j-9.2.0.jar` located in `incident tracking\lib`).

The connection settings are defined in `db.properties` (in the project root):

```properties
# JDBC Connection URL
db.url=jdbc:mysql://localhost:3306/incident_tracking?serverTimezone=UTC&allowPublicKeyRetrieval=true&useSSL=false

# Default user created by the Workbench setup script:
db.user=incident_user
db.password=Incident@123

# Alternatively, you can use your MySQL root credentials:
# db.user=root
# db.password=your_root_password
```

### Connection Fallback Order:
`DatabaseConnections.java` intelligently checks for credentials in this order:
1. Environment variables (`INCIDENT_DB_AUTH_USER`, `INCIDENT_DB_USER_USER`, `INCIDENT_DB_TECHNICIAN_USER`, `INCIDENT_DB_MANAGER_USER`).
2. Properties in `db.properties` (`db.user`, `db.password`, etc.).
3. General environment variables (`DB_USER`, `DB_PASSWORD`, `INCIDENT_DB_USER`, `INCIDENT_DB_PASSWORD`).
4. Default fallback: `incident_user` / `Incident@123`.

This means you do **not** need to set 8 complex environment variables manually—it connects out of the box with the credentials configured in MySQL Workbench.

---

## 4. Running the Application Through the Browser

### Option A: Using the Batch Script (Recommended)
Double-click `run-web.bat` in the project root:
```text
D:\Tracking system\run-web.bat
```
This starts the embedded Java HTTP server and **automatically opens your default browser** to:
```text
http://localhost:8080/
```

### Option B: Running from Terminal (PowerShell / Command Prompt)
```powershell
Set-Location 'D:\Tracking system'
java -cp "incident tracking\dist\incident_tracking.jar;incident tracking\lib\mysql-connector-j-9.2.0.jar" incident.tracking.IncidentTracking
```

### Option C: Running the Console CLI Version
If you ever want to run the original terminal interface:
```powershell
D:\Tracking system\run-console.bat
# or:
java -cp "incident tracking\dist\incident_tracking.jar;incident tracking\lib\mysql-connector-j-9.2.0.jar" incident.tracking.IncidentTracking --console
```

---

## 5. Web Browser Interface Features

The browser application provides role-tailored dashboards:

### 1. Authentication
- **Sign In**: Enter username and password, or click one of the **Quick Demo Login** buttons:
  - `User: Alice`
  - `Technician: Bob`
  - `Manager: Carol`
- **Register**: Public registration creates a `USER` account with password validation ($\ge 12$ characters).

### 2. User Portal (`alice`)
- **Register an Issue**: Select from the 17 standard IT categories, enter a description, and receive an instant tracking token (`TKN-...`).
- **My Submitted Tickets**: Live table of all tickets requested by the user, with colored status badges, ETA, assigned technician, and a **History** button showing the full update stream.
- **My Activity History**: Chronological log of all user events.

### 3. Technician Portal (`bob_tech`)
- **Open Incidents Queue**: Filterable by issue category, sorted by the **Priority Score** algorithm, with one-click **Claim Issue** action.
- **My Assigned Incidents**: Lists tickets assigned to the technician with an **Update Status** button (set `In Progress`, `Waiting`, or `Pending Manager Review`, update remaining ETA, and add user notes).
- **Technician Activity Log**: History of actions performed by the technician.

### 4. Manager Portal (`carol_mgr`)
- **Unresolved Incidents**: Comprehensive review table with:
  - **Review / Status**: Approve as `Resolved` (with ETA 0) or return to any active state.
  - **Reassign**: Select replacement technician from the `technician_directory` view with departmental notes.
  - **History**: Full update timeline.
- **All Incidents**: Complete directory of all tickets across all states.
- **Manager Audit Trail**: Organization-wide activity audit log.

---

## 6. Verifying Data in MySQL Workbench

You can run SQL queries in MySQL Workbench to verify changes made through the browser in real time:

```sql
USE incident_tracking;

-- 1. View all tickets and their current status
SELECT token_id, requester_username, issue_type, issue_status, technician_id, estimated_minutes 
FROM tickets 
ORDER BY created_at DESC;

-- 2. View ticket updates history
SELECT token_id, actor_username, actor_role, status, estimated_minutes, message, created_at 
FROM ticket_updates 
ORDER BY update_id DESC;

-- 3. View manager audit trail
SELECT activity_id, actor_username, actor_role, event_type, status, details, created_at 
FROM manager_activity_log 
ORDER BY activity_id DESC;

-- 4. View technician directory
SELECT * FROM technician_directory;
```

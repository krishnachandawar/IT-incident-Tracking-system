# MySQL setup

MySQL Server 8.0 and the Connector/J driver are required. The application defaults to:

- Host: `localhost`
- Port: `3306`
- Database: `incident_tracking`

The application does not contain database passwords. Its authentication service uses one restricted MySQL account, and each signed-in role uses a separate restricted account.

## 1. Run the complete backend setup script

1. Open MySQL Workbench and connect to your local MySQL server as an administrator.
2. Open `database inserts/incident_tracking_setup.sql`.
3. Execute the complete script. This single file creates the database and tables, activity logs, views, restricted roles, four local service accounts, and their role assignments.

The script creates each service account with a random password. Workbench displays the generated credentials in the result output. Save each password securely for the next step. Do not share or commit these generated credentials. Re-running the script does not reset passwords for accounts that already exist.

These MySQL credentials are private application service credentials. Do not give them to end users or technicians.

## 2. Configure the application environment

In PowerShell, set the database URL and the four generated account credentials in the same terminal that will launch the program. Replace each example locally with the matching password shown in Workbench; do not commit real passwords or send them in chat.

```powershell
$env:INCIDENT_DB_URL = "jdbc:mysql://localhost:3306/incident_tracking?serverTimezone=UTC"
$env:INCIDENT_DB_AUTH_USER = "incident_auth"
$env:INCIDENT_DB_AUTH_PASSWORD = "<the incident_auth password>"
$env:INCIDENT_DB_USER_USER = "incident_user"
$env:INCIDENT_DB_USER_PASSWORD = "<the incident_user password>"
$env:INCIDENT_DB_TECHNICIAN_USER = "incident_technician"
$env:INCIDENT_DB_TECHNICIAN_PASSWORD = "<the incident_technician password>"
$env:INCIDENT_DB_MANAGER_USER = "incident_manager"
$env:INCIDENT_DB_MANAGER_PASSWORD = "<the incident_manager password>"
```

In NetBeans, put the same variables in the process environment before starting NetBeans, then run the project. Alternatively, compile and run from the same PowerShell window after setting the variables. The MySQL Connector/J JAR must be on the runtime classpath.

For the NetBeans Ant project, also set `MYSQL_CONNECTOR_JAR` to the full path of your local Connector/J JAR before starting NetBeans. This lets `nbproject/project.properties` use your local driver path without committing a machine-specific path.

## 3. Run and create application accounts

Start the application. Register normal accounts from the login screen. Public registration can only create `USER` accounts. A MySQL administrator must promote a verified account to a staff role, for example:

```sql
UPDATE incident_tracking.app_users
SET role = 'TECHNICIAN'
WHERE username = 'verified-technician-login';
```

Use `MANAGER` instead of `TECHNICIAN` only for an authorized manager. Do not allow users to choose staff roles during registration.

## Existing service accounts

If you already created service accounts from an earlier setup, the script grants them their roles but does not change their existing passwords. Use their existing passwords in the environment variables. If a password was lost, reset that specific local service account in MySQL Workbench and update its matching environment variable.

## Access boundaries

The manager account is the only application role allowed to approve a ticket as resolved or reassign it. Technicians can claim open tickets and report progress or request manager review. User ticket queries are restricted to the signed-in username. MySQL grants restrict each role's tables and operations; keep the role credentials private because MySQL table grants alone do not provide per-row isolation if those service credentials are used outside the application.

Tickets and their update history are stored in MySQL and persist after the application exits.

## Activity history visibility

- Users select **View my user activity history**. The application queries `user_activity_log` by the signed-in username and shows events for that user's tickets, including technician and manager updates.
- Technicians select **View my technician activity history**. The application queries `technician_activity_log` by the signed-in username and shows their own ticket actions.
- Managers select **View manager audit history**. The manager database role can read `manager_activity_log`, which records actions across users, technicians, and managers.

Every ticket action writes its ticket update and the related activity rows in the same database transaction. Log accounts can read their role's log but cannot update or delete historical entries. Keep application service credentials private; identity filtering is performed by the application using the authenticated account.

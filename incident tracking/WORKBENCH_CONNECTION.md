# Connect this project to MySQL Workbench

MySQL Workbench is a desktop client. It does **not** replace the Java driver. Both talk to the same **MySQL Server** on your PC.

```text
Java app  --(Connector/J JAR)-->  MySQL Server :3306  <--  MySQL Workbench
```

| Piece | Location |
| --- | --- |
| Connector/J driver | `incident tracking/lib/mysql-connector-j-9.2.0.jar` |
| JDBC settings | `incident tracking/db.properties` |
| Workbench SQL script | `incident tracking/database inserts/incident_tracking_workbench_setup.sql` |
| Connection field list | `incident tracking/database inserts/workbench-connection-settings.txt` |
| Java tester | `access.MysqlConnectionTest` |

Local development account created by the script:

- MySQL user: `incident_user`
- Password: `Incident@123`
- Database: `incident_tracking`
- Host / port: `127.0.0.1` / `3306`

Use these only on your own machine. Do not deploy them to a public server.

## 1. Install MySQL Server and Workbench

1. Download **MySQL Installer for Windows** from [https://dev.mysql.com/downloads/installer/](https://dev.mysql.com/downloads/installer/).
2. Choose **Custom** (or Developer Default) and install at least:
   - MySQL Server 8.0 or later
   - MySQL Workbench
   - MySQL Connector/J (optional; this repo already ships `lib/mysql-connector-j-9.2.0.jar`)
3. During server configuration:
   - Keep port **3306**
   - Choose a **root** password and store it somewhere safe
   - Authentication: **caching_sha2_password** (default) is fine
4. Finish the installer and confirm **MySQL80** (or similar) is **Running** in Windows Services.

If Workbench is already installed, skip to step 2.

## 2. Create a Workbench connection (as root)

1. Open **MySQL Workbench**.
2. On the home screen click the **+** next to **MySQL Connections**.
3. Fill in:

   | Field | Value |
   | --- | --- |
   | Connection Name | `MySQL localhost root` |
   | Connection Method | `Standard (TCP/IP)` |
   | Hostname | `127.0.0.1` |
   | Port | `3306` |
   | Username | `root` |
   | Password | click **Store in Vault** and enter your MySQL root password |
   | Default Schema | leave empty for now |

4. Open the **SSL** tab and set **Use SSL** to **No** for local use.
5. Click **Test Connection**.
6. If it succeeds, click **OK** to save. If it fails, see [Troubleshooting](#troubleshooting).
7. Double-click the new connection tile to open a SQL session.

## 3. Run the project setup script

1. In the open Workbench session: **File → Open SQL Script**.
2. Select:

   `incident tracking/database inserts/incident_tracking_workbench_setup.sql`

3. Click the lightning bolt (**Execute**), or press `Ctrl+Shift+Enter`.
4. The last result grid should show:

   `Database incident_tracking setup completed successfully!`

That script creates:

- Database `incident_tracking`
- Tables, indexes, foreign keys, and `technician_directory`
- MySQL login `incident_user` / `Incident@123` with rights on that database
- Sample app users `alice`, `bob_tech`, `carol_mgr` (login password `Password1234!`)
- Sample tickets so you can browse data immediately

You can re-run the script. Existing rows for those sample tickets/users are updated, not wiped.

## 4. Add a Workbench connection as `incident_user`

This is the same account the Java app uses.

1. Home screen → **+** next to MySQL Connections.
2. Fill in:

   | Field | Value |
   | --- | --- |
   | Connection Name | `Incident Tracking (local)` |
   | Hostname | `127.0.0.1` |
   | Port | `3306` |
   | Username | `incident_user` |
   | Password | `Incident@123` (Store in Vault) |
   | Default Schema | `incident_tracking` |

3. SSL: **No**.
4. **Test Connection** → **OK**.
5. Open that connection. In SCHEMAS (left sidebar) you should see `incident_tracking` with tables `app_users`, `tickets`, `ticket_updates`, and the three activity logs.

## 5. Point the Java app at that same server

`incident tracking/db.properties` is already filled in:

```properties
db.url=jdbc:mysql://localhost:3306/incident_tracking?serverTimezone=UTC&allowPublicKeyRetrieval=true&useSSL=false
db.user=incident_user
db.password=Incident@123
```

The NetBeans project now compiles against `lib/mysql-connector-j-9.2.0.jar`. You do not need `MYSQL_CONNECTOR_JAR`.

## 6. Prove the Java driver can connect

In PowerShell:

```powershell
Set-Location "d:\Tracking system\incident tracking"
.\scripts\test-mysql-connection.ps1
```

A successful run prints `Connected.`, the MySQL version, and the table list.

From NetBeans: set **Run → Set Project Configuration / Main Class** to `access.MysqlConnectionTest`, or run `incident.tracking.IncidentTracking` after the test succeeds.

## 7. Browse data in Workbench while the app runs

Keep the `Incident Tracking (local)` connection open. After you register a ticket in the Java app, in Workbench run:

```sql
USE incident_tracking;
SELECT token_id, requester_username, issue_type, issue_status FROM tickets;
SELECT * FROM ticket_updates ORDER BY update_id DESC LIMIT 20;
```

Click the refresh icon on SCHEMAS if a new table does not appear.

---

## Troubleshooting

**Cannot connect to MySQL server on `127.0.0.1:3306`**

- Windows Services: start **MySQL80**.
- Confirm port 3306: `netstat -an | findstr 3306`.

**Access denied for user `root`**

- Use the root password from the MySQL installer, not `Incident@123`.
- If you forgot it, reset it with the installer or `mysqladmin`.

**Access denied for user `incident_user`**

- You have not run `incident_tracking_workbench_setup.sql` yet, or you ran the older `incident_tracking_setup.sql` that creates random passwords.
- Re-run the **workbench** setup script while connected as **root**.

**Public Key Retrieval is not allowed**

- JDBC URL already sets `allowPublicKeyRetrieval=true`. Keep that query string in `db.properties`.
- In Workbench, SSL = No for local TCP.

**ClassNotFoundException: `com.mysql.cj.jdbc.Driver`**

- The JAR must be `incident tracking/lib/mysql-connector-j-9.2.0.jar`.
- In NetBeans: **Clean and Build**. `nbproject/project.properties` references that lib path.

**Unknown database `incident_tracking`**

- The setup script was not executed. Repeat section 3 as **root**.

**Java compiles but login fails with sample users**

- App login is `alice` / `Password1234!` (12+ characters). That is an *application* password, not the MySQL password.
- MySQL password `Incident@123` is only for Workbench and JDBC.

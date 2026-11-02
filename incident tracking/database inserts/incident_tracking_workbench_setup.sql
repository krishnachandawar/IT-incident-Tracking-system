-- ==============================================================================
-- IT Incident Tracking System - MySQL Workbench Complete Setup Script
-- ==============================================================================
-- Open this file in MySQL Workbench and click the lightning bolt icon (Execute).
-- It will:
--   1. Create the incident_tracking database
--   2. Create all necessary tables, indexes, and constraints
--   3. Create the technician_directory view
--   4. Configure the 'incident_user' service account with password 'Incident@123'
--   5. Seed initial accounts (User, Technician, Manager, Admin) with password 'Password1234!'
--   6. Seed sample tickets, updates, and activity history
-- ==============================================================================

CREATE DATABASE IF NOT EXISTS incident_tracking
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

USE incident_tracking;

-- ------------------------------------------------------------------------------
-- 1. Table: app_users
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS app_users (
    username VARCHAR(64) NOT NULL,
    password_salt VARCHAR(32) NOT NULL,
    password_hash VARCHAR(64) NOT NULL,
    employee_id VARCHAR(64) NOT NULL,
    department VARCHAR(120) NOT NULL,
    designation VARCHAR(120) NOT NULL,
    role ENUM('USER', 'TECHNICIAN', 'MANAGER', 'ADMIN') NOT NULL DEFAULT 'USER',
    approval_status ENUM('PENDING', 'APPROVED', 'REJECTED') NOT NULL DEFAULT 'APPROVED',
    approved_by VARCHAR(64) NULL,
    approved_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (username),
    UNIQUE KEY uq_app_users_employee_id (employee_id)
    ,KEY ix_app_users_approval (approval_status, role)
) ENGINE=InnoDB;

-- ------------------------------------------------------------------------------
-- 2. Table: tickets
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tickets (
    token_id VARCHAR(40) NOT NULL,
    requester_username VARCHAR(64) NOT NULL,
    employee_id VARCHAR(64) NOT NULL,
    user_department VARCHAR(120) NOT NULL,
    designation VARCHAR(120) NOT NULL,
    issue_type VARCHAR(100) NOT NULL,
    description TEXT NOT NULL,
    priority_level ENUM('Critical', 'High', 'Medium', 'Low') NOT NULL DEFAULT 'Medium',
    issue_status ENUM(
        'Open',
        'Allocated',
        'In Progress',
        'Waiting',
        'Pending Manager Review',
        'Resolved'
    ) NOT NULL DEFAULT 'Open',
    technician_id VARCHAR(64) NULL,
    technician_department VARCHAR(120) NULL,
    estimated_minutes INT UNSIGNED NULL,
    reissued_count INT UNSIGNED NOT NULL DEFAULT 0,
    closed_by VARCHAR(64) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (token_id),
    KEY ix_tickets_requester (requester_username, created_at),
    KEY ix_tickets_queue (issue_status, issue_type, created_at),
    KEY ix_tickets_technician (technician_id, issue_status),
    CONSTRAINT fk_tickets_requester
        FOREIGN KEY (requester_username) REFERENCES app_users (username)
) ENGINE=InnoDB;

-- ------------------------------------------------------------------------------
-- 3. Table: ticket_updates
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ticket_updates (
    update_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    token_id VARCHAR(40) NOT NULL,
    actor_username VARCHAR(64) NOT NULL,
    actor_role ENUM('USER', 'TECHNICIAN', 'MANAGER', 'ADMIN') NOT NULL,
    status ENUM(
        'Open',
        'Allocated',
        'In Progress',
        'Waiting',
        'Pending Manager Review',
        'Resolved',
        'Reopened'
    ) NOT NULL,
    estimated_minutes INT UNSIGNED NULL,
    message TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (update_id),
    KEY ix_ticket_updates_token (token_id, update_id),
    CONSTRAINT fk_ticket_updates_ticket
        FOREIGN KEY (token_id) REFERENCES tickets (token_id)
) ENGINE=InnoDB;

-- ------------------------------------------------------------------------------
-- 4. Table: user_activity_log
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_activity_log (
    activity_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_username VARCHAR(64) NOT NULL,
    token_id VARCHAR(40) NULL,
    actor_username VARCHAR(64) NOT NULL,
    actor_role ENUM('USER', 'TECHNICIAN', 'MANAGER', 'ADMIN') NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    status ENUM(
        'Open',
        'Allocated',
        'In Progress',
        'Waiting',
        'Pending Manager Review',
        'Resolved',
        'Reopened'
    ) NULL,
    estimated_minutes INT UNSIGNED NULL,
    details TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (activity_id),
    KEY ix_user_activity_owner (user_username, activity_id),
    KEY ix_user_activity_token (token_id, activity_id)
) ENGINE=InnoDB;

-- ------------------------------------------------------------------------------
-- 5. Table: technician_activity_log
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS technician_activity_log (
    activity_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    technician_username VARCHAR(64) NOT NULL,
    token_id VARCHAR(40) NOT NULL,
    actor_username VARCHAR(64) NOT NULL,
    actor_role ENUM('TECHNICIAN') NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    status ENUM(
        'Open',
        'Allocated',
        'In Progress',
        'Waiting',
        'Pending Manager Review',
        'Resolved',
        'Reopened'
    ) NULL,
    estimated_minutes INT UNSIGNED NULL,
    details TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (activity_id),
    KEY ix_technician_activity_owner (technician_username, activity_id),
    KEY ix_technician_activity_token (token_id, activity_id)
) ENGINE=InnoDB;

-- ------------------------------------------------------------------------------
-- 6. Table: manager_activity_log
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS manager_activity_log (
    activity_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    manager_scope VARCHAR(8) NOT NULL DEFAULT 'ALL',
    token_id VARCHAR(40) NULL,
    actor_username VARCHAR(64) NOT NULL,
    actor_role ENUM('USER', 'TECHNICIAN', 'MANAGER', 'ADMIN') NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    status ENUM(
        'Open',
        'Allocated',
        'In Progress',
        'Waiting',
        'Pending Manager Review',
        'Resolved',
        'Reopened'
    ) NULL,
    estimated_minutes INT UNSIGNED NULL,
    details TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (activity_id),
    KEY ix_manager_activity_token (token_id, activity_id),
    KEY ix_manager_activity_actor (actor_username, activity_id)
) ENGINE=InnoDB;

-- ------------------------------------------------------------------------------
-- 7. Table: admin_audit_log
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS admin_audit_log (
    audit_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    admin_username VARCHAR(64) NOT NULL,
    action VARCHAR(64) NOT NULL,
    token_id VARCHAR(40) NULL,
    target_username VARCHAR(64) NULL,
    details TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (audit_id),
    KEY ix_admin_audit_admin (admin_username, audit_id),
    KEY ix_admin_audit_token (token_id)
) ENGINE=InnoDB;

-- ------------------------------------------------------------------------------
-- 8. View: technician_directory
-- ------------------------------------------------------------------------------
CREATE OR REPLACE VIEW technician_directory AS
    SELECT employee_id, username, department
    FROM app_users
    WHERE role = 'TECHNICIAN';

-- ------------------------------------------------------------------------------
-- 9. User creation & privileges for MySQL Workbench & JDBC
-- ------------------------------------------------------------------------------
CREATE USER IF NOT EXISTS 'incident_user'@'localhost' IDENTIFIED BY 'Incident@123';
ALTER USER 'incident_user'@'localhost' IDENTIFIED BY 'Incident@123';
GRANT ALL PRIVILEGES ON incident_tracking.* TO 'incident_user'@'localhost';

CREATE USER IF NOT EXISTS 'incident_user'@'%' IDENTIFIED BY 'Incident@123';
ALTER USER 'incident_user'@'%' IDENTIFIED BY 'Incident@123';
GRANT ALL PRIVILEGES ON incident_tracking.* TO 'incident_user'@'%';

FLUSH PRIVILEGES;

-- ------------------------------------------------------------------------------
-- 10. Seed default users (Password for all is 'Password1234!')
-- ------------------------------------------------------------------------------
INSERT INTO app_users (username, password_salt, password_hash, employee_id, department, designation, role)
VALUES
    ('alice', 'mrqKc4N1ZzP28lSlY/FeGw==', '5AYhPMO5kHrDSXpS7CdDvo+GcNPxC3FiCVGTrCI4zfA=', 'EMP101', 'Engineering', 'SDE2', 'USER'),
    ('bob_tech', 'lpLzlJv2utTkqJomVEie0A==', '/6oRePVue9LGr6PgC5SEPSyhoOED6wv4wioj+HHYHZw=', 'TECH201', 'IT Support', 'Senior Technician', 'TECHNICIAN'),
    ('carol_mgr', 'rSSWdZHEU54AUP4i+4lYKw==', 'x7EOvPtAHzDzAc38dHpeie0O/CdU9+qpcOqomU0q0J4=', 'MGR301', 'IT Operations', 'IT Manager', 'MANAGER'),
    ('admin', 'mrqKc4N1ZzP28lSlY/FeGw==', '5AYhPMO5kHrDSXpS7CdDvo+GcNPxC3FiCVGTrCI4zfA=', 'ADM001', 'IT Administration', 'System Administrator', 'ADMIN')
ON DUPLICATE KEY UPDATE department = VALUES(department), designation = VALUES(designation), role = VALUES(role);

-- Seed Registration Activity
INSERT INTO user_activity_log (user_username, token_id, actor_username, actor_role, event_type, status, estimated_minutes, details)
VALUES
    ('alice', NULL, 'alice', 'USER', 'ACCOUNT_REGISTERED', NULL, NULL, 'User account registered.');

INSERT INTO manager_activity_log (manager_scope, token_id, actor_username, actor_role, event_type, status, estimated_minutes, details)
VALUES
    ('ALL', NULL, 'alice', 'USER', 'ACCOUNT_REGISTERED', NULL, NULL, 'User account registered: alice'),
    ('ALL', NULL, 'bob_tech', 'TECHNICIAN', 'ACCOUNT_REGISTERED', NULL, NULL, 'Technician account initialized: bob_tech'),
    ('ALL', NULL, 'carol_mgr', 'MANAGER', 'ACCOUNT_REGISTERED', NULL, NULL, 'Manager account initialized: carol_mgr');

-- ------------------------------------------------------------------------------
-- 10. Seed sample tickets and activity
-- ------------------------------------------------------------------------------

-- Ticket 1: Open Security Incident (High priority)
INSERT INTO tickets (token_id, requester_username, employee_id, user_department, designation, issue_type, description, issue_status, technician_id, technician_department, estimated_minutes)
VALUES ('TKN-SEC001', 'alice', 'EMP101', 'Engineering', 'SDE2', 'Security', 'Suspicious credential harvesting email received impersonating HR portal.', 'Open', NULL, NULL, NULL)
ON DUPLICATE KEY UPDATE issue_status = VALUES(issue_status);

INSERT INTO ticket_updates (token_id, actor_username, actor_role, status, estimated_minutes, message)
VALUES ('TKN-SEC001', 'alice', 'USER', 'Open', NULL, 'Issue registered: Security concern');

INSERT INTO user_activity_log (user_username, token_id, actor_username, actor_role, event_type, status, estimated_minutes, details)
VALUES ('alice', 'TKN-SEC001', 'alice', 'USER', 'ISSUE_REGISTERED', 'Open', NULL, 'Issue registered: Security.');

-- Ticket 2: Allocated Network Incident
INSERT INTO tickets (token_id, requester_username, employee_id, user_department, designation, issue_type, description, issue_status, technician_id, technician_department, estimated_minutes)
VALUES ('TKN-NET002', 'alice', 'EMP101', 'Engineering', 'SDE2', 'Network connectivity', 'VPN tunnel disconnection during code checkout from git server.', 'Allocated', 'TECH201', 'IT Support', 90)
ON DUPLICATE KEY UPDATE issue_status = VALUES(issue_status);

INSERT INTO ticket_updates (token_id, actor_username, actor_role, status, estimated_minutes, message)
VALUES
    ('TKN-NET002', 'alice', 'USER', 'Open', NULL, 'Issue registered'),
    ('TKN-NET002', 'bob_tech', 'TECHNICIAN', 'Allocated', 90, 'Claimed by technician TECH201 (IT Support).');

INSERT INTO user_activity_log (user_username, token_id, actor_username, actor_role, event_type, status, estimated_minutes, details)
VALUES
    ('alice', 'TKN-NET002', 'alice', 'USER', 'ISSUE_REGISTERED', 'Open', NULL, 'Issue registered: Network connectivity.'),
    ('alice', 'TKN-NET002', 'bob_tech', 'TECHNICIAN', 'ISSUE_CLAIMED', 'Allocated', 90, 'Claimed by technician TECH201 (IT Support).');

INSERT INTO technician_activity_log (technician_username, token_id, actor_username, actor_role, event_type, status, estimated_minutes, details)
VALUES ('bob_tech', 'TKN-NET002', 'bob_tech', 'TECHNICIAN', 'ISSUE_CLAIMED', 'Allocated', 90, 'Claimed by technician TECH201 (IT Support).');

-- Ticket 3: Pending Manager Review Hardware Incident
INSERT INTO tickets (token_id, requester_username, employee_id, user_department, designation, issue_type, description, issue_status, technician_id, technician_department, estimated_minutes)
VALUES ('TKN-HW003', 'alice', 'EMP101', 'Engineering', 'SDE2', 'Hardware', 'Primary dev monitor display flickering and power cycling.', 'Pending Manager Review', 'TECH201', 'IT Support', 15)
ON DUPLICATE KEY UPDATE issue_status = VALUES(issue_status);

INSERT INTO ticket_updates (token_id, actor_username, actor_role, status, estimated_minutes, message)
VALUES
    ('TKN-HW003', 'alice', 'USER', 'Open', NULL, 'Issue registered'),
    ('TKN-HW003', 'bob_tech', 'TECHNICIAN', 'In Progress', 45, 'Inspecting display cable and power adapter.'),
    ('TKN-HW003', 'bob_tech', 'TECHNICIAN', 'Pending Manager Review', 15, 'Replaced faulty power brick. Awaiting manager sign-off.');

INSERT INTO user_activity_log (user_username, token_id, actor_username, actor_role, event_type, status, estimated_minutes, details)
VALUES
    ('alice', 'TKN-HW003', 'alice', 'USER', 'ISSUE_REGISTERED', 'Open', NULL, 'Issue registered: Hardware.'),
    ('alice', 'TKN-HW003', 'bob_tech', 'TECHNICIAN', 'STATUS_UPDATED', 'In Progress', 45, 'Inspecting display cable and power adapter.'),
    ('alice', 'TKN-HW003', 'bob_tech', 'TECHNICIAN', 'STATUS_UPDATED', 'Pending Manager Review', 15, 'Replaced faulty power brick. Awaiting manager sign-off.');

-- Ticket 4: Resolved Database Incident
INSERT INTO tickets (token_id, requester_username, employee_id, user_department, designation, issue_type, description, issue_status, technician_id, technician_department, estimated_minutes)
VALUES ('TKN-DB004', 'alice', 'EMP101', 'Engineering', 'SDE2', 'Database connection', 'Staging database pool exhaustion during stress test.', 'Resolved', 'TECH201', 'IT Support', 0)
ON DUPLICATE KEY UPDATE issue_status = VALUES(issue_status);

INSERT INTO ticket_updates (token_id, actor_username, actor_role, status, estimated_minutes, message)
VALUES
    ('TKN-DB004', 'alice', 'USER', 'Open', NULL, 'Issue registered'),
    ('TKN-DB004', 'bob_tech', 'TECHNICIAN', 'In Progress', 60, 'Optimized connection pool parameters and restarted service.'),
    ('TKN-DB004', 'bob_tech', 'TECHNICIAN', 'Pending Manager Review', 0, 'Connection pool test verified successful.'),
    ('TKN-DB004', 'carol_mgr', 'MANAGER', 'Resolved', 0, 'Approved resolution. Service running normally.');

INSERT INTO user_activity_log (user_username, token_id, actor_username, actor_role, event_type, status, estimated_minutes, details)
VALUES
    ('alice', 'TKN-DB004', 'alice', 'USER', 'ISSUE_REGISTERED', 'Open', NULL, 'Issue registered: Database connection.'),
    ('alice', 'TKN-DB004', 'carol_mgr', 'MANAGER', 'STATUS_UPDATED', 'Resolved', 0, 'Approved resolution. Service running normally.');

INSERT INTO manager_activity_log (manager_scope, token_id, actor_username, actor_role, event_type, status, estimated_minutes, details)
VALUES
    ('ALL', 'TKN-DB004', 'carol_mgr', 'MANAGER', 'STATUS_UPDATED', 'Resolved', 0, 'Approved resolution. Service running normally.');

-- Done!
SELECT 'Database incident_tracking setup completed successfully!' AS Result;

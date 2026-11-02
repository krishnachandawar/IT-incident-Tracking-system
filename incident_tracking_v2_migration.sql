-- ==============================================================================
-- IT Incident Tracking System - V2 Migration Script
-- Run this in MySQL Workbench AFTER the original setup script has been applied.
-- ==============================================================================

USE incident_tracking;

-- ------------------------------------------------------------------------------
-- 0. Add approval state for technician and manager registrations
-- ------------------------------------------------------------------------------
ALTER TABLE app_users
    ADD COLUMN approval_status ENUM('PENDING','APPROVED','REJECTED')
        NOT NULL DEFAULT 'APPROVED' AFTER role,
    ADD COLUMN approved_by VARCHAR(64) NULL AFTER approval_status,
    ADD COLUMN approved_at TIMESTAMP NULL AFTER approved_by;

CREATE INDEX ix_app_users_approval ON app_users (approval_status, role);

-- ------------------------------------------------------------------------------
-- 1. Add ADMIN role to app_users (extend ENUM)
-- ------------------------------------------------------------------------------
ALTER TABLE app_users
    MODIFY COLUMN role ENUM('USER','TECHNICIAN','MANAGER','ADMIN') NOT NULL DEFAULT 'USER';

-- ------------------------------------------------------------------------------
-- 2. Add priority_level, reissued_count, closed_by to tickets
-- ------------------------------------------------------------------------------
ALTER TABLE tickets
    ADD COLUMN priority_level ENUM('Critical','High','Medium','Low') NOT NULL DEFAULT 'Medium'
        AFTER description,
    ADD COLUMN reissued_count INT UNSIGNED NOT NULL DEFAULT 0
        AFTER estimated_minutes,
    ADD COLUMN closed_by VARCHAR(64) NULL
        AFTER reissued_count;

-- Update existing tickets with computed priority
UPDATE tickets SET priority_level = 'Critical' WHERE issue_type = 'Security';
UPDATE tickets SET priority_level = 'High'     WHERE issue_type IN ('Hardware','Backup and recovery') AND priority_level = 'Medium';
UPDATE tickets SET priority_level = 'Medium'   WHERE issue_type IN ('Network connectivity','Database connection','VPN access') AND priority_level = 'Medium';

-- ------------------------------------------------------------------------------
-- 3. Extend ticket_updates status ENUM to include 'Reopened'
-- ------------------------------------------------------------------------------
ALTER TABLE ticket_updates
    MODIFY COLUMN status ENUM(
        'Open','Allocated','In Progress','Waiting',
        'Pending Manager Review','Resolved','Reopened'
    ) NOT NULL;

-- ------------------------------------------------------------------------------
-- 4. Extend activity log status ENUMs to include 'Reopened'
--    and actor_role ENUMs to include 'ADMIN'
-- ------------------------------------------------------------------------------
ALTER TABLE user_activity_log
    MODIFY COLUMN status ENUM(
        'Open','Allocated','In Progress','Waiting',
        'Pending Manager Review','Resolved','Reopened'
    ) NULL,
    MODIFY COLUMN actor_role ENUM('USER','TECHNICIAN','MANAGER','ADMIN') NOT NULL;

ALTER TABLE technician_activity_log
    MODIFY COLUMN status ENUM(
        'Open','Allocated','In Progress','Waiting',
        'Pending Manager Review','Resolved','Reopened'
    ) NULL;

ALTER TABLE manager_activity_log
    MODIFY COLUMN status ENUM(
        'Open','Allocated','In Progress','Waiting',
        'Pending Manager Review','Resolved','Reopened'
    ) NULL,
    MODIFY COLUMN actor_role ENUM('USER','TECHNICIAN','MANAGER','ADMIN') NOT NULL;

-- Extend ticket_updates actor_role too
ALTER TABLE ticket_updates
    MODIFY COLUMN actor_role ENUM('USER','TECHNICIAN','MANAGER','ADMIN') NOT NULL;

-- ------------------------------------------------------------------------------
-- 5. Extend tickets issue_status ENUM to include 'Reopened' (maps back to Open)
-- ------------------------------------------------------------------------------
ALTER TABLE tickets
    MODIFY COLUMN issue_status ENUM(
        'Open','Allocated','In Progress','Waiting',
        'Pending Manager Review','Resolved'
    ) NOT NULL DEFAULT 'Open';
-- Note: We reuse 'Open' for reopened tickets, but track via reissued_count + closed_by

-- ------------------------------------------------------------------------------
-- 6. Create admin_audit_log table
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS admin_audit_log (
    audit_id    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    admin_username VARCHAR(64) NOT NULL,
    action      VARCHAR(64) NOT NULL,
    token_id    VARCHAR(40) NULL,
    target_username VARCHAR(64) NULL,
    details     TEXT NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (audit_id),
    KEY ix_admin_audit_admin (admin_username, audit_id),
    KEY ix_admin_audit_token (token_id)
) ENGINE=InnoDB;

-- ------------------------------------------------------------------------------
-- 7. Seed an ADMIN user (password: Password1234!)
-- ------------------------------------------------------------------------------
INSERT INTO app_users (username, password_salt, password_hash, employee_id, department, designation, role)
VALUES
    ('admin', 'mrqKc4N1ZzP28lSlY/FeGw==', '5AYhPMO5kHrDSXpS7CdDvo+GcNPxC3FiCVGTrCI4zfA=', 'ADM001', 'IT Administration', 'System Administrator', 'ADMIN')
ON DUPLICATE KEY UPDATE role = 'ADMIN', designation = 'System Administrator';

-- Give admin same MySQL privileges as incident_user (already granted via GRANT ALL)

-- Done!
SELECT 'V2 Migration completed successfully!' AS Result;

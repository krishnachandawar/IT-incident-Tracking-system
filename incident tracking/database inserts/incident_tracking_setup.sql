CREATE DATABASE IF NOT EXISTS incident_tracking
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

USE incident_tracking;

CREATE TABLE IF NOT EXISTS app_users (
    username VARCHAR(64) NOT NULL,
    password_salt VARCHAR(32) NOT NULL,
    password_hash VARCHAR(64) NOT NULL,
    employee_id VARCHAR(64) NOT NULL,
    department VARCHAR(120) NOT NULL,
    designation VARCHAR(120) NOT NULL,
    role ENUM('USER', 'TECHNICIAN', 'MANAGER') NOT NULL DEFAULT 'USER',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (username),
    UNIQUE KEY uq_app_users_employee_id (employee_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS tickets (
    token_id VARCHAR(40) NOT NULL,
    requester_username VARCHAR(64) NOT NULL,
    employee_id VARCHAR(64) NOT NULL,
    user_department VARCHAR(120) NOT NULL,
    designation VARCHAR(120) NOT NULL,
    issue_type VARCHAR(100) NOT NULL,
    description TEXT NOT NULL,
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

CREATE TABLE IF NOT EXISTS ticket_updates (
    update_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    token_id VARCHAR(40) NOT NULL,
    actor_username VARCHAR(64) NOT NULL,
    actor_role ENUM('USER', 'TECHNICIAN', 'MANAGER') NOT NULL,
    status ENUM(
        'Open',
        'Allocated',
        'In Progress',
        'Waiting',
        'Pending Manager Review',
        'Resolved'
    ) NOT NULL,
    estimated_minutes INT UNSIGNED NULL,
    message TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (update_id),
    KEY ix_ticket_updates_token (token_id, update_id),
    CONSTRAINT fk_ticket_updates_ticket
        FOREIGN KEY (token_id) REFERENCES tickets (token_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS user_activity_log (
    activity_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_username VARCHAR(64) NOT NULL,
    token_id VARCHAR(40) NULL,
    actor_username VARCHAR(64) NOT NULL,
    actor_role ENUM('USER', 'TECHNICIAN', 'MANAGER') NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    status ENUM(
        'Open',
        'Allocated',
        'In Progress',
        'Waiting',
        'Pending Manager Review',
        'Resolved'
    ) NULL,
    estimated_minutes INT UNSIGNED NULL,
    details TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (activity_id),
    KEY ix_user_activity_owner (user_username, activity_id),
    KEY ix_user_activity_token (token_id, activity_id)
) ENGINE=InnoDB;

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
        'Resolved'
    ) NULL,
    estimated_minutes INT UNSIGNED NULL,
    details TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (activity_id),
    KEY ix_technician_activity_owner (technician_username, activity_id),
    KEY ix_technician_activity_token (token_id, activity_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS manager_activity_log (
    activity_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    manager_scope VARCHAR(8) NOT NULL DEFAULT 'ALL',
    token_id VARCHAR(40) NULL,
    actor_username VARCHAR(64) NOT NULL,
    actor_role ENUM('USER', 'TECHNICIAN', 'MANAGER') NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    status ENUM(
        'Open',
        'Allocated',
        'In Progress',
        'Waiting',
        'Pending Manager Review',
        'Resolved'
    ) NULL,
    estimated_minutes INT UNSIGNED NULL,
    details TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (activity_id),
    KEY ix_manager_activity_token (token_id, activity_id),
    KEY ix_manager_activity_actor (actor_username, activity_id)
) ENGINE=InnoDB;

CREATE OR REPLACE VIEW technician_directory AS
    SELECT employee_id, username, department
    FROM app_users
    WHERE role = 'TECHNICIAN';

CREATE ROLE IF NOT EXISTS 'incident_auth_role'@'%';
CREATE ROLE IF NOT EXISTS 'incident_user_role'@'%';
CREATE ROLE IF NOT EXISTS 'incident_technician_role'@'%';
CREATE ROLE IF NOT EXISTS 'incident_manager_role'@'%';

GRANT SELECT, INSERT ON incident_tracking.app_users
    TO 'incident_auth_role'@'%';
GRANT INSERT ON incident_tracking.user_activity_log
    TO 'incident_auth_role'@'%';
GRANT INSERT ON incident_tracking.manager_activity_log
    TO 'incident_auth_role'@'%';

GRANT SELECT, INSERT ON incident_tracking.tickets
    TO 'incident_user_role'@'%';
GRANT SELECT, INSERT ON incident_tracking.ticket_updates
    TO 'incident_user_role'@'%';
GRANT SELECT, INSERT ON incident_tracking.user_activity_log
    TO 'incident_user_role'@'%';
GRANT INSERT ON incident_tracking.manager_activity_log
    TO 'incident_user_role'@'%';

GRANT SELECT ON incident_tracking.tickets
    TO 'incident_technician_role'@'%';
GRANT UPDATE (issue_status, technician_id, technician_department, estimated_minutes)
    ON incident_tracking.tickets TO 'incident_technician_role'@'%';
GRANT SELECT, INSERT ON incident_tracking.ticket_updates
    TO 'incident_technician_role'@'%';
GRANT SELECT, INSERT ON incident_tracking.technician_activity_log
    TO 'incident_technician_role'@'%';
GRANT INSERT ON incident_tracking.user_activity_log
    TO 'incident_technician_role'@'%';
GRANT INSERT ON incident_tracking.manager_activity_log
    TO 'incident_technician_role'@'%';

GRANT SELECT, UPDATE ON incident_tracking.tickets
    TO 'incident_manager_role'@'%';
GRANT SELECT, INSERT ON incident_tracking.ticket_updates
    TO 'incident_manager_role'@'%';
GRANT SELECT, INSERT ON incident_tracking.manager_activity_log
    TO 'incident_manager_role'@'%';
GRANT INSERT ON incident_tracking.user_activity_log
    TO 'incident_manager_role'@'%';
GRANT SELECT ON incident_tracking.technician_directory
    TO 'incident_manager_role'@'%';

CREATE USER IF NOT EXISTS 'incident_auth'@'localhost'
    IDENTIFIED BY RANDOM PASSWORD;
CREATE USER IF NOT EXISTS 'incident_user'@'localhost'
    IDENTIFIED BY RANDOM PASSWORD;
CREATE USER IF NOT EXISTS 'incident_technician'@'localhost'
    IDENTIFIED BY RANDOM PASSWORD;
CREATE USER IF NOT EXISTS 'incident_manager'@'localhost'
    IDENTIFIED BY RANDOM PASSWORD;

GRANT 'incident_auth_role'@'%' TO 'incident_auth'@'localhost';
SET DEFAULT ROLE 'incident_auth_role'@'%' TO 'incident_auth'@'localhost';

GRANT 'incident_user_role'@'%' TO 'incident_user'@'localhost';
SET DEFAULT ROLE 'incident_user_role'@'%' TO 'incident_user'@'localhost';

GRANT 'incident_technician_role'@'%' TO 'incident_technician'@'localhost';
SET DEFAULT ROLE 'incident_technician_role'@'%' TO 'incident_technician'@'localhost';

GRANT 'incident_manager_role'@'%' TO 'incident_manager'@'localhost';
SET DEFAULT ROLE 'incident_manager_role'@'%' TO 'incident_manager'@'localhost';

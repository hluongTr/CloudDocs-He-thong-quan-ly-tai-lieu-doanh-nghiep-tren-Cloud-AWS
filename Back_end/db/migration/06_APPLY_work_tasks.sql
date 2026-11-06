-- CloudDocs: work assignment, report submission and targeted notifications.
-- Review on a schema backup first. Choose the intended database in your SQL client.
-- This script is additive and must be applied ONCE before starting the new backend.
-- MySQL DDL commits implicitly; do not rely on ROLLBACK to undo these changes.
-- Prerequisites: users, documents, notifications and migrations 01-05 already applied.
-- Run these preflight queries separately and inspect the results before the CREATE/ALTER.
SELECT DATABASE() AS selected_database, @@hostname AS server_hostname, @@port AS server_port;
SHOW CREATE TABLE users;
SHOW CREATE TABLE documents;
SHOW CREATE TABLE notifications;
SHOW TABLES LIKE 'work_tasks';
-- STOP if work_tasks or the new notification columns already exist: inspect them, do not rerun.

CREATE TABLE work_tasks (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description VARCHAR(2000) NOT NULL,
    assigned_by BIGINT NOT NULL,
    assignee_id BIGINT NOT NULL,
    due_at DATETIME(6) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'TODO',
    report_document_id BIGINT NULL,
    report_note VARCHAR(2000) NULL,
    review_feedback VARCHAR(2000) NULL,
    submitted_at DATETIME(6) NULL,
    reviewed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_work_assigned_by FOREIGN KEY (assigned_by) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_work_assignee FOREIGN KEY (assignee_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_work_report_document FOREIGN KEY (report_document_id) REFERENCES documents(id) ON DELETE RESTRICT,
    CONSTRAINT chk_work_status CHECK (status IN ('TODO','IN_PROGRESS','SUBMITTED','COMPLETED')),
    CONSTRAINT chk_work_different_people CHECK (assigned_by <> assignee_id),
    INDEX idx_work_assignee_status (assignee_id,status),
    INDEX idx_work_assigner_status (assigned_by,status),
    INDEX idx_work_due (due_at),
    INDEX idx_work_report_status (report_document_id,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE notifications
    ADD COLUMN recipient_id BIGINT NULL,
    ADD COLUMN work_task_id BIGINT NULL,
    ADD CONSTRAINT fk_notification_recipient FOREIGN KEY (recipient_id) REFERENCES users(id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_notification_work_task FOREIGN KEY (work_task_id) REFERENCES work_tasks(id) ON DELETE SET NULL,
    ADD INDEX idx_notification_recipient_created (recipient_id,created_at);
-- Existing rows stay global announcements because recipient_id remains NULL.
SHOW CREATE TABLE work_tasks;
SHOW CREATE TABLE notifications;

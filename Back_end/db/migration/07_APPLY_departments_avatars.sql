-- Run once on the intended MySQL 8 database after backing it up. Not executed automatically.
SELECT DATABASE();
CREATE TABLE departments (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 name VARCHAR(100) NOT NULL,
 name_key VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
 active BOOLEAN NOT NULL DEFAULT TRUE,
 allow_self_selection BOOLEAN NOT NULL DEFAULT FALSE,
 CONSTRAINT uk_department_name_key UNIQUE(name_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- Preserve existing departments; the unassigned label is not a department.
INSERT INTO departments(name,name_key)
SELECT MIN(TRIM(name)),LOWER(TRIM(name)) COLLATE utf8mb4_bin
FROM (SELECT department AS name FROM users UNION ALL SELECT department FROM documents) source
WHERE name IS NOT NULL AND TRIM(name)<>'' AND LOWER(TRIM(name))<>LOWER('Chưa cập nhật')
GROUP BY LOWER(TRIM(name)) COLLATE utf8mb4_bin;
UPDATE users u JOIN departments d ON LOWER(TRIM(u.department)) COLLATE utf8mb4_bin=d.name_key SET u.department=d.name;
UPDATE documents u JOIN departments d ON LOWER(TRIM(u.department)) COLLATE utf8mb4_bin=d.name_key SET u.department=d.name;
ALTER TABLE users ADD COLUMN avatar_updated_at DATETIME(6) NULL;
CREATE TABLE user_avatars (
 user_id BIGINT NOT NULL PRIMARY KEY,
 data MEDIUMBLOB NOT NULL,
 CONSTRAINT fk_avatar_user FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

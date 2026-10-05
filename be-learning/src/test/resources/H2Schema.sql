-- Minimal schema for the `test` profile.
--
-- Hibernate's create-drop generates the mapped entities but NOT the implicit
-- @JoinTable between User and Role, so H2 starts up missing `user_roles`.
-- This file supplies the join table (and the three seeded roles the tests and
-- any future test fixture rely on) so the in-memory schema matches MySQL's.
-- Flyway is disabled under this profile, so Spring Boot loads it directly.

CREATE TABLE IF NOT EXISTS user_roles (
    user_id     BIGINT NOT NULL,
    role_id     INT    NOT NULL,
    assigned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_ur_user FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT fk_ur_role FOREIGN KEY (role_id) REFERENCES roles (role_id)
);

INSERT INTO roles (role_id, role_code, role_name)
VALUES (1, 'ROLE_ADMIN',   'Quản trị viên hệ thống'),
       (2, 'ROLE_TEACHER', 'Giảng viên'),
       (3, 'ROLE_STUDENT', 'Học sinh / Sinh viên');
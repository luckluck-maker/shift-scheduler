DROP TABLE IF EXISTS flyway_smoke_test;

CREATE TABLE job_position
(
    id   BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(60) NOT NULL,
    CONSTRAINT uk_job_position_name UNIQUE (name)
);

CREATE TABLE shift_type
(
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    name             VARCHAR(40) NOT NULL,
    start_time       TIME        NOT NULL,
    end_time         TIME        NOT NULL,
    crosses_midnight BIT(1)      NOT NULL DEFAULT b'0',
    CONSTRAINT uk_shift_type_name UNIQUE (name)
);

CREATE TABLE employee
(
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    full_name        VARCHAR(100) NOT NULL,
    username         VARCHAR(50)  NOT NULL,
    password_hash    VARCHAR(100) NOT NULL,
    role             VARCHAR(20)  NOT NULL,
    max_weekly_hours INT          NOT NULL,
    active           BIT(1)       NOT NULL DEFAULT b'1',
    job_position_id  BIGINT       NOT NULL,
    CONSTRAINT uk_employee_username UNIQUE (username),
    CONSTRAINT fk_employee_job_position
        FOREIGN KEY (job_position_id) REFERENCES job_position (id)
);

CREATE TABLE schedule
(
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    week_start DATE        NOT NULL,
    status     VARCHAR(20) NOT NULL,
    version    BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uk_schedule_week_start UNIQUE (week_start)
);

CREATE TABLE shift
(
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    shift_date    DATE   NOT NULL,
    schedule_id   BIGINT NOT NULL,
    shift_type_id BIGINT NOT NULL,
    CONSTRAINT uk_shift_schedule_date_type UNIQUE (schedule_id, shift_date, shift_type_id),
    CONSTRAINT fk_shift_schedule
        FOREIGN KEY (schedule_id) REFERENCES schedule (id) ON DELETE CASCADE,
    CONSTRAINT fk_shift_shift_type
        FOREIGN KEY (shift_type_id) REFERENCES shift_type (id)
);

CREATE TABLE shift_requirement
(
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    shift_id        BIGINT NOT NULL,
    job_position_id BIGINT NOT NULL,
    required_count  INT    NOT NULL,
    CONSTRAINT uk_requirement_shift_position UNIQUE (shift_id, job_position_id),
    CONSTRAINT fk_requirement_shift
        FOREIGN KEY (shift_id) REFERENCES shift (id) ON DELETE CASCADE,
    CONSTRAINT fk_requirement_job_position
        FOREIGN KEY (job_position_id) REFERENCES job_position (id)
);

CREATE TABLE assignment
(
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    shift_id    BIGINT NOT NULL,
    employee_id BIGINT NOT NULL,
    CONSTRAINT uk_assignment_shift_employee UNIQUE (shift_id, employee_id),
    CONSTRAINT fk_assignment_shift
        FOREIGN KEY (shift_id) REFERENCES shift (id) ON DELETE CASCADE,
    CONSTRAINT fk_assignment_employee
        FOREIGN KEY (employee_id) REFERENCES employee (id)
);

CREATE TABLE shift_preference
(
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    employee_id BIGINT      NOT NULL,
    shift_id    BIGINT      NOT NULL,
    type        VARCHAR(20) NOT NULL,
    reason      VARCHAR(255) NULL,
    CONSTRAINT uk_preference_employee_shift UNIQUE (employee_id, shift_id),
    CONSTRAINT fk_preference_employee
        FOREIGN KEY (employee_id) REFERENCES employee (id),
    CONSTRAINT fk_preference_shift
        FOREIGN KEY (shift_id) REFERENCES shift (id) ON DELETE CASCADE
);

CREATE TABLE employee_leave
(
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    employee_id BIGINT      NOT NULL,
    start_date  DATE        NOT NULL,
    end_date    DATE        NOT NULL,
    type        VARCHAR(20) NOT NULL,
    CONSTRAINT fk_leave_employee
        FOREIGN KEY (employee_id) REFERENCES employee (id)
);

CREATE INDEX idx_leave_employee_dates ON employee_leave (employee_id, start_date, end_date);
CREATE INDEX idx_shift_date ON shift (shift_date);

-- No unique name here. A position that a published week refers to is kept as
-- it was and a new row takes over, so the same name can appear more than once
-- with only one of them active. The service enforces that.
CREATE TABLE job_position
(
    id     BIGINT AUTO_INCREMENT PRIMARY KEY,
    name   VARCHAR(60) NOT NULL,
    active BIT(1)      NOT NULL DEFAULT b'1'
);

-- Same as job_position: old versions stay behind so published weeks keep the
-- hours they were published with.
CREATE TABLE shift_type
(
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    name             VARCHAR(40) NOT NULL,
    start_time       TIME        NOT NULL,
    end_time         TIME        NOT NULL,
    crosses_midnight BIT(1)      NOT NULL DEFAULT b'0',
    active           BIT(1)      NOT NULL DEFAULT b'1'
);

CREATE TABLE employee
(
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    full_name        VARCHAR(100) NOT NULL,
    username         VARCHAR(120) NOT NULL,
    password_hash    VARCHAR(100) NOT NULL,
    role             VARCHAR(20)  NOT NULL,
    max_weekly_hours INT          NOT NULL,
    active           BIT(1)       NOT NULL DEFAULT b'1',
    job_position_id  BIGINT       NOT NULL,
    version          BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_employee_username UNIQUE (username),
    CONSTRAINT fk_employee_job_position
        FOREIGN KEY (job_position_id) REFERENCES job_position (id)
);

CREATE TABLE schedule
(
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    week_start      DATE        NOT NULL,
    status          VARCHAR(20) NOT NULL,
    last_changed_at DATETIME(6) NOT NULL,
    version         BIGINT      NOT NULL DEFAULT 0,
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
    is_essential    BIT(1) NOT NULL DEFAULT b'1',
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
    is_override BIT(1) NOT NULL DEFAULT b'0',
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
    leave_date  DATE        NOT NULL,
    type        VARCHAR(20) NOT NULL,
    CONSTRAINT uk_leave_employee_date UNIQUE (employee_id, leave_date),
    CONSTRAINT fk_leave_employee
        FOREIGN KEY (employee_id) REFERENCES employee (id)
);

CREATE INDEX idx_leave_date ON employee_leave (leave_date);
CREATE INDEX idx_shift_date ON shift (shift_date);
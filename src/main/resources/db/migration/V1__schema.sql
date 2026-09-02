-- The name is not unique here, since a position a published week refers to is
-- kept as it was while a new row takes over. The service is what stops two
-- active rows sharing a name.
CREATE TABLE job_position
(
    id     BIGINT AUTO_INCREMENT PRIMARY KEY,
    name   VARCHAR(40) NOT NULL,
    active BIT(1)      NOT NULL DEFAULT b'1'
);

-- Old versions stay behind, to allow a published week to keep the hours it was
-- published with.
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
    -- An email address, 254 characters at most by RFC 5321.
    username         VARCHAR(254) NOT NULL,
    -- An Argon2 hash with the Spring Security defaults is 97 characters.
    password_hash    VARCHAR(255) NOT NULL,
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
    submission_closes_at  DATETIME(6) NULL,
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

-- The people to be mailed for changes made on a published week. A removed
-- assignment leaves no row, so the person is kept here instead. Republishing
-- sends the mails and empties it.
CREATE TABLE roster_change
(
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    schedule_id BIGINT NOT NULL,
    employee_id BIGINT NOT NULL,
    shift_id    BIGINT NOT NULL,
    -- 1 = the manager put them on the shift, 0 = took them off.
    added       BIT(1) NOT NULL,
    -- At most one waiting change per person per shift, so a change that was
    -- undone can be found and canceled instead of counting twice.
    CONSTRAINT uk_roster_change_schedule_employee_shift
        UNIQUE (schedule_id, employee_id, shift_id),
    CONSTRAINT fk_roster_change_schedule
        FOREIGN KEY (schedule_id) REFERENCES schedule (id) ON DELETE CASCADE,
    CONSTRAINT fk_roster_change_employee
        FOREIGN KEY (employee_id) REFERENCES employee (id),
    CONSTRAINT fk_roster_change_shift
        FOREIGN KEY (shift_id) REFERENCES shift (id) ON DELETE CASCADE
);

CREATE TABLE shift_preference
(
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    employee_id BIGINT      NOT NULL,
    shift_id    BIGINT      NOT NULL,
    type        VARCHAR(20) NOT NULL,
    reason      VARCHAR(255) NULL,
    version     BIGINT      NOT NULL DEFAULT 0,
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
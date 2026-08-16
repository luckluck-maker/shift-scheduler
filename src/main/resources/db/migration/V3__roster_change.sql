-- Who still needs to hear about a change made after the week was published.
--
-- A removed assignment deletes its row, so once it's gone there is nothing left
-- to say the person was ever on that shift. This table remembers the person so
-- they can still be told. Republishing sends the mails and empties it.
CREATE TABLE roster_change
(
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    schedule_id BIGINT NOT NULL,
    employee_id BIGINT NOT NULL,
    -- One row per person per week: a manager who moves someone twice before
    -- republishing still sends one mail.
    CONSTRAINT uk_roster_change_schedule_employee UNIQUE (schedule_id, employee_id),
    CONSTRAINT fk_roster_change_schedule
        FOREIGN KEY (schedule_id) REFERENCES schedule (id) ON DELETE CASCADE,
    CONSTRAINT fk_roster_change_employee
        FOREIGN KEY (employee_id) REFERENCES employee (id)
);

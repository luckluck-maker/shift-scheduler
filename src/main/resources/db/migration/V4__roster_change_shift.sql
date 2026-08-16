-- Remembering only the person was not enough: taking someone off a shift and
-- putting them straight back is not a change, but it still counted as one.
-- Keeping the shift and the direction lets the two cancel each other out.
--
-- The table is built again rather than altered. Its rows are notifications that
-- have not gone out yet, so there is nothing worth keeping, and swapping the
-- unique key on a live table means working around the foreign keys that lean on
-- it. This says the same thing in one readable block.
DROP TABLE roster_change;

CREATE TABLE roster_change
(
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    schedule_id BIGINT NOT NULL,
    employee_id BIGINT NOT NULL,
    shift_id    BIGINT NOT NULL,
    -- 1 = the manager put them on the shift, 0 = took them off.
    added       BIT(1) NOT NULL,
    -- At most one waiting change per person per shift, so the opposite one can
    -- be found and cancelled.
    CONSTRAINT uk_roster_change_schedule_employee_shift
        UNIQUE (schedule_id, employee_id, shift_id),
    CONSTRAINT fk_roster_change_schedule
        FOREIGN KEY (schedule_id) REFERENCES schedule (id) ON DELETE CASCADE,
    CONSTRAINT fk_roster_change_employee
        FOREIGN KEY (employee_id) REFERENCES employee (id),
    CONSTRAINT fk_roster_change_shift
        FOREIGN KEY (shift_id) REFERENCES shift (id) ON DELETE CASCADE
);

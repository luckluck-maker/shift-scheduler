-- Demo data, for showing capabilities and testing various scenarios

INSERT INTO job_position (id, name)
VALUES (1, 'אחראי משמרת'),
       (2, 'נציג תמיכה'),
       (3, 'נציג בכיר');

INSERT INTO shift_type (id, name, start_time, end_time, crosses_midnight)
VALUES (1, 'בוקר', '07:00:00', '15:00:00', b'0'),
       (2, 'ערב', '15:00:00', '23:00:00', b'0'),
       (3, 'לילה', '23:00:00', '07:00:00', b'1');

-- The username is the email - dual use for both login and email sending.
-- Chose .local as the domain so nothing gets sent.
-- Password is password123
INSERT INTO employee (full_name, username, password_hash, role, max_weekly_hours, active, job_position_id)
VALUES ('דנה לוי', 'dana@shiftscheduler.local',
        '$argon2id$v=19$m=16384,t=2,p=1$ujvd34Ei2IpmENLeV7IBlg$zvLEvYWsW5Aup5Sx97oLt0utUeHrBFpCf1UEYKS2YRs', 'MANAGER',
        40, b'1', 1),
       ('יוני ברק', 'yoni@shiftscheduler.local',
        '$argon2id$v=19$m=16384,t=2,p=1$pq59TOKklOFkP0cim+BoRQ$rUx5elFh0Ze7BeHOYtuTNjCV5v6HSe0g5HLDT6FKHZs', 'EMPLOYEE',
        40, b'1', 1),
       ('מאיה כהן', 'maya@shiftscheduler.local',
        '$argon2id$v=19$m=16384,t=2,p=1$50q3puOmMxcAEp77kL1KIA$QH/SsFa26Yk9KxH3KJYGUZU5bjEyfkGDO1Qz+9JrJpM', 'EMPLOYEE',
        40, b'1', 2),
       ('עומר שני', 'omer@shiftscheduler.local',
        '$argon2id$v=19$m=16384,t=2,p=1$yTg216wjOAXbsWoJRGDXTQ$tU3ya8tVPU7NWQE9fxcs2w2hK6rnNCVz431WgMnqBl8', 'EMPLOYEE',
        40, b'1', 2),
       ('שירה אדלר', 'shira@shiftscheduler.local',
        '$argon2id$v=19$m=16384,t=2,p=1$zMX7aqY/uveS/fliD1fmzA$LrJ5/J2It2SSrcAsZrgPP+5KWPkmi9y9U0A3WUkq3qY', 'EMPLOYEE',
        32, b'1', 2),
       ('איתן פלד', 'eitan@shiftscheduler.local',
        '$argon2id$v=19$m=16384,t=2,p=1$j0mYwBv0x3di1Az+MxCl1A$RXH3dpDBxocQIdpviL/TLqHkmbmm0ohnSvUXqGMdEeI', 'EMPLOYEE',
        40, b'1', 2),
       ('נועה גרשון', 'noa@shiftscheduler.local',
        '$argon2id$v=19$m=16384,t=2,p=1$k8I8SkbePRN6OlYZt8KM0Q$BOOTfkhXW7ut34pgFisGDuZHBeSAhfCHvvHDfvDvItc', 'EMPLOYEE',
        24, b'1', 2),
       ('גלעד רוט', 'gilad@shiftscheduler.local',
        '$argon2id$v=19$m=16384,t=2,p=1$As3QHbZR9tdVAIyNlQ0Wsw$KZBFqmIT2jcpYQ9TcS1kfmNbo5MCqVV4B3eMBZ0baCY', 'EMPLOYEE',
        40, b'1', 3),
       ('תמר ורדי', 'tamar@shiftscheduler.local',
        '$argon2id$v=19$m=16384,t=2,p=1$0fq/CZQKgiWEyQaQo4nh1w$YuPi73uZk+jUY7MZCyPW4/L3zd5EZaVPyvbt19j5NyI', 'EMPLOYEE',
        40, b'1', 3),
       ('אבי מזרחי', 'avi@shiftscheduler.local',
        '$argon2id$v=19$m=16384,t=2,p=1$2huIgm9LA/HIAJxCWMXtPw$HSLZLwybQcNCmedrG+HdLN6x6QisUo8zbYm9hQM2a3w', 'EMPLOYEE',
        40, b'0', 3);


-- ---------------------------------------------------------------------------
-- One week per status.
-- ---------------------------------------------------------------------------

-- Deadline is null to make sure collecting stays collecting and not auto closes by
-- timed submission regardless of when this demo runs
INSERT INTO schedule (week_start, status, submission_closes_at, last_changed_at)
VALUES ('2026-08-02', 'PUBLISHED', NULL, NOW(6)),
       ('2026-08-09', 'DRAFT', NULL, NOW(6)),
       ('2026-08-16', 'COLLECTING', NULL, NOW(6));

-- Every day of every week gets all three shift types, the same as the web does.
INSERT INTO shift (schedule_id, shift_date, shift_type_id)
SELECT s.id, DATE_ADD(s.week_start, INTERVAL d.offset DAY), t.id
FROM schedule s
         CROSS JOIN (SELECT 0 AS offset
                     UNION ALL
                     SELECT 1
                     UNION ALL
                     SELECT 2
                     UNION ALL
                     SELECT 3
                     UNION ALL
                     SELECT 4
                     UNION ALL
                     SELECT 5
                     UNION ALL
                     SELECT 6) d
         CROSS JOIN shift_type t;


-- ---------------------------------------------------------------------------
-- Sunday to Thursday are the busy days, the weekend needs less people.
-- The supervisor is not essential - the shift can run without one.
-- ---------------------------------------------------------------------------

-- Weekday mornings: a supervisor (not essential), two agents, one senior.
INSERT INTO shift_requirement (shift_id, job_position_id, required_count, is_essential)
SELECT sh.id, 1, 1, b'0'
FROM shift sh
         JOIN schedule s ON s.id = sh.schedule_id
         JOIN shift_type t ON t.id = sh.shift_type_id
WHERE t.name = 'בוקר'
  AND DATEDIFF(sh.shift_date, s.week_start) BETWEEN 0 AND 4;

INSERT INTO shift_requirement (shift_id, job_position_id, required_count, is_essential)
SELECT sh.id, 2, 2, b'1'
FROM shift sh
         JOIN schedule s ON s.id = sh.schedule_id
         JOIN shift_type t ON t.id = sh.shift_type_id
WHERE t.name = 'בוקר'
  AND DATEDIFF(sh.shift_date, s.week_start) BETWEEN 0 AND 4;

-- Weekend mornings: one agent instead of two, and no supervisor.
INSERT INTO shift_requirement (shift_id, job_position_id, required_count, is_essential)
SELECT sh.id, 2, 1, b'1'
FROM shift sh
         JOIN schedule s ON s.id = sh.schedule_id
         JOIN shift_type t ON t.id = sh.shift_type_id
WHERE t.name = 'בוקר'
  AND DATEDIFF(sh.shift_date, s.week_start) BETWEEN 5 AND 6;

-- A senior agent every morning, all week.
INSERT INTO shift_requirement (shift_id, job_position_id, required_count, is_essential)
SELECT sh.id, 3, 1, b'1'
FROM shift sh
         JOIN shift_type t ON t.id = sh.shift_type_id
WHERE t.name = 'בוקר';

-- Evenings and nights are covered by a single agent.
INSERT INTO shift_requirement (shift_id, job_position_id, required_count, is_essential)
SELECT sh.id, 2, 1, b'1'
FROM shift sh
         JOIN shift_type t ON t.id = sh.shift_type_id
WHERE t.name IN ('ערב', 'לילה');


-- ---------------------------------------------------------------------------
-- The published week, fully staffed. Everyone keeps the same shift type all
-- week, so nobody ends up with less than 8 hours between shifts.
--
-- Noa has the Saturday night, which ends on the Sunday morning of the next
-- week. Will be used to test if the 8h rule apply between weeks.
-- ---------------------------------------------------------------------------

INSERT INTO assignment (shift_id, employee_id, is_override)
SELECT sh.id, e.id, b'0'
FROM shift sh
         JOIN schedule s ON s.id = sh.schedule_id AND s.week_start = '2026-08-02'
         JOIN shift_type t ON t.id = sh.shift_type_id
         JOIN (SELECT 'yoni@shiftscheduler.local' AS username, 'בוקר' AS shift_type, 0 AS from_day, 4 AS to_day
               UNION ALL
               SELECT 'gilad@shiftscheduler.local', 'בוקר', 0, 4
               UNION ALL
               SELECT 'tamar@shiftscheduler.local', 'בוקר', 5, 6
               UNION ALL
               SELECT 'maya@shiftscheduler.local', 'בוקר', 0, 4
               UNION ALL
               SELECT 'omer@shiftscheduler.local', 'בוקר', 0, 4
               UNION ALL
               SELECT 'noa@shiftscheduler.local', 'בוקר', 5, 5
               UNION ALL
               SELECT 'omer@shiftscheduler.local', 'בוקר', 6, 6
               UNION ALL
               SELECT 'shira@shiftscheduler.local', 'ערב', 0, 5
               UNION ALL
               SELECT 'maya@shiftscheduler.local', 'ערב', 6, 6
               UNION ALL
               SELECT 'eitan@shiftscheduler.local', 'לילה', 0, 5
               UNION ALL
               SELECT 'noa@shiftscheduler.local', 'לילה', 6, 6) j
              ON j.shift_type = t.name
         JOIN employee e ON e.username = j.username
WHERE DATEDIFF(sh.shift_date, s.week_start) BETWEEN j.from_day AND j.to_day;


-- ---------------------------------------------------------------------------
-- The draft week. Two people are manually assigned, the solver gets the rest.
-- ---------------------------------------------------------------------------

INSERT INTO assignment (shift_id, employee_id, is_override)
SELECT sh.id, e.id, b'0'
FROM shift sh
         JOIN schedule s ON s.id = sh.schedule_id AND s.week_start = '2026-08-09'
         JOIN shift_type t ON t.id = sh.shift_type_id AND t.name = 'בוקר'
         JOIN employee e ON e.username IN ('yoni@shiftscheduler.local', 'gilad@shiftscheduler.local')
WHERE DATEDIFF(sh.shift_date, s.week_start) = 0;


-- ---------------------------------------------------------------------------
-- Omer is away in the middle of the draft week, so the solver has to work
-- around him.
-- ---------------------------------------------------------------------------

INSERT INTO employee_leave (employee_id, leave_date, type)
SELECT e.id, d.leave_date, d.leave_type
FROM employee e
         JOIN (SELECT 'omer@shiftscheduler.local' AS username, DATE '2026-08-12' AS leave_date, 'VACATION' AS leave_type
               UNION ALL
               SELECT 'omer@shiftscheduler.local', DATE '2026-08-13', 'VACATION'
               UNION ALL
               SELECT 'shira@shiftscheduler.local', DATE '2026-08-19', 'TRAINING'
               UNION ALL
               SELECT 'tamar@shiftscheduler.local', DATE '2026-08-20', 'SICK') d
              ON d.username = e.username;


-- ---------------------------------------------------------------------------
-- Constraints for the collecting week. No entry means they can work it.
-- ---------------------------------------------------------------------------

INSERT INTO shift_preference (employee_id, shift_id, type, reason)
SELECT e.id, sh.id, p.pref_type, p.reason
FROM shift sh
         JOIN schedule s ON s.id = sh.schedule_id AND s.week_start = '2026-08-16'
         JOIN shift_type t ON t.id = sh.shift_type_id
         JOIN (SELECT 'maya@shiftscheduler.local' AS username,
                      'לילה'                      AS shift_type,
                      0                           AS from_day,
                      6                           AS to_day,
                      'CANNOT'                    AS pref_type,
                      'לימודים'                   AS reason
               UNION ALL
               SELECT 'omer@shiftscheduler.local', 'ערב', 4, 4, 'PREFERS_NOT', 'קבעתי עם משפחה'
               UNION ALL
               SELECT 'noa@shiftscheduler.local', 'בוקר', 0, 1, 'CANNOT', 'מחויבות ילדים'
               UNION ALL
               SELECT 'eitan@shiftscheduler.local', 'לילה', 3, 3, 'PREFERS_NOT', NULL
               UNION ALL
               SELECT 'gilad@shiftscheduler.local', 'בוקר', 5, 6, 'CANNOT', 'תוכניות לסופ"ש') p
              ON p.shift_type = t.name
         JOIN employee e ON e.username = p.username
WHERE DATEDIFF(sh.shift_date, s.week_start) BETWEEN p.from_day AND p.to_day;

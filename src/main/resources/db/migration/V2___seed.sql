INSERT INTO job_position (id, name)
VALUES (1, 'Shift supervisor'),
       (2, 'Support agent'),
       (3, 'Senior agent');

INSERT INTO shift_type (id, name, start_time, end_time, crosses_midnight)
VALUES (1, 'Morning', '07:00:00', '15:00:00', b'0'),
       (2, 'Evening', '15:00:00', '23:00:00', b'0'),
       (3, 'Night', '23:00:00', '07:00:00', b'1');

-- Usernames are work email addresses. The .local suffix isn't a real domain,
-- so nothing sent to these can reach an actual inbox by accident.
INSERT INTO employee (full_name, username, password_hash, role, max_weekly_hours, active, job_position_id)
VALUES ('Dana Levi', 'dana@shiftscheduler.local',
        '$argon2id$v=19$m=16384,t=2,p=1$ujvd34Ei2IpmENLeV7IBlg$zvLEvYWsW5Aup5Sx97oLt0utUeHrBFpCf1UEYKS2YRs', 'MANAGER',
        40, b'1', 1),
       ('Yoni Barak', 'yoni@shiftscheduler.local',
        '$argon2id$v=19$m=16384,t=2,p=1$pq59TOKklOFkP0cim+BoRQ$rUx5elFh0Ze7BeHOYtuTNjCV5v6HSe0g5HLDT6FKHZs', 'EMPLOYEE',
        40, b'1', 1),
       ('Maya Cohen', 'maya@shiftscheduler.local',
        '$argon2id$v=19$m=16384,t=2,p=1$50q3puOmMxcAEp77kL1KIA$QH/SsFa26Yk9KxH3KJYGUZU5bjEyfkGDO1Qz+9JrJpM', 'EMPLOYEE',
        40, b'1', 2),
       ('Omer Shani', 'omer@shiftscheduler.local',
        '$argon2id$v=19$m=16384,t=2,p=1$yTg216wjOAXbsWoJRGDXTQ$tU3ya8tVPU7NWQE9fxcs2w2hK6rnNCVz431WgMnqBl8', 'EMPLOYEE',
        40, b'1', 2),
       ('Shira Adler', 'shira@shiftscheduler.local',
        '$argon2id$v=19$m=16384,t=2,p=1$zMX7aqY/uveS/fliD1fmzA$LrJ5/J2It2SSrcAsZrgPP+5KWPkmi9y9U0A3WUkq3qY', 'EMPLOYEE',
        32, b'1', 2),
       ('Eitan Peled', 'eitan@shiftscheduler.local',
        '$argon2id$v=19$m=16384,t=2,p=1$j0mYwBv0x3di1Az+MxCl1A$RXH3dpDBxocQIdpviL/TLqHkmbmm0ohnSvUXqGMdEeI', 'EMPLOYEE',
        40, b'1', 2),
       ('Noa Gershon', 'noa@shiftscheduler.local',
        '$argon2id$v=19$m=16384,t=2,p=1$k8I8SkbePRN6OlYZt8KM0Q$BOOTfkhXW7ut34pgFisGDuZHBeSAhfCHvvHDfvDvItc', 'EMPLOYEE',
        24, b'1', 2),
       ('Gilad Roth', 'gilad@shiftscheduler.local',
        '$argon2id$v=19$m=16384,t=2,p=1$As3QHbZR9tdVAIyNlQ0Wsw$KZBFqmIT2jcpYQ9TcS1kfmNbo5MCqVV4B3eMBZ0baCY', 'EMPLOYEE',
        40, b'1', 3),
       ('Tamar Vardi', 'tamar@shiftscheduler.local',
        '$argon2id$v=19$m=16384,t=2,p=1$0fq/CZQKgiWEyQaQo4nh1w$YuPi73uZk+jUY7MZCyPW4/L3zd5EZaVPyvbt19j5NyI', 'EMPLOYEE',
        40, b'1', 3),
       ('Avi Mizrahi', 'avi@shiftscheduler.local',
        '$argon2id$v=19$m=16384,t=2,p=1$2huIgm9LA/HIAJxCWMXtPw$HSLZLwybQcNCmedrG+HdLN6x6QisUo8zbYm9hQM2a3w', 'EMPLOYEE',
        40, b'0', 3);
-- Job position names are limited to 40 characters, the same as shift types.
-- The column allowed 60, which the request would never accept.
ALTER TABLE job_position MODIFY name VARCHAR(40) NOT NULL;

-- The username is an email address. RFC 5321 caps a path at 256 characters
-- including the angle brackets, which leaves 254 for the address itself.
ALTER TABLE employee MODIFY username VARCHAR(254) NOT NULL;

-- An Argon2 hash with the Spring Security defaults comes to 97 characters,
-- and the column allowed 100. Changing one parameter would overflow it.
ALTER TABLE employee MODIFY password_hash VARCHAR(255) NOT NULL;

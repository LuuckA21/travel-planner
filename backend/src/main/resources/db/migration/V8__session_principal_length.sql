-- Registration accepts 254-char emails; Spring Session stores the email as principal name.
ALTER TABLE spring_session ALTER COLUMN principal_name TYPE varchar(320);

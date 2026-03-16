UPDATE users SET email_verified = FALSE WHERE email_verified IS NULL;
ALTER TABLE users ALTER COLUMN email_verified SET DEFAULT FALSE;
ALTER TABLE users ALTER COLUMN email_verified SET NOT NULL;

ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check;

ALTER TABLE users
  ADD CONSTRAINT users_role_check
  CHECK (
    role IS NULL OR role IN (
      'PATIENT',
      'DOCTOR',
      'NURSE',
      'KINE',
      'PSYCHIATRIST',
      'ADMIN'
    )
  );

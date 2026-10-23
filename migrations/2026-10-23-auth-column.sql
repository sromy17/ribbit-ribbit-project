-- Run before starting the merged application on a database created by Hibernate
-- with the older camel-case entity mapping. No password values are changed.
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=current_schema() AND table_name='users' AND column_name='user_hashed_salted_password')
     AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=current_schema() AND table_name='users' AND column_name='userhashedsaltedpassword') THEN
    ALTER TABLE users RENAME COLUMN user_hashed_salted_password TO userhashedsaltedpassword;
  END IF;
END $$;
ALTER TABLE users ADD COLUMN IF NOT EXISTS first_name VARCHAR(50);
ALTER TABLE users ADD COLUMN IF NOT EXISTS last_name VARCHAR(50);

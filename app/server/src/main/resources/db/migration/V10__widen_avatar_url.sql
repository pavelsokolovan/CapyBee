-- Google avatar URLs for Family Link (supervised child) accounts can exceed 512 chars.
ALTER TABLE users
    ALTER COLUMN avatar_url TYPE varchar(2048);

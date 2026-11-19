BEGIN IMMEDIATE;
ALTER TABLE item_meetup_locations ADD COLUMN meetup_at TEXT;
ALTER TABLE exchange_terms ADD COLUMN meetup_at TEXT;
ALTER TABLE exchange_proposals ADD COLUMN meetup_at TEXT;
PRAGMA user_version = 5;
COMMIT;

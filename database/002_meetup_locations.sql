BEGIN IMMEDIATE;

CREATE TABLE item_meetup_locations (
    item_id INTEGER PRIMARY KEY REFERENCES items(id),
    campus TEXT NOT NULL CHECK (campus IN ('NANHU', 'GUIZISHAN')),
    location TEXT NOT NULL CHECK (length(trim(location)) BETWEEN 1 AND 100)
) STRICT;

PRAGMA user_version = 2;
COMMIT;

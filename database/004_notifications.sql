BEGIN IMMEDIATE;
CREATE TABLE notifications (
    id INTEGER PRIMARY KEY,
    recipient_id INTEGER NOT NULL REFERENCES users(id),
    item_id INTEGER NOT NULL REFERENCES items(id),
    cause_exchange_id INTEGER NOT NULL REFERENCES exchanges(id),
    body TEXT NOT NULL CHECK (length(trim(body)) BETWEEN 1 AND 1000),
    created_at TEXT NOT NULL,
    read_at TEXT,
    UNIQUE (cause_exchange_id, recipient_id)
) STRICT;
CREATE INDEX notifications_inbox ON notifications(recipient_id, created_at DESC, id DESC);
PRAGMA user_version = 4;
COMMIT;

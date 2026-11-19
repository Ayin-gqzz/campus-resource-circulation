BEGIN IMMEDIATE;
CREATE TABLE exchange_terms (
    exchange_id INTEGER PRIMARY KEY REFERENCES exchanges(id),
    price_cents INTEGER CHECK (price_cents > 0),
    campus TEXT CHECK (campus IN ('NANHU', 'GUIZISHAN')),
    location TEXT,
    version INTEGER NOT NULL DEFAULT 0 CHECK (version >= 0),
    CHECK ((campus IS NULL AND location IS NULL) OR
           (campus IS NOT NULL AND location IS NOT NULL AND length(trim(location)) BETWEEN 1 AND 100))
) STRICT;
INSERT INTO exchange_terms (exchange_id, price_cents, campus, location)
SELECT e.id, i.price_cents, m.campus, m.location FROM exchanges e
JOIN items i ON i.id = e.item_id LEFT JOIN item_meetup_locations m ON m.item_id = i.id;

CREATE TABLE exchange_proposals (
    id INTEGER PRIMARY KEY,
    exchange_id INTEGER NOT NULL REFERENCES exchanges(id),
    proposer_id INTEGER NOT NULL REFERENCES users(id),
    base_version INTEGER NOT NULL,
    price_cents INTEGER CHECK (price_cents > 0),
    campus TEXT NOT NULL CHECK (campus IN ('NANHU', 'GUIZISHAN')),
    location TEXT NOT NULL CHECK (length(trim(location)) BETWEEN 1 AND 100),
    status TEXT NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'AGREED', 'DECLINED', 'WITHDRAWN', 'EXPIRED')),
    created_at TEXT NOT NULL,
    resolved_at TEXT,
    responder_id INTEGER REFERENCES users(id),
    CHECK ((status = 'PENDING' AND resolved_at IS NULL AND responder_id IS NULL)
        OR (status <> 'PENDING' AND resolved_at IS NOT NULL))
) STRICT;
CREATE UNIQUE INDEX one_pending_proposal ON exchange_proposals(exchange_id) WHERE status = 'PENDING';
PRAGMA user_version = 3;
COMMIT;

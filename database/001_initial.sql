PRAGMA foreign_keys = ON;
BEGIN IMMEDIATE;

CREATE TABLE users (
    id INTEGER PRIMARY KEY,
    username TEXT NOT NULL COLLATE NOCASE UNIQUE
        CHECK (length(username) BETWEEN 3 AND 24 AND username NOT GLOB '*[^a-zA-Z0-9_]*'),
    password_hash TEXT NOT NULL CHECK (length(password_hash) > 0),
    role TEXT NOT NULL DEFAULT 'STUDENT' CHECK (role IN ('STUDENT', 'ADMIN')),
    enabled INTEGER NOT NULL DEFAULT 1 CHECK (enabled IN (0, 1)),
    created_at TEXT NOT NULL
) STRICT;

CREATE TABLE categories (
    id INTEGER PRIMARY KEY,
    name TEXT NOT NULL COLLATE NOCASE UNIQUE CHECK (length(trim(name)) BETWEEN 1 AND 40),
    enabled INTEGER NOT NULL DEFAULT 1 CHECK (enabled IN (0, 1)),
    avoided_grams INTEGER CHECK (avoided_grams >= 0),
    factor_source TEXT,
    CHECK ((avoided_grams IS NULL AND factor_source IS NULL)
        OR (avoided_grams IS NOT NULL AND factor_source IS NOT NULL AND length(trim(factor_source)) > 0))
) STRICT;

CREATE TABLE items (
    id INTEGER PRIMARY KEY,
    owner_id INTEGER NOT NULL REFERENCES users(id),
    category_id INTEGER NOT NULL REFERENCES categories(id),
    title TEXT NOT NULL CHECK (length(trim(title)) BETWEEN 1 AND 80),
    description TEXT NOT NULL CHECK (length(trim(description)) BETWEEN 1 AND 1000),
    condition TEXT NOT NULL CHECK (condition IN ('NEW', 'GOOD', 'FAIR')),
    trade_type TEXT NOT NULL CHECK (trade_type IN ('SALE', 'SWAP')),
    price_cents INTEGER,
    swap_note TEXT,
    status TEXT NOT NULL DEFAULT 'AVAILABLE'
        CHECK (status IN ('AVAILABLE', 'RESERVED', 'COMPLETED', 'WITHDRAWN')),
    created_at TEXT NOT NULL,
    CHECK ((trade_type = 'SALE' AND price_cents IS NOT NULL AND price_cents > 0 AND swap_note IS NULL)
        OR (trade_type = 'SWAP' AND price_cents IS NULL AND swap_note IS NOT NULL
            AND length(trim(swap_note)) BETWEEN 1 AND 200))
) STRICT;
CREATE INDEX items_search ON items (status, category_id, price_cents);
CREATE INDEX items_by_owner ON items (owner_id, status);

CREATE TABLE item_tags (
    item_id INTEGER NOT NULL REFERENCES items(id),
    tag TEXT NOT NULL COLLATE NOCASE CHECK (length(trim(tag)) BETWEEN 1 AND 24),
    PRIMARY KEY (item_id, tag)
) STRICT;

CREATE TABLE favorites (
    user_id INTEGER NOT NULL REFERENCES users(id),
    item_id INTEGER NOT NULL REFERENCES items(id),
    created_at TEXT NOT NULL,
    PRIMARY KEY (user_id, item_id)
) STRICT;

CREATE TABLE exchanges (
    id INTEGER PRIMARY KEY,
    item_id INTEGER NOT NULL REFERENCES items(id),
    requester_id INTEGER NOT NULL REFERENCES users(id),
    status TEXT NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'CANCELLED', 'COMPLETED')),
    created_at TEXT NOT NULL,
    completed_at TEXT,
    CHECK ((status = 'COMPLETED' AND completed_at IS NOT NULL AND completed_at >= created_at)
        OR (status <> 'COMPLETED' AND completed_at IS NULL))
) STRICT;
CREATE UNIQUE INDEX exchange_one_pending_per_requester
    ON exchanges (item_id, requester_id) WHERE status = 'PENDING';
CREATE UNIQUE INDEX exchange_one_successful_per_item
    ON exchanges (item_id) WHERE status IN ('ACCEPTED', 'COMPLETED');
CREATE INDEX exchanges_by_requester ON exchanges (requester_id, created_at);
CREATE INDEX exchanges_by_item ON exchanges (item_id, status);
CREATE INDEX exchanges_completed_at ON exchanges (completed_at) WHERE status = 'COMPLETED';

CREATE TABLE exchange_messages (
    id INTEGER PRIMARY KEY,
    exchange_id INTEGER NOT NULL REFERENCES exchanges(id),
    sender_id INTEGER NOT NULL REFERENCES users(id),
    body TEXT NOT NULL CHECK (length(trim(body)) BETWEEN 1 AND 500),
    sent_at TEXT NOT NULL
) STRICT;
CREATE INDEX messages_by_exchange ON exchange_messages (exchange_id, id);

CREATE TABLE reviews (
    exchange_id INTEGER NOT NULL REFERENCES exchanges(id),
    reviewer_id INTEGER NOT NULL REFERENCES users(id),
    reviewee_id INTEGER NOT NULL REFERENCES users(id),
    stars INTEGER NOT NULL CHECK (stars BETWEEN 1 AND 5),
    comment TEXT NOT NULL DEFAULT '' CHECK (length(comment) <= 500),
    created_at TEXT NOT NULL,
    PRIMARY KEY (exchange_id, reviewer_id),
    CHECK (reviewer_id <> reviewee_id)
) STRICT;
CREATE INDEX reviews_by_reviewee ON reviews (reviewee_id);

PRAGMA user_version = 1;
COMMIT;

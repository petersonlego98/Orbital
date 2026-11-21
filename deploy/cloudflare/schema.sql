-- Orbital online relay — D1 schema.
-- One row per authoritative event. seq is a string in D1 (INTEGER is 64-bit signed, but SQLite's
-- driver returns numbers; storing as text avoids precision loss when casting in queries).

CREATE TABLE IF NOT EXISTS events (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    room       TEXT    NOT NULL,
    seq        TEXT    NOT NULL,
    tick       TEXT    NOT NULL,
    type_id    INTEGER NOT NULL,
    payload    TEXT    NOT NULL,          -- base64 of the compact binary payload
    created_at INTEGER NOT NULL DEFAULT (unixepoch('subsec') * 1000)
);

CREATE INDEX IF NOT EXISTS idx_events_room_seq ON events(room, CAST(seq AS INTEGER));
CREATE INDEX IF NOT EXISTS idx_events_created ON events(created_at);

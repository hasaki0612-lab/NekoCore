CREATE TABLE players (
    uuid TEXT PRIMARY KEY NOT NULL,
    name TEXT NOT NULL,
    coins INTEGER NOT NULL DEFAULT 0 CHECK(coins >= 0),
    level INTEGER NOT NULL DEFAULT 1 CHECK(level >= 1),
    exp INTEGER NOT NULL DEFAULT 0 CHECK(exp >= 0),
    playtime_seconds INTEGER NOT NULL DEFAULT 0 CHECK(playtime_seconds >= 0),
    first_join INTEGER NOT NULL,
    last_join INTEGER NOT NULL,
    show_ip INTEGER NOT NULL DEFAULT 0 CHECK(show_ip IN (0, 1))
);
CREATE INDEX idx_players_name ON players(name COLLATE NOCASE);
CREATE TABLE homes (
    player_uuid TEXT NOT NULL REFERENCES players(uuid) ON DELETE CASCADE,
    world_uuid TEXT NOT NULL,
    world_name TEXT NOT NULL,
    x REAL NOT NULL,
    y REAL NOT NULL,
    z REAL NOT NULL,
    yaw REAL NOT NULL,
    pitch REAL NOT NULL,
    PRIMARY KEY(player_uuid, world_uuid)
);
CREATE INDEX idx_homes_world_name ON homes(player_uuid, world_name COLLATE NOCASE);

CREATE TABLE daily_checkins (
    player_uuid TEXT NOT NULL REFERENCES players(uuid) ON DELETE CASCADE,
    claim_date TEXT NOT NULL,
    claimed_at INTEGER NOT NULL,
    timezone TEXT NOT NULL,
    coins_awarded INTEGER NOT NULL CHECK(coins_awarded >= 0),
    exp_awarded INTEGER NOT NULL CHECK(exp_awarded >= 0),
    PRIMARY KEY(player_uuid, claim_date)
);

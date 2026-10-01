CREATE TABLE weekly_coin_earnings (
    period_start TEXT NOT NULL,
    player_uuid TEXT NOT NULL REFERENCES players(uuid),
    earned_coins INTEGER NOT NULL CHECK(earned_coins >= 0),
    updated_at INTEGER NOT NULL,
    PRIMARY KEY(period_start, player_uuid)
);
CREATE INDEX weekly_coin_earnings_ranking ON weekly_coin_earnings(period_start, earned_coins DESC, player_uuid);

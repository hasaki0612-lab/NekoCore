CREATE TABLE owned_titles (
  player_uuid TEXT NOT NULL REFERENCES players(uuid),
  title_id TEXT NOT NULL,
  purchased_at INTEGER NOT NULL,
  price_paid INTEGER NOT NULL CHECK(price_paid >= 0),
  PRIMARY KEY(player_uuid, title_id)
);
CREATE TABLE equipped_titles (
  player_uuid TEXT PRIMARY KEY REFERENCES players(uuid),
  title_id TEXT NOT NULL,
  FOREIGN KEY(player_uuid, title_id) REFERENCES owned_titles(player_uuid, title_id)
);
CREATE TABLE bags (
  player_uuid TEXT PRIMARY KEY REFERENCES players(uuid),
  capacity INTEGER NOT NULL DEFAULT 18 CHECK(capacity IN (18,27,36)),
  revision INTEGER NOT NULL DEFAULT 0,
  contents BLOB NOT NULL DEFAULT X''
);
CREATE TABLE store_quotas (
  player_uuid TEXT NOT NULL REFERENCES players(uuid),
  period TEXT NOT NULL,
  product_id TEXT NOT NULL,
  direction TEXT NOT NULL CHECK(direction IN ('buy','sell')),
  quantity INTEGER NOT NULL CHECK(quantity >= 0),
  PRIMARY KEY(player_uuid, period, product_id, direction)
);
CREATE TABLE store_periods (kind TEXT PRIMARY KEY, period TEXT NOT NULL);
CREATE TABLE enchant_batches (
  batch_id TEXT PRIMARY KEY,
  period TEXT NOT NULL,
  created_at INTEGER NOT NULL
);
CREATE TABLE enchant_offers (
  batch_id TEXT NOT NULL REFERENCES enchant_batches(batch_id),
  slot INTEGER NOT NULL CHECK(slot BETWEEN 0 AND 7),
  enchantment TEXT NOT NULL,
  level INTEGER NOT NULL CHECK(level >= 1),
  price INTEGER NOT NULL CHECK(price > 0),
  maximum INTEGER NOT NULL CHECK(maximum IN (0,1)),
  PRIMARY KEY(batch_id, slot)
);
CREATE TABLE inventory_exchanges (
  exchange_id TEXT PRIMARY KEY,
  player_uuid TEXT NOT NULL REFERENCES players(uuid),
  world_uuid TEXT NOT NULL,
  kind TEXT NOT NULL CHECK(kind IN ('store','bag')),
  state TEXT NOT NULL CHECK(state IN ('prepared','committed','rolled_back')),
  created_at INTEGER NOT NULL,
  inventory_before BLOB NOT NULL,
  inventory_after BLOB NOT NULL,
  coins_delta INTEGER NOT NULL,
  product_id TEXT NOT NULL,
  period TEXT NOT NULL,
  direction TEXT NOT NULL,
  quantity INTEGER NOT NULL,
  bag_before BLOB,
  bag_after BLOB,
  bag_revision INTEGER NOT NULL
);
CREATE UNIQUE INDEX one_pending_exchange_per_player ON inventory_exchanges(player_uuid) WHERE state='prepared';
CREATE INDEX inventory_exchanges_by_player ON inventory_exchanges(player_uuid);

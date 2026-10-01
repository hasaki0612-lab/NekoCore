CREATE TABLE daily_task_rotations (
  date TEXT NOT NULL,
  rotation_id TEXT NOT NULL,
  difficulty TEXT NOT NULL CHECK(difficulty IN ('easy','normal','hard')),
  slot_index INTEGER NOT NULL CHECK(slot_index >= 0),
  task_id TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  PRIMARY KEY(date, difficulty, slot_index),
  UNIQUE(date, task_id)
);
CREATE INDEX daily_task_rotations_by_id ON daily_task_rotations(rotation_id);
CREATE TABLE player_daily_task_progress (
  player_uuid TEXT NOT NULL REFERENCES players(uuid),
  date TEXT NOT NULL,
  rotation_id TEXT NOT NULL,
  task_id TEXT NOT NULL,
  progress INTEGER NOT NULL DEFAULT 0 CHECK(progress >= 0),
  completed INTEGER NOT NULL DEFAULT 0 CHECK(completed IN (0,1)),
  reward_given INTEGER NOT NULL DEFAULT 0 CHECK(reward_given IN (0,1)),
  extra_state TEXT NOT NULL DEFAULT '',
  updated_at INTEGER NOT NULL,
  PRIMARY KEY(player_uuid, date, rotation_id, task_id)
);
CREATE INDEX daily_task_progress_lookup ON player_daily_task_progress(player_uuid, date, rotation_id);

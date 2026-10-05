-- Why this exists
-- ---------------
-- `difficulty_levels` was part of the inherited baseline, where `level_name` is
-- NOT NULL with no default. That makes the table hostile to any INSERT that names
-- only the two columns the application actually maps (`difficulty_id`,
-- `level_code`) - the seeder, a data import, or anything run against a fresh
-- install. The band list is reference data curated by hand, so the default only
-- ever applies to a row inserted without a label.
--
-- The entity now maps `level_name` read-only, which lets the H2 test profile emit
-- a matching DEFAULT and keeps `ddl-auto: validate` checking that the column is
-- still present in MySQL.
ALTER TABLE difficulty_levels
    MODIFY level_name VARCHAR(50) NOT NULL DEFAULT 'Không xác định';
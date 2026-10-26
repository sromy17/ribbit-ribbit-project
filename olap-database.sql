-- PostgreSQL container initialization for an empty volume.
-- Existing volumes are migrated non-destructively by scripts/start-local.sh.
CREATE DATABASE "ribbit-olap";
\connect "ribbit-olap"
\i /schemas/olap.sql

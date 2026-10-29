-- Read the configured name without changing an existing volume.
\getenv reporting_database POSTGRES_OLAP_DB
CREATE DATABASE :"reporting_database";
\connect :reporting_database
\i /opt/ribbit/olap.sql

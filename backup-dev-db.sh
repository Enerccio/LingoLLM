DB=~/.lingollm/lingollm.sqlite
for t in $(sqlite3 "$DB" "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name <> 'flyway_schema_history'"); do
  sqlite3 "$DB" ".headers on" ".mode insert $t" "SELECT * FROM $t;"
done > dev.sql
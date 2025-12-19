# Block Archive Removal

## Summary
- Data retention no longer archives or deletes player blocks; blocks are preserved indefinitely.
- Added migration V6 to drop the unused `player_blocks_archive` table (SQLite and MySQL).
- Data retention now only archives/cleans infractions and cleans old message bus records.

## Rationale
- Player block history should remain intact and not be pruned during maintenance.

## Impact
- Existing installations will drop the unused archive table during the next migration.
- No configuration changes are required.

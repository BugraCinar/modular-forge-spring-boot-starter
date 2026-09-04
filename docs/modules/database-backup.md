# Database backup module

Id `database-backup`; package `dev.modularforge.backup`; default off; toggle `app.modules.database-backup.enabled`.

The built-in implementation owns the manual endpoint, scheduled job, backup directory, and `mysqldump` process. It supports MySQL and MariaDB only. It emails a raw SQL attachment, so production systems with sensitive data should replace it with encrypted object storage and a tested restore/retention process.

## Remove

1. Set the toggle to `false` and verify no backup job or route is registered.
2. Move or destroy backup files according to retention policy.
3. Delete the backup package and tests.
4. Remove `DATABASE_BACKUP_*`, `MYSQLDUMP_PATH`, and `app.database.backup.*` settings.
5. Remove the `backupExecutor` bean if nothing else uses it and remove the catalog entry.
6. Run `./mvnw clean verify`.

Production backup should normally be handled by managed database snapshots with tested encryption, retention, restore, and access controls.

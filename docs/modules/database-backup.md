# Database backup module

Id `database-backup`; package `dev.modularforge.backup`; default off; toggle `app.modules.database-backup.enabled`.

The built-in implementation owns the manual endpoint, scheduled job, backup directory, and `mysqldump` process. It supports MySQL and MariaDB only. It encrypts the dump with AES-256-GCM before attaching it to email and removes local plaintext/encrypted files after delivery or failure. Configure a separate `DATABASE_BACKUP_ENCRYPTION_KEY` (32 random bytes, base64) and keep the key outside the mailbox. MySQL uses `--ssl-mode=VERIFY_IDENTITY`; MariaDB uses `--ssl-verify-server-cert`. Set `DATABASE_BACKUP_SSL_CA` for a private CA and `MARIADB_DUMP_PATH` for the MariaDB tool. The subprocess does not inherit JDBC TLS options.

## Remove

1. Set the toggle to `false` and verify no backup job or route is registered.
2. Move or destroy backup files according to retention policy.
3. Delete the backup package and tests.
4. Remove `DATABASE_BACKUP_*`, `MYSQLDUMP_PATH`, `MARIADB_DUMP_PATH`, and `app.database.backup.*` settings.
5. Remove the `backupExecutor` bean if nothing else uses it and remove the catalog entry.
6. Run `./mvnw clean verify`.

Production backup should normally be handled by managed database snapshots with tested encryption, retention, restore, and access controls.

## Restore

The attachment format is four bytes `M F B 0x01`, a 12-byte nonce, then AES-GCM ciphertext and its 16-byte tag. Use `scripts/RestoreBackup.java` with Java 21 and `DATABASE_BACKUP_ENCRYPTION_KEY` in the environment to authenticate/decrypt it before importing SQL. A wrong key or modified file fails authentication. Test restores before enabling scheduled backups.

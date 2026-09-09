## SQL CREATE DATABASE Statement

# The SQL CREATE DATABASE Statement

--> The CREATE DATABASE statement is used to create a new SQL database.

==> Syntax
CREATE DATABASE databasename;

# CREATE DATABASE Example

--> The following SQL statement creates a database called "testDB":

==> Example
CREATE DATABASE testDB;

**Note** :- Make sure you have admin privilege before creating any database. Once a database is created, you can check it in the list of databases with the following SQL command: SHOW DATABASES;

## SQL DROP DATABASE Statement

# The SQL DROP DATABASE Statement

--> The DROP DATABASE statement is used to drop an existing SQL database.

==> Syntax
DROP DATABASE databasename;

**Note**: Be careful before dropping a database. Deleting a database will result in loss of complete information stored in the database!

# DROP DATABASE Example

--> The following SQL statement drops the existing database "testDB":

==> Example
DROP DATABASE testDB;

**Note**: Make sure you have admin privilege before dropping any database. Once a database is dropped, you can check it in the list of databases with the following SQL command: SHOW DATABASES;

## SQL BACKUP DATABASE for SQL Server

# The SQL BACKUP DATABASE Statement

--> The BACKUP DATABASE statement is used in SQL Server to create a full back up of an existing SQL database.

==> Syntax
BACKUP DATABASE databasename
TO DISK = 'filepath';

# The SQL BACKUP WITH DIFFERENTIAL Statement

--> A differential back up only backs up the parts of the database that have changed since the last full database backup.

==> Syntax
BACKUP DATABASE databasename
TO DISK = 'filepath'
WITH DIFFERENTIAL;

# BACKUP DATABASE Example

--> The following SQL statement creates a full back up of the existing database "testDB" to the D disk:

==> Example
BACKUP DATABASE testDB
TO DISK = 'D:\backups\testDB.bak';

**Note**: Always back up the database to a different drive than the actual database. Then, if you get a disk crash, you will not lose your backup file along with the database.

# BACKUP WITH DIFFERENTIAL Example

The following SQL statement creates a differential back up of the database "testDB":

==> Example
BACKUP DATABASE testDB
TO DISK = 'D:\backups\testDB.bak'
WITH DIFFERENTIAL;

**Note**: A differential back up reduces the back up time (since only the changes are backed up).

## Deep Dive -- The Three Backup Types, Compared

--> **Full backup** -- copies the ENTIRE database -- simplest to restore from (just one file needed), but takes the longest to create and the most storage space.
--> **Differential backup** (covered above) -- copies only what's changed since the LAST FULL backup -- restoring requires the last full backup PLUS the most recent differential (two files).
--> **Incremental backup** -- copies only what's changed since the LAST BACKUP OF ANY KIND (full or incremental) -- smallest and fastest individual backups, but restoring requires the full backup PLUS every single incremental taken since, in exact order (potentially many files) -- a real trade-off between fast backups and slower, more fragile restores.
--> **Transaction log backup** (SQL Server specifically) -- backs up the transaction log itself, enabling "point-in-time recovery" -- restoring to the exact moment just before a specific mistake occurred (e.g. seconds before an accidental mass `DELETE`), rather than only to whenever the last full/differential backup happened to run.

## Deep Dive -- RTO and RPO -- Connecting Backups to Business Continuity

--> Directly connecting to the Business Continuity and Disaster Recovery file in the Cyber Security track -- a database backup STRATEGY is fundamentally a decision about two business metrics, not just a technical choice:
--> **RPO (Recovery Point Objective)** -- how much data can you afford to LOSE, measured in time? A full backup taken once nightly means an RPO of up to 24 hours -- if the database fails at 11 PM, everything since last night's backup is gone. Adding transaction log backups every 15 minutes shrinks that RPO dramatically.
--> **RTO (Recovery Time Objective)** -- how long can the system afford to be DOWN while restoring? Restoring a single full backup file is fast; restoring a full backup plus dozens of incremental backups in sequence takes considerably longer -- directly trading off against the backup-frequency choice made for RPO.
--> **Why this matters practically** -- the "right" backup strategy isn't a purely technical decision -- it's determined by the business's actual tolerance for data loss and downtime, which is exactly why this decision in real organizations usually involves discussion with stakeholders beyond just the database administrator, echoing the same business-context-first approach emphasized in the Data Analyst folder's decision-making philosophy.

## Deep Dive -- Testing Backups -- The Step Most Often Skipped

--> A backup that has never been TESTED by actually performing a restore is not a reliable backup -- corruption, incomplete backup jobs, or a missing dependency (a backup that assumes a specific server configuration that no longer matches production) can all silently render a backup useless, discovered only during an actual emergency restore attempt, when it's far too late to fix.
--> Standard practice -- periodically perform a full test restore (to a separate, non-production environment) and verify the restored database is actually complete and functional -- a genuinely common, well-documented cause of real disaster-recovery failures is discovering, during the actual disaster, that backups had silently stopped working weeks or months earlier.

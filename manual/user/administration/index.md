---
label: Administration
order: 910
icon: tools
---

# Administration

Administrators manage the LingoLLM installation: user accounts, database backups, cleanup of deleted data and
extensions. The first account, created on the [first start](../getting-started/first-start.md), is an administrator;
it can make other users administrators too.

Administrators are also normal users with their own language profiles - they don't see other users' profiles or settings.

## The Admin page

Administrators have an **Admin** button at the bottom of the workspace. It is only in the normal layout: the
[phone layout](../phone.md) (a window narrower than 768 pixels) has no Admin page, so open LingoLLM in a wider window (and reload the page) for administration. It opens the admin page with four tabs:

| Tab | |
|---|---|
| **Users** | Creating, editing and deleting accounts. See [Users](users.md). |
| **Database Backups** | Backups of the whole database, on demand or on a schedule, and restoring them. See [Database backups](database-backups.md). |
| **Cleanup** | Permanently removing deleted data. See [Cleanup](cleanup.md). |
| **Extensions** | Installing and removing extensions. See [Managing extensions](extensions.md). |

## The data folder

Everything LingoLLM stores is in the `.lingollm` folder in the home directory of the user that runs it (`./data`
on the host with the [Docker setup](../getting-started/docker-server.md)):

| Path | |
|---|---|
| `lingollm.sqlite` (with `-wal`, `-shm`) | The database: users, language profiles, chats, settings, everything. |
| `secret.key` | Key that encrypts the API keys in the database. Created on the first start; without it, saved API keys can't be read. |
| `db-backups/` | [Database backups](database-backups.md). |
| `data/<login>/images/`, `data/<login>/resources/` | The files each user uploaded ([Resources](../resources.md)). |
| `extensions/` | Installed [extension](extensions.md) JARs. |
| `desktop/logs/` | Logs of the [desktop app](../getting-started/desktop-app.md). |

To back up a whole installation, stop LingoLLM and copy this folder (including `secret.key`). While it runs, use
[database backups](database-backups.md) for the database; uploaded files and extensions are plain files and can be
copied at any time.

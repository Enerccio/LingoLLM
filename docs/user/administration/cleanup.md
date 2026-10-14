# Cleanup

When users delete something in LingoLLM - a language profile, a chat, an inference provider - it disappears
from their lists, but stays in the database. That makes deleting safe and keeps references working (a chat still
knows the provider that answered). Until then users can restore their deleted objects from the
[Trash](../trash.md) tab. **Cleanup** removes such deleted data for good, to keep the database small.

## Running a cleanup

!!!warning
Cleanup can't be undone, what it removes is gone from the [Trash](../trash.md) too. [Create a database backup](database-backups.md) first.
!!!

1. Open the **Cleanup** tab of the admin page and click **Analyze**. Nothing is changed yet.
2. Check the result (see below).
3. Click **Run Cleanup** and confirm *n objects will be permanently removed*.

Cleanup covers the data of all users.

Files are not removed. Cleanup deletes entries from the database only; the files of deleted
[resources](../resources.md) (files you uploaded) stay in the data folder of their owner.

## Seeing what is deleted

**Trash** below the tables lists the deleted objects of all users, one row per object (open it to load the table),
so you can see what a cleanup would remove and restore what somebody asked you to bring back. It is the same table
as the [Trash](../trash.md) tab, including the filters by user and type and the *Extended Content* link.

## Reading the analysis

The first table has one row per kind of data:

| Column | |
|---|---|
| **Entity** | The kind of data: `Language` (language profiles), `FreeChat`, `FreeChatMessage`, `AI` (inference providers), `Resource`, `User`... |
| **Deleted** | How many are deleted. |
| **To purge** | How many will be removed by *Run Cleanup*, including data that belongs to deleted objects. |
| **Blocked** | Deleted, but still in use, so they are kept. |

**Deleted objects still in use** lists the blocked objects with the name and **Referenced by** - what still uses them.

## What is removed

Cleanup removes deleted objects that nothing alive uses any more, together with the data that belongs only to them:

| Deleted | Removed with it |
|---|---|
| a language profile | all its chats and their messages; it is forgotten as the last profile in *Settings* |
| a chat | all its messages |
| a message | the message (its children were already moved to its parent) |
| an inference provider | cleared from the users' defaults in *Settings*; its usage numbers go with it |
| a user | everything they owned: language profiles (with chats and messages), inference providers and settings |

## What is kept

A deleted object is **blocked** - kept - while something alive still uses it:

| Blocked | Because | To unblock |
|---|---|---|
| a deleted user | something of another user still uses their data (for example a shared inference provider they own is used) | stop using it, or delete what uses it |
| a deleted object that an extension still needs | the extension says so | see the extension |

Run *Analyze* again after unblocking.

## Reference model

**Reference Model** at the bottom lists every reference between the kinds of data and its policy - useful when you
want to understand why something is blocked:

| Policy | Meaning |
|---|---|
| **Blocks purge** | A deleted target is kept while a live object refers to it. |
| **Purged with target** | The referring object belongs to the target and is removed with it (messages with their chat). |
| **Owns target** | The target belongs to the referring object (a usage count to its provider). |
| **Cleared on purge** | The reference is removed when the target is purged (default model, last language profile). |

Extensions can take part in cleanup with their own rules.

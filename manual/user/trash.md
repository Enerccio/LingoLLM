---
label: Trash
order: 955
icon: trash
---

# Trash

Deleting in LingoLLM does not destroy anything right away: a deleted language profile, chat or inference provider
is only marked as deleted and hidden from your lists. The **Trash** tab lists these deleted objects and brings them
back.

Deleted objects stay in the trash until an administrator runs the [cleanup](administration/cleanup.md), which removes
them from the database for good. Whatever the cleanup has removed can no longer be restored (only a
[database backup](administration/database-backups.md) made before still has it).


The table loads as you scroll. The most recently deleted objects are first.

| Column | |
|---|---|
| (tick box) | Selects the object for restoring. The box in the header selects all objects. |
| **Type** | The kind of object: language profile, free chat, message, model (inference provider), resource... |
| **Name** | The name of the object. Messages have no name and show their id; use *Extended Content* to read a message. |
| **User** | Whose object it is. Only administrators see this column. |
| **Deleted** | When the object was deleted. |
| **Extended Content** | Opens the saved data of the object, see [below](#extended-content). |

Above the table, **Type** shows only one kind of object, **Refresh** loads the table again.

## Restoring

Tick the objects you want back and click **Restore Selected**. LingoLLM asks before it restores them. The objects
are back in their lists, exactly as they were deleted, and the other tabs are refreshed.

### Objects that belong to other objects

Deleting an object deletes what belongs to it, and restoring it brings that back: a language profile takes its chats
and their messages with it, a chat its messages, an inference provider its usage numbers. You don't need to tick them:
restore the profile, and its chats come with it. They show up in the table as separate rows; restoring one of them
alone only works when the object it belongs to is not deleted.

Some objects can't live without another object: a chat and a message need their language profile, a message needs its
chat. An object whose profile or chat is deleted **can only be restored together with it**: tick both. If you tick only
the message, LingoLLM restores **nothing** and tells you what is missing:

> Nothing was restored. These objects need a deleted object that is not selected. Select it too, or leave them deleted.

Tick the object it names and restore again. Objects that are not deleted any more (restored in the meantime, or an
object you didn't delete) are not a problem.

!!!info A restored message
A message that was deleted from the middle of a chat had its following messages moved to its parent. Restoring it brings
the message back under its parent as a new branch, **without** the messages that followed it: they stay where they were
moved to. Restore the whole chat instead of single messages when you want it as it was.
!!!

## Deleting for good

Waiting for the cleanup is not always what you want: a message you know you never need again can be removed right away.
Tick it and click **Delete Permanently**; LingoLLM asks first, and **this can't be undone**.

- Only **messages** can be deleted permanently. The button is disabled while anything else is ticked: profiles, chats
  and the rest wait for the administrator's [cleanup](administration/cleanup.md), so you can't remove a whole profile by
  accident.
- Messages that are below a removed message are not removed with it, whether they are deleted or not: they move to its
  parent.
- LingoLLM deletes nothing when something can't be removed (for example an extension still needs the object) and tells
  you which one.

### Extended content

LingoLLM keeps much of an object's data (the text of a message, the note of a profile, notes that
[extensions](extensions/index.md) saved) as JSON next to it. **Extended Content** in a row shows it, so you can
check what an object contains before you restore it, for example to find the right message. Objects without such
data (resources) have no link; an object that has none shows *This object has no extended content.*

## Administrators

Administrators see the deleted objects of **all users** and can restore any of them. The **User** filter above the
table shows the objects of one user; the **User** column tells whose they are. Restored objects go back to their
owner.

The same table is in *Admin → Cleanup* (**Trash**), so you can see what the next cleanup removes. Objects of a deleted
user can't be restored: they depend on the user.

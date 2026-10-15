---
label: Account & settings
order: 920
icon: person
---

# Account & settings

Everything you create in LingoLLM belongs to your account: language profiles, inference providers and settings.
Other users don't see them (except an inference provider an administrator shares), and you don't see theirs.

## Your account

The buttons at the bottom of the workspace:

| Button | |
|---|---|
| **Language** | The language of the user interface, see [Language](#language). |
| **Change Password** | Changes your password, user name and full name, see below. |
| **Logout** | Logs you out on this device. |
| **Admin** | Only for administrators, see [Administration](administration/index.md). |

### Changing the password

**Change Password** opens a dialog with your **Username**, **Full Name**, **Current Password**, **Password** and
**Repeat password**:

- enter your **current password** for every change (leave it empty if your account has no password);
- to change the password, enter the new one in both password fields;
- leave both password fields empty to change only the name and keep the password;
- the user name must not be used by another account.

Click **OK** to save. With a wrong current password nothing is saved.

Changing the password ends all saved logins (*Save login*), on every device including this one: they have to log in
again with the new password. If a device was lost or your password leaked, changing the password logs that device
out. Sessions that are open right now (a browser tab still showing the workspace) stay logged in until they log out
or their session expires.

### Language

The **Language** selector above the buttons switches the user interface to another language. The choice is saved in
your account: the page reloads with the new language, and so does every other page you have open (on another device
too), and it is used again the next time you log in. The login screen itself is always in the default language.

The list contains only the languages that are installed. English comes with LingoLLM; an administrator can add more
by loading an [extension](extensions/index.md). Texts a language doesn't translate are shown in English. Dates are
written the way that language does. This is the language of the interface, not a language you learn (those are your
[language profiles](language-profiles.md)).

### Logging in

**Save login** on the login screen keeps you logged in on this device for 30 days (it needs HTTPS on a server, see
[Server (Docker)](getting-started/docker-server.md#https-and-reverse-proxy)). **Logout** ends it.

An account can have an empty password; you then log in with the user name only. If an administrator cleared your
forgotten password, log in this way and set a new password with **Change Password** right away. That is handy for the desktop app on
your own computer, but never do it on a server others can reach.

### Skipping the login screen

Start LingoLLM with the VM argument `-Dlingollm-autologin=<user name>` (the desktop app: `--lingollm-autologin <user name>`)
and that user is logged in as soon as the page opens, without the login screen. It works only when the account has
**no password**; if it has one, or the user does not exist or is locked, the login screen is shown as usual (the reason
is in the log). Whoever can open the address gets in as that user, so use it only on your own computer. **Logout** shows
the login screen once; the next time the page is opened you are logged in again.

## Settings

The **Settings** tab holds your personal settings.

At the top are two buttons:

- **Discard Changes** - throws away the unsaved changes and loads the saved settings again;
- **Save** - saves the settings.

!!!info Unsaved changes
Settings are not saved automatically. As soon as you change a field (extension settings included), *Unsaved changes*
appears at the top and the other tabs, **Admin** and **Logout** are locked. Click **Save** or **Discard Changes** to
unlock them.
!!!

### General Settings

| Field | |
|---|---|
| **Default Model** | The [inference provider](inference-providers.md) that is used when nothing else is chosen. |

### Extension Settings

Settings of the installed [extensions](extensions/index.md), one section per extension. They are saved with the same
**Save** button. The tab is empty when no extension with settings is installed.

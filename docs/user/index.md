# User guide

This part of the manual is for people who learn with LingoLLM and for those who run it for others. It explains every
screen and setting, from installing LingoLLM and connecting a model to backing up the database. You don't need to know
anything about programming.

If you are new, start with [Getting started](getting-started/index.md) - it takes you from installation to your first
language profile in a few minutes.

## Core concepts

| Concept                | What it is                                                                                                                                      | Read more                                     |
|------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------|
| **Language profile**   | One language you learn: a template (Japanese, German...), your own name for it and a note. Everything you practise belongs to one profile.     | [Language profiles](language-profiles.md)     |
| **Free chat**          | A conversation in the language you learn. A tree of messages; attachments are links to your [resources](resources.md). Not in the app yet.    | [Language profiles](language-profiles.md)     |
| **Inference provider** | The connection to a model: API address, API key, model and its limits.                                                                          | [Inference providers](inference-providers.md) |
| **Extension**          | A plugin that adds features.                                                                                                                     | [Extensions](extensions/index.md)             |

Everything you create belongs to your account. Other users of the same LingoLLM don't see your profiles, providers or
settings - except an inference provider an administrator shares with everybody.

## The workspace

After logging in you see the workspace. The tabs on the left are the main areas of LingoLLM:

| Tab                     |                                                                                                             |
|-------------------------|-------------------------------------------------------------------------------------------------------------|
| **Learning**            | The language selector and what you do in the selected language. See [Language profiles](language-profiles.md). |
| **Settings**            | Your defaults, such as the default model. See [Account & settings](account.md).                             |
| **Inference Providers** | Connections to models. See [Inference providers](inference-providers.md).                                   |
| **Resources**           | The files you uploaded. See [Resources](resources.md).                                                      |
| **Trash**               | What you deleted, until an administrator cleans up. Restore it from here. See [Trash](trash.md).            |

At the bottom are **Change Password**, **Logout**, the interface **Language** and, for administrators, **Admin**.
Extensions may add more tabs and buttons.

On a narrow screen (under 768 pixels) you get a simpler [phone layout](phone.md) instead: the tabs are in a drawer, and
there is no Admin page. The layout is chosen when the page loads: after turning a device or resizing the window,
reload the page to get the other one.

## Sections of this guide

| Section                                       | For                                                                                                                                                                                                                  |
|-----------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [Getting started](getting-started/index.md)   | Installing the [desktop app](getting-started/desktop-app.md) or a [server](getting-started/docker-server.md), the [first start](getting-started/first-start.md) and a [quick start](getting-started/quick-start.md). |
| [Language profiles](language-profiles.md)     | Creating and switching the languages you learn.                                                                                                                                                                      |
| [Inference providers](inference-providers.md) | Connecting cloud and local models, context limits, reasoning models.                                                                                                                                                 |
| [Resources](resources.md)                     | The files you uploaded: download, replace and delete them.                                                                                                                                                           |
| [Trash](trash.md)                             | Deleted objects and how to restore them.                                                                                                                                                                             |
| [Extensions](extensions/index.md)             | What extensions are and how they are installed.                                                                                                                                                                      |
| [Account & settings](account.md)              | Your password, user name and settings.                                                                                                                                                                               |
| [Administration](administration/index.md)     | For administrators: users, database backups, cleanup, installing extensions.                                                                                                                                         |
| [Using LingoLLM on a phone](phone.md)         | The phone layout: how it differs from the desktop, what is missing, which extensions work.                                                                                                                           |
| [Troubleshooting & FAQ](troubleshooting.md)   | Logs, common problems and their solutions.                                                                                                                                                                           |

## Conventions

- Names of tabs, buttons and fields are in **bold** or *italics*, written as they appear in LingoLLM, e.g.
  *Admin → Users*.
- `~/.lingollm` is the data folder in your home directory (`%USERPROFILE%\.lingollm` on Windows); on a Docker
  server it is the `./data` folder next to `docker-compose.yml`.
- Most changes are saved as soon as you make them. The exception is the *Settings* tab, which has a **Save** button.

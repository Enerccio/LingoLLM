---
label: Troubleshooting & FAQ
order: 900
icon: question
---

# Troubleshooting & FAQ

## Where are the logs?

Most problems leave a trace in the log:

| Installation | Log |
|---|---|
| Desktop app | `~/.lingollm/desktop/logs/lingollm.log` (`%USERPROFILE%\.lingollm\desktop\logs` on Windows); the previous run is in `lingollm.log.1` |
| Docker | `docker compose logs -f lingollm` |

Error dialogs in the application (*Internal Server Error*) show the error as well; the log has the full details.

## Installing and starting

**macOS says the app "is damaged" or "can't be opened".**
The app isn't signed yet. Right-click it and choose *Open*, or run
`xattr -dr com.apple.quarantine /Applications/LingoLLM.app`. See [Desktop app](getting-started/desktop-app.md).

**Windows SmartScreen blocks the app.**
Click *More info* → *Run anyway*.

**The browser opens a different port than 8765.**
Port 8765 was taken, so the desktop app picked a free one. Use **Open LingoLLM** in the tray menu, or start it with
`--port` to fix the port.

**"LingoLLM did not start in time".**
The desktop app couldn't start its server. The log says why - often another program holding the files, or a broken
extension (see [below](#extensions)).

**Docker: port 8080 is already in use.**
Change the left side of the port mapping in `docker-compose.yml`, e.g. `"8081:8080"`, and open port 8081.

**LingoLLM is slow or stops with "OutOfMemoryError".**
Raise the memory: `-Xmx1g` (or more) in `JAVA_TOOL_OPTIONS` in `docker-compose.yml`.

## Logging in

**"Save login" doesn't keep me logged in.**
Saved logins need HTTPS on a server. Put LingoLLM behind a reverse proxy with HTTPS, see
[Server (Docker)](getting-started/docker-server.md#https-and-reverse-proxy).

**I forgot my password.**
An administrator can set a new one, or clear it so you can log in with an empty password and set a new one yourself,
in *Admin → Users* (see [Resetting a forgotten password](administration/users.md#resetting-a-forgotten-password)).
There is no password reset by e-mail.

**Nobody can log in as an administrator any more.**
Without an administrator, the password can only be reset in the database:

1. Stop LingoLLM and copy `lingollm.sqlite` from the [data folder](administration/index.md#the-data-folder) as a
   backup.
2. Remove the password of the administrator with the `sqlite3` tool:
   ```sh
   sqlite3 lingollm.sqlite "UPDATE users SET passwordHash = NULL WHERE login = 'admin';"
   ```
   (use the right user name).
3. Start LingoLLM, log in with that user name and an **empty** password, and immediately set a new password with
   **Change Password**.

**"Server connection lost" or the page keeps reloading.**
Behind a reverse proxy, the proxy must forward WebSockets and allow long requests - see the nginx example in
[Server (Docker)](getting-started/docker-server.md#https-and-reverse-proxy). With the desktop app, the app was probably
quit; start it again.

## Models

See also [Inference providers](inference-providers.md#error-messages) for the messages the provider's errors are turned into.

**"Failed to download model list" when adding a provider.**
Check that the URL ends with `/v1`, the API key, and that the server is running and reachable from LingoLLM (with Docker,
`localhost` is the container, see [Models on the same machine](getting-started/docker-server.md#models-on-the-same-machine)).
Some services don't list models: type the model ID.

**"Test Connection" fails.**
The message names the cause: a wrong key, a model the provider doesn't know, a timeout. Raise *Timeout (seconds)* for
slow reasoning models.

## Language profiles

**The "New language" window won't close.**
You have no language profile yet, and the window is the only way to create the first one. Pick a language and click *OK*.
See [Language profiles](language-profiles.md).

**The Learning tab opens another language than the one I used.**
LingoLLM opens the profile you left on at your last login. If that profile was deleted, it opens the first one.

## Backups

**I restored a database backup, but nothing changed.**
A restore is applied on the next start. Restart LingoLLM. See [Restoring](administration/database-backups.md#restoring).

**Uploaded files are missing after moving to another computer.**
Resources are files, not part of the database. Copy the `data` folder too, see
[The data folder](administration/index.md#the-data-folder).

## Extensions

**An installed extension doesn't show up.**
Reload the page.

**An extension fails to load and isn't listed in Admin → Extensions.**
Stop LingoLLM, delete its JAR from the `extensions` folder, start again. See
[When an extension fails](administration/extensions.md#when-an-extension-fails).

**An extension was refused or stopped working after LingoLLM was updated.**
Extensions are built for one version of LingoLLM and are checked when they are loaded. Open the report
(Admin → Extensions → *Verification*) and load the version of the extension that matches your LingoLLM. See
[When an extension fails](administration/extensions.md#when-an-extension-fails).

**After updating an extension, the old version still runs.**
Unload the old version first, then load the new one - or restart LingoLLM.

## FAQ

**Is my writing sent anywhere?**
Only to the inference providers you configure. With a model running on your own computer, nothing leaves it. LingoLLM
has no telemetry.

**Which model should I use?**
Any model that is good in the language you learn and has a large enough context; a context of 32 000 tokens or more is
comfortable.

**Can several people share a language profile?**
No. Every profile belongs to one user. An administrator can share an *inference provider* with everybody, see
[Shared providers](inference-providers.md#shared-providers).

**Can I use LingoLLM offline?**
Yes, with a model running locally (llama.cpp, LM Studio, Ollama...). LingoLLM itself needs no internet connection.

**Does it work on a phone?**
LingoLLM can be added to the home screen like an app (*Install app* / *Add to Home Screen* in the browser menu) and has
a [phone layout](phone.md) when the browser window is narrower than 768 pixels. It has no Admin page and not all
extensions, so set-up and administration are easier on a larger screen.

**Is there a light theme or another language?**
Not yet: the interface is in English, with a dark theme. Other languages of the interface can be added by an
[extension](extensions/index.md).

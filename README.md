# LingoLLM

LingoLLM is a self-hosted tool for learning languages with large language models. For every language you learn you
keep a *language profile* (a template such as Japanese or German, your own name for it and a note), and everything you
practise belongs to that profile.

It runs in your browser - either as a small server for you and your friends (Docker) or as a desktop app on your own
computer - and works with any OpenAI-compatible API: OpenAI, OpenRouter, LiteLLM, llama.cpp, LM Studio, Ollama, vLLM and
others.

The manual is at **[enerccio.github.io/LingoLLM](https://enerccio.github.io/LingoLLM)**.

> **Status: early development.** The platform is in place (accounts, models and limits, resources, trash and cleanup,
> extensions, interface languages) and so are the language profiles and the data model of free chats. There is no chat
> or lesson screen yet, see [TODO.md](TODO.md).

LingoLLM shares its platform with [Marginalia](https://github.com/Enerccio/Marginalia), a tool for writing long fiction
with LLMs, and grew out of the first prototype of the learning side (LLLLM).

## Features

- **Language profiles** - create several profiles, also of the same language; the selector at the top of the
  *Learning* tab switches between them and the last one is opened again at the next login. New things you create
  belong to the profile you work in.
- **Free chats** - the data model of conversations in the language you learn: a tree of messages with loose
  attachments (the first chat screen is planned).
- **Any OpenAI-compatible model** - including reasoning models, with per-model context and response limits; the
  administrator can share a model with all users and limit the tokens each user may use.
- **Multiple users** - each with their own profiles and models; administrators manage users, backups and cleanup.
- **Backups** - full database backups on a cron schedule, with restore.
- **Trash** - deleted objects can be restored until an administrator runs the cleanup.
- **Languages of the interface** - every user picks their own; English is built in, extensions can add more.
- **Extensions** - plugins add features at runtime (OSGi bundles, installed by an administrator).

## Installation

LingoLLM stores everything (database, backups, extensions) in a `.lingollm` folder in the home directory of the
user that runs it. On first start it asks you to create the administrator account.


### Desktop app (Windows, macOS, Linux)

A self-contained version for one computer: no Java or server to install, it listens only on your computer
(`http://127.0.0.1:8765`) and opens in your browser.

1. Download the archive for your system from the
   [releases page](https://github.com/Enerccio/LingoLLM/releases):
   `lingollm-<version>-<os>-<arch>-app` is the app, `lingollm-<version>-<os>-<arch>` a portable folder.
2. Extract it and start LingoLLM:
   - **macOS** - move `LingoLLM.app` to Applications and open it. The app is not signed yet: the first time,
     right-click it and choose *Open*, or run `xattr -dr com.apple.quarantine /Applications/LingoLLM.app`.
   - **Windows** - run `LingoLLM\LingoLLM.exe`. SmartScreen may warn about an unknown publisher: *More info* →
     *Run anyway*.
   - **Linux** - run `LingoLLM/bin/LingoLLM`.
   - **Portable folder** - run `bin/lingollm` (or `bin\lingollm.bat` on Windows).
3. LingoLLM appears as an **M** icon in the system tray (menu bar on macOS) with *Open* and *Quit*.

Data is kept in `~/.lingollm` (`%USERPROFILE%\.lingollm` on Windows), logs in `~/.lingollm/desktop/logs`. Starting
the app again while it runs just opens it in the browser. Options for the portable launcher:

```
lingollm --port 8765        # another port (a free one is picked automatically when 8765 is taken)
lingollm --home /path       # keep the .lingollm folder somewhere else
lingollm --no-browser --no-tray
```


### Server (Docker)

For several users or access from other devices.

```sh
git clone https://github.com/Enerccio/LingoLLM.git
cd LingoLLM
docker compose up -d
```

To use the prebuilt image (`ghcr.io/enerccio/lingollm`, amd64 and arm64) instead of building it, see the
[Docker guide](manual/user/getting-started/docker-server.md#prebuilt-image).

LingoLLM is then available on port 8080 and keeps its data in `./data`. Memory and other JVM options are set in
`docker-compose.yml` (`JAVA_TOOL_OPTIONS`).

LingoLLM uses login cookies, so **don't expose it to the internet over plain HTTP** - put it behind a reverse proxy
with HTTPS (Caddy, nginx, Traefik...) and forward to port 8080. Don't leave an account without a password on a server.

The image reports its health to Docker (`docker ps` shows `healthy` once LingoLLM is up, which takes a minute).

**Upgrading:** the database is migrated automatically when a newer version starts. A migration that fails is rolled
back and LingoLLM does not start, but one that went through stays, and an older version refuses the migrated database.
So before changing the structure of an existing database LingoLLM saves a copy in `db-backups` (the last three are
kept, listed in *Admin → Database Backups*). It's still wise to make your own database backup first, then
`git pull && docker compose up -d --build` (or `docker compose pull && docker compose up -d` with the prebuilt image).

### Build from source

Requirements: JDK 25 or newer and Maven 3.9+. Node.js for the frontend is downloaded by the build.

```sh
cd lingollm
mvn package               # deployable WAR (Vaadin production mode) in target/, runs the tests
mvn package -Pdesktop     # desktop app for the current OS in target/desktop/ (see the desktop profile in pom.xml)
```

The Docker image is built from source by `docker compose build`.

## Connecting a model

In *Inference Providers*, add the base URL of your OpenAI-compatible API (for example `https://api.openai.com/v1`,
`https://openrouter.ai/api/v1` or `http://localhost:8080/v1` for llama.cpp), an API key if it needs one, and pick the
model. *Test Connection* checks the connection and lists the models the provider offers.

## Extensions

Extensions are OSGi bundles (JAR files). Administrators install them in *Admin → Extensions*; they are stored in
`~/.lingollm/extensions`. The only plugin included is the Gemma 4 tools fix (`lingollm/plugins`); how to write one is described in the
[developer manual](manual/developer/plugins/index.md).

The extension API is not frozen in 1.0: extensions are built for one LingoLLM version, and LingoLLM refuses to start
one that does not fit the version it is running.

## License

MIT, see [LICENSE](LICENSE).

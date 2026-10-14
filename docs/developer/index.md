# Developer guide

This part of the manual is for people who want to change LingoLLM itself, write an extension for it, or just
understand how it works inside. If you only want to install and use LingoLLM, see the [User guide](../user/index.md).

LingoLLM is a single Java web application. The browser UI is written in Java with [Vaadin Flow](https://vaadin.com/flow),
the services are plain Spring beans wired in XML, the data lives in one SQLite file accessed through Hibernate, and
extensions are OSGi bundles that hook into the UI at runtime. The same WAR runs on a server (Docker, Jetty) and inside
the desktop app.

## Technology

| Area | Used |
|---|---|
| Language, build | Java 25, Maven 3.9 |
| User interface | Vaadin Flow 25 (server-side Java components, Lumo theme, server push over WebSocket) |
| Application framework | Spring 7 (XML configuration, no Spring Boot), AspectJ compile-time weaving for `@Configurable` and `@Transactional` |
| Persistence | Hibernate 7 (JPA), SQLite (`sqlite-jdbc`, WAL mode), Flyway migrations |
| LLM access | [openai-java](https://github.com/openai/openai-java) against any OpenAI-compatible API |
| Extensions | Apache Felix (OSGi), ByteBuddy agent for bytecode instrumentation of `@Extendable` classes |
| Server | Any Jakarta EE 11 servlet container (Servlet 6.1) - Jetty 12 in Docker and in the desktop app |
| Tests | JUnit 6, AssertJ, Spring Test, a fake OpenAI-compatible server |
| Manual | [Retype](https://retype.com) (sources in `manual/`, generated site in `docs/`) |

## Repository at a glance

```
LingoLLM/
├── lingollm/            the application (Maven project, WAR packaging)
│   ├── src/main/java      application code (com.github.enerccio.lingollm)
│   ├── src/main/resources Spring XML, Flyway migrations, logging
│   ├── src/main/webapp    configuration.properties, persistence.xml
│   ├── src/main/frontend  CSS and JS used by the Vaadin frontend
│   ├── src/desktop        desktop launcher (tray icon, starts Jetty) and launch scripts
│   ├── src/test           tests
├── manual/                this manual (Retype sources)
├── docs/                  generated manual, served by GitHub Pages
├── Dockerfile, docker-compose.yml
└── .github/workflows/     CI (tests, desktop builds, server image, releases)
```

[Project structure](project-structure.md) describes the packages and files in detail.

## Getting started

1. **[Building from source](building.md)** - requirements, building the WAR, running LingoLLM from your IDE or a
   local Jetty, running the tests, building the plugins.
2. **[Architecture](architecture.md)** - how the pieces fit together: startup, Spring wiring, the UI, services and
   data, extensions.
3. Then read the page about the area you want to change.

The shortest path from a fresh clone to a running instance:

```sh
git clone https://github.com/Enerccio/LingoLLM.git
cd LingoLLM/lingollm
mvn package -DskipTests                     # target/lingollm-1.0.0.war
```

Deploy `target/lingollm-1.0.0.war` (or the exploded `target/lingollm-1.0.0/` folder) to Tomcat 11 or Jetty 12,
or use `docker compose up --build` from the repository root. [Building from source](building.md#running-locally) has
the details.

## Pages in this guide

| Page | What it covers |
|---|---|
| [Building from source](building.md) | Requirements, Maven builds, running locally, tests, plugins, the manual. |
| [Architecture](architecture.md) | The big picture: layers, startup, wiring, threading, extension mechanism. |
| [Project structure](project-structure.md) | Packages, resources and what lives where. |
| [Domain model](domain-model.md) | Entities (language profiles, chats, providers...), ownership, soft delete. |
| [Database & migrations](database.md) | SQLite, Flyway migrations, adding an entity or a column. |
| [Services](services.md) | Service and repository layer, transactions, the current user. |
| [User interface](ui.md) | Routes, workspace, dialogs, push and background threads. |
| [Plugin development](plugins/index.md) | Writing extensions: bundles, `@Extendable` hooks, extended attributes, UI extensions. |
| [Testing](testing.md) | Running tests, test bases, the fake LLM server, CRUD contracts, writing tests. |
| [Packaging & releases](packaging.md) | WAR, Docker image, desktop app, CI, making a release. |
| [Contributing](contributing.md) | Reporting bugs, workflow, commits, pull requests, code style, documentation. |

!!!
The code is the final reference - when a page and the code disagree, the code wins, and an issue or pull request
fixing the page is welcome (see [Contributing](contributing.md)).
!!!

## Conventions used in this guide

- Paths like `ui/workspace/Workspace.java` are relative to `lingollm/src/main/java/com/github/enerccio/lingollm/`
  unless they start with `lingollm/` or another top-level folder.
- *Inference provider* is the user-facing name of `AI`; *language profile* is `Language` and *language* (a template)
  is `Languages`. The language of the interface is `loc.Language`.
- Commands are run from the `lingollm/` folder unless said otherwise.
- LingoLLM shares its platform with Marginalia, and some pages were written for it first; the manuscript, lorebook and
  generation parts of Marginalia are not in LingoLLM.

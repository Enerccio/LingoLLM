# LingoLLM

LingoLLM is a self-hosted tool for learning languages with large language models. For every language you learn you
keep a **language profile**, and everything you practise belongs to it.

It runs in your browser - as a desktop app on your own computer, or as a small server for you and your friends - and
works with any OpenAI-compatible API: OpenAI, OpenRouter, LiteLLM, llama.cpp, LM Studio, Ollama, vLLM, and others.

[!button variant="primary" text="Get started"](user/getting-started/index.md)
[!button text="Download"](https://github.com/Enerccio/LingoLLM/releases)
[!button text="Source on GitHub"](https://github.com/Enerccio/LingoLLM)

!!!warning Early development
The platform and the language profiles are in place, the chat and lesson screens are not. See the
[TODO](https://github.com/Enerccio/LingoLLM/blob/master/TODO.md) for what is planned.
!!!

## What it does

- **Language profiles** - several profiles, also of the same language, switched from the *Learning* tab. See
  [Language profiles](user/language-profiles.md).
- **Any OpenAI-compatible model** - including reasoning models, with per-model limits. A shared model can be limited
  per user. See [Inference providers](user/inference-providers.md).
- **Multiple users** - each with their own profiles and models; administrators manage users, backups, and cleanup.
  See [Administration](user/administration/index.md).
- **Backups and trash** - scheduled database backups, and a trash from which deleted objects can be restored. See
  [Database backups](user/administration/database-backups.md) and [Trash](user/trash.md).
- **Languages of the interface** - every user picks their own. See [Account & settings](user/account.md).
- **Extensions** - plugins add features at runtime. See [Extensions](user/extensions/index.md).

## Which guide do you need?

|                                           |                                                                                                                                   |
|-------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------|
| **[User guide](user/index.md)**           | You want to install LingoLLM, run it for others, or learn with it. Start with [Getting started](user/getting-started/index.md). |
| **[Developer guide](developer/index.md)** | You want to build LingoLLM from source, change it, write an extension, or understand how it works inside.                         |

## Quick links

| I want to...                   |                                                          |
|--------------------------------|----------------------------------------------------------|
| Install it on my computer      | [Desktop app](user/getting-started/desktop-app.md)       |
| Run it on a server             | [Server (Docker)](user/getting-started/docker-server.md) |
| Connect a model                | [Inference providers](user/inference-providers.md)       |
| Create a language profile      | [Quick start](user/getting-started/quick-start.md)       |
| Fix a problem                  | [Troubleshooting & FAQ](user/troubleshooting.md)         |
| Write an extension             | [Plugin development](developer/plugins/index.md)         |
| Report a bug or contribute     | [Contributing](developer/contributing.md)                |

## About

LingoLLM is open source under the [MIT License](https://github.com/Enerccio/LingoLLM/blob/master/LICENSE).
It shares its platform with [Marginalia](https://github.com/Enerccio/Marginalia). Releases are on
[GitHub](https://github.com/Enerccio/LingoLLM/releases); bugs and ideas go to the
[issue tracker](https://github.com/Enerccio/LingoLLM/issues).

# Getting started

LingoLLM runs in your browser. You can install it in two ways:

| | [Desktop app](desktop-app.md) | [Server (Docker)](docker-server.md) |
|---|---|---|
| Best for | One person on one computer | Several users, or access from other devices |
| Installation | Download and run, nothing else to install | Docker and Docker Compose |
| Address | `http://127.0.0.1:8765` (your computer only) | Port `8080`, normally behind a reverse proxy with HTTPS |
| Data | `~/.lingollm` | `./data` next to `docker-compose.yml` |

Both versions are the same application with the same features: language profiles, users, backups and extensions.
To write from a phone, run the server version and open it in the phone's browser: see
[Using LingoLLM on a phone](../phone.md).

LingoLLM has no model of its own. It needs an **OpenAI-compatible API** to write text: a cloud service (OpenAI,
OpenRouter...) or a model running on your own computer (llama.cpp, LM Studio, Ollama, vLLM...). Have the API address
and an API key (if the service needs one) ready.

## Steps

1. Install LingoLLM: [desktop app](desktop-app.md) or [server](docker-server.md).
2. [Create the administrator account](first-start.md) on the first start.
3. Follow the [quick start](quick-start.md): connect a model and create your first language profile.

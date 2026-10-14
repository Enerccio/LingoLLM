# Server (Docker)

Run LingoLLM as a server when several people use it, or when you want to open it from other devices. Every user
gets their own language profiles and inference providers.

## Requirements

- [Docker](https://docs.docker.com/get-docker/) with Docker Compose
- About 512 MB of memory for LingoLLM
- For access over the internet: a domain and a reverse proxy with HTTPS (see [below](#https-and-reverse-proxy))

## Install

```sh
git clone https://github.com/Enerccio/LingoLLM.git
cd LingoLLM
docker compose up -d
```

The first `docker compose up` builds the image from source, which takes a few minutes. LingoLLM then listens on port
**8080**. Open `http://<server>:8080` and create the administrator account (see [First start](first-start.md)).

### Prebuilt image

Instead of building from source you can use the image published with each release (`linux/amd64` and `linux/arm64`).
In `docker-compose.yml`, replace the `build:` block with `image:`:

```yaml
services:
  lingollm:
    image: ghcr.io/enerccio/lingollm:latest
```

`latest` is the newest release, `edge` the development version, and `1.1.0` (or `1.1`) pins a release. The rest of
the file stays the same.

## Configuration

Everything is set in `docker-compose.yml`:

```yaml
services:
  lingollm:
    build:
      context: .
      dockerfile: Dockerfile
    container_name: lingollm-app
    restart: unless-stopped
    ports:
      - "8080:8080"
    volumes:
      - ./data:/var/lingollm/.lingollm
    environment:
      - JAVA_TOOL_OPTIONS=-Duser.home=/var/lingollm -Xms256m -Xmx512m -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/var/lingollm/.lingollm/heapdump.hprof
```

| Setting | What it does |
|---|---|
| `ports` | `"8080:8080"` publishes LingoLLM on port 8080 of the host. Use `"127.0.0.1:8080:8080"` when only a reverse proxy on the same host should reach it. |
| `volumes` | `./data` holds the database, backups and extensions. Keep it, and include it in your server backups. It is created on the first start if it doesn't exist; on start the container makes it owned by the user LingoLLM runs as inside the container (`jetty`), so the files in it belong to that user's ID on the host. |
| `-Xmx512m` | Maximum memory. Raise it (for example `-Xmx1g`) for many users or many large chats. |

After changing the file, apply it with `docker compose up -d`.

## HTTPS and reverse proxy

!!!danger Don't use plain HTTP over the internet
Passwords and login cookies are sent with every request. Over plain HTTP anyone on the way can read them.
Put LingoLLM behind a reverse proxy with HTTPS.
!!!

The *Save login* option on the login screen also needs HTTPS: its cookies are marked secure, so browsers keep them
only on HTTPS connections.

LingoLLM updates the page over a WebSocket connection, so the proxy must forward WebSocket upgrades. Examples
for `lingollm.example.com`:

+++ Caddy
Caddy gets the certificate and forwards WebSockets automatically.

```
lingollm.example.com {
    reverse_proxy localhost:8080
}
```
+++ nginx
```nginx
server {
    listen 443 ssl;
    server_name lingollm.example.com;

    ssl_certificate     /etc/letsencrypt/live/lingollm.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/lingollm.example.com/privkey.pem;

    client_max_body_size 100m;   # uploads of backups and extensions

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 1h;   # long requests keep the connection open
    }
}
```
+++

Failed logins are throttled per user and per client address (after 30 failures from one address, logins from it are
refused for a growing time, up to 15 minutes). LingoLLM only believes the `X-Forwarded-For` header of a proxy you
name: without it, all users appear to come from the proxy's address and 30 failures from anyone block everybody for a
while. Keep the header in the proxy configuration (as above) and add `-DtrustedProxies=<address or range of the proxy>`
to `JAVA_TOOL_OPTIONS` in `docker-compose.yml`. A proxy on the Docker host reaches the container from the Docker
network, normally `172.16.0.0/12`; several entries are separated by commas. The throttling is kept in memory,
restarting LingoLLM clears it.

## Models on the same machine

Inside the container, `localhost` is the container itself, not the server. To use a model server that runs on the
Docker host (llama.cpp, Ollama, LM Studio...), use the address `http://host.docker.internal:<port>/v1` in the
[inference provider](../inference-providers.md). On Linux, add this to the service in `docker-compose.yml`:

```yaml
    extra_hosts:
      - "host.docker.internal:host-gateway"
```

The model server must also listen on an address the container can reach, not only on `127.0.0.1`.

## Updating

```sh
git pull
docker compose up -d --build
```

With the [prebuilt image](#prebuilt-image) use `docker compose pull` and `docker compose up -d` instead.

The database is upgraded automatically when the new version starts. LingoLLM saves a
[copy of the database](../administration/database-backups.md#copy-before-an-upgrade) before it changes its structure,
but make a [database backup](../administration/database-backups.md) of your own first as well.

### Automatic updates

`rebuild.sh` (Linux, macOS) and `rebuild.bat` (Windows) in the repository root do the update for you; run them from
the root, for example from a cron job or a scheduled task. They fetch the remote branch and do nothing when there is
nothing new (`--force` rebuilds and restarts anyway, for example after changing `docker-compose.yml`). Otherwise they
stop the container, `git pull`, rebuild the [bundled plugins](#plugins) and copy their JARs into `data/extensions`
(replacing the installed ones), and start the container again with `docker compose up -d --build`. If any step fails,
the script stops with a message.

## Plugins

The Docker image doesn't contain the [extensions](../extensions/index.md), and they have to be built from the same
source as the application. You don't need Java or Maven for that: `lingollm/plugins/docker-compose.yml` builds all
of them in a container, which builds the application first and then every plugin, and copies the JARs into
`lingollm/plugins/build`:

```sh
docker compose -f lingollm/plugins/docker-compose.yml build
docker compose -f lingollm/plugins/docker-compose.yml run --rm plugins
```

The first build takes several minutes (it downloads the dependencies of the application). Then copy the JARs into
`data/extensions` and restart LingoLLM, or load them in *Admin → Extensions* (see
[Extensions](../administration/extensions.md)). `rebuild.sh` / `rebuild.bat` do the copying for you, so on a server
that uses them the plugins are always updated together with the application.

## Health

The image has a Docker health check: `docker ps` shows `(health: starting)` while LingoLLM starts (up to two
minutes), then `(healthy)`. It is `unhealthy` when port 8080 stops answering with a page. Docker does not restart an
unhealthy container by itself; use the status for your monitoring or an auto-heal tool.

## Logs

```sh
docker compose logs -f lingollm
```

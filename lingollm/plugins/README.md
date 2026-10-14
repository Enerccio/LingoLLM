# LingoLLM plugins

Optional extensions for LingoLLM. Each plugin is an OSGi bundle (a JAR) that LingoLLM loads at runtime, without a
restart.

| Plugin | What it adds |
|---|---|
| [Gemma 4 tools fix](gemma4toolsfix/README.md) | Sends the results of function calls to Gemma 4 models with the role `tool_responses`. |

## Installing

1. Get the plugin JAR (`<plugin>/target/<plugin>-<version>.jar`, see *Building*).
2. In LingoLLM, as an administrator, open *Admin → Extensions* and use *Load Extension (.jar)*.

The JAR is stored in `~/.lingollm/extensions` and loaded on every start. Plugins are built for one LingoLLM version:
use plugins built from the same source tree as the application.

## Building

Plugins compile against the application, which has to be installed in the local Maven repository first:

```sh
mvn -N install                   # in the repository root: installs the parent POM (holds the version)

cd lingollm
mvn install -DskipTests          # installs the application (and its classes JAR used by plugins)

cd plugins/gemma4toolsfix
mvn package                      # target/gemma4toolsfix-1.0.0.jar
```

`mvn package` in the repository root builds the application and all plugins in one go.

## Writing a plugin

See the [developer manual](../../manual/developer/plugins/index.md). [Gemma 4 tools fix](gemma4toolsfix/README.md) is
the smallest example.

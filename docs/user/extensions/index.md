# Extensions

Extensions add features to LingoLLM without changing the application itself. Extensions that come with LingoLLM are
listed below; how to write one is described in the [developer manual](../../developer/plugins/index.md).

| Extension | What it does |
|---|---|
| [Gemma 4 tools fix](gemma-4-tools-fix.md) | Function calls work with Gemma 4 models whose API expects the role `tool_responses`. |

## Installing

Extensions are installed by an **administrator** for the whole LingoLLM installation, in *Admin → Extensions*:
they are JAR files, uploaded with **Load Extension (.jar)**. See [Managing extensions](../administration/extensions.md)
for the details.

Once installed, an extension is available to every user. An extension loaded while LingoLLM runs shows up in
windows opened afterwards: reload the page to see it.

!!!warning Use matching versions
Extensions hook into the user interface of one LingoLLM version. An extension built for a different version may stop
working or break parts of the application. Use the extensions that come with your version of LingoLLM.
!!!

## Extensions on a phone

The [phone layout](../phone.md) has its own classes, so an extension has to support it. Extensions are installed from
the Admin page, which a phone doesn't have.

## Extension settings

Extensions with settings add a section to **Settings → Extension Settings** (the second tab of the *Settings* page).
These settings belong to your account, like the rest of *Settings*, and are saved with its **Save** button.

## Where extension data is kept

Extensions keep their data together with the data it belongs to, so it is included in
[database backups](../administration/database-backups.md).

Uninstalling an extension doesn't delete its data: installing it again brings it back.

## Languages of the interface

An extension can add a language of the interface (see [Account & settings](../account.md#language)).

# Example plugin

This page builds a small plugin from scratch - *Language Notes*, which adds a notes field to every language profile -
and explains each piece. The code compiles against the classes of LingoLLM 1.0.0 (it was compiled while this page was
written, but it is an example: bundle it and try it before relying on it).

## What it does

- Below the screen of the selected language, the **Learning** tab shows a text area *Notes*.
- The text is stored in the `attributes` of the language profile, so it is part of the database and its backups, and
  nothing in the database schema changes.

Three things are used: a **decorator** on an `@Extendable` method of the application (`LanguagePart.showLanguage`), an
**attribute** of an entity, and a **service** injected into the plugin class with `@Configurable`.

## Project

```
languagenotes/
├── pom.xml
└── src/main/java/com/example/lingollm/languagenotes/
    ├── LanguageNotesActivator.java
    ├── LanguageNotesExtension.java
    └── NotesService.java
```

The `pom.xml` is the one described in [Plugin basics](overview.md#pomxml): `bundle` packaging, the classes of the
application as a `provided` dependency, AspectJ for `@Configurable`, and the bundle plugin with

- `Bundle-Activator` `com.example.lingollm.languagenotes.LanguageNotesActivator`,
- `Import-Package` `!com.example.lingollm.languagenotes.*` and `Export-Package` `com.example.lingollm.languagenotes.*`.

## The activator

Registers the extension as an OSGi service - nothing else:

```java
import com.github.enerccio.lingollm.extensions.LingoLLMExtension;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

public class LanguageNotesActivator implements BundleActivator {

    private ServiceRegistration<LingoLLMExtension> registration;

    @Override
    public void start(BundleContext context) {
        registration = context.registerService(LingoLLMExtension.class, new LanguageNotesExtension(), null);
    }

    @Override
    public void stop(BundleContext context) {
        if (registration != null) {
            registration.unregister();
            registration = null;
        }
    }
}
```

## The service

A plain class that is not a Spring bean, but is annotated `@Configurable`: because the plugin is compiled with the
AspectJ compiler, Spring injects the `@Autowired` field from the application's context when the object is created with
`new`. The notes are one value under a key of the profile's `attributes`:

```java
import com.github.enerccio.lingollm.domain.model.impl.Language;
import com.github.enerccio.lingollm.domain.service.LanguageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;

/**
 * Keeps the notes in the attributes of the language profile. @Configurable: the field is injected by Spring.
 */
@Configurable
public class NotesService {

    static final String KEY = "com.example.lingollm.languagenotes";

    @Autowired
    private LanguageService languageService;

    public String load(Language language) throws Exception {
        // the profile held by the UI may be stale: read the stored one
        Language stored = languageService.find(language.getId());
        if (stored == null || !stored.getAttributes().has(KEY)) {
            return "";
        }
        return stored.getAttributes().get(KEY).getAsString();
    }

    public void save(Language language, String notes) throws Exception {
        Language stored = languageService.find(language.getId());
        stored.getAttributes().addProperty(KEY, notes);
        languageService.save(stored);   // attributes are persisted only by the service's save
    }
}
```

Two rules from [Extended attributes](extended-attributes.md) are in the code: the key is namespaced with the plugin
(`com.example.lingollm.languagenotes`), and the profile is read again before it is changed - the object the UI holds
may be stale, and the attributes are written only by `save` of the service.

## The extension

```java
import com.github.enerccio.lingollm.domain.model.impl.Language;
import com.github.enerccio.lingollm.domain.service.ExtensionService;
import com.github.enerccio.lingollm.domain.service.ExtensionService.ExtendableMethodContext;
import com.github.enerccio.lingollm.domain.service.ExtensionService.ExtensionDecorator;
import com.github.enerccio.lingollm.domain.service.OsgiService;
import com.github.enerccio.lingollm.extensions.LingoLLMExtension;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.textfield.TextArea;
import org.osgi.framework.Bundle;
import org.slf4j.Logger;

public class LanguageNotesExtension implements LingoLLMExtension {

    private static final String LANGUAGE_PART = "com.github.enerccio.lingollm.ui.workspace.parts.LanguagePart";
    private static final String MARKER = "com.example.lingollm.languagenotes.notes";

    private Logger log;
    private ExtensionDecorator decorator;
    private final NotesService notesService = new NotesService();

    @Override
    public void onExtensionLoad(Bundle bundle, OsgiService parentService, ExtensionService extensionService) {
        log = parentService.createLogger(bundle);

        decorator = new ExtensionDecorator() {
            @Override
            public void onMethodEnter(Object instrumented, ExtendableMethodContext context) {
            }

            @Override
            public void onMethodLeave(Object instrumented, ExtendableMethodContext context, Throwable throwing) throws Exception {
                if (throwing != null) {
                    return;
                }
                Language language = context.getMethodArgument("language", Language.class);
                Div content = context.getReflectiveFieldValue(instrumented, "content", Div.class);
                if (language == null || content == null || ComponentUtil.getData(content, MARKER) != null) {
                    return;     // no profile, or our notes are already there
                }

                TextArea notes = new TextArea("Notes");
                notes.setWidthFull();
                notes.setValue(notesService.load(language));
                notes.addValueChangeListener(e -> {
                    try {
                        notesService.save(language, e.getValue());
                    } catch (Exception ex) {
                        log.error("Cannot save the notes of {}", language.getId(), ex);
                    }
                });
                content.add(notes);
                ComponentUtil.setData(content, MARKER, notes);
            }
        };

        try {
            extensionService.registerDecorator(decorator, LANGUAGE_PART, "showLanguage");
        } catch (Exception e) {
            throw new IllegalStateException("Language Notes failed to load", e);
        }
    }

    @Override
    public void onExtensionUnload(Bundle bundle, OsgiService parentService, ExtensionService extensionService) {
        extensionService.unregisterDecorator(decorator);
        // the text areas of the open pages stay until the page is reloaded
    }
}
```

- **The hook.** `LanguagePart.showLanguage(language)` builds the screen of a language into the `content` field every
  time a profile is selected, after emptying it. A decorator registered with `registerDecorator(decorator,
  className, "showLanguage")` runs `onMethodLeave` after the method; the profile is its argument `language`, the
  container is the field `content` ([@Extendable hooks](extendable.md) explains the context).
- **Idempotence.** The method can run many times; `ComponentUtil.setData(content, MARKER, notes)` marks the container so
  the field is not added twice if the method ran again without emptying it.
- **Settings and state** are read when needed, not captured: the listener saves to the profile it was created for
  (`language`), and a new profile gets a new text area.
- **The logger** comes from `parentService.createLogger(bundle)`, not from `LoggerFactory`, so the lines reach the
  extension log shown in *Admin → Extensions → Log* ([Plugin basics](overview.md#logging)).
- **Unloading.** `onExtensionUnload` removes the decorator. The text areas that are already in open pages stay until the
  page is reloaded; a plugin that must remove its components uses `OsgiService.bindAttachableComponent`, see
  [Extending the UI](ui-extensions.md#unloading).
- **Phone.** `LanguagePart` is the same class in the desktop and the phone layout, so the plugin works in both.

## Build and load

```sh
mvn -N install                    # once, in the repository root
cd lingollm && mvn install -DskipTests -Dvaadin.skip=true
cd /path/to/languagenotes && mvn package
```

Then load `target/languagenotes-1.0.0.jar` in *Admin → Extensions* of a running LingoLLM (or copy it to
`~/.lingollm/extensions` before the start). LingoLLM [verifies](extendable.md#verification-before-loading) the names the
decorator uses (`LanguagePart`, `showLanguage`, `language`, `content`) before it starts the extension; a typo is
reported there instead of silently doing nothing.

## What to try next

- Add the same field to the phone-only screens, or to the *Settings* tab: decorate `UserPart.refresh` and `save`
  ([Extending the UI](ui-extensions.md#settings-panels)).
- Add a language: implement `ExtensionHasLocalization` and return a `LocalizationBundle`
  ([Plugin basics](overview.md#extension-events)).
- Add a tab to the workspace by decorating `DesktopWorkspace.create` (and `PhoneWorkspace.create`).

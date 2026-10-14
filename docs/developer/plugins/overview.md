# Plugin basics

This page covers what every plugin needs: the Maven project, the bundle activator, the `LingoLLMExtension` and how
LingoLLM loads, starts and unloads it.

## Project layout

A plugin is an ordinary Maven project with `bundle` packaging:

```
myplugin/
├── pom.xml
└── src/main/java/com/example/lingollm/myplugin/
    ├── MyPluginActivator.java     BundleActivator - registers the extension
    ├── MyPluginExtension.java     LingoLLMExtension - registers hooks, cleans up
    ├── model/                     data classes stored as JSON in attributes (optional)
    ├── service/                   plugin logic, @Configurable (optional)
    └── ui/                        Vaadin components (optional)
```

Use your own package; `com.github.enerccio.lingollm.extensions` is the application's (the `LingoLLMExtension` interface).

## pom.xml

The pom of a plugin follows the sketch below (`lingollm/plugins/gemma4toolsfix/pom.xml` is a complete one;
Marginalia's plugins have the same shape). A plugin inside the repository would inherit its version from the parent POM
(`<parent>` with `lingollm-parent`) and be a module of it; a plugin of your own outside the repository has no
`<parent>` and gives its `<version>` itself. The important parts:

```xml
<packaging>bundle</packaging>

<dependencies>
    <!-- the application's classes, installed by `mvn install` in lingollm/ -->
    <dependency>
        <groupId>io.github.enerccio</groupId>
        <artifactId>lingollm</artifactId>
        <version>1.0.0</version>
        <classifier>classes</classifier>
        <scope>provided</scope>
    </dependency>
    <!-- plus, all `provided`: org.apache.felix.framework, vaadin-core, and any library
         of the application you use directly (gson, commons-lang3, font-awesome-iron-iconset...) -->
</dependencies>

<build>
    <plugins>
        <!-- maven-compiler-plugin, release 25 -->
        <!-- aspectj-maven-plugin with spring-aspects as aspect library - needed for @Configurable -->
        <plugin>
            <groupId>org.apache.felix</groupId>
            <artifactId>maven-bundle-plugin</artifactId>
            <version>6.2.0</version>
            <extensions>true</extensions>
            <configuration>
                <instructions>
                    <Bundle-SymbolicName>${project.artifactId}</Bundle-SymbolicName>
                    <Bundle-Version>${project.version}</Bundle-Version>
                    <Bundle-Activator>com.example.lingollm.myplugin.MyPluginActivator</Bundle-Activator>
                    <Import-Package>!com.example.lingollm.myplugin.*</Import-Package>
                    <Export-Package>com.example.lingollm.myplugin.*</Export-Package>
                </instructions>
            </configuration>
        </plugin>
    </plugins>
</build>
```

- **Everything from the application is `provided`.** LingoLLM runs the bundle with boot delegation for all packages
  (see [Class loading](#class-loading)), so the bundle sees exactly the classes and libraries of the running WAR.
  Bundling your own copy of Vaadin, Spring or Gson would create a second, incompatible copy of their classes.
- **Versions must match the application.** Take the Vaadin, Gson and commons versions from `lingollm/pom.xml`.
  Compiling against a different Vaadin version than the one that runs usually works until it doesn't.
- **`Import-Package` imports nothing.** The single negated entry stops bnd from generating `Import-Package` headers -
  they are not needed with boot delegation and would make the bundle fail to resolve.
- **AspectJ** weaves Spring's `AnnotationBeanConfigurerAspect` into the plugin's classes, so a plugin class annotated
  `@Configurable` gets its `@Autowired` fields injected from the application's Spring context, like the
  application's own UI classes ([AspectJ weaving](../building.md#aspectj-weaving)). Without it, those fields stay
  `null`.
- Libraries the application doesn't have must be embedded in the bundle (`Embed-Dependency`) or shaded. Keep this to a minimum.

Build with `mvn package`; the result is `target/<artifactId>-<version>.jar`. The application must be installed in the
local Maven repository first (`mvn install -DskipTests` in `lingollm/`), and reinstalled whenever you change
classes the plugin uses.

## The activator

The bundle's activator registers one `LingoLLMExtension` as an OSGi service. That's all it should do - LingoLLM
discovers the service and calls it:

```java
public class MyPluginActivator implements BundleActivator {

    private ServiceRegistration<LingoLLMExtension> registration;

    @Override
    public void start(BundleContext context) {
        registration = context.registerService(LingoLLMExtension.class, new MyPluginExtension(), null);
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

Only bundles that register a `LingoLLMExtension` are listed in *Admin → Extensions*.

## The extension

```java
public interface LingoLLMExtension {

    void onExtensionLoad(Bundle bundle, OsgiService parentService, ExtensionService extensionService);

    void onExtensionUnload(Bundle b, OsgiService osgiService, ExtensionService extensionService);
}
```

- `onExtensionLoad` registers everything the plugin needs: decorators (`extensionService.registerDecorator(...)`,
  see [@Extendable hooks](extendable.md)), cleanup contributors ([Extended attributes](extended-attributes.md#references-to-other-entities)). It should not build UI - there is no UI at that moment. UI is added later, from decorators, when the
  user opens the screen the plugin extends.
- `onExtensionUnload` undoes all of it: unregister every decorator and listener, remove the components the plugin
  added to open screens ([Unloading](ui-extensions.md#unloading)), stop background work. Nothing is removed
  automatically - a decorator left registered keeps running, from a bundle that no longer exists.

Annotate the extension `@Configurable` and inject what you need:

```java
@Configurable
public class MyPluginExtension implements LingoLLMExtension {

    @Autowired
    private Localization loc;

    @Autowired
    private LanguageService languageService;
    ...
}
```

Any bean of the application can be injected - every service in `domain/service` ([Services](../services.md)),
`Localization`, `InferenceServices`, `Configuration`, `OsgiService`... Plugin classes created with `new` (services,
dialogs, forms) can be `@Configurable` too; the [example plugin](example.md) creates its service object in the
extension (`new NotesService()`) and it is injected the same way.

!!!warning Errors in onExtensionLoad
At startup `onExtensionLoad` runs without a UI, so `UIUtils.internalServerError(...)` (which opens a dialog) can't
show anything. Throw instead: an exception that escapes `onExtensionLoad` is logged, `onExtensionUnload` is called to
undo what was registered so far and the bundle is stopped. The other extensions load normally; an upload shows the
error to the administrator.
!!!

## Lifecycle

```mermaid
sequenceDiagram
    participant S as Spring (root context)
    participant O as OsgiServiceImpl
    participant F as Felix
    participant A as Activator
    participant E as LingoLLMExtension

    S->>O: ContextRefreshedEvent
    O->>F: start framework (storage extensions/org.eclipse.osgi, cleaned)
    O->>F: installBundle(file:...) for every *.jar in extensions/
    loop every bundle
        O->>O: verify (reuse name.valid / name.invalid or run ExtensionService.verifyExtension)
        O->>F: bundle.start() - only when valid
        F->>A: start(context)
        A->>F: registerService(LingoLLMExtension)
        O->>E: onExtensionLoad(bundle, osgiService, extensionService)
    end
    Note over O,E: Admin → Extensions → Unload
    O->>E: run bound component callbacks, then onExtensionUnload(...)
    O->>F: bundle.uninstall() (stops it → Activator.stop), delete the JAR
```

- **Startup.** `OsgiServiceImpl` listens for the root context's `ContextRefreshedEvent`, starts Felix with its
  storage in `~/.lingollm/extensions/org.eclipse.osgi` (wiped on every start, so the JARs are always read fresh),
  installs every `*.jar` in `~/.lingollm/extensions` and starts them. For each `LingoLLMExtension` service a
  bundle registered, it calls `onExtensionLoad`. Each JAR is installed and started on its own: one that can't be
  installed (not a bundle, a second copy of an installed plugin) or fails to start is logged with its file name and
  skipped. When the folder holds several versions of one extension (same symbolic name, e.g. an update copied next to
  the old JAR), only the newest is started; a warning names the skipped JARs, which are moved to `extensions/old-versions` (not deleted). Before a bundle
  is started it is **verified**: the decorators it registers are checked against this version
  of the application ([Verification before loading](extendable.md#verification-before-loading)). An invalid extension
  stays installed but is not started, and its report is logged. A bundle whose JAR has a `<jar name>.STOPPED` flag
  next to it (see **Stopping**) is installed but not started either.
- **Installing at runtime.** *Admin → Extensions → Load Extension (.jar)* writes the uploaded file into
  `~/.lingollm/extensions` (the file name is sanitized: only letters, digits, `.`, `_` and `-`) and installs and
  starts it the same way, verification included (`OsgiService.installPackage`, like `uninstallPackage` administrators only: `AdminGuard.requireAdmin()`; an invalid upload throws `ExtensionVerificationException` with the report). Screens that are already open are not rebuilt: the plugin's
  decorators run the next time the decorated methods run (the next time the user selects a language profile, opens a tab...).
- **Updating.** Uploading a bundle whose symbolic name (or file name) is already installed replaces it: the old one is
  unloaded as by *Unload*, uninstalled and its JAR deleted, then the new one is loaded. If the new one fails to
  install, verify or start, its JAR is deleted and the old one is put back.
- **Unloading.** *Unload* calls the callbacks registered with `bindAttachableComponent`, then `onExtensionUnload`,
  uninstalls the bundle and **deletes its JAR and its verification reports** (`OsgiService.uninstallPackage`). Bundles that failed to start are
  listed too (state *Installed* or *Resolved*) and can be unloaded the same way.
- **Stopping.** *Stop* (`OsgiService.stopExtension`, administrators only) creates the `<jar name>.STOPPED` flag
  file, unloads the extension like *Unload* does (bound callbacks, `onExtensionUnload`) and stops the bundle; the
  bundle stays installed and is listed with the state *Stopped* (`isStopped`). *Start* (`startExtension`) verifies it,
  starts it and deletes the flag; when that fails, the flag stays. The flag survives restarts. Uploading a
  replacement of a stopped extension deletes the flag, and *Unload* deletes the flag too.
- **Shutdown.** When the application stops (the root context closes), every extension is unloaded the same way -
  bound callbacks, `onExtensionUnload`, the activator's `stop` - and the framework is stopped. Still save data as you
  go: a killed process runs none of this.

### Logging

An extension should not use `LoggerFactory` (its lines are mixed with everything else and the administrator cannot
see them next to the extension). `OsgiService.createLogger(bundle)` returns an `org.slf4j.Logger` that writes to its own file,
`<jar name>.log` in the extensions folder (`myplugin.jar.log`), which administrators read in *Admin → Extensions →
Log*:

```java
private Logger log;

@Override
public void onExtensionLoad(Bundle bundle, OsgiService parentService, ExtensionService extensionService) {
    log = parentService.createLogger(bundle);           // or createLogger(bundle, "my.name")
    log.info("Loaded {} {}", bundle.getSymbolicName(), bundle.getVersion());
}
```

- The usual SLF4J API: `{}` placeholders and a trailing throwable (logged with its stack trace). TRACE is dropped,
  DEBUG and above are written. Each line has the time, level, thread, logger name (the symbolic name by default) and
  the message. The line is also chained to the application log (logger `extension.<name>`, same level, so the
  level settings of the application apply - the release build logs at INFO and keeps chat content out of its log,
  so don't log chat text above DEBUG).
- The logger is thread safe and can be kept; the file is opened for every line, so nothing stays open after the
  extension is gone. A file over 2 MB is moved to `<jar name>.log.old` (replacing the previous one).
- `OsgiService.readLog(bundle, maxBytes)` and `clearLog(bundle)` (administrators only) read the end of the file and
  delete it. The log is deleted with the extension when it is unloaded.
- Code that is not created by the extension class (components made by `new`, services) needs the logger too: pass it
  on to your service or keep it in a static holder that is set in `onExtensionLoad` and
  reset in `onExtensionUnload`, with `LoggerFactory` as the fallback before and after.
- The class `org.slf4j.Logger` comes from the application (boot delegation); if your bundle lists its
  `Import-Package` explicitly, include `org.slf4j`.

### Extension events

`OsgiService` reports single extensions that are loaded or unloaded to `ExtensionListener`s, for code that has to
react to the extension set - the application itself uses it to keep the styles of extensions in the pages
([Styles of a plugin](ui-extensions.md#styles-of-a-plugin)):

```java
osgiService.addExtensionListener(listener);       // held weakly: the subscriber keeps the reference
osgiService.removeExtensionListener(listener);
osgiService.getLoadedExtensions();                // the extensions that are loaded now: (Bundle, LingoLLMExtension)
```

- `extensionLoaded(bundle, extension)` is called after `onExtensionLoad` returned. An extension whose load failed is
  never reported (and not as unloaded either); it is unloaded again as before.
- `extensionUnloaded(bundle, extension)` is called after `onExtensionUnload`, for every extension that was reported as
  loaded: *Unload*, an update that replaces the bundle, the shutdown of the application.
- The methods run on the thread that loads or unloads (an administrator's request, the startup), with no UI session.
  Change a UI through `ui.access(...)`. An exception is logged and doesn't stop other listeners or the loading.
- Listeners get no events for extensions that are loaded already. Subscribe first, then read `getLoadedExtensions()`;
  an extension loaded between the two is seen twice, so handle repeats (`CssRegistry.attach` does).

**Optional interfaces.** Besides `LingoLLMExtension`, the extension class can implement `ExtensionHasCss` (styles
put into every page, see [Styles of a plugin](ui-extensions.md#styles-of-a-plugin)) and `ExtensionHasLocalization`
(languages for the UI: `OsgiServiceImpl` registers the returned `LocalizationBundle`s in `Localization` right after
`onExtensionLoad` returned and removes them when the extension is unloaded; a language that already has a bundle is refused, English can't be replaced).

The older `ExtensionObserver` (`addSubscriber`) reports the start and stop of the whole framework, including the
pauses during an upload or unload.

## Class loading

Felix is started with `org.osgi.framework.bootdelegation=*` and the framework class loader as the bundle parent, which
is the web application's class loader. Every class a bundle asks for is therefore looked up in the application
first: LingoLLM's own classes, Vaadin, Spring, Hibernate, Gson, everything in `WEB-INF/lib`. The consequences:

- Plugins use the application's classes directly - no imports, no service lookups, `instanceof` and casts work.
- A plugin can't override or replace a library of the application, and can't use a different version of one.
- Plugins don't see each other's classes through imports either; if two plugins need to cooperate, do it through
  shared data (attributes) or through the application's components. A plugin that uses the objects of another one
  (which may be missing, or unloaded and installed again) finds it among `OsgiService.getLoadedExtensions()` by the
  *name* of its class and calls it by reflection, taking the types it needs from `extension.getClass().getClassLoader()`.
  Do not import its packages (`Import-Package`, `DynamicImport-Package`) or name its classes in your code: the
  wiring is to one revision of the other bundle, so after it is unloaded and installed again your bundle keeps the old
  classes and does not recognize the new objects, and the JVM remembers a class reference that failed to resolve
  (`NoClassDefFoundError`), so a reference used while the other bundle was missing keeps failing after it is installed.
- The plugin's own classes are loaded by the bundle's class loader, so the class names you pass to
  `registerDecorator` must be those of the **application's** classes; a plugin's own classes are never instrumented.

## The development loop

1. Run LingoLLM from your IDE or a local Jetty with a separate data folder
   ([Running locally](../building.md#running-locally)).
2. `mvn package` the plugin.
3. Load the JAR in *Admin → Extensions*, or copy it into `<data folder>/extensions` and restart.
4. Open the screen the plugin extends. If nothing happens, check the log: a decorator that can't find an argument,
   local variable or field logs `Mod '<class>' skipped on <class>.<method>: ...` with the missing name; other
   exceptions are logged as `Unhandled exception in extension ...`.
5. To try a new build: *Unload* the plugin (it deletes the JAR), load the new one.

When a plugin uses a class or method of the application that changed, the plugin fails at runtime with a
`NoSuchMethodError` or `NoClassDefFoundError`. Rebuild it against the current application.

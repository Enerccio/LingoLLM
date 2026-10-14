---
label: Extending the UI
order: 70
covers:
  - lingollm/src/main/java/com/github/enerccio/lingollm/ui
  - lingollm/src/main/java/com/github/enerccio/lingollm/instruct
  - lingollm/src/main/java/com/github/enerccio/lingollm/domain/service/OsgiService.java
---

# Extending the UI

Plugins add to the user interface from decorators: when a method that builds part of a screen runs, the plugin's
decorator finds the component it built and adds to it. This page collects the patterns plugins use. Read
[@Extendable hooks](extendable.md) first, and [User interface](../ui.md) for the conventions of the UI code.

Some screens exist twice: the desktop class and its phone twin (`DesktopWorkspace` and `PhoneWorkspace`, `AIPart` and
`PhoneAIPart`), and a decorator only runs for the class it was registered for. Register it a second time for the phone
class to support phones, or leave it out and the extension simply adds nothing there, see
[The phone twins](extendable.md#the-phone-twins). `LanguagePart` and `UserPart` are shared.

## The basic pattern

```java
@Override
public void onMethodLeave(Object instrumented, ExtendableMethodContext context, Throwable throwing) throws Exception {
    if (throwing != null) {
        return;                                                   // the method failed, leave it alone
    }
    Grid<AI> grid = context.getReflectiveFieldValue(instrumented, "grid", Grid.class);   // AIPart.create
    if (grid == null || ComponentUtil.getData(grid, MY_COLUMN_KEY) != null) {
        return;                                                   // nothing to extend, or already extended
    }
    Grid.Column<AI> column = grid.addComponentColumn(ai -> new Button("My action", event -> myAction(ai)));
    ComponentUtil.setData(grid, MY_COLUMN_KEY, column);           // mark it, keep a handle for unloading
    trackedGrids.add(grid);                                       // remember it for onExtensionUnload
}
```

1. **Find the component** - a field, a local variable or an argument of the decorated method.
2. **Add only once.** Many methods run more than once for the same component (`refresh()`, `UserPart.refresh`...),
   so mark the component with `ComponentUtil.setData(component, key, value)` and check the mark first. Prefix the
   keys with your plugin's package (`KEY + ".menuItem"`, see [Extended attributes](extended-attributes.md)) - the
   components are shared with the application and every other plugin.
3. **Remember what you added**, so that `onExtensionUnload` can remove it ([Unloading](#unloading)).

## Recipes

### Panels in an existing layout

Find the layout and insert at a position relative to a known child. `LanguagePart.create()` builds a `VerticalLayout`
(local `layout`) with the language selector (local `header`) and the container of the language screen:

```java
VerticalLayout layout = context.getLocalVariable("layout", VerticalLayout.class);
Component header = context.getLocalVariable("header", Component.class);
layout.addComponentAtIndex(layout.indexOf(header) + 1, myPanel);       // right below the selector
```

A component created once for a view must not hold on to things that change in it: `LanguagePart` stays the same when the
user picks another profile, so a panel bound to the profile it was created with ends up acting on the wrong one. Look
the current value up when it is needed, or hook the method that runs on every change (`selectLanguage(language,
remember)` has the profile as its argument, `showLanguage(language)` rebuilds the screen of a language - the
[example plugin](example.md) uses it).

### A tab in the workspace

`DesktopWorkspace.create()` builds the `HTabSheet` held by the field `tabs`. On leave a decorator can add a tab:

```java
HTabSheet tabs = context.getReflectiveFieldValue(instrumented, "tabs", HTabSheet.class);
Tab tab = tabs.add("My tab", myComponent);
```

The tab is not a `WorkspaceComponent` of the workspace: it gets no `refresh`/`onTabSwitched` calls and is not disabled
while the settings have unsaved changes, so keep it self-contained. Do the same for `PhoneWorkspace`, which has a drawer
instead of a tab sheet (its `tabs` field is a `Tabs`, the content is shown with `layout.setContent`; read the class
before you decorate it).

### Columns in an existing grid

A grid the part keeps in a field can get more columns. `AIPart.create()` builds `grid`, a `Grid<AI>`; after it, add a
button to every row and keep the column to remove it on unload:

```java
Grid<AI> grid = context.getReflectiveFieldValue(instrumented, "grid", Grid.class);
Grid.Column<AI> column = grid.addComponentColumn(ai -> new Button(icon, e -> openDialog(ai)));
ComponentUtil.setData(grid, COLUMN_KEY, column);          // onExtensionUnload: grid.removeColumn(column)
```

The row's item is the entity itself; read the context that is not part of it from the part, not from the row.

### Changing what the application built

The decorator can change any component the method built: read it from a local variable, a field or an argument and
change it (a label, a style class, a tooltip). If more plugins change the same component, they run in the order they
were loaded and the last one wins - append or prefix rather than replace where you can.


### Settings panels

The *Settings* screen (`UserPart`) has an *Extension Settings* tab with an `Accordion` in the field
`extensionSettings`, empty unless plugins add to it. A plugin uses two decorators:

```java
// UserPart.refresh, on leave: add the panel once, refresh it on later calls
Accordion accordion = context.getReflectiveFieldValue(instrumented, "extensionSettings", Accordion.class);
AccordionPanel panel = (AccordionPanel) ComponentUtil.getData(accordion, SETTINGS_PANEL_KEY);
if (panel == null) {
    panel = accordion.add("Notes Settings", new NotesSettingsForm(notesService));
    ComponentUtil.setData(accordion, SETTINGS_PANEL_KEY, panel);
} else {
    ((NotesSettingsForm) panel.getContent().findFirst().orElseThrow()).refresh();
}

// UserPart.save, on ENTER: put the form's values into the user's settings object...
UserSetting userSetting = context.getReflectiveFieldValue(instrumented, "userSetting", UserSetting.class);
notesService.saveSettings(form.save(), userSetting);     // writes into userSetting.getAttributes()
// ...which the body of save() then stores with settingService.save(userSetting)
```

Changes in the panel's fields are tracked like the application's own: any value change outside of
`UserPart.refresh` (and its decorators) locks the workspace navigation until the user saves or discards. Fill the form
in the `refresh` decorator, not later (e.g. from a background thread), or the loaded values count as changes.

Saving on enter lets the application's own `settingService.save(userSetting)` store the plugin's values too. Note that
`save()` returns early when the application's own fields don't validate - then the plugin's values are not stored
either.

### Dialogs and own components

Plugin dialogs and components are ordinary Vaadin classes in the bundle. Annotate them `@Configurable` to inject
services. Follow the application's conventions ([User interface → Conventions](../ui.md#conventions)).

What plugins can't do:

- **Add frontend resources.** The browser side of LingoLLM is one bundle built with the WAR. A plugin can use every
  Vaadin component and add-on the application already uses (Vaadin core components, the FontAwesome icons,
  Viritin's `VTabSheet`...), but `@CssImport`, `@JsModule`, `@NpmPackage` and add-ons with their own web
  components don't work from a plugin. Style with the classes of `CssStyles`
  (`addClassName(CssStyles.TEXT_SECONDARY)`), which the application's `shared-styles.css` defines. A style only your
  plugin uses goes into a CSS file in the bundle's `src/main/resources` that the application puts into the page for
  you ([Styles of a plugin](#styles-of-a-plugin)), with its class names as constants in the plugin. Use `getStyle()` only for a value known at runtime.
- **Add localization keys.** `Localization` knows only the application's `L` keys. The bundled plugins use English
  strings; a plugin that wants translations of its own texts has to bring its own (a `ResourceBundle` in the bundle,
  chosen by `Localization.getCurrentLanguage()`). A plugin can add a *language* for the application's texts with
  `ExtensionHasLocalization`, see [Plugin basics](overview.md#extension-events).
- **Add routes.** Routes are registered when the servlet starts. Use dialogs and tabs instead.

## Styles of a plugin

A plugin can't add to the application's stylesheet, but it can ship its own CSS. Implement `ExtensionHasCss` on the
class that implements `LingoLLMExtension`:

```java
public class MyExtension implements LingoLLMExtension, ExtensionHasCss {

    @Override
    public String getCss(ApplicationEntryPoint entryPoint) {
        // the card lives in the workspace only
        return entryPoint instanceof Main ? MyStyles.css() : null;
    }
    ...
}
```

`getCss` returns the CSS text for a page, or `null`/blank when the extension has no styles there. The entry point is
the root component of the page: `Main` (the workspace), which implements `ApplicationEntryPoint`. The text is usually a resource of the bundle, read once and cached:

```java
try (InputStream in = MyStyles.class.getResourceAsStream("/my-plugin.css")) {
    css = new String(in.readAllBytes(), StandardCharsets.UTF_8);
}
```

Use Lumo variables (`var(--lumo-contrast-10pct)`) in the rules and keep the class names as constants next to the
loader (a `MyStyles` class and `my-plugin.css`); prefix them with the plugin's name, the page is shared
with the application and other plugins.

**What the application does.** The entry point owns a `CssRegistry` (created in `onAttach`, released in `onDetach`).
For every UI it

- adds the style of each extension that is loaded already when the entry point is attached (an extension loaded at
  startup, or before the user opened the page),
- adds the style of an extension that is loaded while the page is open (*Admin → Extensions → Load*), so it works
  without a reload,
- removes the style again when the extension is unloaded, replaced by a newer version or the application stops.

The CSS goes into a `<style id="lingollm-extension-css-<extension class name>">` element in the document head; the
element is replaced if it exists, so nothing is added twice. The registry listens to `OsgiService` through an
`ExtensionListener` ([Extension events](overview.md#extension-events)). `getCss` is called once per page and extension
and an exception from it is logged and skips only that extension, but it should not do anything expensive.

!!!warning Do not inject the style yourself
Don't add the `<style>` element from your own components (`executeJs` in a constructor): the style would not be
removed when the extension is unloaded and would be added again for each component. Return it from `getCss`.
!!!

## Errors

Inside click listeners and other event handlers, catch exceptions and report them like the application does:
`UIUtils.internalServerError(loc, e)` logs and shows an error dialog - pass an injected `Localization`. Expected problems go to `Notification`. Failures of the model API (`InferenceException`) are explained by `UIUtils.inferenceError(loc, e)` or, as text, `InferenceErrors.messageOf(loc, e)`. Exceptions in decorators themselves are logged by the
`ExtensionService` and don't need handling unless you want to show something.

## Threads

Decorators run on the thread that called the decorated method. For UI methods that's a request thread holding the
Vaadin session lock, so decorators can change components directly and services see the current user.

Anything slow - calling a model, large imports - belongs on another thread, with the same rules as in the
application ([Background work and push](../ui.md#background-work-and-push)): capture `UI.getCurrent()` on the UI
thread, change components only inside `ui.access(...)`, push with `UIPushGuard.push(ui)`, and wrap service calls that
need the current user in the request scope.

### Calling the model

`InferenceServices.forAI(ai)` returns the client for a provider: `stream(...)` and `infer(...)` send a list of messages
and call back on another thread, with the limits and the usage of the user applied
([Services → Inference](../services.md#inference)). A plugin asks for a whole answer like this:

```java
UI ui = UI.getCurrent();
InferenceService service = inferenceServices.forAI(ai);
List<LLMChatMessage> payload = List.of(
        LLMChatMessage.of(LLMRole.SYSTEM, "You are a language teacher."),
        LLMChatMessage.of(LLMRole.USER, "Correct this sentence: " + sentence));

CancellationToken token = service.infer(payload, new InferenceService.InferenceResultCallback() {
    @Override
    public void onResult(String text, SchemaCheck check) {
        ui.access(() -> output.setValue(text));
    }

    @Override
    public void onCancel() {
    }

    @Override
    public void onError(Throwable error) {
        ui.access(() -> UIUtils.inferenceError(loc, error));
    }
});
// token.cancel() when the dialog closes
```

The callbacks run on a worker thread, so the `UI` is captured first and the component changes go through `ui.access`.
An error of the provider arrives as an `InferenceException`, which `UIUtils.inferenceError` explains to the user. For a
structured answer pass a class instead (`infer(payload, MyAnswer.class, callback)`), for function calls a `FunctionSet`.

## Unloading

When a plugin is unloaded, components it added to open screens stay there - with listeners pointing into a bundle
that is gone - unless the plugin removes them. There are two ways.

**Track and remove** - keep the extended components in weak sets (so that closed
screens can be garbage collected) and undo the changes in `onExtensionUnload`:

```java
private final Set<Grid<AI>> grids = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

@Override
public void onExtensionUnload(Bundle b, OsgiService osgiService, ExtensionService extensionService) {
    extensionService.unregisterDecorator(gridDecorator);
    synchronized (grids) {
        for (Grid<AI> grid : grids) {
            grid.getUI().ifPresent(ui -> ui.access(() -> {
                Grid.Column<AI> column = (Grid.Column<AI>) ComponentUtil.getData(grid, MY_COLUMN_KEY);
                if (column != null) {
                    grid.removeColumn(column);
                    ComponentUtil.setData(grid, MY_COLUMN_KEY, null);
                }
            }));
        }
        grids.clear();
    }
}
```

**Bind a callback** - `OsgiService.bindAttachableComponent(component, callback, extension)` registers `callback` to
run when the extension is unloaded, for as long as `component` is attached (a component that is detached and attached
again - moved, in a tab sheet, in a reopened dialog - stays bound):

```java
NotesView view = new NotesView(notesService);
content.add(view);
osgiService.bindAttachableComponent(view, () -> content.remove(view), this);
```

(`osgiService` is the one passed to `onExtensionLoad`, or injected.) The callbacks run before `onExtensionUnload`,
each inside `ui.access(...)` of its component's UI, so they can change the component directly. A component that is
detached when the extension is unloaded runs its callback when it is attached again. Calling
`bindAttachableComponent` for an extension that isn't loaded throws an `IllegalStateException`.

!!!warning Other users' sessions
`onExtensionUnload` runs on the request thread of the administrator who clicked *Unload*, but the components belong
to every user's open screens. Change them inside `component.getUI().ifPresent(ui -> ui.access(...))`, as above, or
with `UIUtils.accessComponent(component, () -> ...)`, which does the same and runs the change directly when the
component isn't attached. 
!!!

Data in attributes stays after unloading, so loading the plugin again picks up where it left off. If your plugin is
uninstalled for good, its data stays in the database (and in backups) - harmless, but say so in its README.

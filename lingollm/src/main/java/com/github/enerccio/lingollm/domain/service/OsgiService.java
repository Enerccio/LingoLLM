package com.github.enerccio.lingollm.domain.service;

import com.github.enerccio.lingollm.extensions.LingoLLMExtension;
import com.vaadin.flow.component.Component;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.List;

public interface OsgiService {

    BundleContext getContext();

    Bundle findBundleByJar(String jarName);

    void start() throws Exception;

    void stop() throws Exception;

    void restart() throws Exception;

    /**
     * Stores an uploaded JAR in the extensions folder (under a sanitized file name) and loads it. An installed bundle
     * with the same symbolic name or file name is unloaded and replaced. When the new bundle can't be installed or
     * started, the uploaded file is deleted and the replaced bundle is restored.
     *
     * @throws ExtensionVerificationException when the extension asks for something this version of the application
     *                                        doesn't have, the exception carries the report
     */
    Bundle installPackage(String fileName, byte[] data) throws Exception;

    /**
     * Unloads the bundle, uninstalls it and deletes its JAR.
     */
    void uninstallPackage(Bundle b) throws Exception;

    /**
     * Every installed bundle except the framework itself, also the ones that failed to start.
     */
    List<Bundle> getBundles();

    List<ServiceReference<?>> getServices(Bundle b, Class<?> service) throws InvalidSyntaxException;

    List<ServiceReference<?>> getServices(Class<?> service) throws InvalidSyntaxException;

    void addSubscriber(ExtensionObserver observer);

    void removeSubscriber(ExtensionObserver observer);

    /**
     * Subscribes to the loading and unloading of single extensions. The listener is held weakly, like the
     * {@link ExtensionObserver}s - the subscriber has to keep a reference to it. Subscribing twice has no effect.
     * Extensions that are loaded already are not reported, use {@link #getLoadedExtensions()} for them.
     */
    void addExtensionListener(ExtensionListener listener);

    void removeExtensionListener(ExtensionListener listener);

    /**
     * The extensions that finished loading ({@code onExtensionLoad} returned) and were not unloaded yet.
     */
    List<LoadedExtension> getLoadedExtensions();

    String getExtensionsPath();

    /**
     * Whether the extension is stopped by the administrator: its JAR has a {@code <jar name>.STOPPED} flag file next
     * to it. A stopped extension stays installed (and shown in Admin → Extensions) but is not started, also after the
     * application restarts, until it is started again.
     */
    boolean isStopped(Bundle b);

    /**
     * The SHA-256 of the JAR of the extension as lowercase hex, so the administrator can compare it with the hash
     * published by the author of the extension. Cached until the JAR changes.
     */
    String getSha256(Bundle b) throws IOException;

    /**
     * Unloads the extension ({@code onExtensionUnload}), stops its bundle and creates the {@code .STOPPED} flag.
     * Does nothing when it is stopped already.
     */
    void stopExtension(Bundle b) throws Exception;

    /**
     * Deletes the {@code .STOPPED} flag and starts the extension, it is verified first like at the start of the
     * application. When that fails the extension stays stopped and the exception is thrown.
     */
    void startExtension(Bundle b) throws Exception;

    /**
     * A logger for the extension, in the usual SLF4J way ({@code log.info("Loaded {}", name)}, a trailing throwable is
     * logged with its stack trace). Everything it logs goes to {@code <jar name>.log} in the extensions folder (for
     * {@code sidequery.jar} that is {@code sidequery.jar.log}), and also, chained, to the application log (logger {@code extension.<name>}, at the level of the line); TRACE is dropped. A file
     * over 2 MB is moved to {@code .log.old}. Call it from {@code onExtensionLoad} with the bundle passed there; the
     * returned logger can be kept and used from any thread.
     */
    Logger createLogger(Bundle b);

    /**
     * Same as {@link #createLogger(Bundle)} with the given logger name (shown in every line), by default the symbolic
     * name of the bundle. Loggers of one extension write into the same file.
     */
    Logger createLogger(Bundle b, String name);

    /**
     * The last {@code maxBytes} bytes of the log of the extension (see {@link #createLogger(Bundle)}), cut at a line
     * start; empty when nothing was logged.
     */
    String readLog(Bundle b, int maxBytes) throws Exception;

    /**
     * Deletes the log of the extension (and its {@code .old} rotation).
     */
    void clearLog(Bundle b) throws Exception;

    /**
     * The report of the verification of the extension ({@link ExtensionService#verifyExtension}), {@code null} when
     * there is none yet. An extension whose report says it is invalid is installed but not started.
     */
    ExtensionReport getVerificationReport(Bundle b);

    record ExtensionReport(boolean valid, String text) {
    }

    record LoadedExtension(Bundle bundle, LingoLLMExtension extension) {
    }

    /**
     * Registers a cleanup callback for a component the extension added to the UI. When the extension is unloaded, the
     * callback runs for every bound component that is attached, inside {@code ui.access(...)} of the component's UI;
     * a component that is detached at that moment runs it when it is attached again.
     *
     * @throws IllegalStateException when the extension is not loaded
     */
    <T extends Component> T bindAttachableComponent(T component, Runnable callback, LingoLLMExtension extension) throws Exception;

    /**
     * Reports single extensions that are loaded or unloaded. The methods are called on the thread that loads or
     * unloads the extension (an administrator's request, the start of the application), not in a UI session: change
     * a UI only through {@code ui.access(...)}. An exception thrown by a listener is logged and doesn't stop the
     * others.
     */
    interface ExtensionListener {

        /**
         * The extension finished {@code onExtensionLoad}.
         */
        void extensionLoaded(Bundle bundle, LingoLLMExtension extension);

        /**
         * The extension was unloaded ({@code onExtensionUnload} was called), only for an extension that was reported
         * by {@link #extensionLoaded}. The extension is unloaded also when its bundle is replaced and when the
         * framework stops.
         */
        void extensionUnloaded(Bundle bundle, LingoLLMExtension extension);

    }

    interface ExtensionObserver {

        void beforeServiceStarted(OsgiService service);
        void afterServiceStarted(OsgiService service);
        void beforeServiceStopped(OsgiService service);
        void afterServiceStopped(OsgiService service);

    }
}

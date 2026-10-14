package com.github.enerccio.lingollm.domain.service.impl;

import com.github.enerccio.lingollm.Configuration;
import com.github.enerccio.lingollm.domain.security.AdminGuard;
import com.github.enerccio.lingollm.domain.service.ExtensionService;
import com.github.enerccio.lingollm.domain.service.ExtensionVerificationException;
import com.github.enerccio.lingollm.domain.service.OsgiService;
import com.github.enerccio.lingollm.extensions.ExtensionHasLocalization;
import com.github.enerccio.lingollm.extensions.LingoLLMExtension;
import com.github.enerccio.lingollm.instruct.verify.ExtensionVerification;
import com.github.enerccio.lingollm.instruct.verify.ExtensionVerifier;
import com.github.enerccio.lingollm.loc.Localization;
import com.github.enerccio.lingollm.loc.LocalizationBundle;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.shared.Registration;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.NonNull;
import org.osgi.framework.*;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.launch.FrameworkFactory;
import org.osgi.framework.wiring.FrameworkWiring;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.web.context.support.XmlWebApplicationContext;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.CodeSource;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

public class OsgiServiceImpl implements OsgiService, ApplicationListener<ContextRefreshedEvent>, InitializingBean, DisposableBean {
    private static final Logger log = LoggerFactory.getLogger(OsgiServiceImpl.class);

    private static final long FRAMEWORK_TIMEOUT_MS = 30_000;
    /** Where the JARs of older versions of an installed extension go, inside the extensions folder. */
    private static final String OLD_VERSIONS_FOLDER = "old-versions";
    /** The flag file next to the JAR of an extension the administrator stopped. */
    private static final String STOPPED_SUFFIX = ".STOPPED";

    @Autowired
    private Configuration configuration;

    @Autowired
    private ExtensionService extensionService;

    @Autowired
    private AdminGuard adminGuard;

    @Autowired
    private Localization localization;

    private String extensionPath;
    private Framework f;
    private BundleContext context;

    private final Map<ServiceReference<?>, LingoLLMExtension> activeExtensions = new ConcurrentHashMap<>();
    private final Map<LingoLLMExtension, Set<ComponentBinding>> componentCallbacks = new ConcurrentHashMap<>();
    private final Map<ServiceReference<?>, LoadedExtension> loadedExtensions = new ConcurrentHashMap<>();
    private final Map<LingoLLMExtension, List<LocalizationBundle>> localizationBundles = new ConcurrentHashMap<>();
    private final Set<WeakReference<ExtensionObserver>> observers = new HashSet<>();
    private final List<WeakReference<ExtensionListener>> extensionListeners = new CopyOnWriteArrayList<>();
    private final Map<String, String> sha256Cache = new ConcurrentHashMap<>();
    private volatile Long applicationTimestamp;

    @Override
    public void afterPropertiesSet() throws Exception {
        extensionPath = new File(configuration.getFolder(), "extensions").getAbsolutePath();
        File testFile = new File(extensionPath);
        if (!testFile.exists()) {
            testFile.mkdirs();
        }
    }

    @Override
    public void onApplicationEvent(@NonNull ContextRefreshedEvent event) {
        try {
            XmlWebApplicationContext applicationContext = (XmlWebApplicationContext) event.getSource();
            if (applicationContext.getDisplayName().startsWith("Root"))
                start();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    @Override
    public void destroy() {
        onExit();
    }

    @Override
    public BundleContext getContext() {
        return context;
    }

    @Override
    public Bundle findBundleByJar(String jarName) {
        for (Bundle b : context.getBundles()) {
            if (locationFile(b).getName().equals(jarName)) {
                return b;
            }
        }
        return null;
    }

    protected void onExit() {
        try {
            if (f != null) {
                stop();
            }
        } catch (Exception e) {
            log.error(e.getMessage());
            log.debug(e.getMessage(), e);
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public synchronized void start() throws Exception {
        onBeforeStart();
        Map<String, String> config = new HashMap<>();

        FrameworkFactory ff = ServiceLoader.load(FrameworkFactory.class).iterator().next();

        File extdir = new File(extensionPath);
        if (!extdir.exists())
            extdir.mkdir();

        File bundleInternalStorage = new File(extdir, "org.eclipse.osgi");
        if (bundleInternalStorage.exists())
            FileUtils.deleteDirectory(bundleInternalStorage);

        config.put(Constants.FRAMEWORK_STORAGE, bundleInternalStorage.getAbsolutePath());
        config.put(Constants.FRAMEWORK_STORAGE_CLEAN, "true");
        config.put(Constants.FRAMEWORK_STORAGE_CLEAN_ONFIRSTINIT, "true");
        config.put(Constants.FRAMEWORK_BUNDLE_PARENT, Constants.FRAMEWORK_BUNDLE_PARENT_FRAMEWORK);
        config.put(Constants.FRAMEWORK_BOOTDELEGATION, "*");

        ClassLoader current = Thread.currentThread().getContextClassLoader();
        try {
            f = ff.newFramework(config);
            f.start();
        } finally {
            Thread.currentThread().setContextClassLoader(current);
        }
        context = f.getBundleContext();

        // every JAR on its own - a broken one must not stop the others from loading
        File[] jars = extdir.listFiles(f -> f.exists() && f.isFile() && f.getName().endsWith(".jar"));
        if (jars != null) {
            Arrays.sort(jars);
            for (File jar : jars) {
                try {
                    context.installBundle(location(jar));
                } catch (Exception e) {
                    log.error("Failed to install extension {}: {}", jar.getName(), e.getMessage(), e);
                }
            }
        }

        uninstallOlderVersions();

        for (Bundle b : getBundles()) {
            if (isStopped(b)) {
                log.info("Extension {} ({}) is stopped, it is not started", b.getSymbolicName(), locationFile(b).getName());
                continue;
            }
            try {
                startBundle(b);
            } catch (ExtensionVerificationException e) {
                log.error("Extension {} is not started, it does not fit this version of LingoLLM:\n{}", e.getExtension(), e.getReport());
            } catch (Exception e) {
                log.error("Failed to start extension {} ({}): {}", b.getSymbolicName(), locationFile(b).getName(), e.getMessage(), e);
            }
        }

        onStart();
    }

    /**
     * Several versions of one extension in the folder (an update copied next to the old JAR instead of replacing it)
     * would all be started and their listeners would clash, so only the newest of each symbolic name stays. The JARs
     * of the older ones are moved to {@link #OLD_VERSIONS_FOLDER} (not deleted, they can be put back).
     */
    private void uninstallOlderVersions() {
        Map<String, List<Bundle>> byName = new LinkedHashMap<>();
        for (Bundle b : getBundles()) {
            byName.computeIfAbsent(String.valueOf(b.getSymbolicName()), n -> new ArrayList<>()).add(b);
        }
        for (List<Bundle> versions : byName.values()) {
            if (versions.size() < 2) {
                continue;
            }
            versions.sort(Comparator.comparing(Bundle::getVersion).reversed());
            Bundle newest = versions.getFirst();
            for (Bundle older : versions.subList(1, versions.size())) {
                File jar = locationFile(older);
                log.warn("Extension {} is installed in several versions, {} ({}) is not loaded because {} ({}) is newer; moving it to {}",
                        older.getSymbolicName(), older.getVersion(), jar.getName(),
                        newest.getVersion(), locationFile(newest).getName(), OLD_VERSIONS_FOLDER);
                try {
                    older.uninstall();
                    File folder = new File(jar.getParentFile(), OLD_VERSIONS_FOLDER);
                    Files.createDirectories(folder.toPath());
                    Files.move(jar.toPath(), new File(folder, jar.getName()).toPath(), StandardCopyOption.REPLACE_EXISTING);
                    deleteReports(jar);
                } catch (BundleException | IOException e) {
                    log.error("Failed to skip extension {} ({}): {}", older.getSymbolicName(), jar.getName(), e.getMessage(), e);
                }
            }
        }
    }

    @Override
    public synchronized void stop() throws Exception {
        onBeforeStop();
        if (f != null && f.getState() == Framework.ACTIVE) {
            for (Bundle b : getBundles()) {
                try {
                    stopBundleInternal(b);
                    b.stop();
                } catch (Exception e) {
                    log.error("Failed to stop extension {}: {}", b.getSymbolicName(), e.getMessage(), e);
                }
            }

            log.trace("stopping extension start");
            f.stop();
            FrameworkEvent event = f.waitForStop(FRAMEWORK_TIMEOUT_MS);
            if (event.getType() == FrameworkEvent.WAIT_TIMEDOUT) {
                log.warn("OSGi framework did not stop in {} ms", FRAMEWORK_TIMEOUT_MS);
            }
            log.trace("stopping extension stop");
        }
        activeExtensions.clear();
        loadedExtensions.clear();
        componentCallbacks.clear();
        onStop();
    }

    @Override
    public void restart() throws Exception {
        stop();
        start();
    }

    @Override
    public synchronized Bundle installPackage(String fileName, byte[] data) throws Exception {
        adminGuard.requireAdmin();
        File extdir = new File(extensionPath);
        File target = new File(extdir, sanitizeJarName(fileName));
        // not a .jar, so a crash in the middle doesn't leave it to be loaded on the next start
        File upload = File.createTempFile("upload-", ".tmp", extdir);
        Map<File, File> backups = new LinkedHashMap<>();
        boolean targetWritten = false;

        onBeforeStop();
        onStop();
        onBeforeStart();
        try {
            FileUtils.writeByteArrayToFile(upload, data);
            String symbolicName = readSymbolicName(upload);

            // an upload of an installed bundle (same file or same symbolic name) replaces it
            for (Bundle old : getBundles()) {
                if (symbolicName.equals(old.getSymbolicName()) || location(target).equals(old.getLocation())) {
                    File oldFile = locationFile(old);
                    log.info("Replacing extension {} ({})", old.getSymbolicName(), oldFile.getName());
                    try {
                        stopBundleInternal(old);
                    } catch (Exception e) {
                        log.error("Failed to unload extension {}: {}", old.getSymbolicName(), e.getMessage(), e);
                    }
                    old.uninstall();
                    if (oldFile.exists()) {
                        File backup = new File(oldFile.getPath() + ".old");
                        Files.move(oldFile.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        backups.put(oldFile, backup);
                    }
                    deleteReports(oldFile);
                    // an uploaded extension is started, also over a stopped one
                    Files.deleteIfExists(stoppedFlag(oldFile).toPath());
                }
            }
            if (!backups.isEmpty()) {
                refreshBundles();
            }

            Files.move(upload.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            targetWritten = true;

            Bundle b = context.installBundle(location(target));
            try {
                startBundle(b);
            } catch (Exception e) {
                b.uninstall();
                throw e;
            }

            for (File backup : backups.values()) {
                Files.deleteIfExists(backup.toPath());
            }
            return b;
        } catch (Exception e) {
            if (targetWritten) {
                Files.deleteIfExists(target.toPath());
                deleteReports(target);
            }
            restoreBackups(backups);
            throw e;
        } finally {
            Files.deleteIfExists(upload.toPath());
            onStart();
        }
    }

    private void restoreBackups(Map<File, File> backups) {
        for (Map.Entry<File, File> entry : backups.entrySet()) {
            try {
                Files.move(entry.getValue().toPath(), entry.getKey().toPath(), StandardCopyOption.REPLACE_EXISTING);
                startBundle(context.installBundle(location(entry.getKey())));
            } catch (Exception e) {
                log.error("Failed to restore extension {}: {}", entry.getKey().getName(), e.getMessage(), e);
            }
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public synchronized void uninstallPackage(Bundle b) throws Exception {
        adminGuard.requireAdmin();
        if (b != null && b.getBundleId() != 0) {
            onBeforeStop();
            onStop();
            onBeforeStart();
            try {
                try {
                    stopBundleInternal(b);
                } finally {
                    b.uninstall();
                    File jar = locationFile(b);
                    jar.delete();
                    deleteReports(jar);
                    Files.deleteIfExists(stoppedFlag(jar).toPath());
                    ExtensionLogger.delete(ExtensionLogger.logFile(jar));
                }
                refreshBundles();
            } finally {
                onStart();
            }
        }
    }

    @Override
    public String getSha256(Bundle b) throws IOException {
        File jar = locationFile(b);
        String key = jar.getPath() + '|' + jar.lastModified() + '|' + jar.length();
        String cached = sha256Cache.get(key);
        if (cached != null) {
            return cached;
        }
        String hash;
        try (InputStream in = Files.newInputStream(jar.toPath())) {
            hash = DigestUtils.sha256Hex(in);
        }
        sha256Cache.keySet().removeIf(k -> k.startsWith(jar.getPath() + '|'));
        sha256Cache.put(key, hash);
        return hash;
    }

    @Override
    public boolean isStopped(Bundle b) {
        return stoppedFlag(locationFile(b)).exists();
    }

    @Override
    public synchronized void stopExtension(Bundle b) throws Exception {
        adminGuard.requireAdmin();
        requireInstalled(b);
        if (isStopped(b)) {
            return;
        }
        onBeforeStop();
        onStop();
        onBeforeStart();
        try {
            // the flag first: when the unload fails, the extension is still not started the next time
            Files.createFile(stoppedFlag(locationFile(b)).toPath());
            try {
                stopBundleInternal(b);
            } finally {
                b.stop();
            }
        } finally {
            onStart();
        }
    }

    @Override
    public synchronized void startExtension(Bundle b) throws Exception {
        adminGuard.requireAdmin();
        requireInstalled(b);
        File flag = stoppedFlag(locationFile(b));
        if (!flag.exists()) {
            return;
        }
        onBeforeStop();
        onStop();
        onBeforeStart();
        try {
            startBundle(b);
            Files.deleteIfExists(flag.toPath());
        } finally {
            onStart();
        }
    }

    private static void requireInstalled(Bundle b) {
        if (b == null || b.getBundleId() == 0 || b.getState() == Bundle.UNINSTALLED) {
            throw new IllegalArgumentException("Not an installed extension");
        }
    }

    /**
     * The flag file of a stopped extension: {@code <jar name>.STOPPED} next to the JAR.
     */
    private static File stoppedFlag(File jar) {
        return new File(jar.getAbsoluteFile().getParentFile(), jar.getName() + STOPPED_SUFFIX);
    }

    @Override
    public Logger createLogger(Bundle b) {
        return createLogger(b, b.getSymbolicName());
    }

    @Override
    public Logger createLogger(Bundle b, String name) {
        return new ExtensionLogger(name, ExtensionLogger.logFile(locationFile(b)));
    }

    @Override
    public String readLog(Bundle b, int maxBytes) throws Exception {
        adminGuard.requireAdmin();
        return ExtensionLogger.tail(ExtensionLogger.logFile(locationFile(b)), maxBytes);
    }

    @Override
    public void clearLog(Bundle b) throws Exception {
        adminGuard.requireAdmin();
        ExtensionLogger.delete(ExtensionLogger.logFile(locationFile(b)));
    }

    @Override
    public List<Bundle> getBundles() {
        if (f != null && f.getState() == Framework.ACTIVE) {
            List<Bundle> bundles = new ArrayList<>();
            for (Bundle bundle : context.getBundles()) {
                // 0 is the framework itself
                if (bundle.getBundleId() != 0) {
                    bundles.add(bundle);
                }
            }
            return bundles;
        }

        return new ArrayList<>();
    }

    private void startBundle(Bundle b) throws Exception {
        verifyBundle(b);
        b.start();
        try {
            startBundleInternal(b);
        } catch (Exception e) {
            try {
                b.stop();
            } catch (Exception ex) {
                log.error(ex.getMessage(), ex);
            }
            throw e;
        }
    }

    /**
     * An extension is started only when it was verified ({@link ExtensionService#verifyExtension}). The report of an
     * earlier verification is used while it is newer than the JAR and than the application; otherwise it is deleted
     * and the extension is verified again - a new version of the application can break extensions that were fine.
     *
     * @throws ExtensionVerificationException when the extension is invalid
     */
    private void verifyBundle(Bundle b) throws Exception {
        File jar = locationFile(b);
        File validReport = ExtensionVerifier.reportFile(jar, true);
        File invalidReport = ExtensionVerifier.reportFile(jar, false);
        long newest = Math.max(jar.lastModified(), applicationTimestamp());
        boolean valid = isFresh(validReport, newest);
        boolean invalid = isFresh(invalidReport, newest);

        if (valid && !invalid) {
            return;
        }
        if (invalid && !valid) {
            throw new ExtensionVerificationException(jar.getName(), Files.readString(invalidReport.toPath(), StandardCharsets.UTF_8));
        }
        // no report, an outdated one (deleted above) or contradicting ones
        deleteReports(jar);
        ExtensionVerification verification = extensionService.verifyExtension(b, jar);
        if (!verification.valid()) {
            throw new ExtensionVerificationException(jar.getName(), verification.report());
        }
    }

    /**
     * Whether the report exists and was written after {@code newest}. An outdated report is deleted.
     */
    private static boolean isFresh(File report, long newest) throws IOException {
        if (!report.exists()) {
            return false;
        }
        if (report.lastModified() > newest) {
            return true;
        }
        Files.deleteIfExists(report.toPath());
        return false;
    }

    private static void deleteReports(File jar) {
        try {
            ExtensionVerifier.deleteReports(jar);
        } catch (IOException e) {
            log.error("Failed to delete the verification report of {}: {}", jar.getName(), e.getMessage(), e);
        }
    }

    /**
     * When the application was built or installed: the newer of the creation and modification time of the WAR (the
     * folder it was unpacked to, or the classes when it is not packed). Reports older than that are void.
     */
    protected long applicationTimestamp() {
        Long timestamp = applicationTimestamp;
        if (timestamp == null) {
            timestamp = findApplicationTimestamp();
            applicationTimestamp = timestamp;
        }
        return timestamp;
    }

    private static long findApplicationTimestamp() {
        try {
            CodeSource source = OsgiServiceImpl.class.getProtectionDomain().getCodeSource();
            if (source == null) {
                return 0;
            }
            URL location = source.getLocation();
            String spec = location.toString();
            if (spec.startsWith("jar:")) {
                int end = spec.indexOf("!/");
                spec = spec.substring("jar:".length(), end >= 0 ? end : spec.length());
            }
            Path path = Path.of(URI.create(spec));
            // WEB-INF/classes and WEB-INF/lib belong to the web application folder
            for (Path parent = path; parent != null; parent = parent.getParent()) {
                if (parent.getFileName() != null && parent.getFileName().toString().equals("WEB-INF") && parent.getParent() != null) {
                    path = parent.getParent();
                    break;
                }
            }
            BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
            return Math.max(attributes.creationTime().toMillis(), attributes.lastModifiedTime().toMillis());
        } catch (Exception e) {
            log.warn("Cannot find out when the application was installed, verification reports are kept: {}", e.getMessage());
            return 0;
        }
    }

    @Override
    public ExtensionReport getVerificationReport(Bundle b) {
        File jar = locationFile(b);
        for (boolean valid : new boolean[]{true, false}) {
            File report = ExtensionVerifier.reportFile(jar, valid);
            if (report.exists()) {
                try {
                    return new ExtensionReport(valid, Files.readString(report.toPath(), StandardCharsets.UTF_8));
                } catch (IOException e) {
                    log.error("Failed to read the verification report {}: {}", report.getName(), e.getMessage(), e);
                }
            }
        }
        return null;
    }

    private void startBundleInternal(Bundle b) throws Exception {
        for (ServiceReference<?> sr : getServices(b, LingoLLMExtension.class)) {
            if (activeExtensions.containsKey(sr)) {
                continue;
            }
            LingoLLMExtension extension = (LingoLLMExtension) getContext().getService(sr);
            if (extension != null) {
                componentCallbacks.put(extension, ConcurrentHashMap.newKeySet());
                activeExtensions.put(sr, extension);
                try {
                    extension.onExtensionLoad(b, this, extensionService);
                } catch (RuntimeException e) {
                    // undo what the extension managed to register before it failed
                    activeExtensions.remove(sr);
                    try {
                        unloadExtension(b, sr, extension);
                    } catch (Exception ex) {
                        log.error(ex.getMessage(), ex);
                    }
                    throw e;
                }
                registerLocalizationBundles(extension);
                LoadedExtension loaded = new LoadedExtension(b, extension);
                loadedExtensions.put(sr, loaded);
                fireExtensionEvent(listener -> listener.extensionLoaded(b, extension));
            }
        }
    }

    private void stopBundleInternal(Bundle b) throws Exception {
        for (ServiceReference<?> sr : getServices(b, LingoLLMExtension.class)) {
            LingoLLMExtension extension = activeExtensions.remove(sr);
            if (extension != null) {
                unloadExtension(b, sr, extension);
            }
        }
    }

    private void unloadExtension(Bundle b, ServiceReference<?> sr, LingoLLMExtension extension) {
        Set<ComponentBinding> bindings = componentCallbacks.remove(extension);
        if (bindings != null) {
            for (ComponentBinding binding : bindings) {
                binding.release();
            }
        }
        // an extension that failed to load was never reported, so it is not reported as unloaded either
        boolean reported = loadedExtensions.remove(sr) != null;
        unregisterLocalizationBundles(extension);
        try {
            extension.onExtensionUnload(b, this, extensionService);
        } finally {
            getContext().ungetService(sr);
            if (reported) {
                fireExtensionEvent(listener -> listener.extensionUnloaded(b, extension));
            }
        }
    }

    /**
     * Adds the languages of an {@link ExtensionHasLocalization} extension; a bundle that is refused (its language is
     * taken) is logged and doesn't stop the extension.
     */
    private void registerLocalizationBundles(LingoLLMExtension extension) {
        if (!(extension instanceof ExtensionHasLocalization hasLocalization)) {
            return;
        }
        List<LocalizationBundle> registered = new ArrayList<>();
        try {
            for (LocalizationBundle bundle : hasLocalization.getLocalizationBundles()) {
                try {
                    localization.registerBundle(bundle);
                    registered.add(bundle);
                } catch (RuntimeException e) {
                    log.error("Extension {} localization bundle {} is not registered: {}", extension.getClass().getName(),
                            bundle.getLanguage(), e.getMessage());
                }
            }
        } catch (RuntimeException e) {
            log.error("Extension {} failed to provide its localization bundles: {}", extension.getClass().getName(), e.getMessage(), e);
        }
        localizationBundles.put(extension, registered);
    }

    private void unregisterLocalizationBundles(LingoLLMExtension extension) {
        List<LocalizationBundle> registered = localizationBundles.remove(extension);
        if (registered != null) {
            registered.forEach(localization::unregisterBundle);
        }
    }

    private void fireExtensionEvent(Consumer<ExtensionListener> event) {
        for (WeakReference<ExtensionListener> reference : extensionListeners) {
            ExtensionListener listener = reference.get();
            if (listener == null) {
                extensionListeners.remove(reference);
                continue;
            }
            try {
                event.accept(listener);
            } catch (Exception e) {
                log.error("Extension listener {} failed: {}", listener.getClass().getName(), e.getMessage(), e);
            }
        }
    }

    @Override
    public <T extends Component> T bindAttachableComponent(T component, Runnable callback, LingoLLMExtension extension) throws Exception {
        Set<ComponentBinding> bindings = componentCallbacks.get(extension);
        if (bindings == null) {
            throw new IllegalStateException("Extension " + extension.getClass().getName() + " is not loaded");
        }
        ComponentBinding binding = new ComponentBinding(component, callback);
        if (component.isAttached()) {
            bindings.add(binding);
        }
        binding.attachRegistration = component.addAttachListener(event -> {
            Set<ComponentBinding> current = componentCallbacks.get(extension);
            if (current != null) {
                current.add(binding);
            } else {
                // the extension was unloaded while the component was detached
                binding.release();
            }
        });
        binding.detachRegistration = component.addDetachListener(event -> {
            Set<ComponentBinding> current = componentCallbacks.get(extension);
            if (current != null) {
                current.remove(binding);
            }
        });
        return component;
    }

    private void refreshBundles() throws InterruptedException {
        CountDownLatch done = new CountDownLatch(1);
        f.adapt(FrameworkWiring.class).refreshBundles(null, event -> done.countDown());
        if (!done.await(FRAMEWORK_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            log.warn("OSGi bundle refresh did not finish in {} ms", FRAMEWORK_TIMEOUT_MS);
        }
    }

    private static String location(File jar) {
        return "file:" + jar.getPath();
    }

    private static File locationFile(Bundle b) {
        String s = b.getLocation().replaceAll("file:", "");
        s = s.replaceAll("\\\\", "/");
        return new File(s);
    }

    static String sanitizeJarName(String fileName) {
        String baseName = FilenameUtils.getBaseName(StringUtils.defaultIfBlank(fileName, "extension"));
        baseName = baseName.replaceAll("[^A-Za-z0-9._-]", "_").replaceAll("^\\.+", "");
        return StringUtils.defaultIfBlank(baseName, "extension") + ".jar";
    }

    private static String readSymbolicName(File jar) throws IOException, BundleException {
        try (JarFile jarFile = new JarFile(jar)) {
            Manifest manifest = jarFile.getManifest();
            String symbolicName = manifest != null ? manifest.getMainAttributes().getValue(Constants.BUNDLE_SYMBOLICNAME) : null;
            if (StringUtils.isBlank(symbolicName)) {
                throw new BundleException("Not an OSGi bundle: the manifest has no " + Constants.BUNDLE_SYMBOLICNAME);
            }
            return StringUtils.substringBefore(symbolicName, ";").trim();
        }
    }

    @Override
    public List<ServiceReference<?>> getServices(Bundle b, Class<?> service) throws InvalidSyntaxException {
        List<ServiceReference<?>> l = new ArrayList<>();
        String serviceName = service != null ? service.getName() : null;

        if (b != null) {
            ServiceReference<?>[] sa = context.getServiceReferences(serviceName, null);
            if (sa != null) {
                for (ServiceReference<?> s : sa) {
                    if (s.getBundle() == b) {
                        l.add(s);
                    }
                }
            }
        }

        return l;
    }

    @Override
    public List<ServiceReference<?>> getServices(Class<?> service) throws InvalidSyntaxException {
        ServiceReference<?>[] sa = context.getServiceReferences(service.getName(), null);
        List<ServiceReference<?>> ls = new ArrayList<>();

        if (sa != null) {
            ls.addAll(Arrays.asList(sa));
        }

        return ls;
    }

    @Override
    public synchronized void addSubscriber(ExtensionObserver observer) {
        WeakReference<ExtensionObserver> wes = new WeakReference<>(observer);
        observers.add(wes);
        observer.beforeServiceStarted(this);
        observer.afterServiceStarted(this);
    }

    @Override
    public synchronized void removeSubscriber(ExtensionObserver observer) {
        for (WeakReference<ExtensionObserver> wes : observers) {
            if (wes.get() != null && wes.get() == observer) {
                observers.remove(wes);
                observer.beforeServiceStopped(this);
                observer.afterServiceStopped(this);
                return;
            }
        }
    }

    @Override
    public void addExtensionListener(ExtensionListener listener) {
        synchronized (extensionListeners) {
            for (WeakReference<ExtensionListener> reference : extensionListeners) {
                if (reference.get() == listener) {
                    return;
                }
            }
            extensionListeners.add(new WeakReference<>(listener));
        }
    }

    @Override
    public void removeExtensionListener(ExtensionListener listener) {
        extensionListeners.removeIf(reference -> reference.get() == null || reference.get() == listener);
    }

    @Override
    public List<LoadedExtension> getLoadedExtensions() {
        return new ArrayList<>(loadedExtensions.values());
    }

    @Override
    public String getExtensionsPath() {
        return extensionPath;
    }

    protected synchronized void onBeforeStart() {
        runObserverMethod(ExtensionObserver::beforeServiceStarted);
    }

    protected synchronized void onStart() {
        runObserverMethod(ExtensionObserver::afterServiceStarted);
    }

    protected synchronized void onBeforeStop() {
        runObserverMethod(ExtensionObserver::beforeServiceStopped);
    }

    protected synchronized void onStop() {
        runObserverMethod(ExtensionObserver::afterServiceStopped);
    }

    private synchronized void runObserverMethod(ObserverCallback method) {
        Set<WeakReference<ExtensionObserver>> remSet = new HashSet<>();
        for (WeakReference<ExtensionObserver> observer : observers) {
            if (observer.get() != null)
                method.run(Objects.requireNonNull(observer.get()), this);
            else
                remSet.add(observer);
        }
        observers.removeAll(remSet);
    }

    protected interface ObserverCallback {

        void run(ExtensionObserver observer, OsgiService service);

    }

    /**
     * A cleanup callback of an extension, bound to a component while the component is attached.
     */
    private static final class ComponentBinding {

        private final Component component;
        private final Runnable callback;
        private Registration attachRegistration;
        private Registration detachRegistration;

        private ComponentBinding(Component component, Runnable callback) {
            this.component = component;
            this.callback = callback;
        }

        /**
         * Runs the callback once, in the session of the component's UI, and stops tracking the component.
         */
        private void release() {
            component.getUI().ifPresentOrElse(ui -> ui.access(this::releaseNow), this::releaseNow);
        }

        private void releaseNow() {
            try {
                if (attachRegistration != null) {
                    attachRegistration.remove();
                }
                if (detachRegistration != null) {
                    detachRegistration.remove();
                }
                callback.run();
            } catch (Exception e) {
                log.error(e.getMessage(), e);
            }
        }
    }
}

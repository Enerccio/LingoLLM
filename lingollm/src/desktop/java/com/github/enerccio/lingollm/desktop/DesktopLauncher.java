package com.github.enerccio.lingollm.desktop;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.Method;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Desktop entry point: runs LingoLLM on the bundled Jetty ({@code jetty-home}, same server as the Docker image) in
 * this JVM, listening on localhost only, and opens it in the browser.
 * <p>
 * Server files are in a {@code server/} directory ({@code jetty-home/} and {@code lingollm.war}) next to the launcher
 * jar's directory or above it - jpackage places app content differently per OS. The writable Jetty base (module
 * configuration, deployment descriptor, extracted webapp) lives in {@code <home>/.lingollm/desktop}, so the
 * installation itself can be read-only (e.g. inside a macOS app bundle).
 * <p>
 * Deliberately depends on the JDK only - Jetty is started through {@code start.jar} by reflection, so this class can be
 * compiled without Jetty on the classpath and never ends up in the WAR.
 */
public final class DesktopLauncher {

    private static final String APP_NAME = "LingoLLM";
    private static final int DEFAULT_PORT = 8765;
    private static final Duration STARTUP_TIMEOUT = Duration.ofMinutes(3);
    private static final String MODULES = "server,http,ee11-deploy,ee11-webapp,ee11-annotations,ee11-websocket-jakarta,ee11-jsp";

    private final Options options;
    private final Path serverDir;
    private URI uri;
    private TrayIcon trayIcon;
    private Frame fallbackWindow;

    private DesktopLauncher(Options options, Path serverDir) {
        this.options = options;
        this.serverDir = serverDir;
    }

    public static void main(String[] args) throws Exception {
        Options options;
        try {
            options = Options.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.err.println();
            System.err.println(Options.USAGE);
            System.exit(2);
            return;
        }
        if (options.help) {
            System.out.println(Options.USAGE);
            return;
        }
        try {
            new DesktopLauncher(options, serverDirectory()).run();
        } catch (IllegalStateException e) {
            // expected startup problems (port taken, broken installation) - a message is enough
            System.err.println("LingoLLM cannot start: " + e.getMessage());
            System.exit(1);
        }
    }

    private void run() throws Exception {
        if (options.home != null) {
            // LingoLLM keeps its data in ${user.home}/.lingollm, same as -Duser.home in the Docker setup
            System.setProperty("user.home", options.home.toAbsolutePath().toString());
        }
        if (options.reinitializeSql != null) {
            // read by Configuration in the application, which runs in this JVM
            System.setProperty("reinitialize-with-sql", options.reinitializeSql.toAbsolutePath().toString());
        }
        Path dataDir = Path.of(System.getProperty("user.home"), ".lingollm");
        Path desktopDir = dataDir.resolve("desktop");
        Files.createDirectories(desktopDir);
        redirectOutput(desktopDir.resolve("logs"));

        Path jettyHome = serverDir.resolve("jetty-home");
        Path war = serverDir.resolve("lingollm.war");
        require(jettyHome.resolve("start.jar"), "Jetty");
        require(war, "web application");

        int port = options.port;
        uri = uri(options.host, port);
        if (!isPortFree(options.host, port)) {
            if (isLingoLLM(uri)) {
                log("LingoLLM is already running at " + uri);
                if (options.openBrowser) {
                    openBrowser();
                }
                return;
            }
            if (options.portExplicit) {
                throw new IllegalStateException("Port " + port + " is used by another application");
            }
            port = freePort(options.host);
            uri = uri(options.host, port);
            log("Port " + options.port + " is used by another application, using " + port);
        }
        if (!isLoopback(options.host)) {
            log("WARNING: listening on " + options.host + " makes LingoLLM reachable from the network without HTTPS");
        }

        Path base = prepareJettyBase(desktopDir.resolve("jetty-base"), war);
        showControls("Starting " + APP_NAME + "...");
        log("Starting " + APP_NAME + " (data in " + dataDir + ")");

        startJetty(jettyHome, base, options.host, port);

        if (!waitUntilReady(uri)) {
            log("LingoLLM did not start in time, see the log in " + desktopDir.resolve("logs"));
            showControls("Failed to start - see the log");
            return;
        }
        log("LingoLLM is running at " + uri);
        showControls(APP_NAME + " is running at " + uri);
        if (options.openBrowser) {
            openBrowser();
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // jetty
    // ------------------------------------------------------------------------------------------------------------

    /**
     * Writes the Jetty base: enabled modules and the webapp descriptor. The webapp is extracted once into
     * {@code work/} and reused while the WAR doesn't change.
     */
    static Path prepareJettyBase(Path base, Path war) throws IOException {
        Files.createDirectories(base.resolve("start.d"));
        Files.createDirectories(base.resolve("webapps"));
        // the deployer warns when it's missing
        Files.createDirectories(base.resolve("environments"));
        Path work = base.resolve("work").resolve("lingollm");
        Files.createDirectories(work);

        // properties in start.d are not applied when started in-process, host and port are passed as arguments
        Files.writeString(base.resolve("start.d").resolve("lingollm.ini"), "--module=" + MODULES + "\n", StandardCharsets.UTF_8);
        Files.writeString(base.resolve("webapps").resolve("ROOT.xml"), """
                <?xml version="1.0"?>
                <!DOCTYPE Configure PUBLIC "-//Jetty//Configure//EN" "https://jetty.org/configure_10_0.dtd">
                <Configure class="org.eclipse.jetty.ee11.webapp.WebAppContext">
                  <Set name="contextPath">/</Set>
                  <Set name="war">%s</Set>
                  <Set name="tempDirectory">%s</Set>
                  <Set name="tempDirectoryPersistent">true</Set>
                </Configure>
                """.formatted(xml(war.toAbsolutePath().toString()), xml(work.toAbsolutePath().toString())), StandardCharsets.UTF_8);
        return base;
    }

    private static void startJetty(Path jettyHome, Path base, String host, int port) throws Exception {
        URLClassLoader loader = new URLClassLoader(new URL[]{jettyHome.resolve("start.jar").toUri().toURL()},
                DesktopLauncher.class.getClassLoader());
        Class<?> main = loader.loadClass("org.eclipse.jetty.start.Main");
        Method mainMethod = main.getMethod("main", String[].class);
        String[] args = {
                "jetty.home=" + jettyHome.toAbsolutePath(),
                "jetty.base=" + base.toAbsolutePath(),
                "jetty.http.host=" + host,
                "jetty.http.port=" + port,
                "jetty.deploy.scanInterval=0",
        };
        // the WAR has duplicate classes in its jars (dependency clean-up pending), which the annotation scanner
        // reports in thousands of warnings on every start
        System.setProperty("org.eclipse.jetty.annotations.AnnotationParser.LEVEL", "ERROR");
        Thread.currentThread().setContextClassLoader(loader);
        // returns once the server is started, Jetty's threads keep the JVM running
        mainMethod.invoke(null, (Object) args);
    }

    private static boolean waitUntilReady(URI uri) {
        long deadline = System.nanoTime() + STARTUP_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            if (isLingoLLM(uri)) {
                return true;
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private static boolean isLingoLLM(URI uri) {
        try {
            HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setConnectTimeout(1000);
            connection.setReadTimeout(5000);
            if (connection.getResponseCode() != 200) {
                return false;
            }
            try (InputStream in = connection.getInputStream()) {
                String page = new String(in.readNBytes(64 * 1024), StandardCharsets.UTF_8);
                return page.contains("<title>" + APP_NAME + "</title>");
            }
        } catch (IOException e) {
            return false;
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // desktop integration
    // ------------------------------------------------------------------------------------------------------------

    private void showControls(String status) {
        if (!options.controls || GraphicsEnvironment.isHeadless()) {
            return;
        }
        EventQueue.invokeLater(() -> {
            try {
                if (SystemTray.isSupported()) {
                    showTray(status);
                } else {
                    showWindow(status);
                }
            } catch (Exception e) {
                log("Cannot show desktop controls: " + e.getMessage());
            }
        });
    }

    private void showTray(String status) throws AWTException {
        if (trayIcon == null) {
            PopupMenu menu = new PopupMenu();
            MenuItem open = new MenuItem("Open " + APP_NAME);
            open.addActionListener(_ -> openBrowser());
            MenuItem quit = new MenuItem("Quit " + APP_NAME);
            quit.addActionListener(_ -> quit());
            menu.add(open);
            menu.addSeparator();
            menu.add(quit);
            trayIcon = new TrayIcon(icon(64), status, menu);
            trayIcon.setImageAutoSize(true);
            trayIcon.addActionListener(_ -> openBrowser());
            SystemTray.getSystemTray().add(trayIcon);
        }
        trayIcon.setToolTip(status);
    }

    /**
     * Desktops without a system tray (some Linux environments) get a small window instead.
     */
    private void showWindow(String status) {
        if (fallbackWindow == null) {
            fallbackWindow = new Frame(APP_NAME);
            fallbackWindow.setIconImage(icon(64));
            fallbackWindow.setLayout(new BorderLayout(8, 8));
            Label label = new Label(status, Label.CENTER);
            label.setName("status");
            Panel buttons = new Panel(new FlowLayout());
            Button open = new Button("Open in browser");
            open.addActionListener(_ -> openBrowser());
            Button quit = new Button("Quit");
            quit.addActionListener(_ -> quit());
            buttons.add(open);
            buttons.add(quit);
            fallbackWindow.add(label, BorderLayout.CENTER);
            fallbackWindow.add(buttons, BorderLayout.SOUTH);
            fallbackWindow.addWindowListener(new java.awt.event.WindowAdapter() {
                @Override
                public void windowClosing(java.awt.event.WindowEvent e) {
                    quit();
                }
            });
            fallbackWindow.setSize(420, 140);
            fallbackWindow.setLocationRelativeTo(null);
            fallbackWindow.setVisible(true);
        }
        for (Component component : fallbackWindow.getComponents()) {
            if (component instanceof Label label) {
                label.setText(status);
            }
        }
    }

    private void quit() {
        log("Quitting " + APP_NAME);
        // Jetty stops gracefully from its shutdown hook
        new Thread(() -> System.exit(0), "quit").start();
    }

    private void openBrowser() {
        if (uri == null) {
            return;
        }
        try {
            if (!GraphicsEnvironment.isHeadless() && Desktop.isDesktopSupported()
                    && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(uri);
                return;
            }
        } catch (Exception e) {
            log("Desktop browser integration failed: " + e.getMessage());
        }
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        List<String> command = os.contains("win") ? List.of("rundll32", "url.dll,FileProtocolHandler", uri.toString())
                : os.contains("mac") ? List.of("open", uri.toString())
                : List.of("xdg-open", uri.toString());
        try {
            new ProcessBuilder(command).inheritIO().start();
        } catch (IOException e) {
            log("Open " + uri + " in your browser");
        }
    }

    static BufferedImage icon(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(0x2b, 0x4c, 0x7e));
        g.fillRoundRect(0, 0, size, size, size / 4, size / 4);
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SERIF, Font.BOLD, (int) (size * 0.72)));
        FontMetrics metrics = g.getFontMetrics();
        String letter = "L";
        g.drawString(letter, (size - metrics.stringWidth(letter)) / 2, (size - metrics.getHeight()) / 2 + metrics.getAscent());
        g.dispose();
        return image;
    }

    // ------------------------------------------------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------------------------------------------------

    /**
     * The {@code server/} directory: {@code -Dlingollm.serverDir}, or the nearest one next to the launcher jar's
     * directory or one of its parents (portable folder: {@code app/../server}, macOS app: {@code Contents/server},
     * Windows/Linux app images: the image root or {@code lib/}).
     */
    private static Path serverDirectory() throws URISyntaxException {
        String override = System.getProperty("lingollm.serverDir");
        if (override != null) {
            return Path.of(override);
        }
        Path location = Path.of(DesktopLauncher.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Path dir = Files.isDirectory(location) ? location : location.getParent();
        for (Path candidate = dir; candidate != null; candidate = candidate.getParent()) {
            Path server = candidate.resolve("server");
            if (Files.exists(server.resolve("jetty-home").resolve("start.jar"))) {
                return server;
            }
            if (candidate.getNameCount() < dir.getNameCount() - 3) {
                break;
            }
        }
        throw new IllegalStateException("LingoLLM server files (server/jetty-home, server/lingollm.war) not found near " + dir);
    }

    private static void require(Path path, String what) {
        if (!Files.exists(path)) {
            throw new IllegalStateException("Bundled " + what + " not found: " + path);
        }
    }

    /**
     * A port is taken when something accepts connections on it. Binding a test socket would also report ports of a
     * just stopped instance (TCP TIME_WAIT) as taken, while Jetty binds them fine with SO_REUSEADDR.
     */
    static boolean isPortFree(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(InetAddress.getByName(host), port), 500);
            return false;
        } catch (IOException e) {
            return true;
        }
    }

    private static int freePort(String host) throws IOException {
        try (ServerSocket socket = new ServerSocket()) {
            socket.bind(new InetSocketAddress(InetAddress.getByName(host), 0));
            return socket.getLocalPort();
        }
    }

    private static URI uri(String host, int port) {
        return URI.create("http://" + (host.contains(":") ? "[" + host + "]" : host) + ":" + port + "/");
    }

    private static boolean isLoopback(String host) {
        try {
            return InetAddress.getByName(host).isLoopbackAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }

    private static String xml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /**
     * Desktop launches have no console - everything also goes to {@code logs/lingollm.log} (previous run kept as
     * {@code lingollm.log.1}).
     */
    private static void redirectOutput(Path logDir) throws IOException {
        Files.createDirectories(logDir);
        Path log = logDir.resolve("lingollm.log");
        if (Files.exists(log)) {
            Files.move(log, logDir.resolve("lingollm.log.1"), StandardCopyOption.REPLACE_EXISTING);
        }
        OutputStream file = new BufferedOutputStream(Files.newOutputStream(log), 8192) {
            @Override
            public synchronized void write(byte[] b, int off, int len) throws IOException {
                super.write(b, off, len);
                flush();
            }
        };
        System.setOut(new PrintStream(new TeeOutputStream(System.out, file), true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(new TeeOutputStream(System.err, file), true, StandardCharsets.UTF_8));
    }

    private static void log(String message) {
        System.out.println(LocalDateTime.now().withNano(0) + " [launcher] " + message);
    }

    private static final class TeeOutputStream extends OutputStream {
        private final OutputStream first;
        private final OutputStream second;

        TeeOutputStream(OutputStream first, OutputStream second) {
            this.first = first;
            this.second = second;
        }

        @Override
        public synchronized void write(int b) throws IOException {
            first.write(b);
            second.write(b);
        }

        @Override
        public synchronized void write(byte[] b, int off, int len) throws IOException {
            first.write(b, off, len);
            second.write(b, off, len);
        }

        @Override
        public synchronized void flush() throws IOException {
            first.flush();
            second.flush();
        }
    }

    static final class Options {
        static final String USAGE = """
                Usage: lingollm [options]
                  --port <port>     port to listen on (default %d, a free one is picked when taken)
                  --host <address>  address to listen on (default 127.0.0.1, others expose the app to the network)
                  --home <dir>      directory that holds .lingollm data (default: your home directory)
                  --reinitialize-with-sql <file>
                                    DELETES all LingoLLM data (except the launcher's own files), creates the database again
                                    and runs the SQL file on it; give it for one start only
                  --no-browser      don't open the browser
                  --no-tray         no tray icon / window, stop with Ctrl+C
                  --help            show this help
                """.formatted(DEFAULT_PORT);

        int port = DEFAULT_PORT;
        boolean portExplicit;
        String host = "127.0.0.1";
        Path home;
        Path reinitializeSql;
        boolean openBrowser = true;
        boolean controls = true;
        boolean help;

        static Options parse(String[] args) {
            Options options = new Options();
            List<String> list = new ArrayList<>(List.of(args));
            for (int i = 0; i < list.size(); i++) {
                String arg = list.get(i);
                switch (arg) {
                    case "--port" -> {
                        try {
                            options.port = Integer.parseInt(value(list, ++i, arg));
                        } catch (NumberFormatException e) {
                            throw new IllegalArgumentException("Invalid port: " + list.get(i));
                        }
                        if (options.port < 1 || options.port > 65535) {
                            throw new IllegalArgumentException("Invalid port: " + options.port);
                        }
                        options.portExplicit = true;
                    }
                    case "--host" -> options.host = value(list, ++i, arg);
                    case "--home" -> options.home = Path.of(value(list, ++i, arg));
                    case "--reinitialize-with-sql" -> options.reinitializeSql = Path.of(value(list, ++i, arg));
                    case "--no-browser" -> options.openBrowser = false;
                    case "--no-tray" -> options.controls = false;
                    case "--help", "-h" -> options.help = true;
                    // macOS passes a process serial number to apps started from Finder
                    default -> {
                        if (!arg.startsWith("-psn_")) {
                            throw new IllegalArgumentException("Unknown option: " + arg);
                        }
                    }
                }
            }
            return options;
        }

        private static String value(List<String> list, int i, String option) {
            if (i >= list.size()) {
                throw new IllegalArgumentException("Missing value for " + option);
            }
            return list.get(i);
        }
    }
}

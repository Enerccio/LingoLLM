package com.github.enerccio.lingollm.instruct;

import com.github.enerccio.lingollm.instruct.fixture.verify.VerifyFixtures;
import com.github.enerccio.lingollm.instruct.verify.ExtensionVerification;
import com.github.enerccio.lingollm.instruct.verify.ExtensionVerifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.osgi.framework.Bundle;
import org.osgi.framework.Version;

import java.io.File;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The extension verifier against the extensions of {@link VerifyFixtures}: their class files are packed into a JAR, the
 * bundle is a stand-in that serves the entries of that JAR.
 */
class ExtensionVerifierTest {

    @TempDir
    File folder;

    private ExtensionVerification verify(String extension) throws Exception {
        File jar = jarOf(extension);
        Bundle bundle = (Bundle) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Bundle.class}, (proxy, method, args) ->
                switch (method.getName()) {
                    case "getEntry" -> URI.create("jar:" + jar.toURI() + "!/" + args[0]).toURL();
                    case "getSymbolicName" -> "test." + extension;
                    case "getVersion" -> Version.parseVersion("1.0.0");
                    case "loadClass" -> throw new ClassNotFoundException((String) args[0]);
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        return new ExtensionVerifier(getClass().getClassLoader()).verify(bundle, jar);
    }

    /**
     * A JAR with the fixture class {@code VerifyFixtures$<extension>} and the classes nested in it (the decorators).
     */
    private File jarOf(String extension) throws Exception {
        Path classes = Path.of(VerifyFixtures.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                .resolve(VerifyFixtures.class.getPackageName().replace('.', '/'));
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        File jar = new File(folder, extension.toLowerCase() + ".jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar.toPath()), manifest);
             Stream<Path> files = Files.list(classes)) {
            for (Path file : files.toList()) {
                String name = file.getFileName().toString();
                if (name.equals("VerifyFixtures$" + extension + ".class") || name.startsWith("VerifyFixtures$" + extension + "$")) {
                    out.putNextEntry(new ZipEntry(VerifyFixtures.class.getPackageName().replace('.', '/') + "/" + name));
                    out.write(Files.readAllBytes(file));
                    out.closeEntry();
                }
            }
        }
        return jar;
    }

    @Test
    void extensionThatAsksForWhatExistsIsValid() throws Exception {
        ExtensionVerification result = verify("Valid");

        assertThat(result.errors()).isEmpty();
        assertThat(result.valid()).isTrue();
        assertThat(result.checked()).containsExactlyInAnyOrder("VerifyTarget.render <- VerifyFixtures$Valid$1", "VerifyTarget$Card.edit <- VerifyFixtures$Valid$2");
        // optional accesses of things that aren't there
        assertThat(result.warnings()).hasSize(2)
                .anyMatch(w -> w.contains("no argument 'absent'"))
                .anyMatch(w -> w.contains("no object local variable 'absent'"));
        assertThat(result.report()).contains("Result: VALID").contains("Extension: valid.jar");
    }

    @Test
    void targetsThatDontExistAreErrors() throws Exception {
        ExtensionVerification result = verify("BrokenTargets");

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).hasSize(5)
                .anyMatch(e -> e.contains("class com.github.enerccio.lingollm.instruct.fixture.verify.NoSuchClass does not exist"))
                .anyMatch(e -> e.contains("NotExtendable is not @Extendable"))
                .anyMatch(e -> e.contains("has no instrumented method nothing"))
                .anyMatch(e -> e.contains("has no instrumented method staticMethod"))
                .anyMatch(e -> e.contains("VerifyChild has no instrumented method render")
                        && e.contains("declared by com.github.enerccio.lingollm.instruct.fixture.verify.VerifyTarget"));
        assertThat(result.report()).contains("Result: INVALID").contains("Errors (5):");
    }

    @Test
    void argumentsLocalsFieldsAndMethodsAreCheckedAgainstTheApplication() throws Exception {
        ExtensionVerification result = verify("BrokenAccess");

        assertThat(result.valid()).isFalse();
        assertThat(result.errors())
                // arguments
                .anyMatch(e -> e.contains("onMethodEnter line") && e.contains("no argument 'nme'") && e.contains("name"))
                .anyMatch(e -> e.contains("argument 'count' is [int], the decorator asks for java.lang.Integer"))
                .anyMatch(e -> e.contains("no argument 'cnt' to replace"))
                // locals
                .anyMatch(e -> e.contains("local variable 'out' is asked in onMethodEnter"))
                .anyMatch(e -> e.contains("no object local variable 'outt'"))
                .anyMatch(e -> e.contains("local variable 'out' is [java.lang.StringBuilder], the decorator asks for java.lang.String"))
                // return values
                .anyMatch(e -> e.contains("onMethodEnter line") && e.contains("return value is asked in onMethodEnter"))
                .anyMatch(e -> e.contains("the method returns [java.lang.String], the decorator asks for java.lang.Integer"))
                .anyMatch(e -> e.contains("VerifyTarget$Card.edit") && e.contains("the method is void"))
                // fields
                .anyMatch(e -> e.contains("VerifyTarget has no field 'titel'"))
                .anyMatch(e -> e.contains("field 'items' of") && e.contains("asks for java.lang.String"))
                .anyMatch(e -> e.contains("VerifyTarget has no field 'nothing'"))
                // methods
                .anyMatch(e -> e.contains("VerifyTarget has no method nothing()"))
                .anyMatch(e -> e.contains("has no method helper(java.lang.String), only [helper(java.lang.String, int)]"))
                .anyMatch(e -> e.contains("method 'helper'") && e.contains("returns java.lang.String") && e.contains("asks for java.lang.Integer"))
                .hasSize(15);
        assertThat(result.warnings())
                // optional access with the wrong type
                .anyMatch(w -> w.contains("argument 'name' is [java.lang.String]") && w.contains("asks for java.lang.Integer"))
                .anyMatch(w -> w.contains("local variable 'out' is [java.lang.StringBuilder]"))
                // declared Object, asked String: works when the value is a String at run time
                .anyMatch(w -> w.contains("local variable 'result' is declared [java.lang.Object]") && w.contains("at run time"))
                // an object nothing is known about
                .anyMatch(w -> w.contains("'anything'") && w.contains("could not be determined"));
    }

    @Test
    void registrationsThatCantBeFollowedAreWarnings() throws Exception {
        ExtensionVerification result = verify("Dynamic");

        assertThat(result.valid()).isTrue();
        assertThat(result.warnings()).hasSize(2)
                .anyMatch(w -> w.contains("not a constant"))
                .anyMatch(w -> w.contains("decorator registered for VerifyTarget.render could not be determined"));
    }

    @Test
    void jarThatDiffersFromTheBundleIsInvalid() throws Exception {
        File jar = jarOf("Valid");
        Bundle bundle = (Bundle) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Bundle.class},
                (proxy, method, args) -> method.getName().equals("getEntry") ? null : null);

        ExtensionVerification result = new ExtensionVerifier(getClass().getClassLoader()).verify(bundle, jar);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).isNotEmpty().allMatch(e -> e.contains("not in the installed bundle"));
    }

    @Test
    void reportIsWrittenNextToTheJarAndReplacesTheOppositeOne() throws Exception {
        File jar = jarOf("Valid");
        ExtensionVerification valid = verify("Valid");
        ExtensionVerification invalid = verify("BrokenTargets");

        ExtensionVerifier.writeReport(jar, invalid);
        assertThat(ExtensionVerifier.reportFile(jar, false)).exists().isEqualTo(new File(folder, "valid.invalid"));
        assertThat(ExtensionVerifier.reportFile(jar, true)).doesNotExist();

        ExtensionVerifier.writeReport(jar, valid);
        assertThat(ExtensionVerifier.reportFile(jar, true)).exists().hasContent(valid.report());
        assertThat(ExtensionVerifier.reportFile(jar, false)).doesNotExist();

        ExtensionVerifier.deleteReports(jar);
        assertThat(ExtensionVerifier.reportFile(jar, true)).doesNotExist();
    }
}

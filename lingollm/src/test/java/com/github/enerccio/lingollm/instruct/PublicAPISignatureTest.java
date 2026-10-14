package com.github.enerccio.lingollm.instruct;

import com.github.enerccio.lingollm.instruct.PublicAPISignatures.Method;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.pool.TypePool;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Every instrumented method of an {@code @Extendable} class declares the signature plugins see ({@code @PublicAPI}),
 * and the declaration is what the compiled class has. The classes are read with ByteBuddy's type pool, so nothing is
 * loaded. A failure names the method and prints the annotation to put on it.
 */
class PublicAPISignatureTest {

    private static final Pattern VERSION = Pattern.compile("\\d+\\.\\d+\\.\\d+");

    private final TypePool pool = PublicAPISignatures.pool();
    private final ClassFileLocator locator = ClassFileLocator.ForClassLoader.of(PublicAPISignatures.class.getClassLoader());

    @Test
    void foundTheExtendableClasses() {
        // guards the scan itself: an empty list would make the other checks pass for nothing
        assertTrue(PublicAPISignatures.extendableTypes(pool).size() > 10);
    }

    @Test
    void everyInstrumentedMethodIsAnnotated() {
        List<String> problems = new ArrayList<>();
        for (TypeDescription type : PublicAPISignatures.extendableTypes(pool)) {
            for (Method method : PublicAPISignatures.methods(type, locator)) {
                if (method.declared() == null) {
                    problems.add(method.where() + " has no @PublicAPI, add:\n" + PublicAPISignatures.render("<version>", method.actual()));
                }
            }
        }
        assertNoProblems("instrumented methods without @PublicAPI", problems);
    }

    @Test
    void declaredSignaturesMatchTheCompiledClasses() {
        List<String> problems = new ArrayList<>();
        for (TypeDescription type : PublicAPISignatures.extendableTypes(pool)) {
            for (Method method : PublicAPISignatures.methods(type, locator)) {
                if (method.declared() == null) {
                    continue; // reported by everyInstrumentedMethodIsAnnotated
                }
                if (!method.actual().equals(method.declared())) {
                    problems.add(method.where() + " differs from its @PublicAPI\n  declared: " + method.declared()
                            + "\n  compiled: " + method.actual() + "\n  expected annotation:\n"
                            + PublicAPISignatures.render(method.from(), method.actual()));
                }
            }
        }
        assertNoProblems("@PublicAPI that does not describe the method", problems);
    }

    @Test
    void parametersAreNamed() {
        // the context names an argument "arg0" without the MethodParameters attribute (-parameters)
        List<String> problems = new ArrayList<>();
        for (TypeDescription type : PublicAPISignatures.extendableTypes(pool)) {
            for (Method method : PublicAPISignatures.methods(type, locator)) {
                if (!method.namedParameters()) {
                    problems.add(method.where() + " is compiled without parameter names");
                }
            }
        }
        assertNoProblems("methods compiled without -parameters", problems);
    }

    @Test
    void fromIsAVersion() {
        List<String> problems = new ArrayList<>();
        for (TypeDescription type : PublicAPISignatures.extendableTypes(pool)) {
            for (Method method : PublicAPISignatures.methods(type, locator)) {
                if (method.from() != null && !VERSION.matcher(method.from()).matches()) {
                    problems.add(method.where() + " has from = \"" + method.from() + "\", expected major.minor.patch");
                }
            }
        }
        assertNoProblems("@PublicAPI(from) that is not a version", problems);
    }

    @Test
    void noAnnotationOnMethodsThatAreNotInstrumented() {
        List<String> problems = new ArrayList<>();
        for (TypeDescription type : PublicAPISignatures.allTypes(pool)) {
            problems.addAll(PublicAPISignatures.annotatedButNotInstrumented(type));
        }
        assertNoProblems("@PublicAPI on constructors, static, synthetic or bodiless methods, or in classes that are not @Extendable", problems);
    }

    private static void assertNoProblems(String what, List<String> problems) {
        if (!problems.isEmpty()) {
            fail(problems.size() + " " + what + ":\n" + String.join("\n", problems));
        }
    }
}

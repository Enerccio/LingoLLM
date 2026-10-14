package com.github.enerccio.lingollm.instruct;

import com.github.enerccio.lingollm.domain.traits.Extendable;
import com.github.enerccio.lingollm.domain.traits.PublicAPI;
import net.bytebuddy.description.annotation.AnnotationDescription;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.ParameterDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.pool.TypePool;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * Reads the signatures that plugins see on the {@code @Extendable} classes of the application - from the compiled
 * classes, through ByteBuddy's type pool (nothing is loaded or initialized) and the local variable table - and the
 * ones declared by {@link PublicAPI}. Used by {@code PublicAPISignatureTest}.
 */
final class PublicAPISignatures {

    private static final String AJC_PREFIX = "ajc$";

    private PublicAPISignatures() {
    }

    /**
     * A name and the erased type ({@code a.B$C}, {@code java.lang.String[]}, {@code int}).
     */
    record Named(String name, String type) {
    }

    record Signature(String returns, List<Named> parameters, List<Named> locals) {
    }

    /**
     * A method of an {@code @Extendable} class that is instrumented, with the signature the context gets and the
     * one declared by its {@link PublicAPI} ({@code null} without the annotation).
     */
    record Method(String owner, String name, String descriptor, Signature actual, boolean namedParameters,
                  String from, Signature declared) {

        String where() {
            return owner + "." + name + descriptor;
        }
    }

    static Path classesRoot() {
        try {
            return Path.of(Extendable.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    static TypePool pool() {
        return TypePool.Default.of(ClassFileLocator.ForClassLoader.of(Extendable.class.getClassLoader()));
    }

    /**
     * All classes of the application, nested ones included.
     */
    static List<TypeDescription> allTypes(TypePool pool) {
        Path root = classesRoot();
        try (Stream<Path> files = Files.walk(root.resolve("com/github/enerccio"))) {
            return files
                    .filter(file -> file.toString().endsWith(".class"))
                    .map(file -> root.relativize(file).toString())
                    .map(name -> name.substring(0, name.length() - ".class".length()).replace('/', '.'))
                    .sorted()
                    .map(name -> pool.describe(name).resolve())
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static boolean isExtendable(TypeDescription type) {
        return type.getDeclaredAnnotations().isAnnotationPresent(Extendable.class);
    }

    /**
     * All classes of the application annotated {@code @Extendable}, nested ones included.
     */
    static List<TypeDescription> extendableTypes(TypePool pool) {
        return allTypes(pool).stream().filter(PublicAPISignatures::isExtendable).collect(Collectors.toList());
    }

    /**
     * Every method of the type that the agent instruments and a decorator can reach: not a constructor, not static,
     * not synthetic (what {@code RuntimeInstrumentationInitializer} matches) and with a body.
     */
    static List<Method> methods(TypeDescription type, ClassFileLocator locator) {
        ClassNode node = readClass(type, locator);
        List<Method> result = new ArrayList<>();
        for (MethodDescription.InDefinedShape method : type.getDeclaredMethods()) {
            if (!isInstrumented(method)) {
                continue;
            }
            MethodNode body = node.methods.stream()
                    .filter(candidate -> candidate.name.equals(method.getInternalName()) && candidate.desc.equals(method.getDescriptor()))
                    .findFirst().orElseThrow();
            boolean named = method.getParameters().stream().allMatch(ParameterDescription::isNamed);
            Signature actual = new Signature(
                    name(method.getReturnType().asErasure()),
                    parameters(method),
                    locals(body));
            AnnotationDescription declared = method.getDeclaredAnnotations().stream()
                    .filter(annotation -> annotation.getAnnotationType().represents(PublicAPI.class))
                    .findFirst().orElse(null);
            result.add(new Method(type.getName(), method.getInternalName(), method.getDescriptor(), actual, named,
                    declared == null ? null : string(declared, "from"),
                    declared == null ? null : declared(declared)));
        }
        return result;
    }

    /**
     * Methods carrying {@link PublicAPI} that the agent doesn't instrument (static, synthetic, abstract...) or that
     * are in a class that is not {@code @Extendable}: the annotation describes something a decorator can never reach.
     */
    static List<String> annotatedButNotInstrumented(TypeDescription type) {
        List<String> result = new ArrayList<>();
        for (MethodDescription.InDefinedShape method : type.getDeclaredMethods()) {
            // javac copies the annotations of a method to its bridge method
            if (!method.isBridge() && (!isExtendable(type) || !isInstrumented(method)) && method.getDeclaredAnnotations().stream()
                    .anyMatch(annotation -> annotation.getAnnotationType().represents(PublicAPI.class))) {
                result.add(type.getName() + "." + method.getInternalName() + method.getDescriptor());
            }
        }
        return result;
    }

    static boolean isInstrumented(MethodDescription method) {
        return !method.isConstructor() && !method.isTypeInitializer()
                && !method.isStatic() && !method.isSynthetic()
                && !method.isAbstract() && !method.isNative()
                && !method.getInternalName().startsWith(AJC_PREFIX)
                && !isWovenSerializationSupport(method);
    }

    /**
     * AspectJ adds {@code readResolve()} to the {@code @Configurable} classes (they implement the aspect's
     * {@code ConfigurableDeserializationSupport}). It is not in the source, so it cannot carry an annotation.
     */
    private static boolean isWovenSerializationSupport(MethodDescription method) {
        return method.getInternalName().equals("readResolve") && method.getParameters().isEmpty()
                && method.getDeclaringType().asErasure().getInterfaces().asErasures().stream()
                .anyMatch(type -> type.getName().startsWith("org.springframework.beans.factory.aspectj."));
    }

    /**
     * {@code a.B$C}, {@code java.lang.String[]}, {@code int}: nested classes with {@code $} like
     * {@code Type.getClassName()} of ASM, arrays with brackets.
     */
    private static String name(TypeDescription type) {
        return type.isArray() ? name(type.getComponentType().asErasure()) + "[]" : type.getName();
    }

    private static List<Named> parameters(MethodDescription.InDefinedShape method) {
        List<Named> result = new ArrayList<>();
        for (ParameterDescription parameter : method.getParameters()) {
            result.add(new Named(parameter.getName(), name(parameter.getType().asErasure())));
        }
        return result;
    }

    /**
     * Named object locals that are not arguments, as {@code ExtendableMethodVisitor} names them for the context:
     * the local variable table, objects and arrays only (primitive locals are not recorded).
     */
    private static List<Named> locals(MethodNode body) {
        int argumentSlots = Type.getArgumentsAndReturnSizes(body.desc) >> 2;
        Set<Named> result = new LinkedHashSet<>();
        if (body.localVariables != null) {
            for (LocalVariableNode variable : body.localVariables) {
                Type type = Type.getType(variable.desc);
                boolean object = type.getSort() == Type.OBJECT || type.getSort() == Type.ARRAY;
                if (object && variable.index >= argumentSlots && !variable.name.equals("this")) {
                    result.add(new Named(variable.name, type.getClassName()));
                }
            }
        }
        return new ArrayList<>(result);
    }

    private static Signature declared(AnnotationDescription publicApi) {
        AnnotationDescription returns = single(publicApi, "returns");
        List<Named> parameters = new ArrayList<>();
        for (AnnotationDescription parameter : array(publicApi, "parameters")) {
            parameters.add(new Named(string(parameter, "name"), type(parameter, "type")));
        }
        List<Named> locals = new ArrayList<>();
        for (AnnotationDescription local : array(publicApi, "locals")) {
            locals.add(new Named(string(local, "name"), type(local, "type")));
        }
        return new Signature(type(returns, "type"), parameters, locals);
    }

    private static MethodDescription.InDefinedShape property(AnnotationDescription annotation, String name) {
        return annotation.getAnnotationType().getDeclaredMethods().filter(named(name)).getOnly();
    }

    private static String string(AnnotationDescription annotation, String name) {
        return annotation.getValue(property(annotation, name)).resolve(String.class);
    }

    private static String type(AnnotationDescription annotation, String name) {
        return name(annotation.getValue(property(annotation, name)).resolve(TypeDescription.class));
    }

    private static AnnotationDescription single(AnnotationDescription annotation, String name) {
        return annotation.getValue(property(annotation, name)).resolve(AnnotationDescription.class);
    }

    private static AnnotationDescription[] array(AnnotationDescription annotation, String name) {
        return annotation.getValue(property(annotation, name)).resolve(AnnotationDescription[].class);
    }

    private static ClassNode readClass(TypeDescription type, ClassFileLocator locator) {
        try {
            ClassNode node = new ClassNode();
            new ClassReader(locator.locate(type.getName()).resolve()).accept(node, ClassReader.SKIP_FRAMES);
            return node;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * The annotation as it has to be written in the source for the method. {@code typeReference} turns a type name
     * ({@code a.B$C}, {@code java.lang.String[]}, {@code int}) into the text before {@code .class}.
     */
    static String render(String from, Signature signature, UnaryOperator<String> typeReference) {
        StringBuilder text = new StringBuilder("@PublicAPI(from = \"").append(from).append("\",\n")
                .append("        returns = @APIReturn(type = ").append(typeReference.apply(signature.returns())).append(".class)");
        if (!signature.parameters().isEmpty()) {
            text.append(",\n        parameters = {");
            text.append(signature.parameters().stream()
                    .map(parameter -> "@APIParameter(name = \"" + parameter.name() + "\", type = " + typeReference.apply(parameter.type()) + ".class)")
                    .collect(Collectors.joining(",\n                ")));
            text.append("}");
        }
        if (!signature.locals().isEmpty()) {
            text.append(",\n        locals = {");
            text.append(signature.locals().stream()
                    .map(local -> "@APILocals(name = \"" + local.name() + "\", type = " + typeReference.apply(local.type()) + ".class)")
                    .collect(Collectors.joining(",\n                ")));
            text.append("}");
        }
        return text.append(")").toString();
    }

    /**
     * {@link #render} with simple class names, for messages.
     */
    static String render(String from, Signature signature) {
        return render(from, signature, name -> {
            String dims = "";
            while (name.endsWith("[]")) {
                dims += "[]";
                name = name.substring(0, name.length() - 2);
            }
            return name.substring(name.lastIndexOf('.') + 1).replace('$', '.') + dims;
        });
    }
}

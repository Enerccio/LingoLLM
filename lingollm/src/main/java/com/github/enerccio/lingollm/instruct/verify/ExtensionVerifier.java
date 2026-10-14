package com.github.enerccio.lingollm.instruct.verify;

import com.github.enerccio.lingollm.domain.traits.Extendable;
import com.github.enerccio.lingollm.instruct.verify.ContextValue.*;
import org.apache.commons.io.FilenameUtils;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Frame;
import org.osgi.framework.Bundle;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static com.github.enerccio.lingollm.instruct.verify.ContextInterpreter.CONTEXT_TYPE;

/**
 * Checks that an extension asks the application for things it has: every {@code registerDecorator} call in the
 * extension is followed to the class and method it decorates, and the decorator that is registered is searched for
 * the calls it makes on its {@code ExtendableMethodContext}, which are checked against the real application classes
 * the same way the context checks them when a decorated method runs:
 * <ul>
 *     <li>the class exists, is {@code @Extendable} and declares an instrumented method of that name;</li>
 *     <li>{@code getMethodArgument}, {@code getLocalVariable} and their {@code has*} variants: the argument / local
 *     variable exists and has a compatible type;</li>
 *     <li>{@code getOriginalReturn}, {@code getCurrentReturn}, {@code setCurrentReturn}: the method returns a value,
 *     of a compatible type, and it is not asked in {@code onMethodEnter};</li>
 *     <li>{@code getReflectiveFieldValue}, {@code setReflectiveFieldValue}, {@code callReflectiveMethod}: the field /
 *     method exists on the object, which is followed through the code - {@code instrumented}, a value read from a
 *     field of it ({@code this$0} to reach the outer class), an argument, a cast.</li>
 * </ul>
 * Only the decorator classes themselves are scanned (their methods, lambdas and helper methods included). A context
 * handed over to another class is not followed - what that class does with it is up to the developer of the extension.
 * <p>
 * The classes of the extension are read from the installed bundle, the JAR file lists them.
 */
public final class ExtensionVerifier {

    private static final String SERVICE_TYPE = "com/github/enerccio/lingollm/domain/service/ExtensionService";
    private static final String REGISTER_DESC = "(L" + SERVICE_TYPE + "$ExtensionDecorator;Ljava/lang/String;Ljava/lang/String;)V";
    private static final String ENTER_DESC = "(Ljava/lang/Object;L" + CONTEXT_TYPE + ";)V";
    private static final String LEAVE_DESC = "(Ljava/lang/Object;L" + CONTEXT_TYPE + ";Ljava/lang/Throwable;)V";

    private final ClassLoader runtime;

    /**
     * @param runtime class loader of the application: the classes extensions decorate are looked up there
     */
    public ExtensionVerifier(ClassLoader runtime) {
        this.runtime = runtime;
    }

    public ExtensionVerification verify(Bundle bundle, File jar) throws IOException {
        return new Run(bundle, jar).execute();
    }

    /**
     * The report of an extension JAR: next to it, named after the JAR, {@code .valid} or {@code .invalid}.
     */
    public static File reportFile(File jar, boolean valid) {
        return new File(jar.getAbsoluteFile().getParentFile(), FilenameUtils.getBaseName(jar.getName()) + (valid ? ".valid" : ".invalid"));
    }

    /**
     * Writes the report of the verification, replaces the one of the opposite result.
     */
    public static void writeReport(File jar, ExtensionVerification verification) throws IOException {
        Files.deleteIfExists(reportFile(jar, !verification.valid()).toPath());
        Files.writeString(reportFile(jar, verification.valid()).toPath(), verification.report(), StandardCharsets.UTF_8);
    }

    public static void deleteReports(File jar) throws IOException {
        Files.deleteIfExists(reportFile(jar, true).toPath());
        Files.deleteIfExists(reportFile(jar, false).toPath());
    }

    private record Registration(ClassNode owner, MethodNode method, int line, ContextValue decorator,
                                ContextValue className, ContextValue methodName) {
    }

    private record ContextCall(ClassNode owner, MethodNode method, int line, boolean inEnter, String name,
                               List<ContextValue> args) {
    }

    private record Param(String name, Type type) {
    }

    /**
     * One method of the decorated class. All overloads of a name share the decorators, a name has to be found in one.
     */
    private record TargetMethod(String descriptor, List<Param> params, Map<String, List<Type>> locals) {

        Type returnType() {
            return Type.getReturnType(descriptor);
        }
    }

    private record Target(Class<?> cls, String methodName, List<TargetMethod> methods) {

        String label() {
            return simpleName(cls.getName()) + "." + methodName;
        }
    }

    private static String simpleName(String name) {
        return name.substring(name.lastIndexOf('.') + 1);
    }

    private final class Run {

        private final Bundle bundle;
        private final File jar;

        private final Set<String> errors = new LinkedHashSet<>();
        private final Set<String> warnings = new LinkedHashSet<>();
        private final List<String> checked = new ArrayList<>();

        private final Map<String, ClassNode> extensionClasses = new LinkedHashMap<>();
        private final List<Registration> registrations = new ArrayList<>();
        private final Map<String, Set<Type>> fieldAssignments = new HashMap<>();
        private final Map<String, List<ContextCall>> contextCalls = new HashMap<>();

        private final Map<String, Optional<Class<?>>> classes = new HashMap<>();
        private final Map<Class<?>, Optional<ClassNode>> runtimeNodes = new HashMap<>();

        private Run(Bundle bundle, File jar) {
            this.bundle = bundle;
            this.jar = jar;
        }

        ExtensionVerification execute() throws IOException {
            readClasses();
            for (ClassNode cn : extensionClasses.values()) {
                for (MethodNode mn : cn.methods) {
                    scan(cn, mn);
                }
            }
            verifyRegistrations();
            return result();
        }

        // --- reading the extension ---

        private void readClasses() throws IOException {
            try (JarFile jarFile = new JarFile(jar)) {
                for (JarEntry entry : Collections.list(jarFile.entries())) {
                    String name = entry.getName();
                    if (!name.endsWith(".class") || name.startsWith("META-INF/") || name.equals("module-info.class")) {
                        continue;
                    }
                    URL url = bundle.getEntry(name);
                    if (url == null) {
                        errors.add("Class " + name + " is in " + jar.getName() + " but not in the installed bundle; the JAR was changed after the bundle was installed");
                        continue;
                    }
                    try (InputStream in = url.openStream()) {
                        ClassNode node = new ClassNode();
                        new ClassReader(in).accept(node, ClassReader.SKIP_FRAMES);
                        extensionClasses.put(node.name, node);
                    } catch (IOException | RuntimeException e) {
                        warnings.add("Class " + name + " could not be read, it was not checked: " + e);
                    }
                }
            }
        }

        private void scan(ClassNode cn, MethodNode mn) {
            if (mn.instructions.size() == 0) {
                return;
            }
            boolean enter = mn.name.equals("onMethodEnter") && mn.desc.equals(ENTER_DESC);
            boolean leave = mn.name.equals("onMethodLeave") && mn.desc.equals(LEAVE_DESC);
            Frame<ContextValue>[] frames;
            try {
                frames = new Analyzer<>(new ContextInterpreter(enter || leave ? 1 : -1)).analyze(cn.name, mn);
            } catch (AnalyzerException | RuntimeException e) {
                warnings.add(simpleName(dotted(cn.name)) + "." + mn.name + " could not be analysed, it was not checked: " + e.getMessage());
                return;
            }

            AbstractInsnNode[] instructions = mn.instructions.toArray();
            for (int i = 0; i < instructions.length; i++) {
                Frame<ContextValue> frame = frames[i];
                if (frame == null) {
                    continue; // unreachable
                }
                AbstractInsnNode insn = instructions[i];
                if (insn instanceof MethodInsnNode call) {
                    if (call.name.equals("registerDecorator") && call.desc.equals(REGISTER_DESC)) {
                        List<ContextValue> args = arguments(frame, call.desc);
                        registrations.add(new Registration(cn, mn, lineOf(instructions, i), args.get(0), args.get(1), args.get(2)));
                    } else if (call.owner.equals(CONTEXT_TYPE)) {
                        contextCalls.computeIfAbsent(cn.name, _ -> new ArrayList<>())
                                .add(new ContextCall(cn, mn, lineOf(instructions, i), enter, call.name, arguments(frame, call.desc)));
                    }
                } else if (insn instanceof FieldInsnNode field
                        && (field.getOpcode() == Opcodes.PUTFIELD || field.getOpcode() == Opcodes.PUTSTATIC)) {
                    if (frame.getStack(frame.getStackSize() - 1) instanceof Typed value) {
                        fieldAssignments.computeIfAbsent(field.owner + "." + field.name, _ -> new LinkedHashSet<>()).add(value.type());
                    }
                }
            }
        }

        private List<ContextValue> arguments(Frame<ContextValue> frame, String desc) {
            int count = Type.getArgumentTypes(desc).length;
            int top = frame.getStackSize();
            List<ContextValue> args = new ArrayList<>(count);
            for (int i = top - count; i < top; i++) {
                args.add(frame.getStack(i));
            }
            return args;
        }

        private int lineOf(AbstractInsnNode[] instructions, int index) {
            for (int i = index; i >= 0; i--) {
                if (instructions[i] instanceof LineNumberNode line) {
                    return line.line;
                }
            }
            return 0;
        }

        // --- registrations ---

        private void verifyRegistrations() {
            Set<String> done = new HashSet<>();
            for (Registration registration : registrations) {
                String where = simpleName(dotted(registration.owner.name)) + "." + registration.method.name + " line " + registration.line;
                if (!(registration.className instanceof Str className) || !(registration.methodName instanceof Str methodName)) {
                    warnings.add(where + ": registerDecorator is called with a class or method name that is not a constant, it was not checked");
                    continue;
                }
                Target target = target(className.value(), methodName.value(), where);

                Set<Type> decorators = decorators(registration.decorator);
                if (decorators.isEmpty()) {
                    warnings.add(where + ": the decorator registered for " + simpleName(className.value()) + "." + methodName.value()
                            + " could not be determined, what it asks from the context was not checked");
                    continue;
                }
                for (Type decorator : decorators) {
                    String name = decorator.getInternalName();
                    if (target == null || !extensionClasses.containsKey(name)) {
                        continue;
                    }
                    if (done.add(name + "->" + className.value() + "." + methodName.value())) {
                        checked.add(target.label() + " <- " + simpleName(dotted(name)));
                        for (ContextCall call : contextCalls.getOrDefault(name, List.of())) {
                            check(call, target);
                        }
                    }
                }
            }
        }

        private Set<Type> decorators(ContextValue decorator) {
            if (decorator instanceof Typed typed) {
                return Set.of(typed.type());
            }
            if (decorator instanceof Member member) {
                return fieldAssignments.getOrDefault(member.owner() + "." + member.name(), Set.of());
            }
            return Set.of();
        }

        /**
         * The decorated class and method, {@code null} (and an error) when the application has no such thing.
         */
        private Target target(String className, String methodName, String where) {
            Class<?> cls = loadByName(className);
            if (cls == null) {
                errors.add(where + ": class " + className + " does not exist in this version of the application");
                return null;
            }
            if (!cls.isAnnotationPresent(Extendable.class)) {
                errors.add(where + ": class " + className + " is not @Extendable, its methods are not instrumented and the decorator would never be called");
                return null;
            }
            Optional<ClassNode> node = runtimeNode(cls);
            if (node.isEmpty()) {
                warnings.add(where + ": the code of " + className + " could not be read, its methods were not checked");
                return null;
            }
            List<TargetMethod> methods = new ArrayList<>();
            for (MethodNode method : node.get().methods) {
                // what RuntimeInstrumentationInitializer instruments
                if (method.name.equals(methodName) && (method.access & (Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC)) == 0) {
                    methods.add(describe(method));
                }
            }
            if (methods.isEmpty()) {
                String hint = "";
                for (Class<?> parent = cls.getSuperclass(); parent != null; parent = parent.getSuperclass()) {
                    for (Method method : parent.getDeclaredMethods()) {
                        if (method.getName().equals(methodName)) {
                            hint = "; it is declared by " + parent.getName() + ", register the decorator for that class";
                        }
                    }
                }
                errors.add(where + ": class " + className + " has no instrumented method " + methodName
                        + " (constructors, static and synthetic methods are not decorated)" + hint);
                return null;
            }
            return new Target(cls, methodName, methods);
        }

        /**
         * Names the way {@code ExtendableMethodVisitor} gives them to the context.
         */
        private TargetMethod describe(MethodNode method) {
            Map<Integer, String> slotNames = new HashMap<>();
            Map<Integer, Type> slotTypes = new HashMap<>();
            if (method.localVariables != null) {
                for (LocalVariableNode variable : method.localVariables) {
                    if (!variable.name.equals("this")) {
                        slotNames.put(variable.index, variable.name);
                        slotTypes.put(variable.index, Type.getType(variable.desc));
                    }
                }
            }

            List<String> parameterNames = new ArrayList<>();
            if (method.parameters != null) {
                for (ParameterNode parameter : method.parameters) {
                    if (parameter.name != null) {
                        parameterNames.add(parameter.name);
                    }
                }
            }
            List<Param> params = new ArrayList<>();
            Type[] argumentTypes = Type.getArgumentTypes(method.desc);
            int slot = 1;
            for (int i = 0; i < argumentTypes.length; i++) {
                String name = i < parameterNames.size() ? parameterNames.get(i) : slotNames.get(slot);
                params.add(new Param(name != null ? name : "arg" + i, argumentTypes[i]));
                slot += argumentTypes[i].getSize();
            }

            // object locals only: the ones that are stored with ASTORE
            Map<String, List<Type>> locals = new LinkedHashMap<>();
            AbstractInsnNode[] instructions = method.instructions.toArray();
            for (int i = 0; i < instructions.length; i++) {
                if (instructions[i] instanceof VarInsnNode store && store.getOpcode() == Opcodes.ASTORE) {
                    LocalVariableNode scope = scopeOf(method, store.var, i);
                    String name = scope != null ? scope.name : slotNames.getOrDefault(store.var, "var" + store.var);
                    Type type = scope != null ? Type.getType(scope.desc)
                            : slotTypes.getOrDefault(store.var, Type.getObjectType("java/lang/Object"));
                    List<Type> types = locals.computeIfAbsent(name, _ -> new ArrayList<>());
                    if (!types.contains(type)) {
                        types.add(type);
                    }
                }
            }
            return new TargetMethod(method.desc, params, locals);
        }

        /**
         * Javac reuses a slot for variables in different scopes: the variable of the store is the one whose scope
         * contains it or starts right after it (the initializing store).
         */
        private LocalVariableNode scopeOf(MethodNode method, int slot, int position) {
            if (method.localVariables == null) {
                return null;
            }
            LocalVariableNode best = null;
            int bestStart = -1;
            for (LocalVariableNode variable : method.localVariables) {
                if (variable.index != slot || variable.name.equals("this")) {
                    continue;
                }
                int start = method.instructions.indexOf(variable.start);
                int end = method.instructions.indexOf(variable.end);
                if (start <= position + 1 && position < end && start > bestStart) {
                    best = variable;
                    bestStart = start;
                }
            }
            return best;
        }

        // --- what a decorator asks from the context ---

        private void check(ContextCall call, Target target) {
            String at = simpleName(dotted(call.owner.name)) + "." + call.method.name + " line " + call.line + " (" + target.label() + ")";
            List<ContextValue> args = call.args;
            switch (call.name) {
                case "getMethodArgument" -> checkArgument(at, target, true, args.get(0), args.get(1));
                case "hasMethodArgument" -> checkArgument(at, target, false, args.get(0), args.size() > 1 ? args.get(1) : null);
                case "registerMethodArgument" -> checkRegisteredArgument(at, target, args.get(0), args.get(2));
                case "getLocalVariable" -> checkLocal(at, target, true, call.inEnter, args.get(0), args.get(1));
                case "hasLocalVariable" -> checkLocal(at, target, false, call.inEnter, args.get(0), args.size() > 1 ? args.get(1) : null);
                case "getOriginalReturn", "getCurrentReturn" -> checkReturn(at, target, call.inEnter, args.get(0));
                case "setCurrentReturn" -> checkReturn(at, target, call.inEnter, null);
                case "getReflectiveFieldValue" -> checkField(at, target, args.get(0), args.get(1), args.get(2));
                case "setReflectiveFieldValue" -> checkField(at, target, args.get(0), args.get(1), args.get(3));
                case "callReflectiveMethod" -> checkMethod(at, target, args.get(0), args.get(1), args.get(2), args.get(4));
                default -> {
                    // registerLocalVariable and the like are not requests to the decorated code
                }
            }
        }

        private void issue(boolean error, String message) {
            (error ? errors : warnings).add(message);
        }

        private List<Type> declaredArguments(Target target, String name) {
            List<Type> declared = new ArrayList<>();
            for (TargetMethod method : target.methods) {
                for (Param param : method.params) {
                    if (param.name.equals(name)) {
                        declared.add(param.type);
                    }
                }
            }
            return declared;
        }

        private Set<String> argumentNames(Target target) {
            Set<String> names = new TreeSet<>();
            for (TargetMethod method : target.methods) {
                for (Param param : method.params) {
                    names.add(param.name);
                }
            }
            return names;
        }

        private void checkArgument(String at, Target target, boolean required, ContextValue nameValue, ContextValue typeValue) {
            if (!(nameValue instanceof Str name)) {
                warnings.add(at + ": the name of the argument is not a constant, it was not checked");
                return;
            }
            List<Type> declared = declaredArguments(target, name.value());
            Set<String> names = argumentNames(target);
            if (declared.isEmpty()) {
                issue(required, at + ": the method has no argument '" + name.value() + "' (it has " + (names.isEmpty() ? "none" : names) + ")");
                return;
            }
            Class<?> expected = expectedType(at, typeValue);
            if (expected == null) {
                return;
            }
            boolean unresolved = false;
            for (Type type : declared) {
                Class<?> actual = load(type);
                if (actual == null) {
                    unresolved = true;
                } else if (expected.isAssignableFrom(actual)) {
                    return;
                }
            }
            if (unresolved) {
                warnings.add(at + ": the type of argument '" + name.value() + "' could not be loaded, it was not checked");
            } else {
                issue(required, at + ": argument '" + name.value() + "' is " + typeNames(declared) + ", the decorator asks for " + expected.getTypeName()
                        + " (the registered type of an argument is its declared type, primitives included)");
            }
        }

        private void checkRegisteredArgument(String at, Target target, ContextValue nameValue, ContextValue typeValue) {
            if (!(nameValue instanceof Str name)) {
                warnings.add(at + ": the name of the argument is not a constant, it was not checked");
                return;
            }
            List<Type> declared = declaredArguments(target, name.value());
            Set<String> names = argumentNames(target);
            if (declared.isEmpty()) {
                errors.add(at + ": the method has no argument '" + name.value() + "' to replace (it has " + (names.isEmpty() ? "none" : names) + ")");
            } else if (typeValue instanceof Cls type && !declared.contains(type.type())) {
                warnings.add(at + ": argument '" + name.value() + "' is replaced with type " + type.type().getClassName()
                        + ", its declared type is " + typeNames(declared));
            }
        }

        private void checkLocal(String at, Target target, boolean required, boolean inEnter, ContextValue nameValue, ContextValue typeValue) {
            if (!(nameValue instanceof Str name)) {
                warnings.add(at + ": the name of the local variable is not a constant, it was not checked");
                return;
            }
            if (inEnter) {
                issue(required, at + ": local variable '" + name.value() + "' is asked in onMethodEnter, where there are no local variables yet");
                return;
            }
            List<Type> declared = new ArrayList<>();
            Set<String> names = new TreeSet<>();
            for (TargetMethod method : target.methods) {
                names.addAll(method.locals.keySet());
                declared.addAll(method.locals.getOrDefault(name.value(), List.of()));
            }
            if (declared.isEmpty()) {
                issue(required, at + ": the method has no object local variable '" + name.value() + "' (it has " + (names.isEmpty() ? "none" : names)
                        + "; primitive locals are not recorded)");
                return;
            }
            Class<?> expected = expectedType(at, typeValue);
            if (expected == null) {
                return;
            }
            boolean unresolved = false;
            boolean narrower = false;
            for (Type type : declared) {
                Class<?> actual = load(type);
                if (actual == null) {
                    unresolved = true;
                } else if (expected.isAssignableFrom(actual)) {
                    return;
                } else if (actual.isAssignableFrom(expected) || (expected.isInterface() && !Modifier.isFinal(actual.getModifiers()))) {
                    narrower = true;
                }
            }
            if (unresolved) {
                warnings.add(at + ": the type of local variable '" + name.value() + "' could not be loaded, it was not checked");
            } else if (narrower) {
                // the context checks the runtime class of the value, not the declared type of the variable
                warnings.add(at + ": local variable '" + name.value() + "' is declared " + typeNames(declared) + ", the decorator asks for "
                        + expected.getTypeName() + ", which works only when the value is of that type at run time");
            } else {
                issue(required, at + ": local variable '" + name.value() + "' is " + typeNames(declared) + ", the decorator asks for " + expected.getTypeName());
            }
        }

        private void checkReturn(String at, Target target, boolean inEnter, ContextValue typeValue) {
            if (inEnter) {
                errors.add(at + ": the return value is asked in onMethodEnter, where the method has not returned yet");
                return;
            }
            List<Type> declared = new ArrayList<>();
            for (TargetMethod method : target.methods) {
                if (method.returnType().getSort() != Type.VOID) {
                    declared.add(method.returnType());
                }
            }
            if (declared.isEmpty()) {
                errors.add(at + ": the method is void, it has no return value");
                return;
            }
            if (typeValue == null) {
                return;
            }
            Class<?> expected = expectedType(at, typeValue);
            if (expected == null) {
                return;
            }
            boolean unresolved = false;
            for (Type type : declared) {
                Class<?> actual = load(type);
                if (actual == null) {
                    unresolved = true;
                } else if (expected.isAssignableFrom(actual)) {
                    return;
                }
            }
            if (unresolved) {
                warnings.add(at + ": the return type of the method could not be loaded, it was not checked");
            } else {
                errors.add(at + ": the method returns " + typeNames(declared) + ", the decorator asks for " + expected.getTypeName()
                        + " (the registered type of the return value is its declared type, primitives included)");
            }
        }

        private void checkField(String at, Target target, ContextValue object, ContextValue nameValue, ContextValue typeValue) {
            if (!(nameValue instanceof Str name)) {
                warnings.add(at + ": the name of the field is not a constant, it was not checked");
                return;
            }
            Class<?> owner = owner(object, target);
            if (owner == null) {
                warnings.add(at + ": the object whose field '" + name.value() + "' is accessed could not be determined, it was not checked");
                return;
            }
            Field field = field(owner, name.value());
            if (field == null) {
                errors.add(at + ": " + owner.getName() + " has no field '" + name.value() + "'");
                return;
            }
            Class<?> expected = expectedType(at, typeValue);
            if (expected != null && !expected.isAssignableFrom(field.getType())) {
                errors.add(at + ": field '" + name.value() + "' of " + owner.getName() + " is " + field.getType().getTypeName()
                        + ", the decorator asks for " + expected.getTypeName());
            }
        }

        private void checkMethod(String at, Target target, ContextValue object, ContextValue nameValue, ContextValue argumentTypes, ContextValue returnValue) {
            if (!(nameValue instanceof Str name)) {
                warnings.add(at + ": the name of the method is not a constant, it was not checked");
                return;
            }
            Class<?> owner = owner(object, target);
            if (owner == null) {
                warnings.add(at + ": the object whose method '" + name.value() + "' is called could not be determined, it was not checked");
                return;
            }
            List<Class<?>> signature = classesOf(argumentTypes);
            List<Method> candidates = methods(owner, name.value());
            Method method = null;
            if (signature != null) {
                for (Method candidate : candidates) {
                    if (Arrays.asList(candidate.getParameterTypes()).equals(signature)) {
                        method = candidate;
                        break;
                    }
                }
                if (method == null) {
                    errors.add(at + ": " + owner.getName() + " has no method " + signature(name.value(), signature)
                            + (candidates.isEmpty() ? "" : ", only " + candidates.stream()
                            .map(m -> signature(name.value(), Arrays.asList(m.getParameterTypes()))).toList()));
                    return;
                }
            } else if (candidates.isEmpty()) {
                errors.add(at + ": " + owner.getName() + " has no method '" + name.value() + "'");
                return;
            } else {
                warnings.add(at + ": the argument types of method '" + name.value() + "' are not constants, the signature was not checked");
                return;
            }
            Class<?> expected = expectedType(at, returnValue);
            if (expected != null && !expected.isAssignableFrom(method.getReturnType())) {
                errors.add(at + ": method '" + name.value() + "' of " + owner.getName() + " returns " + method.getReturnType().getTypeName()
                        + ", the decorator asks for " + expected.getTypeName());
            }
        }

        /**
         * The class the object is looked up in, {@code null} when the code doesn't tell.
         */
        private Class<?> owner(ContextValue object, Target target) {
            if (object instanceof Instrumented) {
                return target.cls;
            }
            if (object instanceof Typed typed) {
                return load(typed.type());
            }
            if (object instanceof FieldValue value) {
                Class<?> owner = owner(value.owner(), target);
                Field field = owner == null ? null : field(owner, value.field());
                return field == null ? null : field.getType();
            }
            if (object instanceof MethodValue value) {
                Class<?> owner = owner(value.owner(), target);
                List<Class<?>> signature = classesOf(value.argumentTypes());
                if (owner == null || signature == null) {
                    return null;
                }
                for (Method method : methods(owner, value.method())) {
                    if (Arrays.asList(method.getParameterTypes()).equals(signature)) {
                        return method.getReturnType();
                    }
                }
            }
            return null;
        }

        private Class<?> expectedType(String at, ContextValue typeValue) {
            if (typeValue == null) {
                return null;
            }
            if (!(typeValue instanceof Cls type)) {
                warnings.add(at + ": the requested type is not a class literal, it was not checked");
                return null;
            }
            Class<?> expected = load(type.type());
            if (expected == null) {
                warnings.add(at + ": the requested type " + type.type().getClassName() + " could not be loaded, it was not checked");
            }
            return expected;
        }

        private List<Class<?>> classesOf(ContextValue value) {
            if (!(value instanceof Array array)) {
                return null;
            }
            List<Class<?>> types = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                if (!(array.get(i) instanceof Cls cls) || load(cls.type()) == null) {
                    return null;
                }
                types.add(load(cls.type()));
            }
            return types;
        }

        // --- the real application ---

        /**
         * Fields the way {@code ReflectUtils.getField} finds them: declared by the class or a superclass.
         */
        private Field field(Class<?> cls, String name) {
            for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
                try {
                    return c.getDeclaredField(name);
                } catch (NoSuchFieldException ignored) {
                    // next superclass
                } catch (LinkageError e) {
                    return null;
                }
            }
            return null;
        }

        private List<Method> methods(Class<?> cls, String name) {
            List<Method> methods = new ArrayList<>();
            for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
                try {
                    for (Method method : c.getDeclaredMethods()) {
                        if (method.getName().equals(name)) {
                            methods.add(method);
                        }
                    }
                } catch (LinkageError e) {
                    break;
                }
            }
            return methods;
        }

        private Class<?> load(Type type) {
            return switch (type.getSort()) {
                case Type.BOOLEAN -> boolean.class;
                case Type.BYTE -> byte.class;
                case Type.CHAR -> char.class;
                case Type.SHORT -> short.class;
                case Type.INT -> int.class;
                case Type.LONG -> long.class;
                case Type.FLOAT -> float.class;
                case Type.DOUBLE -> double.class;
                case Type.VOID -> void.class;
                case Type.ARRAY -> loadByName(type.getDescriptor().replace('/', '.'));
                case Type.OBJECT -> loadByName(type.getClassName());
                default -> null;
            };
        }

        private Class<?> loadByName(String name) {
            return classes.computeIfAbsent(name, n -> {
                try {
                    return Optional.of(Class.forName(n, false, runtime));
                } catch (ClassNotFoundException | LinkageError e) {
                    try {
                        // classes of the extension itself, or of what it imports
                        return n.startsWith("[") ? Optional.empty() : Optional.of(bundle.loadClass(n));
                    } catch (ClassNotFoundException | RuntimeException | LinkageError ex) {
                        return Optional.empty();
                    }
                }
            }).orElse(null);
        }

        /**
         * The class file as the compiler wrote it, not as the agent rewrites it.
         */
        private Optional<ClassNode> runtimeNode(Class<?> cls) {
            return runtimeNodes.computeIfAbsent(cls, c -> {
                ClassLoader loader = c.getClassLoader() != null ? c.getClassLoader() : runtime;
                try (InputStream in = loader.getResourceAsStream(Type.getInternalName(c) + ".class")) {
                    if (in == null) {
                        return Optional.empty();
                    }
                    ClassNode node = new ClassNode();
                    new ClassReader(in).accept(node, ClassReader.SKIP_FRAMES);
                    return Optional.of(node);
                } catch (IOException | RuntimeException e) {
                    return Optional.empty();
                }
            });
        }

        // --- the report ---

        private ExtensionVerification result() {
            boolean valid = errors.isEmpty();
            List<String> errorList = List.copyOf(errors);
            List<String> warningList = List.copyOf(warnings);
            StringBuilder report = new StringBuilder();
            report.append("Extension: ").append(jar.getName()).append('\n');
            report.append("Bundle: ").append(bundle.getSymbolicName()).append(' ').append(bundle.getVersion()).append('\n');
            report.append("Verified: ").append(Instant.now()).append('\n');
            report.append("Result: ").append(valid ? "VALID" : "INVALID").append('\n');
            section(report, "Errors", errorList);
            section(report, "Warnings", warningList);
            section(report, "Checked decorators", checked);
            report.append("\nOnly the decorators themselves are checked. A context handed over to another class is not followed.\n");
            return new ExtensionVerification(jar.getName(), valid, errorList, warningList, List.copyOf(checked), report.toString());
        }

        private void section(StringBuilder report, String title, List<String> lines) {
            if (lines.isEmpty()) {
                return;
            }
            report.append('\n').append(title).append(" (").append(lines.size()).append("):\n");
            for (String line : lines) {
                report.append("  - ").append(line).append('\n');
            }
        }
    }

    private static String signature(String name, List<Class<?>> parameters) {
        return name + parameters.stream().map(Class::getTypeName).toList().toString().replace('[', '(').replace(']', ')');
    }

    private static String dotted(String internalName) {
        return internalName.replace('/', '.');
    }

    private static String typeNames(List<Type> types) {
        return types.stream().map(Type::getClassName).distinct().toList().toString();
    }
}

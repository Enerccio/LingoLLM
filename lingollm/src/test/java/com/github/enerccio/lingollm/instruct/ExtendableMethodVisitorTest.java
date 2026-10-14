package com.github.enerccio.lingollm.instruct;

import com.github.enerccio.lingollm.domain.service.ExtensionService;
import com.github.enerccio.lingollm.domain.service.ExtensionService.ExtendableMethodContext;
import com.github.enerccio.lingollm.domain.service.ExtensionService.ExtensionDecorator;
import com.github.enerccio.lingollm.domain.service.impl.ExtensionServiceImpl;
import com.github.enerccio.lingollm.instruct.fixture.AbstractSubject;
import com.github.enerccio.lingollm.instruct.fixture.InstrumentedSubject;
import com.github.enerccio.lingollm.instruct.fixture.InstrumentedSubjectChild;
import com.github.enerccio.lingollm.instruct.fixture.Marker;
import com.github.enerccio.lingollm.test.ExpectedLog;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.util.CheckClassAdapter;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Bytecode produced by {@link ExtendableMethodVisitor}: it must verify, keep the class schema (required for
 * retransformation), behave exactly like the original, and expose enter/leave hooks, arguments and local variables to
 * decorators through {@link ExtensionServiceImpl}.
 */
class ExtendableMethodVisitorTest {

    private static final String SUBJECT = InstrumentedSubject.class.getName();
    private static final String CHILD = InstrumentedSubjectChild.class.getName();
    private static final String ABSTRACT = AbstractSubject.class.getName();

    private static byte[] originalSubject;
    private static byte[] instrumentedSubject;

    private Instrumenter.IsolatedLoader loader;
    private ExtensionServiceImpl extensionService;
    private ExtensionService previousService;

    @BeforeAll
    static void instrumentFixtures() throws Exception {
        originalSubject = Instrumenter.originalBytes(InstrumentedSubject.class);
        instrumentedSubject = Instrumenter.instrument(originalSubject, ExtendableMethodVisitorTest.class.getClassLoader());
    }

    @BeforeEach
    void setUp() throws Exception {
        ClassLoader parent = getClass().getClassLoader();
        loader = new Instrumenter.IsolatedLoader(parent)
                .add(SUBJECT, instrumentedSubject)
                .add(CHILD, Instrumenter.instrument(Instrumenter.originalBytes(InstrumentedSubjectChild.class), parent));
        extensionService = new ExtensionServiceImpl();
        previousService = setHolder(extensionService);
    }

    @AfterEach
    void restoreHolder() throws Exception {
        setHolder(previousService);
    }

    static ExtensionService setHolder(ExtensionService service) throws Exception {
        Field field = ExtensionServiceHolder.class.getDeclaredField("instance");
        field.setAccessible(true);
        ExtensionService previous = (ExtensionService) field.get(null);
        field.set(null, service);
        return previous;
    }

    private Object newInstrumented() throws Exception {
        return loader.loadClass(SUBJECT).getConstructor().newInstance();
    }

    private Object newInstrumentedChild() throws Exception {
        return loader.loadClass(CHILD).getConstructor().newInstance();
    }

    private static Method method(Class<?> type, String name) {
        return Arrays.stream(type.getMethods()).filter(m -> m.getName().equals(name)).findFirst()
                .orElseThrow(() -> new AssertionError("No method " + name));
    }

    private static Object call(Object target, String name, Object... args) throws Throwable {
        try {
            return method(target.getClass(), name).invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private static ClassNode node(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        return node;
    }

    private static boolean callsHolder(MethodNode method) {
        for (AbstractInsnNode insn : method.instructions) {
            if (insn instanceof MethodInsnNode m && m.owner.equals(Type.getInternalName(ExtensionServiceHolder.class))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Records enter/leave of one method and lets a test act on the context.
     */
    static class Recorder implements ExtensionDecorator {
        final List<String> events = new CopyOnWriteArrayList<>();
        final List<Object> selves = new CopyOnWriteArrayList<>();
        final List<ExtendableMethodContext> contexts = new CopyOnWriteArrayList<>();
        final List<Throwable> thrown = new CopyOnWriteArrayList<>();
        Consumer<ExtendableMethodContext> onEnter = _ -> {
        };
        Consumer<ExtendableMethodContext> onLeave = _ -> {
        };

        @Override
        public void onMethodEnter(Object instrumented, ExtendableMethodContext context) {
            events.add("enter");
            selves.add(instrumented);
            contexts.add(context);
            onEnter.accept(context);
        }

        @Override
        public void onMethodLeave(Object instrumented, ExtendableMethodContext context, Throwable throwing) {
            events.add("leave");
            thrown.add(throwing == null ? new NoThrow() : throwing);
            onLeave.accept(context);
        }
    }

    /**
     * Placeholder for "no exception" in the recorded list (the list doesn't take nulls).
     */
    static class NoThrow extends Throwable {
    }

    private Recorder decorate(String method) {
        return decorate(SUBJECT, method);
    }

    private Recorder decorate(String className, String method) {
        Recorder recorder = new Recorder();
        extensionService.registerDecorator(recorder, className, method);
        return recorder;
    }

    // ------------------------------------------------------------------------------------------------------------
    // verification
    // ------------------------------------------------------------------------------------------------------------

    @Test
    void instrumentedClassPassesAsmVerifier() {
        StringWriter errors = new StringWriter();

        CheckClassAdapter.verify(new ClassReader(instrumentedSubject), loader, false, new PrintWriter(errors));

        assertThat(errors.toString()).isEmpty();
    }

    @Test
    void instrumentedClassesPassJvmVerification() throws Exception {
        // defining and initializing runs the JVM bytecode verifier on the instrumented code
        assertThat(Class.forName(SUBJECT, true, loader).getClassLoader()).isSameAs(loader);
        assertThat(Class.forName(CHILD, true, loader).getClassLoader()).isSameAs(loader);
    }

    @Test
    void abstractMethodsAreLeftAlone() throws Exception {
        byte[] instrumented = Instrumenter.instrument(Instrumenter.originalBytes(AbstractSubject.class), getClass().getClassLoader());
        ClassNode node = node(instrumented);

        MethodNode name = node.methods.stream().filter(m -> m.name.equals("name")).findFirst().orElseThrow();
        assertThat(name.access & Opcodes.ACC_ABSTRACT).isNotZero();
        assertThat(name.instructions.size()).isZero();

        Instrumenter.IsolatedLoader abstractLoader = new Instrumenter.IsolatedLoader(getClass().getClassLoader()).add(ABSTRACT, instrumented);
        assertThat(Class.forName(ABSTRACT, true, abstractLoader)).isNotNull();
        StringWriter errors = new StringWriter();
        CheckClassAdapter.verify(new ClassReader(instrumented), abstractLoader, false, new PrintWriter(errors));
        assertThat(errors.toString()).isEmpty();
    }

    @Test
    void classSchemaIsUnchanged() {
        ClassNode before = node(originalSubject);
        ClassNode after = node(instrumentedSubject);

        assertThat(after.access).isEqualTo(before.access);
        assertThat(after.superName).isEqualTo(before.superName);
        assertThat(after.interfaces).isEqualTo(before.interfaces);
        assertThat(after.fields).extracting(f -> f.access + " " + f.name + " " + f.desc)
                .containsExactlyElementsOf(before.fields.stream().map((FieldNode f) -> f.access + " " + f.name + " " + f.desc).toList());
        assertThat(after.methods).extracting(m -> m.access + " " + m.name + m.desc + " " + m.signature + " " + m.exceptions)
                .containsExactlyElementsOf(before.methods.stream()
                        .map((MethodNode m) -> m.access + " " + m.name + m.desc + " " + m.signature + " " + m.exceptions).toList());
    }

    @Test
    void onlyEligibleMethodsAreInstrumented() {
        ClassNode before = node(originalSubject);
        ClassNode after = node(instrumentedSubject);

        for (MethodNode method : after.methods) {
            boolean expected = Instrumenter.isInstrumented(method.access, method.name);
            assertThat(callsHolder(method)).as(method.name + method.desc).isEqualTo(expected);
        }
        assertThat(after.methods).filteredOn(m -> m.name.startsWith("lambda$")).isNotEmpty()
                .allMatch(m -> !callsHolder(m));
        // untouched methods keep their exact code
        for (MethodNode original : before.methods) {
            if (!Instrumenter.isInstrumented(original.access, original.name)) {
                MethodNode copy = after.methods.stream().filter(m -> m.name.equals(original.name) && m.desc.equals(original.desc))
                        .findFirst().orElseThrow();
                assertThat(copy.instructions.size()).as(original.name).isEqualTo(original.instructions.size());
            }
        }
    }

    @Test
    void parametersKeepTheirSlotsInLocalVariableTable() {
        ClassNode before = node(originalSubject);
        ClassNode after = node(instrumentedSubject);

        for (MethodNode original : before.methods) {
            if (!Instrumenter.isInstrumented(original.access, original.name) || original.localVariables == null) {
                continue;
            }
            MethodNode instrumented = after.methods.stream().filter(m -> m.name.equals(original.name) && m.desc.equals(original.desc))
                    .findFirst().orElseThrow();
            int argSlots = Type.getArgumentsAndReturnSizes(original.desc) >> 2;
            int injected = 3 + Type.getArgumentTypes(original.desc).length;

            for (LocalVariableNode variable : original.localVariables) {
                int expectedIndex = variable.index < argSlots ? variable.index : variable.index + injected;
                assertThat(instrumented.localVariables).as(original.name + " " + variable.name)
                        .anyMatch(v -> v.name.equals(variable.name) && v.desc.equals(variable.desc) && v.index == expectedIndex);
            }
            assertThat(instrumented.localVariables).as(original.name + " injected slots stay unnamed")
                    .noneMatch(v -> v.index >= argSlots && v.index < argSlots + injected);
        }
    }

    @Test
    void annotationsArePreserved() throws Exception {
        Method annotated = method(loader.loadClass(SUBJECT), "annotated");

        assertThat(annotated.getAnnotation(Marker.class)).isNotNull();
        assertThat(annotated.getAnnotation(Marker.class).value()).isEqualTo("method");
        Marker parameterMarker = annotated.getParameters()[0].getAnnotation(Marker.class);
        assertThat(parameterMarker).isNotNull();
        assertThat(parameterMarker.value()).isEqualTo("parameter");
    }

    @Test
    void methodParametersAttributeIsPreserved() throws Exception {
        ClassNode before = node(originalSubject);
        ClassNode after = node(instrumentedSubject);

        for (MethodNode original : before.methods) {
            MethodNode instrumented = after.methods.stream().filter(m -> m.name.equals(original.name) && m.desc.equals(original.desc))
                    .findFirst().orElseThrow();
            assertThat(names(instrumented)).as(original.name).isEqualTo(names(original));
        }
        Method wide = method(loader.loadClass(SUBJECT), "wide");
        assertThat(Arrays.stream(wide.getParameters()).map(Parameter::getName).toList())
                .isEqualTo(Arrays.stream(method(InstrumentedSubject.class, "wide").getParameters()).map(Parameter::getName).toList());
    }

    private static List<String> names(MethodNode method) {
        return method.parameters == null ? null : method.parameters.stream().map(p -> p.name).toList();
    }

    // ------------------------------------------------------------------------------------------------------------
    // behaviour
    // ------------------------------------------------------------------------------------------------------------

    @Test
    void behavesLikeOriginal() throws Throwable {
        Object[][] calls = {
                {"noop"},
                {"echo", "text"},
                {"echo", (Object) null},
                {"add", 2, 3},
                {"wide", 5L, 3, 0.5, "L"},
                {"scale", 2.5, 4f},
                {"primitives", true, (byte) 1, 'x', (short) 2, 3, 4L, 5.5f, 6.25},
                {"returnsLong", Long.MAX_VALUE - 1},
                {"returnsBoolean", -1},
                {"returnsBoolean", 1},
                {"returnsChar", "zeta"},
                {"locals", "x"},
                {"branches", -7},
                {"branches", 0},
                {"branches", 7},
                {"tableSwitch", 0}, {"tableSwitch", 1}, {"tableSwitch", 2}, {"tableSwitch", 3},
                {"lookupSwitch", 10}, {"lookupSwitch", 1000}, {"lookupSwitch", -5}, {"lookupSwitch", 7},
                {"stringSwitch", "red"}, {"stringSwitch", "blue"}, {"stringSwitch", "green"},
                {"loop", 0}, {"loop", 10},
                {"catchInside", "12"}, {"catchInside", "twelve"},
                {"lambda", List.of(1, 2, 3), 10},
                {"synchronizedMethod", "ab"},
                {"synchronizedBlock", "ab"},
                {"nested", "abc"},
                {"callsHidden", "abc"},
                {"varargs", (Object) new String[]{"a", "b", "c"}},
                {"pattern", "four"}, {"pattern", ""}, {"pattern", 42},
                {"scopes", true}, {"scopes", false},
                {"nullLocal"},
                {"annotated", "a"},
                {"staticMethod", "s"},
        };
        Object original = new InstrumentedSubject();
        Object instrumented = newInstrumented();

        for (Object[] c : calls) {
            String name = (String) c[0];
            Object[] args = Arrays.copyOfRange(c, 1, c.length);
            assertThat(call(instrumented, name, args)).as(name + Arrays.deepToString(args))
                    .isEqualTo(call(original, name, args));
        }
        assertThat((int[]) call(instrumented, "squares", 5)).containsExactly(0, 1, 4, 9, 16);
        Object[][] grid = (Object[][]) call(instrumented, "multiArray", 2, 3);
        assertThat(grid[1][2]).isEqualTo("corner");
        assertThat(instrumented.getClass().getField("finallyCount").getInt(instrumented)).isEqualTo(2);
        assertThat(instrumented.getClass().getField("constructorCalls").getInt(instrumented)).isEqualTo(1);
    }

    @Test
    void exceptionsAreUnchanged() throws Exception {
        Object instrumented = newInstrumented();

        assertThatThrownBy(() -> call(instrumented, "fail", "boom"))
                .isExactlyInstanceOf(IllegalStateException.class).hasMessage("boom");
        assertThatThrownBy(() -> call(instrumented, "rethrow", "io"))
                .isExactlyInstanceOf(Exception.class).hasMessage("wrapped io")
                .cause().isExactlyInstanceOf(java.io.IOException.class).hasMessage("io");
        assertThatThrownBy(() -> call(instrumented, "returnsChar", ""))
                .isInstanceOf(StringIndexOutOfBoundsException.class);
    }

    @Test
    void worksWithoutDecorators() throws Throwable {
        Object instrumented = newInstrumented();

        assertThat(call(instrumented, "add", 1, 1)).isEqualTo(2);
    }

    // ------------------------------------------------------------------------------------------------------------
    // hooks
    // ------------------------------------------------------------------------------------------------------------

    @Test
    void enterAndLeaveOncePerCallOnEveryReturnPath() throws Throwable {
        Recorder recorder = decorate("branches");
        Object instrumented = newInstrumented();

        call(instrumented, "branches", -1);
        call(instrumented, "branches", 0);
        call(instrumented, "branches", 1);

        assertThat(recorder.events).containsExactly("enter", "leave", "enter", "leave", "enter", "leave");
        assertThat(recorder.thrown).allMatch(t -> t instanceof NoThrow);
        assertThat(recorder.selves).allMatch(self -> self == instrumented);
    }

    @Test
    void voidMethodIsDecorated() throws Throwable {
        Recorder recorder = decorate("noop");

        call(newInstrumented(), "noop");

        assertThat(recorder.events).containsExactly("enter", "leave");
    }

    @Test
    void decoratorsAreMatchedByMethodName() throws Throwable {
        Recorder echo = decorate("echo");
        Recorder add = decorate("add");

        call(newInstrumented(), "add", 1, 2);

        assertThat(echo.events).isEmpty();
        assertThat(add.events).containsExactly("enter", "leave");
    }

    @Test
    void leaveReceivesThrownExceptionWhichIsRethrown() throws Exception {
        Recorder recorder = decorate("fail");
        Object instrumented = newInstrumented();

        Throwable thrown = null;
        try {
            call(instrumented, "fail", "boom");
        } catch (Throwable t) {
            thrown = t;
        }

        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        assertThat(recorder.events).containsExactly("enter", "leave");
        assertThat(recorder.thrown).containsExactly(thrown);
    }

    @Test
    void exceptionCaughtInsideMethodIsNotReported() throws Throwable {
        Recorder recorder = decorate("catchInside");

        assertThat(call(newInstrumented(), "catchInside", "not a number")).isEqualTo("text");

        assertThat(recorder.events).containsExactly("enter", "leave");
        assertThat(recorder.thrown).allMatch(t -> t instanceof NoThrow);
    }

    @Test
    void argumentsAreRegisteredWithNamesTypesAndValues() throws Throwable {
        Recorder recorder = decorate("primitives");
        List<Object> seen = new ArrayList<>();
        recorder.onEnter = context -> {
            try {
                seen.add(context.getMethodArgument("flag", boolean.class));
                seen.add(context.getMethodArgument("b", byte.class));
                seen.add(context.getMethodArgument("c", char.class));
                seen.add(context.getMethodArgument("s", short.class));
                seen.add(context.getMethodArgument("i", int.class));
                seen.add(context.getMethodArgument("l", long.class));
                seen.add(context.getMethodArgument("f", float.class));
                seen.add(context.getMethodArgument("d", double.class));
                seen.add(context.hasMethodArgument("i", Integer.class));
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        };

        call(newInstrumented(), "primitives", true, (byte) 1, 'x', (short) 2, 3, 4L, 5.5f, 6.25);

        assertThat(seen).containsExactly(true, (byte) 1, 'x', (short) 2, 3, 4L, 5.5f, 6.25, false);
    }

    @Test
    void objectArgumentsUseDeclaredType() throws Throwable {
        Recorder recorder = decorate("pattern");
        List<Boolean> seen = new ArrayList<>();
        recorder.onEnter = context -> {
            seen.add(context.hasMethodArgument("value", Object.class));
            seen.add(context.hasMethodArgument("value", String.class));
        };

        call(newInstrumented(), "pattern", "text");

        // registered as the declared parameter type, not the runtime type
        assertThat(seen).containsExactly(true, false);
    }

    @Test
    void decoratorCanReplaceArguments() throws Throwable {
        decorate("add").onEnter = context -> context.registerMethodArgument("a", 40, int.class);
        decorate("echo").onEnter = context -> context.registerMethodArgument("text", "replaced", String.class);
        decorate("wide").onEnter = context -> {
            context.registerMethodArgument("big", 7L, long.class);
            context.registerMethodArgument("ratio", 2.0, double.class);
            context.registerMethodArgument("label", "X", String.class);
        };
        decorate("returnsBoolean").onEnter = context -> context.registerMethodArgument("value", 5, int.class);
        Object instrumented = newInstrumented();

        assertThat(call(instrumented, "add", 1, 2)).isEqualTo(42);
        assertThat(call(instrumented, "echo", "original")).isEqualTo("replaced");
        assertThat(call(instrumented, "wide", 5L, 3, 0.5, "L")).isEqualTo("X:7/14/6.0");
        assertThat(call(instrumented, "returnsBoolean", -1)).isEqualTo(true);
    }

    @Test
    void localVariablesAreVisibleOnLeave() throws Throwable {
        Recorder recorder = decorate("locals");
        List<Object> seen = new ArrayList<>();
        recorder.onLeave = context -> {
            try {
                seen.add(context.getLocalVariable("builder", StringBuilder.class).toString());
                seen.add(context.getLocalVariable("suffix", String.class));
                seen.add(context.getLocalVariable("result", CharSequence.class));
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        };

        call(newInstrumented(), "locals", "x");

        assertThat(seen).containsExactly("x!", "!", "x!");
    }

    @Test
    void localsAfterWideArgumentsHaveCorrectValues() throws Throwable {
        Recorder recorder = decorate("wide");
        List<Object> seen = new ArrayList<>();
        recorder.onLeave = context -> {
            try {
                seen.add(context.getLocalVariable("combined", String.class));
                seen.add(context.getLocalVariable("result", String.class));
                // only reference locals (ASTORE) are registered
                seen.add(context.hasLocalVariable("doubled"));
                seen.add(context.hasLocalVariable("scaled"));
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        };

        call(newInstrumented(), "wide", 5L, 3, 0.5, "L");

        assertThat(seen).containsExactly("L:5", "L:5/10/1.5", false, false);
    }

    @Test
    void localsInScopesSharingASlotKeepTheirNames() throws Throwable {
        Recorder recorder = decorate("scopes");
        List<String> names = new ArrayList<>();
        recorder.onLeave = context -> {
            if (context.hasLocalVariable("first")) names.add("first");
            if (context.hasLocalVariable("second")) names.add("second");
        };
        Object instrumented = newInstrumented();

        call(instrumented, "scopes", true);
        call(instrumented, "scopes", false);

        assertThat(names).containsExactly("first", "second");
    }

    @Test
    void nullLocalIsRegisteredAsObject() throws Throwable {
        Recorder recorder = decorate("nullLocal");
        List<Object> seen = new ArrayList<>();
        recorder.onLeave = context -> {
            seen.add(context.hasLocalVariable("nothing"));
            seen.add(context.hasLocalVariable("nothing", Object.class));
            seen.add(context.hasLocalVariable("nothing", String.class));
        };

        call(newInstrumented(), "nullLocal");

        assertThat(seen).containsExactly(true, true, false);
    }

    @Test
    void localsInsideSynchronizedBlockAreVisible() throws Throwable {
        Recorder recorder = decorate("synchronizedBlock");
        List<Object> seen = new ArrayList<>();
        recorder.onLeave = context -> seen.add(context.hasLocalVariable("doubled", String.class));

        call(newInstrumented(), "synchronizedBlock", "ab");

        assertThat(seen).containsExactly(true);
    }

    @Test
    void nestedCallsGetTheirOwnContexts() throws Throwable {
        Recorder nested = decorate("nested");
        Recorder echo = decorate("echo");
        List<Object> echoed = new ArrayList<>();
        echo.onEnter = context -> {
            try {
                echoed.add(context.getMethodArgument("text", String.class));
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        };

        assertThat(call(newInstrumented(), "nested", "ab")).isEqualTo("ab|AB");

        assertThat(nested.events).containsExactly("enter", "leave");
        assertThat(echo.events).containsExactly("enter", "leave", "enter", "leave");
        assertThat(echoed).containsExactly("ab", "AB");
        assertThat(echo.contexts.get(0)).isNotSameAs(echo.contexts.get(1)).isNotSameAs(nested.contexts.getFirst());
    }

    @Test
    void privateMethodsAreDecorated() throws Throwable {
        Recorder recorder = decorate("hidden");

        call(newInstrumented(), "callsHidden", "x");

        assertThat(recorder.events).containsExactly("enter", "leave");
    }

    @Test
    void staticMethodsAndConstructorsAreNotDecorated() throws Throwable {
        Recorder staticMethod = decorate("staticMethod");
        Recorder constructor = decorate("<init>");

        call(newInstrumented(), "staticMethod", "x");

        assertThat(staticMethod.events).isEmpty();
        assertThat(constructor.events).isEmpty();
    }

    @Test
    void inheritedMethodIsDecoratedForDeclaringClass() throws Throwable {
        Recorder recorder = decorate(SUBJECT, "echo");
        Object child = newInstrumentedChild();

        assertThat(call(child, "echo", "x")).isEqualTo("x");

        assertThat(recorder.events).containsExactly("enter", "leave");
        assertThat(recorder.selves).containsExactly(child);
    }

    @Test
    void subclassOwnMethodsUseSubclassName() throws Throwable {
        Recorder recorder = decorate(CHILD, "own");

        call(newInstrumentedChild(), "own", "x");

        assertThat(recorder.events).containsExactly("enter", "leave");
    }

    @Test
    void failingDecoratorDoesNotBreakMethod() throws Throwable {
        Recorder recorder = decorate("add");
        recorder.onEnter = _ -> {
            throw new RuntimeException("broken extension on enter");
        };
        recorder.onLeave = context -> {
            try {
                context.getLocalVariable("doesNotExist", String.class);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };

        try (ExpectedLog log = ExpectedLog.capture(ExtensionServiceImpl.class)) {
            assertThat(call(newInstrumented(), "add", 2, 2)).isEqualTo(4);

            // both failures are reported against the extension, with their cause
            assertThat(log.errors()).hasSize(2).allMatch(m -> m.contains(Recorder.class.getName()) && m.contains("add"));
            assertThat(log.entries()).extracting(e -> e.throwable().getMessage())
                    .anyMatch(m -> m.contains("broken extension on enter"))
                    .anyMatch(m -> m.contains("doesNotExist"));
        }
        assertThat(recorder.events).containsExactly("enter", "leave");
    }

    @Test
    void unregisteredDecoratorIsNotCalled() throws Throwable {
        Recorder recorder = decorate("add");
        Object instrumented = newInstrumented();
        call(instrumented, "add", 1, 1);

        extensionService.unregisterDecorator(recorder);
        call(instrumented, "add", 1, 1);

        assertThat(recorder.events).containsExactly("enter", "leave");
    }

    @Test
    void concurrentCallsDoNotShareContexts() throws Throwable {
        Recorder recorder = decorate("echo");
        List<String> mismatches = new CopyOnWriteArrayList<>();
        recorder.onLeave = context -> {
            try {
                String text = context.getMethodArgument("text", String.class);
                if (!Objects.equals(text, Thread.currentThread().getName())) {
                    mismatches.add(text);
                }
            } catch (Exception e) {
                mismatches.add(e.getMessage());
            }
        };
        Object instrumented = newInstrumented();

        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            Thread thread = new Thread(() -> {
                for (int j = 0; j < 200; j++) {
                    try {
                        call(instrumented, "echo", Thread.currentThread().getName());
                    } catch (Throwable t) {
                        mismatches.add(t.toString());
                    }
                }
            }, "worker-" + i);
            threads.add(thread);
            thread.start();
        }
        for (Thread thread : threads) {
            thread.join();
        }

        assertThat(mismatches).isEmpty();
        assertThat(recorder.events).hasSize(8 * 200 * 2);
    }

    // ------------------------------------------------------------------------------------------------------------
    // return values
    // ------------------------------------------------------------------------------------------------------------

    @Test
    void decoratorReplacesReturnValue() throws Throwable {
        Recorder recorder = decorate("echo");
        recorder.onLeave = context -> context.setCurrentReturn("changed " + context.getCurrentReturn(String.class));

        assertThat(call(newInstrumented(), "echo", "text")).isEqualTo("changed text");
        assertThat(recorder.events).containsExactly("enter", "leave");
    }

    @Test
    void returnValueIsChainedThroughTheDecoratorsInOrder() throws Throwable {
        List<String> seen = new ArrayList<>();
        Recorder first = decorate("echo");
        first.onLeave = context -> {
            seen.add("first original=" + context.getOriginalReturn(String.class) + " current=" + context.getCurrentReturn(String.class));
            context.setCurrentReturn(context.getCurrentReturn(String.class) + "-a");
        };
        Recorder second = decorate("echo");
        second.onLeave = context -> {
            seen.add("second original=" + context.getOriginalReturn(String.class) + " current=" + context.getCurrentReturn(String.class));
            context.setCurrentReturn(context.getCurrentReturn(String.class) + "-b");
        };

        assertThat(call(newInstrumented(), "echo", "x")).isEqualTo("x-a-b");
        assertThat(seen).containsExactly("first original=x current=x", "second original=x current=x-a");
    }

    @Test
    void originalReturnIsKeptWhenTheReturnIsReplaced() throws Throwable {
        List<Object> originals = new ArrayList<>();
        Recorder recorder = decorate("echo");
        recorder.onLeave = context -> {
            context.setCurrentReturn("one");
            context.setCurrentReturn("two");
            originals.add(context.getOriginalReturn(String.class));
            originals.add(context.getCurrentReturn(String.class));
        };

        assertThat(call(newInstrumented(), "echo", "text")).isEqualTo("two");
        assertThat(originals).containsExactly("text", "two");
    }

    @Test
    void returnValueIsNullAndCanBeSetToNull() throws Throwable {
        Recorder recorder = decorate("echo");
        recorder.onLeave = context -> context.setCurrentReturn(null);

        assertThat(call(newInstrumented(), "echo", "text")).isNull();

        recorder.onLeave = context -> {
            assertThat(context.getOriginalReturn(String.class)).isNull();
            context.setCurrentReturn("filled");
        };
        assertThat(call(newInstrumented(), "echo", (Object) null)).isEqualTo("filled");
    }

    @Test
    void primitiveReturnValuesCanBeReadAndReplaced() throws Throwable {
        List<Object> originals = new ArrayList<>();
        decorate("add").onLeave = context -> {
            originals.add(context.getOriginalReturn(int.class));
            context.setCurrentReturn(context.getCurrentReturn(int.class) * 10);
        };
        decorate("returnsLong").onLeave = context -> {
            originals.add(context.getOriginalReturn(long.class));
            context.setCurrentReturn(Long.MAX_VALUE);
        };
        decorate("returnsBoolean").onLeave = context -> {
            originals.add(context.getOriginalReturn(boolean.class));
            context.setCurrentReturn(false);
        };
        decorate("returnsChar").onLeave = context -> {
            originals.add(context.getOriginalReturn(char.class));
            context.setCurrentReturn('z');
        };
        decorate("scale").onLeave = context -> {
            originals.add(context.getOriginalReturn(double.class));
            context.setCurrentReturn(0.5d);
        };
        Object subject = newInstrumented();

        assertThat(call(subject, "add", 2, 3)).isEqualTo(50);
        assertThat(call(subject, "returnsLong", 41L)).isEqualTo(Long.MAX_VALUE);
        assertThat(call(subject, "returnsBoolean", 5)).isEqualTo(false);
        assertThat(call(subject, "returnsChar", "abc")).isEqualTo('z');
        assertThat(call(subject, "scale", 4.0d, 2.0f)).isEqualTo(0.5d);
        assertThat(originals).containsExactly(5, 42L, true, 'a', 8.0d);
    }

    @Test
    void arrayReturnValueCanBeReplaced() throws Throwable {
        decorate("squares").onLeave = context -> {
            int[] original = context.getOriginalReturn(int[].class);
            context.setCurrentReturn(new int[]{original.length});
        };

        assertThat((int[]) call(newInstrumented(), "squares", 4)).containsExactly(4);
    }

    @Test
    void everyReturnOfAMethodGoesThroughTheDecorators() throws Throwable {
        Recorder recorder = decorate("branches");
        recorder.onLeave = context -> context.setCurrentReturn(context.getCurrentReturn(String.class).toUpperCase());
        Object subject = newInstrumented();

        assertThat(call(subject, "branches", -1)).isEqualTo("NEGATIVE");
        assertThat(call(subject, "branches", 0)).isEqualTo("ZERO");
        assertThat(call(subject, "branches", 1)).isEqualTo("POSITIVE");
        assertThat(recorder.events).hasSize(6);
    }

    @Test
    void returnValueIsNotThereOnEnterVoidAndExceptions() throws Throwable {
        List<String> seen = new ArrayList<>();
        Recorder echo = decorate("echo");
        echo.onEnter = context -> seen.add("enter " + context.hasReturn());
        echo.onLeave = context -> seen.add("leave " + context.hasReturn());
        Recorder noop = decorate("noop");
        noop.onLeave = context -> seen.add("void " + context.hasReturn());
        Recorder rethrow = decorate("rethrow");
        rethrow.onLeave = context -> seen.add("throws " + context.hasReturn());
        Object subject = newInstrumented();

        call(subject, "echo", "x");
        call(subject, "noop");
        assertThatThrownBy(() -> call(subject, "rethrow", "x")).hasMessage("wrapped x");

        assertThat(seen).containsExactly("enter false", "leave true", "void false", "throws false");
    }

    @Test
    void invalidUseOfReturnValueIsRefusedAndTheMethodIsUnaffected() throws Throwable {
        Recorder wrongValue = decorate("echo");
        wrongValue.onLeave = context -> context.setCurrentReturn(42);
        Recorder wrongType = decorate("add");
        wrongType.onLeave = context -> context.getOriginalReturn(Integer.class);
        Recorder nullPrimitive = decorate("returnsLong");
        nullPrimitive.onLeave = context -> context.setCurrentReturn(null);
        Recorder wrongPrimitive = decorate("returnsBoolean");
        wrongPrimitive.onLeave = context -> context.setCurrentReturn(1);
        Recorder voidMethod = decorate("noop");
        voidMethod.onLeave = context -> context.setCurrentReturn("anything");
        Recorder onEnter = decorate("scale");
        onEnter.onEnter = context -> context.getCurrentReturn(double.class);
        Object subject = newInstrumented();

        try (ExpectedLog log = ExpectedLog.capture(ExtensionServiceImpl.class)) {
            assertThat(call(subject, "echo", "text")).isEqualTo("text");
            assertThat(call(subject, "add", 1, 2)).isEqualTo(3);
            assertThat(call(subject, "returnsLong", 1L)).isEqualTo(2L);
            assertThat(call(subject, "returnsBoolean", 1)).isEqualTo(true);
            call(subject, "noop");
            assertThat(call(subject, "scale", 1.0d, 2.0f)).isEqualTo(2.0d);

            // onMethodLeave: a refused request is a warning of the extension, onMethodEnter: an error
            assertThat(log.warnings()).filteredOn(m -> m.contains("skipped on")).hasSize(5);
            assertThat(log.warnings()).anyMatch(m -> m.contains("set java.lang.Integer"));
            assertThat(log.warnings()).anyMatch(m -> m.contains("expected java.lang.Integer"));
            assertThat(log.warnings()).anyMatch(m -> m.contains("set null"));
            assertThat(log.warnings()).anyMatch(m -> m.contains("which has none yet"));
            assertThat(log.errors()).hasSize(1).allMatch(m -> m.contains("InstrumentedSubject.scale"));
        }
    }

    @Test
    void returnValueOfASubclassOfTheDeclaredTypeIsAccepted() throws Throwable {
        // the type asked for may be a supertype of the declared one
        Recorder recorder = decorate("echo");
        recorder.onLeave = context -> {
            CharSequence original = context.getOriginalReturn(CharSequence.class);
            context.setCurrentReturn(new StringBuilder(original).reverse().toString());
        };

        assertThat(call(newInstrumented(), "echo", "abc")).isEqualTo("cba");
    }
}

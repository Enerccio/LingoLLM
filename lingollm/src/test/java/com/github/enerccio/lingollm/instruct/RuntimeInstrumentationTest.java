package com.github.enerccio.lingollm.instruct;

import com.github.enerccio.lingollm.domain.service.ExtensionService;
import com.github.enerccio.lingollm.domain.service.ExtensionService.ExtendableMethodContext;
import com.github.enerccio.lingollm.domain.service.ExtensionService.ExtensionDecorator;
import com.github.enerccio.lingollm.domain.service.impl.ExtensionServiceImpl;
import com.github.enerccio.lingollm.instruct.fixture.agent.AgentEarlySubject;
import com.github.enerccio.lingollm.instruct.fixture.agent.AgentPlainSubject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The real agent installed by {@link RuntimeInstrumentationInitializer}: {@code @Extendable} classes are instrumented
 * whether they were loaded before (retransformation) or after (load-time transformation) the installation, and
 * decorators registered the way plugins do it see arguments and locals.
 */
class RuntimeInstrumentationTest {

    private static final String LATE = "com.github.enerccio.lingollm.instruct.fixture.agent.AgentLateSubject";
    private static boolean installed;

    private ExtensionServiceImpl extensionService;
    private ExtensionService previousService;
    private final List<ExtensionDecorator> registered = new ArrayList<>();

    @BeforeAll
    static void installAgent() throws Exception {
        // load one subject before installation, so it has to be retransformed
        assertThat(new AgentEarlySubject().greet("x")).isEqualTo("hello x");
        synchronized (RuntimeInstrumentationTest.class) {
            if (!installed) {
                // installing twice would register two transformers
                new RuntimeInstrumentationInitializer().afterPropertiesSet();
                installed = true;
            }
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        extensionService = new ExtensionServiceImpl();
        previousService = ExtendableMethodVisitorTest.setHolder(extensionService);
    }

    @AfterEach
    void tearDown() throws Exception {
        registered.forEach(extensionService::unregisterDecorator);
        ExtendableMethodVisitorTest.setHolder(previousService);
    }

    private List<String> decorate(String className, String method, ContextAction onEnter, ContextAction onLeave) {
        List<String> events = new ArrayList<>();
        ExtensionDecorator decorator = new ExtensionDecorator() {
            @Override
            public void onMethodEnter(Object instrumented, ExtendableMethodContext context) throws Exception {
                events.add("enter");
                onEnter.accept(context, null);
            }

            @Override
            public void onMethodLeave(Object instrumented, ExtendableMethodContext context, Throwable throwing) throws Exception {
                events.add("leave" + (throwing == null ? "" : ":" + throwing.getClass().getSimpleName()));
                onLeave.accept(context, throwing);
            }
        };
        extensionService.registerDecorator(decorator, className, method);
        registered.add(decorator);
        return events;
    }

    @FunctionalInterface
    interface ContextAction {
        void accept(ExtendableMethodContext context, Throwable throwing) throws Exception;
    }

    private static final ContextAction NOTHING = (_, _) -> {
    };

    @Test
    void classLoadedBeforeInstallationIsRetransformed() {
        List<String> seen = new ArrayList<>();
        List<String> events = decorate(AgentEarlySubject.class.getName(), "greet",
                (context, _) -> seen.add(context.getMethodArgument("name", String.class)),
                (context, _) -> seen.add(context.getLocalVariable("greeting", StringBuilder.class).toString()));

        assertThat(new AgentEarlySubject().greet("world")).isEqualTo("hello world");

        assertThat(events).containsExactly("enter", "leave");
        assertThat(seen).containsExactly("world", "hello world");
    }

    @Test
    void classLoadedAfterInstallationIsTransformed() throws Exception {
        List<Object> seen = new ArrayList<>();
        List<String> events = decorate(LATE, "build",
                (context, _) -> context.registerMethodArgument("count", 2L, long.class),
                (context, _) -> seen.add(context.getLocalVariable("items", List.class)));

        Object late = Class.forName(LATE).getConstructor().newInstance();
        Object result = late.getClass().getMethod("build", String.class, long.class).invoke(late, "item", 5L);

        assertThat(events).containsExactly("enter", "leave");
        assertThat(result).isEqualTo(List.of("item0", "item1"));
        assertThat(seen).containsExactly(List.of("item0", "item1"));
    }

    @Test
    void returnValuesAreReplacedInRetransformedClasses() {
        decorate(AgentEarlySubject.class.getName(), "greet", NOTHING,
                (context, _) -> context.setCurrentReturn(context.getOriginalReturn(String.class).toUpperCase()));
        decorate(AgentEarlySubject.class.getName(), "divide", NOTHING,
                (context, _) -> context.setCurrentReturn(context.getCurrentReturn(int.class) + 100));

        assertThat(new AgentEarlySubject().greet("world")).isEqualTo("HELLO WORLD");
        assertThat(new AgentEarlySubject().divide(6, 3)).isEqualTo(102);
    }

    @Test
    void exceptionsReachDecoratorsAndCaller() {
        List<String> events = decorate(AgentEarlySubject.class.getName(), "divide", NOTHING, NOTHING);

        assertThatThrownBy(() -> new AgentEarlySubject().divide(1, 0)).isInstanceOf(ArithmeticException.class);
        assertThat(new AgentEarlySubject().divide(6, 3)).isEqualTo(2);

        assertThat(events).containsExactly("enter", "leave:ArithmeticException", "enter", "leave");
    }

    @Test
    void classesWithoutAnnotationAreNotInstrumented() {
        List<String> events = decorate(AgentPlainSubject.class.getName(), "greet", NOTHING, NOTHING);

        assertThat(new AgentPlainSubject().greet("x")).isEqualTo("hi x");

        assertThat(events).isEmpty();
    }
}

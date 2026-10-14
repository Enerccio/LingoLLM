package com.github.enerccio.lingollm.instruct.fixture.verify;

import com.github.enerccio.lingollm.domain.service.ExtensionService;
import com.github.enerccio.lingollm.domain.service.ExtensionService.ExtendableMethodContext;
import com.github.enerccio.lingollm.domain.service.ExtensionService.ExtensionDecorator;

import java.util.List;

/**
 * Extensions as the verifier sees them: classes that register decorators for {@link VerifyTarget}. They are never run,
 * the tests read their class files.
 */
public final class VerifyFixtures {

    static final String PACKAGE = "com.github.enerccio.lingollm.instruct.fixture.verify.";
    static final String TARGET = PACKAGE + "VerifyTarget";
    static final String CARD = TARGET + "$Card";

    private VerifyFixtures() {
    }

    /**
     * Everything it asks for exists.
     */
    public static class Valid {

        private ExtensionDecorator renderDecorator;
        private ExtensionDecorator cardDecorator;

        public void load(ExtensionService service) throws Exception {
            renderDecorator = new ExtensionDecorator() {
                @Override
                public void onMethodEnter(Object instrumented, ExtendableMethodContext context) throws Exception {
                    context.getMethodArgument("name", String.class);
                    context.getMethodArgument("count", int.class);
                    context.getMethodArgument("values", List.class);
                    context.hasMethodArgument("absent");
                    context.registerMethodArgument("count", 5, int.class);
                }

                @Override
                public void onMethodLeave(Object instrumented, ExtendableMethodContext context, Throwable throwing) throws Exception {
                    context.getLocalVariable("out", StringBuilder.class);
                    context.getLocalVariable("out", CharSequence.class);
                    context.hasLocalVariable("absent", String.class);
                    context.getReflectiveFieldValue(instrumented, "title", String.class);
                    context.getReflectiveFieldValue(instrumented, "items", List.class);
                    context.setReflectiveFieldValue(instrumented, "title", "x", String.class);
                    context.callReflectiveMethod(instrumented, "helper", new Class<?>[]{String.class, int.class},
                            new Object[]{"a", 1}, String.class);
                    context.hasReturn();
                    context.getOriginalReturn(String.class);
                    context.getCurrentReturn(CharSequence.class);
                    context.setCurrentReturn("x");
                }
            };
            service.registerDecorator(renderDecorator, TARGET, "render");

            // the outer instance of an inner class is reached through this$0
            cardDecorator = new ExtensionDecorator() {
                @Override
                public void onMethodEnter(Object instrumented, ExtendableMethodContext context) throws Exception {
                }

                @Override
                public void onMethodLeave(Object instrumented, ExtendableMethodContext context, Throwable throwing) throws Exception {
                    context.getMethodArgument("text", String.class);
                    context.getReflectiveFieldValue(instrumented, "message", String.class);
                    VerifyTarget outer = context.getReflectiveFieldValue(instrumented, "this$0", VerifyTarget.class);
                    context.getReflectiveFieldValue(outer, "items", List.class);
                    context.callReflectiveMethod(outer, "helper", new Class<?>[]{String.class, int.class},
                            new Object[]{"a", 1}, String.class);
                }
            };
            service.registerDecorator(cardDecorator, CARD, "edit");
        }
    }

    /**
     * Targets that don't exist.
     */
    public static class BrokenTargets {

        public void load(ExtensionService service) throws Exception {
            service.registerDecorator(new Nothing(), PACKAGE + "NoSuchClass", "render");
            service.registerDecorator(new Nothing(), PACKAGE + "NotExtendable", "run");
            service.registerDecorator(new Nothing(), TARGET, "nothing");
            service.registerDecorator(new Nothing(), TARGET, "staticMethod");
            service.registerDecorator(new Nothing(), PACKAGE + "VerifyChild", "render");
        }
    }

    public static class Nothing implements ExtensionDecorator {
        @Override
        public void onMethodEnter(Object instrumented, ExtendableMethodContext context) {
        }

        @Override
        public void onMethodLeave(Object instrumented, ExtendableMethodContext context, Throwable throwing) {
        }
    }

    /**
     * Asks the context for things the method doesn't have.
     */
    public static class BrokenAccess {

        public void load(ExtensionService service) throws Exception {
            service.registerDecorator(new ExtensionDecorator() {
                @Override
                public void onMethodEnter(Object instrumented, ExtendableMethodContext context) throws Exception {
                    context.getMethodArgument("nme", String.class);
                    context.getMethodArgument("count", Integer.class);
                    context.hasMethodArgument("name", Integer.class);
                    context.registerMethodArgument("cnt", 1, int.class);
                    context.getLocalVariable("out", StringBuilder.class);
                    context.getCurrentReturn(String.class);
                }

                @Override
                public void onMethodLeave(Object instrumented, ExtendableMethodContext context, Throwable throwing) throws Exception {
                    context.getOriginalReturn(Integer.class);
                    context.getLocalVariable("outt", StringBuilder.class);
                    context.getLocalVariable("out", String.class);
                    context.getLocalVariable("result", String.class);
                    context.hasLocalVariable("out", String.class);
                    context.getReflectiveFieldValue(instrumented, "titel", String.class);
                    context.getReflectiveFieldValue(instrumented, "items", String.class);
                    Object other = System.getProperties();
                    context.getReflectiveFieldValue(other, "anything", String.class);
                    context.callReflectiveMethod(instrumented, "nothing", new Class<?>[0], new Object[0], String.class);
                    context.callReflectiveMethod(instrumented, "helper", new Class<?>[]{String.class}, new Object[]{"a"}, String.class);
                    context.callReflectiveMethod(instrumented, "helper", new Class<?>[]{String.class, int.class},
                            new Object[]{"a", 1}, Integer.class);
                }
            }, TARGET, "render");

            service.registerDecorator(new ExtensionDecorator() {
                @Override
                public void onMethodEnter(Object instrumented, ExtendableMethodContext context) throws Exception {
                }

                @Override
                public void onMethodLeave(Object instrumented, ExtendableMethodContext context, Throwable throwing) throws Exception {
                    VerifyTarget outer = context.getReflectiveFieldValue(instrumented, "this$0", VerifyTarget.class);
                    context.getReflectiveFieldValue(outer, "nothing", String.class);
                    context.setCurrentReturn("x");
                }
            }, CARD, "edit");
        }
    }

    /**
     * Registrations that can't be followed.
     */
    public static class Dynamic {

        public void load(ExtensionService service, String method) throws Exception {
            service.registerDecorator(new Nothing(), TARGET, method);
            service.registerDecorator(make(), TARGET, "render");
        }

        private ExtensionDecorator make() {
            return null;
        }
    }
}

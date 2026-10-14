package com.github.enerccio.lingollm.instruct;

import net.bytebuddy.jar.asm.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Applies {@link ExtendableMethodVisitor} to class files the same way {@link RuntimeInstrumentationInitializer} does
 * (every declared method except constructors, static and synthetic methods), without installing an agent.
 */
public final class Instrumenter {

    private Instrumenter() {
    }

    public static byte[] originalBytes(Class<?> type) throws IOException {
        try (InputStream in = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
            if (in == null) {
                throw new IOException("Class file of " + type.getName() + " not found");
            }
            return in.readAllBytes();
        }
    }

    /**
     * Same method filter as the agent matcher in {@link RuntimeInstrumentationInitializer}.
     */
    public static boolean isInstrumented(int access, String name) {
        return !name.equals("<init>") && !name.equals("<clinit>")
                && (access & Opcodes.ACC_STATIC) == 0
                && (access & Opcodes.ACC_SYNTHETIC) == 0;
    }

    public static byte[] instrument(byte[] original, ClassLoader resolver) {
        ClassReader reader = new ClassReader(original);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES) {
            @Override
            protected ClassLoader getClassLoader() {
                return resolver;
            }
        };
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            private String owner;

            @Override
            public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                owner = name;
                super.visit(version, access, name, signature, superName, interfaces);
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                MethodVisitor target = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (!isInstrumented(access, name)) {
                    return target;
                }
                // ByteBuddy's MethodDescription.getStackSize(): argument slots including "this"
                int argSlots = Type.getArgumentsAndReturnSizes(descriptor) >> 2;
                return new ExtendableMethodVisitor(Opcodes.ASM9, target, argSlots, owner, name, descriptor);
            }
        }, 0);
        return writer.toByteArray();
    }

    /**
     * Child-first loader for the given classes, everything else comes from the parent - so instrumented classes see
     * the same {@link ExtensionServiceHolder} as the test.
     */
    public static class IsolatedLoader extends ClassLoader {
        private final Map<String, byte[]> classes = new HashMap<>();

        public IsolatedLoader(ClassLoader parent) {
            super(parent);
        }

        public IsolatedLoader add(String name, byte[] bytes) {
            classes.put(name, bytes);
            return this;
        }

        public byte[] bytes(String name) {
            return classes.get(name);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                byte[] bytes = classes.get(name);
                if (bytes == null) {
                    return super.loadClass(name, resolve);
                }
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) {
                    loaded = defineClass(name, bytes, 0, bytes.length);
                }
                if (resolve) {
                    resolveClass(loaded);
                }
                return loaded;
            }
        }
    }
}

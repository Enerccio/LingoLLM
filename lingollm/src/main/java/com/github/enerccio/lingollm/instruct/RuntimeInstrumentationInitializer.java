package com.github.enerccio.lingollm.instruct;

import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.AsmVisitorWrapper;
import net.bytebuddy.jar.asm.ClassWriter;
import net.bytebuddy.jar.asm.Opcodes;
import net.bytebuddy.matcher.ElementMatchers;
import org.springframework.beans.factory.InitializingBean;

public class RuntimeInstrumentationInitializer implements InitializingBean {

    @Override
    public void afterPropertiesSet() throws Exception {
        ByteBuddyAgent.install();

        new AgentBuilder.Default()
                .disableClassFormatChanges() // Required for JVM retransformation of pre-loaded classes
                .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                .with(AgentBuilder.DescriptionStrategy.Default.POOL_ONLY)
                .ignore(ElementMatchers.not(ElementMatchers.nameStartsWith("com.github.enerccio")))
//                .with(AgentBuilder.Listener.StreamWriting.toSystemOut())
                .type(ElementMatchers.isAnnotatedWith(ElementMatchers.named("com.github.enerccio.lingollm.domain.traits.Extendable")))
                .transform((builder, typeDescription, classLoader, module, protectionDomain) -> builder
                        .visit(new AsmVisitorWrapper.ForDeclaredMethods()
                                .writerFlags(ClassWriter.COMPUTE_FRAMES)
                                .method(
                                        ElementMatchers
                                                .not(ElementMatchers.isConstructor())
                                                .and(ElementMatchers.not(ElementMatchers.isSynthetic()))
                                                .and(ElementMatchers.not(ElementMatchers.isStatic())),
                                        (instrumentedType, instrumentedMethod, methodVisitor, implementationContext, typePool, writerFlags, readerFlags) ->
                                                new ExtendableMethodVisitor(
                                                        Opcodes.ASM9,
                                                        methodVisitor,
                                                        instrumentedMethod.getStackSize(),
                                                        instrumentedType.getInternalName(),
                                                        instrumentedMethod.getName(),
                                                        instrumentedMethod.getDescriptor()
                                                )
                                )
                        ))
                .installOnByteBuddyAgent();
    }
}
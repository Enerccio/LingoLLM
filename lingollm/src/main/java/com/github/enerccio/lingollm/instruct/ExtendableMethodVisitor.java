package com.github.enerccio.lingollm.instruct;

import net.bytebuddy.jar.asm.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class ExtendableMethodVisitor extends MethodVisitor {

    private static final String SERVICE_TYPE = "com/github/enerccio/lingollm/domain/service/ExtensionService";
    private static final String SERVICE_DESC = "L" + SERVICE_TYPE + ";";
    private static final String CONTEXT_TYPE = "com/github/enerccio/lingollm/domain/service/ExtensionService$ExtendableMethodContext";
    private static final String CONTEXT_DESC = "L" + CONTEXT_TYPE + ";";
    private static final String REFRESHER_TYPE = "com/github/enerccio/lingollm/domain/service/ExtensionService$MethodArgumentRefresher";
    private static final String REFRESHER_DESC = "L" + REFRESHER_TYPE + ";";

    private final MethodVisitor targetVisitor;
    private final int argSlots;
    private final String ownerClassName;
    private final String ownerName;
    private final String methodDescriptor;
    private final Type[] argumentTypes;
    private final Type returnType;

    private final int serviceSlot;
    private final int contextSlot;
    private final int thrownSlot;
    private final int refresherStartSlot;
    private final int extraSlotsCount;

    private final Map<Integer, String> slotToNameMap = new HashMap<>();
    private final List<LocalVariable> localVariables = new ArrayList<>();
    private final Map<Label, Integer> labelPositions = new HashMap<>();
    private boolean hasCode;
    private final List<String> parameterNames = new ArrayList<>();
    private final List<Consumer<MethodVisitor>> tryCatchBlocks = new ArrayList<>();
    private final List<Consumer<MethodVisitor>> instructions = new ArrayList<>();
    private final List<Consumer<MethodVisitor>> localVarsToReplay = new ArrayList<>();

    private final Label startLabel = new Label();
    private final Label endLabel = new Label();
    private final Label handlerLabel = new Label();

    public ExtendableMethodVisitor(int api, MethodVisitor targetVisitor, int argSlots, String ownerClassName, String ownerName) {
        this(api, targetVisitor, argSlots, ownerClassName, ownerName, "()V");
    }

    public ExtendableMethodVisitor(int api, MethodVisitor targetVisitor, int argSlots, String ownerClassName, String ownerName, String methodDescriptor) {
        super(api, null);
        this.targetVisitor = targetVisitor;
        this.argSlots = argSlots;
        this.ownerClassName = ownerClassName;
        this.ownerName = ownerName;
        this.methodDescriptor = methodDescriptor;
        this.argumentTypes = Type.getArgumentTypes(methodDescriptor != null ? methodDescriptor : "()V");
        this.returnType = Type.getReturnType(methodDescriptor != null ? methodDescriptor : "()V");

        this.serviceSlot = argSlots;
        this.contextSlot = argSlots + 1;
        this.thrownSlot = argSlots + 2;
        this.refresherStartSlot = argSlots + 3;
        this.extraSlotsCount = 3 + this.argumentTypes.length;
    }

    private record LocalVariable(String name, Label start, Label end, int index) {
    }

    // --- 0. Method header (annotations, parameters) is not part of the body, pass it through ---

    @Override
    public void visitParameter(String name, int access) {
        if (name != null) {
            parameterNames.add(name);
        }
        targetVisitor.visitParameter(name, access);
    }

    @Override
    public AnnotationVisitor visitAnnotationDefault() {
        return targetVisitor.visitAnnotationDefault();
    }

    @Override
    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
        return targetVisitor.visitAnnotation(descriptor, visible);
    }

    @Override
    public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
        return targetVisitor.visitTypeAnnotation(typeRef, typePath, descriptor, visible);
    }

    @Override
    public void visitAnnotableParameterCount(int parameterCount, boolean visible) {
        targetVisitor.visitAnnotableParameterCount(parameterCount, visible);
    }

    @Override
    public AnnotationVisitor visitParameterAnnotation(int parameter, String descriptor, boolean visible) {
        return targetVisitor.visitParameterAnnotation(parameter, descriptor, visible);
    }

    @Override
    public void visitAttribute(Attribute attribute) {
        targetVisitor.visitAttribute(attribute);
    }

    @Override
    public void visitCode() {
        hasCode = true;
    }

    // --- 1. Buffer Method Body Instructions ---

    @Override
    public void visitInsn(int opcode) {
        instructions.add(mv -> {
            if (opcode == Opcodes.RETURN) {
                emitLeaveCall(mv);
                mv.visitInsn(opcode);
            } else if (opcode >= Opcodes.IRETURN && opcode <= Opcodes.ARETURN) {
                emitValueReturn(mv);
            } else {
                mv.visitInsn(opcode);
            }
        });
    }

    @Override
    public void visitIntInsn(int opcode, int operand) {
        instructions.add(mv -> mv.visitIntInsn(opcode, operand));
    }

    @Override
    public void visitVarInsn(int opcode, int var) {
        int position = instructions.size();
        instructions.add(mv -> {
            int remappedVar = (var >= argSlots) ? var + extraSlotsCount : var;
            mv.visitVarInsn(opcode, remappedVar);

            if (opcode == Opcodes.ASTORE) {
                String varName = localName(var, position);

                Label skipRegister = new Label();
                mv.visitVarInsn(Opcodes.ALOAD, contextSlot);
                mv.visitJumpInsn(Opcodes.IFNULL, skipRegister);

                mv.visitVarInsn(Opcodes.ALOAD, contextSlot);
                mv.visitLdcInsn(varName);
                mv.visitVarInsn(Opcodes.ALOAD, remappedVar);

                Label nullClassLabel = new Label();
                Label afterClassLabel = new Label();
                mv.visitVarInsn(Opcodes.ALOAD, remappedVar);
                mv.visitJumpInsn(Opcodes.IFNULL, nullClassLabel);
                mv.visitVarInsn(Opcodes.ALOAD, remappedVar);
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Object", "getClass", "()Ljava/lang/Class;", false);
                mv.visitJumpInsn(Opcodes.GOTO, afterClassLabel);

                mv.visitLabel(nullClassLabel);
                mv.visitLdcInsn(Type.getType("Ljava/lang/Object;"));

                mv.visitLabel(afterClassLabel);

                mv.visitMethodInsn(
                        Opcodes.INVOKEINTERFACE,
                        CONTEXT_TYPE,
                        "registerLocalVariable",
                        "(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Class;)V",
                        true
                );

                mv.visitLabel(skipRegister);
            }
        });
    }

    @Override
    public void visitTypeInsn(int opcode, String type) {
        instructions.add(mv -> mv.visitTypeInsn(opcode, type));
    }

    @Override
    public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
        instructions.add(mv -> mv.visitFieldInsn(opcode, owner, name, descriptor));
    }

    @Override
    public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
        instructions.add(mv -> mv.visitMethodInsn(opcode, owner, name, descriptor, isInterface));
    }

    @Override
    public void visitInvokeDynamicInsn(String name, String descriptor, net.bytebuddy.jar.asm.Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
        instructions.add(mv -> mv.visitInvokeDynamicInsn(name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments));
    }

    @Override
    public void visitJumpInsn(int opcode, Label label) {
        instructions.add(mv -> mv.visitJumpInsn(opcode, label));
    }

    @Override
    public void visitLabel(Label label) {
        labelPositions.put(label, instructions.size());
        instructions.add(mv -> mv.visitLabel(label));
    }

    @Override
    public void visitLdcInsn(Object value) {
        instructions.add(mv -> mv.visitLdcInsn(value));
    }

    @Override
    public void visitIincInsn(int var, int increment) {
        instructions.add(mv -> {
            int remappedVar = (var >= argSlots) ? var + extraSlotsCount : var;
            mv.visitIincInsn(remappedVar, increment);
        });
    }

    @Override
    public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
        instructions.add(mv -> mv.visitTableSwitchInsn(min, max, dflt, labels));
    }

    @Override
    public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
        instructions.add(mv -> mv.visitLookupSwitchInsn(dflt, keys, labels));
    }

    @Override
    public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
        instructions.add(mv -> mv.visitMultiANewArrayInsn(descriptor, numDimensions));
    }

    @Override
    public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
        tryCatchBlocks.add(mv -> mv.visitTryCatchBlock(start, end, handler, type));
    }

    @Override
    public void visitLineNumber(int line, Label start) {
        instructions.add(mv -> mv.visitLineNumber(line, start));
    }

    // --- 2. Record Variable Names from LocalVariableTable ---

    @Override
    public void visitLocalVariable(String name, String descriptor, String signature, Label start, Label end, int index) {
        if (name != null && !name.equals("this")) {
            slotToNameMap.put(index, name);
            localVariables.add(new LocalVariable(name, start, end, index));
        }
        localVarsToReplay.add(mv -> {
            int remappedIndex = (index >= argSlots) ? index + extraSlotsCount : index;
            mv.visitLocalVariable(name, descriptor, signature, start, end, remappedIndex);
        });
    }

    // --- 3. Replay Bytecode & Emit Calls at visitEnd ---

    @Override
    public void visitEnd() {
        if (!hasCode) {
            // abstract or native method, nothing to instrument
            targetVisitor.visitEnd();
            super.visitEnd();
            return;
        }

        targetVisitor.visitCode();

        for (Consumer<MethodVisitor> action : tryCatchBlocks) {
            action.accept(targetVisitor);
        }

        targetVisitor.visitTryCatchBlock(startLabel, endLabel, handlerLabel, "java/lang/Throwable");

        targetVisitor.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "com/github/enerccio/lingollm/instruct/ExtensionServiceHolder",
                "getInstance",
                "()Lcom/github/enerccio/lingollm/domain/service/ExtensionService;",
                false
        );
        targetVisitor.visitVarInsn(Opcodes.ASTORE, serviceSlot);

        targetVisitor.visitInsn(Opcodes.ACONST_NULL);
        targetVisitor.visitVarInsn(Opcodes.ASTORE, contextSlot);
        targetVisitor.visitInsn(Opcodes.ACONST_NULL);
        targetVisitor.visitVarInsn(Opcodes.ASTORE, thrownSlot);

        for (int i = 0; i < argumentTypes.length; i++) {
            targetVisitor.visitInsn(Opcodes.ACONST_NULL);
            targetVisitor.visitVarInsn(Opcodes.ASTORE, refresherStartSlot + i);
        }

        Label skipEnter = new Label();
        targetVisitor.visitVarInsn(Opcodes.ALOAD, serviceSlot);
        targetVisitor.visitJumpInsn(Opcodes.IFNULL, skipEnter);

        // 1. Create ExtendableMethodContext on enter
        targetVisitor.visitVarInsn(Opcodes.ALOAD, serviceSlot);
        targetVisitor.visitVarInsn(Opcodes.ALOAD, 0);
        targetVisitor.visitMethodInsn(
                Opcodes.INVOKEINTERFACE,
                SERVICE_TYPE,
                "createContextHolder",
                "(Ljava/lang/Object;)" + CONTEXT_DESC,
                true
        );
        targetVisitor.visitVarInsn(Opcodes.ASTORE, contextSlot);

        // 2. Fill context with method arguments and register MethodArgumentRefresher callbacks
        int currentSlot = 1;
        for (int i = 0; i < argumentTypes.length; i++) {
            Type argType = argumentTypes[i];
            String argName = null;
            if (i < parameterNames.size()) {
                argName = parameterNames.get(i);
            }
            if (argName == null) {
                argName = slotToNameMap.get(currentSlot);
            }
            if (argName == null) {
                argName = "arg" + i;
            }

            targetVisitor.visitVarInsn(Opcodes.ALOAD, contextSlot);
            targetVisitor.visitLdcInsn(argName);
            emitLoadArg(targetVisitor, argType, currentSlot);
            emitBox(targetVisitor, argType);
            emitLoadClass(targetVisitor, argType);

            targetVisitor.visitMethodInsn(
                    Opcodes.INVOKEINTERFACE,
                    CONTEXT_TYPE,
                    "registerMethodArgument",
                    "(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Class;)" + REFRESHER_DESC,
                    true
            );
            targetVisitor.visitVarInsn(Opcodes.ASTORE, refresherStartSlot + i);

            currentSlot += argType.getSize();
        }

        // 3. Call onExtendableMethodEnter with context
        targetVisitor.visitVarInsn(Opcodes.ALOAD, serviceSlot);
        targetVisitor.visitLdcInsn(Type.getObjectType(ownerClassName));
        targetVisitor.visitVarInsn(Opcodes.ALOAD, 0);
        targetVisitor.visitVarInsn(Opcodes.ALOAD, contextSlot);
        targetVisitor.visitLdcInsn(ownerName);
        targetVisitor.visitMethodInsn(
                Opcodes.INVOKEINTERFACE,
                SERVICE_TYPE,
                "onExtendableMethodEnter",
                "(Ljava/lang/Class;Ljava/lang/Object;" + CONTEXT_DESC + "Ljava/lang/String;)V",
                true
        );

        // 4. Refresh argument variables from registered MethodArgumentRefresher callbacks
        currentSlot = 1;
        for (int i = 0; i < argumentTypes.length; i++) {
            Type argType = argumentTypes[i];
            Label skipRefresh = new Label();

            targetVisitor.visitVarInsn(Opcodes.ALOAD, refresherStartSlot + i);
            targetVisitor.visitJumpInsn(Opcodes.IFNULL, skipRefresh);

            targetVisitor.visitVarInsn(Opcodes.ALOAD, refresherStartSlot + i);
            targetVisitor.visitMethodInsn(
                    Opcodes.INVOKEINTERFACE,
                    REFRESHER_TYPE,
                    "get",
                    "()Ljava/lang/Object;",
                    true
            );

            emitUnboxAndStore(targetVisitor, argType, currentSlot);

            targetVisitor.visitLabel(skipRefresh);

            currentSlot += argType.getSize();
        }

        targetVisitor.visitLabel(skipEnter);
        targetVisitor.visitLabel(startLabel);

        for (Consumer<MethodVisitor> action : instructions) {
            action.accept(targetVisitor);
        }

        targetVisitor.visitLabel(endLabel);
        Label skipHandler = new Label();
        targetVisitor.visitJumpInsn(Opcodes.GOTO, skipHandler);

        targetVisitor.visitLabel(handlerLabel);
        targetVisitor.visitVarInsn(Opcodes.ASTORE, thrownSlot);

        emitLeaveCall(targetVisitor);

        targetVisitor.visitVarInsn(Opcodes.ALOAD, thrownSlot);
        targetVisitor.visitInsn(Opcodes.ATHROW);

        targetVisitor.visitLabel(skipHandler);

        for (Consumer<MethodVisitor> action : localVarsToReplay) {
            action.accept(targetVisitor);
        }

        targetVisitor.visitMaxs(0, 0);
        targetVisitor.visitEnd();

        super.visitEnd();
    }

    /**
     * Name of the local variable stored to {@code slot} by the instruction at {@code position}. Javac reuses slots
     * for variables in different scopes, so the slot alone is ambiguous: the matching LocalVariableTable entry is the
     * one whose scope contains the store, or starts right after it (the initializing store).
     */
    private String localName(int slot, int position) {
        LocalVariable best = null;
        int bestStart = -1;
        for (LocalVariable variable : localVariables) {
            if (variable.index() != slot) {
                continue;
            }
            Integer start = labelPositions.get(variable.start());
            Integer end = labelPositions.get(variable.end());
            if (start == null || end == null) {
                continue;
            }
            if (start <= position + 1 && position < end && start > bestStart) {
                best = variable;
                bestStart = start;
            }
        }
        if (best != null) {
            return best.name();
        }
        return slotToNameMap.getOrDefault(slot, "var" + slot);
    }

    private void emitLeaveCall(MethodVisitor mv) {
        Label skipLeave = new Label();
        mv.visitVarInsn(Opcodes.ALOAD, serviceSlot);
        mv.visitJumpInsn(Opcodes.IFNULL, skipLeave);

        mv.visitVarInsn(Opcodes.ALOAD, serviceSlot);
        mv.visitLdcInsn(Type.getObjectType(ownerClassName));
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitVarInsn(Opcodes.ALOAD, contextSlot);
        mv.visitLdcInsn(ownerName);
        mv.visitVarInsn(Opcodes.ALOAD, thrownSlot);

        mv.visitMethodInsn(
                Opcodes.INVOKEINTERFACE,
                SERVICE_TYPE,
                "onExtendableMethodLeave",
                "(Ljava/lang/Class;Ljava/lang/Object;" + CONTEXT_DESC + "Ljava/lang/String;Ljava/lang/Throwable;)V",
                true
        );

        mv.visitLabel(skipLeave);
    }

    /**
     * Return of a value (on the stack): the decorators see it as the original return value and may replace it, what the
     * method returns is the value they leave in the context.
     */
    private void emitValueReturn(MethodVisitor mv) {
        emitBox(mv, returnType);

        Label skipRegister = new Label();
        mv.visitVarInsn(Opcodes.ALOAD, contextSlot);
        mv.visitJumpInsn(Opcodes.IFNULL, skipRegister);
        mv.visitInsn(Opcodes.DUP);
        mv.visitVarInsn(Opcodes.ALOAD, contextSlot);
        mv.visitInsn(Opcodes.SWAP);
        emitLoadClass(mv, returnType);
        mv.visitMethodInsn(
                Opcodes.INVOKEINTERFACE,
                CONTEXT_TYPE,
                "registerReturnValue",
                "(Ljava/lang/Object;Ljava/lang/Class;)V",
                true
        );
        mv.visitLabel(skipRegister);

        emitLeaveCall(mv);

        Label keepOriginal = new Label();
        mv.visitVarInsn(Opcodes.ALOAD, contextSlot);
        mv.visitJumpInsn(Opcodes.IFNULL, keepOriginal);
        mv.visitInsn(Opcodes.POP);
        mv.visitVarInsn(Opcodes.ALOAD, contextSlot);
        mv.visitMethodInsn(
                Opcodes.INVOKEINTERFACE,
                CONTEXT_TYPE,
                "resolveReturnValue",
                "()Ljava/lang/Object;",
                true
        );
        mv.visitLabel(keepOriginal);

        emitUnbox(mv, returnType);
        mv.visitInsn(returnType.getOpcode(Opcodes.IRETURN));
    }

    private void emitLoadArg(MethodVisitor mv, Type type, int slot) {
        switch (type.getSort()) {
            case Type.BOOLEAN:
            case Type.BYTE:
            case Type.CHAR:
            case Type.SHORT:
            case Type.INT:
                mv.visitVarInsn(Opcodes.ILOAD, slot);
                break;
            case Type.LONG:
                mv.visitVarInsn(Opcodes.LLOAD, slot);
                break;
            case Type.FLOAT:
                mv.visitVarInsn(Opcodes.FLOAD, slot);
                break;
            case Type.DOUBLE:
                mv.visitVarInsn(Opcodes.DLOAD, slot);
                break;
            default:
                mv.visitVarInsn(Opcodes.ALOAD, slot);
                break;
        }
    }

    private void emitBox(MethodVisitor mv, Type type) {
        switch (type.getSort()) {
            case Type.BOOLEAN:
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;", false);
                break;
            case Type.BYTE:
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Byte", "valueOf", "(B)Ljava/lang/Byte;", false);
                break;
            case Type.CHAR:
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Character", "valueOf", "(C)Ljava/lang/Character;", false);
                break;
            case Type.SHORT:
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Short", "valueOf", "(S)Ljava/lang/Short;", false);
                break;
            case Type.INT:
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
                break;
            case Type.LONG:
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false);
                break;
            case Type.FLOAT:
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;", false);
                break;
            case Type.DOUBLE:
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false);
                break;
            default:
                break;
        }
    }

    private void emitLoadClass(MethodVisitor mv, Type type) {
        switch (type.getSort()) {
            case Type.BOOLEAN:
                mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/Boolean", "TYPE", "Ljava/lang/Class;");
                break;
            case Type.BYTE:
                mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/Byte", "TYPE", "Ljava/lang/Class;");
                break;
            case Type.CHAR:
                mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/Character", "TYPE", "Ljava/lang/Class;");
                break;
            case Type.SHORT:
                mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/Short", "TYPE", "Ljava/lang/Class;");
                break;
            case Type.INT:
                mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/Integer", "TYPE", "Ljava/lang/Class;");
                break;
            case Type.LONG:
                mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/Long", "TYPE", "Ljava/lang/Class;");
                break;
            case Type.FLOAT:
                mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/Float", "TYPE", "Ljava/lang/Class;");
                break;
            case Type.DOUBLE:
                mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/Double", "TYPE", "Ljava/lang/Class;");
                break;
            default:
                mv.visitLdcInsn(type);
                break;
        }
    }

    private void emitUnboxAndStore(MethodVisitor mv, Type type, int slot) {
        emitUnbox(mv, type);
        mv.visitVarInsn(type.getOpcode(Opcodes.ISTORE), slot);
    }

    /**
     * Object on the stack to a value of the type: wrappers of primitives are unboxed, references are cast.
     */
    private void emitUnbox(MethodVisitor mv, Type type) {
        switch (type.getSort()) {
            case Type.BOOLEAN:
                mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Boolean");
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false);
                break;
            case Type.BYTE:
                mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Byte");
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Byte", "byteValue", "()B", false);
                break;
            case Type.CHAR:
                mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Character");
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Character", "charValue", "()C", false);
                break;
            case Type.SHORT:
                mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Short");
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Short", "shortValue", "()S", false);
                break;
            case Type.INT:
                mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Integer");
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false);
                break;
            case Type.LONG:
                mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Long");
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Long", "longValue", "()J", false);
                break;
            case Type.FLOAT:
                mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Float");
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Float", "floatValue", "()F", false);
                break;
            case Type.DOUBLE:
                mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Double");
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Double", "doubleValue", "()D", false);
                break;
            case Type.ARRAY:
                mv.visitTypeInsn(Opcodes.CHECKCAST, type.getDescriptor());
                break;
            default:
                mv.visitTypeInsn(Opcodes.CHECKCAST, type.getInternalName());
                break;
        }
    }
}

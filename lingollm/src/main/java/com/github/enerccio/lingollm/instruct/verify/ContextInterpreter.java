package com.github.enerccio.lingollm.instruct.verify;

import com.github.enerccio.lingollm.instruct.verify.ContextValue.*;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Interpreter;

import java.util.List;
import java.util.Map;

/**
 * Follows the constants and the objects of an {@code ExtendableMethodContext} through one method, so that the call
 * {@code context.getReflectiveFieldValue(parent, "sidebarList", VerticalLayout.class)} is known to ask for the field
 * {@code sidebarList} of whatever {@code parent} is.
 */
final class ContextInterpreter extends Interpreter<ContextValue> implements Opcodes {

    static final String CONTEXT_TYPE = "com/github/enerccio/lingollm/domain/service/ExtensionService$ExtendableMethodContext";

    private static final Map<String, Type> PRIMITIVE_WRAPPERS = Map.of(
            "java/lang/Boolean", Type.BOOLEAN_TYPE,
            "java/lang/Byte", Type.BYTE_TYPE,
            "java/lang/Character", Type.CHAR_TYPE,
            "java/lang/Short", Type.SHORT_TYPE,
            "java/lang/Integer", Type.INT_TYPE,
            "java/lang/Long", Type.LONG_TYPE,
            "java/lang/Float", Type.FLOAT_TYPE,
            "java/lang/Double", Type.DOUBLE_TYPE,
            "java/lang/Void", Type.VOID_TYPE);

    private final int instrumentedLocal;

    /**
     * @param instrumentedLocal local variable holding the {@code instrumented} argument, -1 when the method has none
     */
    ContextInterpreter(int instrumentedLocal) {
        super(ASM9);
        this.instrumentedLocal = instrumentedLocal;
    }

    @Override
    public ContextValue newValue(Type type) {
        if (type == null) {
            return ContextValue.ONE;
        }
        if (type.getSort() == Type.VOID) {
            return null;
        }
        return ContextValue.unknown(type.getSize());
    }

    @Override
    public ContextValue newParameterValue(boolean isInstanceMethod, int local, Type type) {
        if (local == instrumentedLocal) {
            return ContextValue.INSTRUMENTED;
        }
        return super.newParameterValue(isInstanceMethod, local, type);
    }

    @Override
    public ContextValue newOperation(AbstractInsnNode insn) {
        switch (insn.getOpcode()) {
            case ICONST_M1, ICONST_0, ICONST_1, ICONST_2, ICONST_3, ICONST_4, ICONST_5:
                return new Int(insn.getOpcode() - ICONST_0);
            case BIPUSH, SIPUSH:
                return new Int(((IntInsnNode) insn).operand);
            case LCONST_0, LCONST_1, DCONST_0, DCONST_1:
                return ContextValue.TWO;
            case LDC:
                return constant(((LdcInsnNode) insn).cst);
            case GETSTATIC:
                FieldInsnNode field = (FieldInsnNode) insn;
                Type primitive = PRIMITIVE_WRAPPERS.get(field.owner);
                if (primitive != null && field.name.equals("TYPE") && field.desc.equals("Ljava/lang/Class;")) {
                    return new Cls(primitive);
                }
                return field(field);
            case NEW:
                return new Typed(Type.getObjectType(((TypeInsnNode) insn).desc));
            default:
                // ACONST_NULL, FCONST_*, JSR
                return ContextValue.ONE;
        }
    }

    private static ContextValue constant(Object cst) {
        if (cst instanceof String s) {
            return new Str(s);
        }
        if (cst instanceof Type t) {
            return t.getSort() == Type.METHOD ? ContextValue.ONE : new Cls(t);
        }
        if (cst instanceof Integer i) {
            return new Int(i);
        }
        if (cst instanceof Long || cst instanceof Double) {
            return ContextValue.TWO;
        }
        if (cst instanceof org.objectweb.asm.ConstantDynamic dynamic) {
            return ContextValue.unknown(Type.getType(dynamic.getDescriptor()).getSize());
        }
        return ContextValue.ONE;
    }

    private static ContextValue field(FieldInsnNode field) {
        int size = Type.getType(field.desc).getSize();
        return size == 2 ? ContextValue.TWO : new Member(field.owner, field.name);
    }

    @Override
    public ContextValue copyOperation(AbstractInsnNode insn, ContextValue value) {
        return value;
    }

    @Override
    public ContextValue unaryOperation(AbstractInsnNode insn, ContextValue value) {
        switch (insn.getOpcode()) {
            case LNEG, DNEG, I2L, I2D, F2L, F2D, L2D, D2L:
                return ContextValue.TWO;
            case INEG, FNEG, I2F, I2B, I2C, I2S, F2I, L2I, L2F, D2I, D2F, IINC, ARRAYLENGTH, INSTANCEOF, NEWARRAY:
                return ContextValue.ONE;
            case GETFIELD:
                return field((FieldInsnNode) insn);
            case ANEWARRAY:
                return value instanceof Int length ? new Array(length.value()) : ContextValue.ONE;
            case CHECKCAST:
                // the cast says less than what is known about a value that came from the context
                if (value instanceof ContextValue.FieldValue || value instanceof MethodValue
                        || value instanceof ContextValue.Instrumented) {
                    return value;
                }
                return new Typed(Type.getObjectType(((TypeInsnNode) insn).desc));
            default:
                // branches, switches, returns, athrow, monitors, putstatic
                return null;
        }
    }

    @Override
    public ContextValue binaryOperation(AbstractInsnNode insn, ContextValue value1, ContextValue value2) {
        switch (insn.getOpcode()) {
            case LALOAD, DALOAD, LADD, DADD, LSUB, DSUB, LMUL, DMUL, LDIV, DDIV, LREM, DREM, LSHL, LSHR, LUSHR,
                 LAND, LOR, LXOR:
                return ContextValue.TWO;
            case AALOAD:
                if (value1 instanceof Array array && value2 instanceof Int index && array.get(index.value()) != null) {
                    return array.get(index.value());
                }
                return ContextValue.ONE;
            case IF_ICMPEQ, IF_ICMPNE, IF_ICMPLT, IF_ICMPGE, IF_ICMPGT, IF_ICMPLE, IF_ACMPEQ, IF_ACMPNE, PUTFIELD:
                return null;
            default:
                return ContextValue.ONE;
        }
    }

    @Override
    public ContextValue ternaryOperation(AbstractInsnNode insn, ContextValue value1, ContextValue value2, ContextValue value3) {
        if (insn.getOpcode() == AASTORE && value1 instanceof Array array && value2 instanceof Int index) {
            array.set(index.value(), value3);
        }
        return null;
    }

    @Override
    public ContextValue naryOperation(AbstractInsnNode insn, List<? extends ContextValue> values) {
        if (insn.getOpcode() == MULTIANEWARRAY) {
            return ContextValue.ONE;
        }
        if (insn instanceof InvokeDynamicInsnNode dynamic) {
            return newValue(Type.getReturnType(dynamic.desc));
        }
        MethodInsnNode call = (MethodInsnNode) insn;
        Type returnType = Type.getReturnType(call.desc);
        if (call.owner.equals(CONTEXT_TYPE) && values.size() > 1) {
            ContextValue result = contextResult(call.name, values);
            if (result != null) {
                return result;
            }
        }
        return newValue(returnType);
    }

    /**
     * Receiver is {@code values[0]}, the arguments follow.
     */
    private static ContextValue contextResult(String method, List<? extends ContextValue> values) {
        switch (method) {
            case "getOriginalReturn", "getCurrentReturn":
                if (values.get(1) instanceof Cls cls && (cls.type().getSort() == Type.OBJECT || cls.type().getSort() == Type.ARRAY)) {
                    return new Typed(cls.type());
                }
                return null;
            case "getMethodArgument", "getLocalVariable":
                if (values.size() > 2 && values.get(2) instanceof Cls cls && (cls.type().getSort() == Type.OBJECT || cls.type().getSort() == Type.ARRAY)) {
                    return new Typed(cls.type());
                }
                return null;
            case "getReflectiveFieldValue":
                return values.size() > 2 && values.get(2) instanceof Str name ? new FieldValue(values.get(1), name.value()) : null;
            case "callReflectiveMethod":
                return values.size() > 3 && values.get(2) instanceof Str name
                        ? new MethodValue(values.get(1), name.value(), values.get(3)) : null;
            default:
                return null;
        }
    }

    @Override
    public void returnOperation(AbstractInsnNode insn, ContextValue value, ContextValue expected) {
        // nothing to check
    }

    @Override
    public ContextValue merge(ContextValue value1, ContextValue value2) {
        return value1.equals(value2) ? value1 : ContextValue.unknown(value1.getSize());
    }
}

package dev.skidfuscator.obfuscator.transform.impl.remapper.mixin.util;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Helpers for reading SpongePowered Mixin annotations from ASM trees.
 */
public final class MixinAnnotations {

    public static final String MIXIN = "Lorg/spongepowered/asm/mixin/Mixin;";
    public static final String SHADOW = "Lorg/spongepowered/asm/mixin/Shadow;";
    public static final String OVERWRITE = "Lorg/spongepowered/asm/mixin/Overwrite;";
    public static final String INJECT = "Lorg/spongepowered/asm/mixin/injection/Inject;";
    public static final String REDIRECT = "Lorg/spongepowered/asm/mixin/injection/Redirect;";
    public static final String MODIFY_ARG = "Lorg/spongepowered/asm/mixin/injection/ModifyArg;";
    public static final String MODIFY_VARIABLE = "Lorg/spongepowered/asm/mixin/injection/ModifyVariable;";
    public static final String AT = "Lorg/spongepowered/asm/mixin/injection/At;";
    public static final String MIXIN_CONFIG_PLUGIN = "org/spongepowered/asm/mixin/extensibility/IMixinConfigPlugin";

    private MixinAnnotations() {
    }

    public static boolean hasAnnotation(final List<AnnotationNode> annotations, final String desc) {
        if (annotations == null) {
            return false;
        }
        for (final AnnotationNode annotation : annotations) {
            if (desc.equals(annotation.desc)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isShadowField(final FieldNode field) {
        return hasAnnotation(field.visibleAnnotations, SHADOW)
                || hasAnnotation(field.invisibleAnnotations, SHADOW);
    }

    public static boolean isShadowMethod(final MethodNode method) {
        return hasAnnotation(method.visibleAnnotations, SHADOW)
                || hasAnnotation(method.invisibleAnnotations, SHADOW);
    }

    public static boolean isOverwriteMethod(final MethodNode method) {
        return hasAnnotation(method.visibleAnnotations, OVERWRITE)
                || hasAnnotation(method.invisibleAnnotations, OVERWRITE);
    }

    public static boolean isInjectionHandler(final MethodNode method) {
        return hasAnnotation(method.visibleAnnotations, INJECT)
                || hasAnnotation(method.invisibleAnnotations, INJECT)
                || hasAnnotation(method.visibleAnnotations, REDIRECT)
                || hasAnnotation(method.invisibleAnnotations, REDIRECT)
                || hasAnnotation(method.visibleAnnotations, MODIFY_ARG)
                || hasAnnotation(method.invisibleAnnotations, MODIFY_ARG)
                || hasAnnotation(method.visibleAnnotations, MODIFY_VARIABLE)
                || hasAnnotation(method.invisibleAnnotations, MODIFY_VARIABLE);
    }

    public static List<String> readMixinTargets(final org.objectweb.asm.tree.ClassNode node) {
        final List<AnnotationNode> annotations = new ArrayList<>();
        if (node.visibleAnnotations != null) {
            annotations.addAll(node.visibleAnnotations);
        }
        if (node.invisibleAnnotations != null) {
            annotations.addAll(node.invisibleAnnotations);
        }

        for (final AnnotationNode annotation : annotations) {
            if (!MIXIN.equals(annotation.desc)) {
                continue;
            }
            return readMixinTargetValues(annotation);
        }
        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    private static List<String> readMixinTargetValues(final AnnotationNode annotation) {
        if (annotation.values == null) {
            return Collections.emptyList();
        }

        final List<String> targets = new ArrayList<>();
        for (int i = 0; i < annotation.values.size(); i += 2) {
            final String key = (String) annotation.values.get(i);
            final Object value = annotation.values.get(i + 1);
            if (!"value".equals(key) && !"targets".equals(key)) {
                continue;
            }
            if (value instanceof Type) {
                targets.add(((Type) value).getInternalName());
            } else if (value instanceof String) {
                targets.add(((String) value).replace('.', '/'));
            } else if (value instanceof String[]) {
                for (final String entry : (String[]) value) {
                    targets.add(entry.replace('.', '/'));
                }
            } else if (value instanceof List) {
                for (final Object entry : (List<?>) value) {
                    if (entry instanceof Type) {
                        targets.add(((Type) entry).getInternalName());
                    } else if (entry instanceof String) {
                        targets.add(((String) entry).replace('.', '/'));
                    }
                }
            }
        }
        return targets;
    }

    public static String readAnnotationString(final AnnotationNode annotation, final String key) {
        if (annotation.values == null) {
            return null;
        }
        for (int i = 0; i < annotation.values.size(); i += 2) {
            if (key.equals(annotation.values.get(i))) {
                final Object value = annotation.values.get(i + 1);
                return value == null ? null : value.toString();
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public static List<AnnotationNode> readNestedAnnotations(final AnnotationNode annotation, final String key) {
        if (annotation.values == null) {
            return Collections.emptyList();
        }
        for (int i = 0; i < annotation.values.size(); i += 2) {
            if (!key.equals(annotation.values.get(i))) {
                continue;
            }
            final Object value = annotation.values.get(i + 1);
            if (value instanceof List) {
                return (List<AnnotationNode>) value;
            }
            if (value instanceof AnnotationNode) {
                return Collections.singletonList((AnnotationNode) value);
            }
        }
        return Collections.emptyList();
    }

    public static boolean implementsMixinConfigPlugin(final org.objectweb.asm.tree.ClassNode node) {
        if (node.interfaces != null) {
            for (final String iface : node.interfaces) {
                if (MIXIN_CONFIG_PLUGIN.equals(iface)) {
                    return true;
                }
            }
        }
        return MIXIN_CONFIG_PLUGIN.equals(node.superName);
    }

    public static String toBinaryName(final String internalOrBinary) {
        return internalOrBinary.replace('/', '.');
    }

    public static String toInternalName(final String internalOrBinary) {
        return internalOrBinary.replace('.', '/');
    }

    public static String remapMethodDescriptor(final String descriptor, final java.util.function.BiFunction<String, String, String> typeMapper) {
        final Type methodType = Type.getMethodType(descriptor);
        final Type[] args = methodType.getArgumentTypes();
        final Type[] remappedArgs = new Type[args.length];
        for (int i = 0; i < args.length; i++) {
            remappedArgs[i] = remapType(args[i], typeMapper);
        }
        return Type.getMethodDescriptor(remapType(methodType.getReturnType(), typeMapper), remappedArgs);
    }

    private static Type remapType(final Type type, final java.util.function.BiFunction<String, String, String> typeMapper) {
        if (type.getSort() != Type.OBJECT) {
            return type;
        }
        final String internal = typeMapper.apply(type.getInternalName(), type.getClassName());
        return Type.getObjectType(internal);
    }

    public static String remapMemberReference(final String reference, final java.util.function.Function<String, String> classMapper) {
        if (reference == null || reference.isEmpty()) {
            return reference;
        }
        final int semi = reference.indexOf(';');
        if (semi <= 1 || reference.charAt(0) != 'L') {
            return reference;
        }
        final String owner = reference.substring(1, semi);
        final String tail = reference.substring(semi + 1);
        return 'L' + classMapper.apply(owner) + ';' + tail;
    }

    public static String remapInjectMethodTarget(
            final String target,
            final java.util.function.Function<String, String> classMapper,
            final java.util.function.BiFunction<String, String, String> methodMapper
    ) {
        if (target == null || target.isEmpty()) {
            return target;
        }
        final int open = target.indexOf('(');
        if (open < 0) {
            return target;
        }
        final String head = target.substring(0, open);
        final String descriptor = target.substring(open);
        final int dot = head.lastIndexOf('.');
        if (dot < 0) {
            return target;
        }
        final String owner = head.substring(0, dot).replace('.', '/');
        final String name = head.substring(dot + 1);
        final String mappedOwner = classMapper.apply(owner);
        final String mappedDescriptor = remapMethodDescriptor(descriptor, (internal, ignored) -> classMapper.apply(internal));
        final String mappedName = methodMapper.apply(mappedOwner + '.' + name + mappedDescriptor, name);
        return MixinAnnotations.toBinaryName(mappedOwner) + '.' + mappedName + mappedDescriptor;
    }

    public static boolean looksLikeMixinConfigPath(final String path) {
        final String lower = path.toLowerCase(Locale.ROOT);
        return lower.endsWith(".mixins.json")
                || lower.contains("mixins.") && lower.endsWith(".json");
    }
}

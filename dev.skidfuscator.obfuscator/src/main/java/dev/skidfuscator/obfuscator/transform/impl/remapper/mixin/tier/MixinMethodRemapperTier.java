package dev.skidfuscator.obfuscator.transform.impl.remapper.mixin.tier;

import dev.skidfuscator.obfuscator.Skidfuscator;
import dev.skidfuscator.obfuscator.skidasm.SkidClassNode;
import dev.skidfuscator.obfuscator.transform.impl.remapper.mixin.MixinRemapperContext;
import dev.skidfuscator.obfuscator.transform.impl.remapper.mixin.util.MixinAnnotations;
import dev.skidfuscator.obfuscator.transform.impl.remapper.mixin.util.MixinNameFactory;
import org.mapleir.asm.ClassNode;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import org.topdank.byteengineer.commons.data.JarClassData;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Remaps mixin handler methods and keeps injection annotation targets consistent.
 */
public final class MixinMethodRemapperTier {

    private MixinMethodRemapperTier() {
    }

    public static int remap(
            final Skidfuscator skidfuscator,
            final MixinRemapperContext context,
            final MixinNameFactory names
    ) {
        buildApplicationMethodMappings(skidfuscator, context, names);
        int remapped = 0;
        for (final SkidClassNode mixinClass : context.getMixinClasses()) {
            remapped += remapShadowAndOverwriteMethods(mixinClass, context, names, skidfuscator);
            remapped += remapInjectionHandlers(mixinClass, context, names);
            remapped += remapMethodInsns(mixinClass, context, skidfuscator);
            remapped += remapInjectionAnnotations(mixinClass, context, skidfuscator);
        }
        return remapped;
    }

    private static void buildApplicationMethodMappings(
            final Skidfuscator skidfuscator,
            final MixinRemapperContext context,
            final MixinNameFactory names
    ) {
        for (final JarClassData classData : skidfuscator.getJarContents().getClassContents()) {
            final ClassNode classNode = classData.getClassNode();
            if (!skidfuscator.getClassSource().isApplicationClass(classNode.getName())) {
                continue;
            }
            if (classNode instanceof SkidClassNode && ((SkidClassNode) classNode).isMixin()) {
                continue;
            }
            for (final org.mapleir.asm.MethodNode method : classNode.getMethods()) {
                if (method.getName().startsWith("<")) {
                    continue;
                }
                final String key = classNode.getName() + '.' + method.getName() + method.getDesc();
                if (context.getMethodMappings().containsKey(key)) {
                    continue;
                }
                final String renamed = names.nextMemberName();
                context.getMethodMappings().put(key, renamed);
                skidfuscator.getClassRemapper().add(key, renamed);
                method.node.name = renamed;
            }
        }
    }

    private static int remapShadowAndOverwriteMethods(
            final SkidClassNode mixinClass,
            final MixinRemapperContext context,
            final MixinNameFactory names,
            final Skidfuscator skidfuscator
    ) {
        int remapped = 0;
        final Set<String> targets = new HashSet<>(MixinAnnotations.readMixinTargets(mixinClass.node));
        for (final MethodNode method : mixinClass.node.methods) {
            if (!MixinAnnotations.isShadowMethod(method) && !MixinAnnotations.isOverwriteMethod(method)) {
                continue;
            }
            final String owner = resolveTargetOwner(targets, mixinClass.getName());
            final String key = owner + '.' + method.name + method.desc;
            final String mapped = context.mapMethod(owner, method.name, method.desc);
            if (!method.name.equals(mapped)) {
                context.getMethodMappings().put(mixinClass.getName() + '.' + method.name + method.desc, mapped);
                method.name = mapped;
                remapped++;
            } else if (context.getMethodMappings().containsKey(key)) {
                final String existing = context.getMethodMappings().get(key);
                context.getMethodMappings().put(mixinClass.getName() + '.' + method.name + method.desc, existing);
                method.name = existing;
                remapped++;
            } else if (shouldRemapOwner(skidfuscator, owner)) {
                final String generated = names.nextMemberName();
                context.getMethodMappings().put(key, generated);
                context.getMethodMappings().put(mixinClass.getName() + '.' + method.name + method.desc, generated);
                skidfuscator.getClassRemapper().add(key, generated);
                method.name = generated;
                remapped++;
            }
        }
        return remapped;
    }

    private static int remapInjectionHandlers(
            final SkidClassNode mixinClass,
            final MixinRemapperContext context,
            final MixinNameFactory names
    ) {
        int remapped = 0;
        for (final MethodNode method : mixinClass.node.methods) {
            if (!MixinAnnotations.isInjectionHandler(method)) {
                continue;
            }
            if (MixinAnnotations.isShadowMethod(method) || MixinAnnotations.isOverwriteMethod(method)) {
                continue;
            }
            final String key = mixinClass.getName() + '.' + method.name + method.desc;
            if (context.getMethodMappings().containsKey(key)) {
                continue;
            }
            final String renamed = names.nextMemberName();
            context.getMethodMappings().put(key, renamed);
            method.name = renamed;
            remapped++;
        }
        return remapped;
    }

    private static int remapMethodInsns(
            final SkidClassNode mixinClass,
            final MixinRemapperContext context,
            final Skidfuscator skidfuscator
    ) {
        int remapped = 0;
        for (final MethodNode method : mixinClass.node.methods) {
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (!(insn instanceof MethodInsnNode)) {
                    continue;
                }
                final MethodInsnNode methodInsn = (MethodInsnNode) insn;
                if (!shouldRemapOwner(skidfuscator, methodInsn.owner)) {
                    continue;
                }
                final String mappedOwner = context.mapClass(methodInsn.owner);
                final String mappedName = context.mapMethod(methodInsn.owner, methodInsn.name, methodInsn.desc);
                final String mappedDesc = MixinAnnotations.remapMethodDescriptor(
                        methodInsn.desc,
                        (internal, ignored) -> context.mapClass(internal)
                );
                if (!methodInsn.owner.equals(mappedOwner)
                        || !methodInsn.name.equals(mappedName)
                        || !methodInsn.desc.equals(mappedDesc)) {
                    methodInsn.owner = mappedOwner;
                    methodInsn.name = mappedName;
                    methodInsn.desc = mappedDesc;
                    remapped++;
                }
            }
        }
        return remapped;
    }

    private static int remapInjectionAnnotations(
            final SkidClassNode mixinClass,
            final MixinRemapperContext context,
            final Skidfuscator skidfuscator
    ) {
        int remapped = 0;
        for (final MethodNode method : mixinClass.node.methods) {
            remapped += remapMethodAnnotations(method.visibleAnnotations, context, skidfuscator);
            remapped += remapMethodAnnotations(method.invisibleAnnotations, context, skidfuscator);
        }
        return remapped;
    }

    private static int remapMethodAnnotations(
            final List<AnnotationNode> annotations,
            final MixinRemapperContext context,
            final Skidfuscator skidfuscator
    ) {
        if (annotations == null) {
            return 0;
        }
        int remapped = 0;
        for (final AnnotationNode annotation : annotations) {
            remapped += remapInjectMethodAttribute(annotation, context, skidfuscator);
            for (final AnnotationNode at : MixinAnnotations.readNestedAnnotations(annotation, "at")) {
                remapped += remapAtTarget(at, context, skidfuscator);
            }
        }
        return remapped;
    }

    private static int remapInjectMethodAttribute(
            final AnnotationNode annotation,
            final MixinRemapperContext context,
            final Skidfuscator skidfuscator
    ) {
        final String methodTarget = MixinAnnotations.readAnnotationString(annotation, "method");
        if (methodTarget == null) {
            return 0;
        }
        final String remapped = MixinAnnotations.remapInjectMethodTarget(
                methodTarget,
                owner -> shouldRemapOwner(skidfuscator, owner) ? context.mapClass(owner) : owner,
                (key, fallback) -> {
                    final int dot = key.lastIndexOf('.');
                    final int open = key.indexOf('(');
                    if (dot < 0 || open < 0) {
                        return fallback;
                    }
                    final String owner = key.substring(0, dot);
                    final String name = key.substring(dot + 1, open);
                    final String desc = key.substring(open);
                    return context.mapMethod(owner, name, desc);
                }
        );
        if (!methodTarget.equals(remapped)) {
            setAnnotationValue(annotation, "method", remapped);
            return 1;
        }
        return 0;
    }

    private static int remapAtTarget(
            final AnnotationNode at,
            final MixinRemapperContext context,
            final Skidfuscator skidfuscator
    ) {
        final String target = MixinAnnotations.readAnnotationString(at, "target");
        if (target == null) {
            return 0;
        }
        final String remapped = MixinAnnotations.remapMemberReference(target, owner -> {
            if (!shouldRemapOwner(skidfuscator, owner)) {
                return owner;
            }
            final String mappedOwner = context.mapClass(owner);
            return mappedOwner;
        });
        final String withMethod = remapMethodInReference(remapped, context, skidfuscator);
        if (!target.equals(withMethod)) {
            setAnnotationValue(at, "target", withMethod);
            return 1;
        }
        return 0;
    }

    private static String remapMethodInReference(
            final String reference,
            final MixinRemapperContext context,
            final Skidfuscator skidfuscator
    ) {
        final int semi = reference.indexOf(';');
        if (semi < 0) {
            return reference;
        }
        final int nameStart = semi + 1;
        final int open = reference.indexOf('(', nameStart);
        if (open < 0) {
            return reference;
        }
        final String owner = reference.substring(1, semi);
        final String name = reference.substring(nameStart, open);
        final String desc = reference.substring(open);
        if (!shouldRemapOwner(skidfuscator, owner)) {
            return reference;
        }
        final String mappedOwner = context.mapClass(owner);
        final String mappedDesc = MixinAnnotations.remapMethodDescriptor(desc, (internal, ignored) -> context.mapClass(internal));
        final String mappedName = context.mapMethod(owner, name, desc);
        return 'L' + mappedOwner + ';' + mappedName + mappedDesc;
    }

    private static void setAnnotationValue(final AnnotationNode annotation, final String key, final Object value) {
        if (annotation.values == null) {
            annotation.values = new java.util.ArrayList<>();
        }
        for (int i = 0; i < annotation.values.size(); i += 2) {
            if (key.equals(annotation.values.get(i))) {
                annotation.values.set(i + 1, value);
                return;
            }
        }
        annotation.values.add(key);
        annotation.values.add(value);
    }

    private static String resolveTargetOwner(final Set<String> targets, final String mixinName) {
        if (targets.size() == 1) {
            return targets.iterator().next();
        }
        return mixinName;
    }

    private static boolean shouldRemapOwner(final Skidfuscator skidfuscator, final String owner) {
        return skidfuscator.getClassSource().isApplicationClass(owner);
    }
}

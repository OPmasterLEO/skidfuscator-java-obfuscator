package dev.skidfuscator.obfuscator.transform.impl.remapper.mixin.tier;

import dev.skidfuscator.obfuscator.Skidfuscator;
import dev.skidfuscator.obfuscator.skidasm.SkidClassNode;
import dev.skidfuscator.obfuscator.transform.impl.remapper.mixin.MixinRemapperContext;
import dev.skidfuscator.obfuscator.transform.impl.remapper.mixin.util.MixinAnnotations;
import dev.skidfuscator.obfuscator.transform.impl.remapper.mixin.util.MixinNameFactory;
import org.mapleir.asm.ClassNode;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.HashSet;
import org.topdank.byteengineer.commons.data.JarClassData;

import java.util.List;
import java.util.Set;

/**
 * Remaps {@code @Shadow} fields and field references inside mixin classes.
 */
public final class MixinFieldRemapperTier {

    private MixinFieldRemapperTier() {
    }

    public static int remap(
            final Skidfuscator skidfuscator,
            final MixinRemapperContext context,
            final MixinNameFactory names
    ) {
        buildApplicationFieldMappings(skidfuscator, context, names);
        int remapped = 0;
        for (final SkidClassNode mixinClass : context.getMixinClasses()) {
            remapped += remapShadowFields(mixinClass, context, names);
            remapped += remapFieldInsns(mixinClass, context, skidfuscator);
            remapped += remapFieldAnnotations(mixinClass, context, skidfuscator);
        }
        return remapped;
    }

    private static void buildApplicationFieldMappings(
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
            for (final org.mapleir.asm.FieldNode field : classNode.getFields()) {
                final String key = classNode.getName() + '.' + field.getName();
                if (context.getFieldMappings().containsKey(key)) {
                    continue;
                }
                if (shouldPreserveMember(skidfuscator, classNode.getName())) {
                    continue;
                }
                final String renamed = names.nextMemberName();
                context.getFieldMappings().put(key, renamed);
                skidfuscator.getClassRemapper().add(key, renamed);
                field.node.name = renamed;
            }
        }
    }

    private static int remapShadowFields(
            final SkidClassNode mixinClass,
            final MixinRemapperContext context,
            final MixinNameFactory names
    ) {
        int remapped = 0;
        final Set<String> targets = new HashSet<>(MixinAnnotations.readMixinTargets(mixinClass.node));
        for (final FieldNode field : mixinClass.node.fields) {
            if (!MixinAnnotations.isShadowField(field)) {
                continue;
            }
            final String owner = resolveShadowOwner(targets, mixinClass.getName());
            final String mapped = context.mapField(owner, field.name);
            if (!field.name.equals(mapped)) {
                context.getFieldMappings().put(mixinClass.getName() + '.' + field.name, mapped);
                field.name = mapped;
                remapped++;
            } else if (context.getFieldMappings().containsKey(owner + '.' + field.name)) {
                final String shadowMapped = context.getFieldMappings().get(owner + '.' + field.name);
                context.getFieldMappings().put(mixinClass.getName() + '.' + field.name, shadowMapped);
                field.name = shadowMapped;
                remapped++;
            } else if (!owner.equals(mixinClass.getName())) {
                final String generated = names.nextMemberName();
                context.getFieldMappings().put(owner + '.' + field.name, generated);
                context.getFieldMappings().put(mixinClass.getName() + '.' + field.name, generated);
                field.name = generated;
                remapped++;
            }
        }
        return remapped;
    }

    private static int remapFieldInsns(
            final SkidClassNode mixinClass,
            final MixinRemapperContext context,
            final Skidfuscator skidfuscator
    ) {
        int remapped = 0;
        for (final MethodNode method : mixinClass.node.methods) {
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (!(insn instanceof FieldInsnNode)) {
                    continue;
                }
                final FieldInsnNode fieldInsn = (FieldInsnNode) insn;
                if (!shouldRemapOwner(skidfuscator, fieldInsn.owner)) {
                    continue;
                }
                final String mappedOwner = context.mapClass(fieldInsn.owner);
                final String mappedName = context.mapField(fieldInsn.owner, fieldInsn.name);
                if (!fieldInsn.owner.equals(mappedOwner) || !fieldInsn.name.equals(mappedName)) {
                    fieldInsn.owner = mappedOwner;
                    fieldInsn.name = mappedName;
                    remapped++;
                }
            }
        }
        return remapped;
    }

    private static int remapFieldAnnotations(
            final SkidClassNode mixinClass,
            final MixinRemapperContext context,
            final Skidfuscator skidfuscator
    ) {
        int remapped = 0;
        for (final MethodNode method : mixinClass.node.methods) {
            remapped += remapAnnotationFields(method.visibleAnnotations, context, skidfuscator);
            remapped += remapAnnotationFields(method.invisibleAnnotations, context, skidfuscator);
        }
        return remapped;
    }

    private static int remapAnnotationFields(
            final List<AnnotationNode> annotations,
            final MixinRemapperContext context,
            final Skidfuscator skidfuscator
    ) {
        if (annotations == null) {
            return 0;
        }
        int remapped = 0;
        for (final AnnotationNode annotation : annotations) {
            final String target = MixinAnnotations.readAnnotationString(annotation, "target");
            if (target == null) {
                continue;
            }
            final String remappedTarget = MixinAnnotations.remapMemberReference(target, owner -> {
                if (!shouldRemapOwner(skidfuscator, owner)) {
                    return owner;
                }
                return context.mapClass(owner);
            });
            if (!target.equals(remappedTarget)) {
                setAnnotationValue(annotation, "target", remappedTarget);
                remapped++;
            }
        }
        return remapped;
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

    private static String resolveShadowOwner(final Set<String> targets, final String mixinName) {
        if (targets.size() == 1) {
            return targets.iterator().next();
        }
        return mixinName;
    }

    private static boolean shouldRemapOwner(final Skidfuscator skidfuscator, final String owner) {
        return skidfuscator.getClassSource().isApplicationClass(owner);
    }

    private static boolean shouldPreserveMember(final Skidfuscator skidfuscator, final String owner) {
        return !skidfuscator.getClassSource().isApplicationClass(owner);
    }
}

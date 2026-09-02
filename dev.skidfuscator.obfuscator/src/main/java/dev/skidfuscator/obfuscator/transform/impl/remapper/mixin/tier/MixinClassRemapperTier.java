package dev.skidfuscator.obfuscator.transform.impl.remapper.mixin.tier;

import dev.skidfuscator.obfuscator.Skidfuscator;
import dev.skidfuscator.obfuscator.skidasm.SkidClassNode;
import dev.skidfuscator.obfuscator.transform.impl.remapper.mixin.MixinRemapperContext;
import dev.skidfuscator.obfuscator.transform.impl.remapper.mixin.util.MixinAnnotations;
import dev.skidfuscator.obfuscator.transform.impl.remapper.mixin.util.MixinNameFactory;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.topdank.byteengineer.commons.data.JarClassData;

import java.util.List;
import java.util.Map;

/**
 * Renames {@code @Mixin} classes and records mappings for downstream tiers.
 */
public final class MixinClassRemapperTier {

    private MixinClassRemapperTier() {
    }

    public static int remap(
            final Skidfuscator skidfuscator,
            final MixinRemapperContext context,
            final MixinNameFactory names
    ) {
        int remapped = 0;
        for (final SkidClassNode mixinClass : context.getMixinClasses()) {
            if (!shouldRemap(skidfuscator, context, mixinClass)) {
                continue;
            }
            if (remapClass(skidfuscator, context, names, mixinClass)) {
                remapped++;
            }
        }
        return remapped;
    }

    public static boolean remapClass(
            final Skidfuscator skidfuscator,
            final MixinRemapperContext context,
            final MixinNameFactory names,
            final SkidClassNode classNode
    ) {
        if (!shouldRemap(skidfuscator, context, classNode)) {
            return false;
        }
        final String original = classNode.getName();
        if (context.getClassMappings().containsKey(original)) {
            return false;
        }
        final String renamed = names.nextClassName(original);
        applyClassMapping(skidfuscator, context, classNode, original, renamed);
        return true;
    }

    private static boolean shouldRemapClass(final Skidfuscator skidfuscator, final SkidClassNode classNode) {
        if (!skidfuscator.getClassSource().isApplicationClass(classNode.getName())) {
            return false;
        }
        return !skidfuscator.getExemptAnalysis().isExempt(classNode);
    }

    public static boolean shouldRemap(
            final Skidfuscator skidfuscator,
            final MixinRemapperContext context,
            final SkidClassNode classNode
    ) {
        return shouldRemapClass(skidfuscator, classNode) && !context.isExcluded(classNode.getName());
    }

    private static void applyClassMapping(
            final Skidfuscator skidfuscator,
            final MixinRemapperContext context,
            final SkidClassNode classNode,
            final String original,
            final String renamed
    ) {
        context.getClassMappings().put(original, renamed);
        skidfuscator.getClassRemapper().add(original, renamed);
        classNode.node.name = renamed;

        updateMixinAnnotationTargets(classNode, context);
        updateJarClassName(skidfuscator, original, renamed);
    }

    private static void updateJarClassName(final Skidfuscator skidfuscator, final String original, final String renamed) {
        final Map<String, JarClassData> classes = skidfuscator.getJarContents().getClassContents().namedMap();
        final JarClassData data = classes.remove(original + ".class");
        if (data != null) {
            classes.put(renamed + ".class", data);
        }
    }

    @SuppressWarnings("unchecked")
    private static void updateMixinAnnotationTargets(final SkidClassNode classNode, final MixinRemapperContext context) {
        updateAnnotationList(classNode.node.visibleAnnotations, context);
        updateAnnotationList(classNode.node.invisibleAnnotations, context);
    }

    private static void updateAnnotationList(final List<AnnotationNode> annotations, final MixinRemapperContext context) {
        if (annotations == null) {
            return;
        }
        for (final AnnotationNode annotation : annotations) {
            if (!MixinAnnotations.MIXIN.equals(annotation.desc) || annotation.values == null) {
                continue;
            }
            for (int i = 0; i < annotation.values.size(); i += 2) {
                final String key = (String) annotation.values.get(i);
                if (!"value".equals(key) && !"targets".equals(key)) {
                    continue;
                }
                final Object value = annotation.values.get(i + 1);
                annotation.values.set(i + 1, remapAnnotationValue(value, context));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Object remapAnnotationValue(final Object value, final MixinRemapperContext context) {
        if (value instanceof Type) {
            final Type type = (Type) value;
            return Type.getObjectType(context.mapClass(type.getInternalName()));
        }
        if (value instanceof String) {
            return MixinAnnotations.toBinaryName(context.mapClass(MixinAnnotations.toInternalName((String) value)));
        }
        if (value instanceof String[]) {
            final String[] array = (String[]) value;
            for (int i = 0; i < array.length; i++) {
                array[i] = MixinAnnotations.toBinaryName(context.mapClass(MixinAnnotations.toInternalName(array[i])));
            }
            return array;
        }
        if (value instanceof List) {
            final List<Object> list = (List<Object>) value;
            for (int i = 0; i < list.size(); i++) {
                list.set(i, remapAnnotationValue(list.get(i), context));
            }
            return list;
        }
        return value;
    }
}

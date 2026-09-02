package dev.skidfuscator.obfuscator.transform.impl.remapper.mixin.tier;

import dev.skidfuscator.obfuscator.Skidfuscator;
import dev.skidfuscator.obfuscator.skidasm.SkidClassNode;
import dev.skidfuscator.obfuscator.transform.impl.remapper.mixin.MixinRemapperConfig;
import dev.skidfuscator.obfuscator.transform.impl.remapper.mixin.MixinRemapperContext;
import dev.skidfuscator.obfuscator.transform.impl.remapper.mixin.util.MixinNameFactory;

/**
 * Applies {@code IMixinConfigPlugin} remapping policy.
 */
public final class MixinConfigPluginTier {

    private MixinConfigPluginTier() {
    }

    public static int apply(
            final Skidfuscator skidfuscator,
            final MixinRemapperConfig config,
            final MixinRemapperContext context,
            final MixinNameFactory names
    ) {
        if (!config.isConfigPluginEnabled()) {
            return 0;
        }
        if (config.isConfigPluginRemapEnabled()) {
            return remapPlugins(skidfuscator, context, names);
        }
        return excludePlugins(context);
    }

    private static int excludePlugins(final MixinRemapperContext context) {
        int excluded = 0;
        for (final SkidClassNode plugin : context.getPluginClasses()) {
            if (context.getExcludedClasses().add(plugin.getName())) {
                excluded++;
            }
        }
        return excluded;
    }

    private static int remapPlugins(
            final Skidfuscator skidfuscator,
            final MixinRemapperContext context,
            final MixinNameFactory names
    ) {
        int remapped = 0;
        for (final SkidClassNode plugin : context.getPluginClasses()) {
            if (MixinClassRemapperTier.remapClass(skidfuscator, context, names, plugin)) {
                remapped++;
            }
        }
        return remapped;
    }
}

package dev.skidfuscator.obfuscator.transform.impl.remapper.mixin;

import dev.skidfuscator.config.DefaultTransformerConfig;
import dev.skidfuscator.obfuscator.Skidfuscator;
import dev.skidfuscator.obfuscator.event.EventPriority;
import dev.skidfuscator.obfuscator.event.annotation.Listen;
import dev.skidfuscator.obfuscator.event.impl.transform.clazz.InitClassTransformEvent;
import dev.skidfuscator.obfuscator.event.impl.transform.skid.FinalSkidTransformEvent;
import dev.skidfuscator.obfuscator.event.impl.transform.skid.InitSkidTransformEvent;
import dev.skidfuscator.obfuscator.skidasm.SkidClassNode;
import dev.skidfuscator.obfuscator.transform.AbstractTransformer;
import dev.skidfuscator.obfuscator.transform.impl.remapper.mixin.json.MixinConfigDiscovery;
import dev.skidfuscator.obfuscator.transform.impl.remapper.mixin.json.MixinConfigJsonRewriter;
import dev.skidfuscator.obfuscator.transform.impl.remapper.mixin.tier.MixinClassRemapperTier;
import dev.skidfuscator.obfuscator.transform.impl.remapper.mixin.tier.MixinConfigPluginTier;
import dev.skidfuscator.obfuscator.transform.impl.remapper.mixin.tier.MixinFieldRemapperTier;
import dev.skidfuscator.obfuscator.transform.impl.remapper.mixin.tier.MixinMethodRemapperTier;
import dev.skidfuscator.obfuscator.transform.impl.remapper.mixin.util.MixinAnnotations;
import dev.skidfuscator.obfuscator.transform.impl.remapper.mixin.util.MixinNameFactory;
import dev.skidfuscator.obfuscator.util.MiscUtil;

/**
 * Full-scope mixin remapper with independent tiers for class, field, method, JSON, and plugin handling.
 */
public class MixinRemapperTransformer extends AbstractTransformer {

    private final MixinRemapperContext context = new MixinRemapperContext();

    public MixinRemapperTransformer(final Skidfuscator skidfuscator) {
        super(skidfuscator, "Mixin Remapper");
    }

    @Listen
    void onInitSkid(final InitSkidTransformEvent event) {
        final MixinRemapperConfig config = getConfig();
        if (config.isConfigJsonEnabled()) {
            MixinConfigDiscovery.discover(skidfuscator, config, context);
        }
    }

    @Listen(EventPriority.MONITOR)
    void onInitClass(final InitClassTransformEvent event) {
        final SkidClassNode classNode = event.getClassNode();
        if (classNode.isMixin()) {
            context.getMixinClasses().add(classNode);
        }
        if (MixinAnnotations.implementsMixinConfigPlugin(classNode.node)) {
            context.getPluginClasses().add(classNode);
        }
    }

    @Listen(EventPriority.FINALIZER)
    void onFinalize(final FinalSkidTransformEvent event) {
        final MixinRemapperConfig config = getConfig();
        if (context.getMixinClasses().isEmpty() && context.getPluginClasses().isEmpty()) {
            this.skip();
            return;
        }

        final MixinNameFactory names = new MixinNameFactory(skidfuscator);
        if (config.isConfigPluginEnabled()) {
            final int pluginResult = MixinConfigPluginTier.apply(skidfuscator, config, context, names);
            if (pluginResult > 0) {
                this.success();
            }
        }

        if (config.isClassRemappingEnabled()) {
            final int classes = MixinClassRemapperTier.remap(skidfuscator, context, names);
            if (classes > 0) {
                this.success();
            } else if (!context.getMixinClasses().isEmpty()) {
                this.skip();
            }
        }

        if (config.isFieldRemappingEnabled()) {
            final int fields = MixinFieldRemapperTier.remap(skidfuscator, context, names);
            if (fields > 0) {
                this.success();
            }
        }

        if (config.isMethodRemappingEnabled()) {
            final int methods = MixinMethodRemapperTier.remap(skidfuscator, context, names);
            if (methods > 0) {
                this.success();
            }
        }

        if (config.isConfigJsonEnabled()) {
            final int json = MixinConfigJsonRewriter.rewriteConfigs(context);
            if (json > 0) {
                this.success();
            } else if (!context.getCustomBootstrapConfigs().isEmpty()) {
                Skidfuscator.LOGGER.warn(
                        "Mixin JSON tier skipped " + context.getCustomBootstrapConfigs().size()
                                + " CUSTOM bootstrap config(s); verify manually."
                );
            }
        }
    }

    @Override
    protected <T extends DefaultTransformerConfig> T createConfig() {
        return (T) new MixinRemapperConfig(skidfuscator.getTsConfig(), MiscUtil.toCamelCase(name));
    }

    @Override
    public MixinRemapperConfig getConfig() {
        return (MixinRemapperConfig) super.getConfig();
    }
}

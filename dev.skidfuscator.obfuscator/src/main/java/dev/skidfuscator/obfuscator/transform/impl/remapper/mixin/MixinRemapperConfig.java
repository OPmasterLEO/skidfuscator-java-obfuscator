package dev.skidfuscator.obfuscator.transform.impl.remapper.mixin;

import com.typesafe.config.Config;
import dev.skidfuscator.config.DefaultTransformerConfig;

import java.util.Collections;
import java.util.List;

public class MixinRemapperConfig extends DefaultTransformerConfig {

    public MixinRemapperConfig(final Config config, final String path) {
        super(config, path);
    }

    @Override
    public boolean isEnabled() {
        return isClassRemappingEnabled()
                || isFieldRemappingEnabled()
                || isMethodRemappingEnabled()
                || isConfigJsonEnabled()
                || isConfigPluginEnabled();
    }

    public boolean isClassRemappingEnabled() {
        return getBoolean("classRemapping.enabled", true);
    }

    public boolean isFieldRemappingEnabled() {
        return getBoolean("fieldRemapping.enabled", false);
    }

    public boolean isMethodRemappingEnabled() {
        return getBoolean("methodRemapping.enabled", false);
    }

    public boolean isConfigJsonEnabled() {
        return getBoolean("configJson.enabled", false);
    }

    public MixinConfigFormat getConfigJsonFormat() {
        return getEnum("configJson.format", MixinConfigFormat.AUTO);
    }

    public List<String> getConfigJsonPaths() {
        return getStringList("configJson.paths", Collections.emptyList());
    }

    public boolean isConfigPluginEnabled() {
        return getBoolean("configPlugin.enabled", false);
    }

    /** Advanced mode: remap plugin classes consistently with mixin classes. */
    public boolean isConfigPluginRemapEnabled() {
        return getBoolean("configPlugin.remapPlugins", false);
    }
}

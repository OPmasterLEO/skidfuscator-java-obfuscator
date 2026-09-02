package dev.skidfuscator.obfuscator.transform.impl.remapper.mixin;

import dev.skidfuscator.obfuscator.skidasm.SkidClassNode;
import org.topdank.byteengineer.commons.data.JarResource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Mutable state shared across mixin remapper tiers for a single obfuscation run.
 */
public final class MixinRemapperContext {

    private final List<SkidClassNode> mixinClasses = new ArrayList<>();
    private final List<SkidClassNode> pluginClasses = new ArrayList<>();
    private final Map<String, String> classMappings = new LinkedHashMap<>();
    private final Map<String, String> fieldMappings = new LinkedHashMap<>();
    private final Map<String, String> methodMappings = new LinkedHashMap<>();
    private final Set<String> excludedClasses = new LinkedHashSet<>();
    private final Set<String> customBootstrapConfigs = new LinkedHashSet<>();
    private final Map<String, JarResource> mixinConfigResources = new LinkedHashMap<>();
    private final Map<String, JarResource> refmapResources = new LinkedHashMap<>();
    private final Map<String, MixinConfigFormat> detectedFormats = new LinkedHashMap<>();

    public List<SkidClassNode> getMixinClasses() {
        return mixinClasses;
    }

    public List<SkidClassNode> getPluginClasses() {
        return pluginClasses;
    }

    public Map<String, String> getClassMappings() {
        return classMappings;
    }

    public Map<String, String> getFieldMappings() {
        return fieldMappings;
    }

    public Map<String, String> getMethodMappings() {
        return methodMappings;
    }

    public Set<String> getCustomBootstrapConfigs() {
        return customBootstrapConfigs;
    }

    public Set<String> getExcludedClasses() {
        return excludedClasses;
    }

    public boolean isExcluded(final String internalName) {
        return excludedClasses.contains(internalName);
    }

    public Map<String, JarResource> getMixinConfigResources() {
        return mixinConfigResources;
    }

    public Map<String, JarResource> getRefmapResources() {
        return refmapResources;
    }

    public Map<String, MixinConfigFormat> getDetectedFormats() {
        return detectedFormats;
    }

    public String mapClass(final String internalName) {
        return classMappings.getOrDefault(internalName, internalName);
    }

    public String mapField(final String owner, final String name) {
        return fieldMappings.getOrDefault(owner + '.' + name, name);
    }

    public String mapMethod(final String owner, final String name, final String descriptor) {
        return methodMappings.getOrDefault(owner + '.' + name + descriptor, name);
    }
}

package dev.skidfuscator.obfuscator.transform.impl.remapper.mixin.json;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.skidfuscator.obfuscator.Skidfuscator;
import dev.skidfuscator.obfuscator.transform.impl.remapper.mixin.MixinConfigFormat;
import dev.skidfuscator.obfuscator.transform.impl.remapper.mixin.MixinRemapperConfig;
import dev.skidfuscator.obfuscator.transform.impl.remapper.mixin.MixinRemapperContext;
import dev.skidfuscator.obfuscator.transform.impl.remapper.mixin.util.MixinAnnotations;
import org.topdank.byteengineer.commons.data.JarResource;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Discovers mixin configuration and refmap resources inside the input jar.
 */
public final class MixinConfigDiscovery {

    private MixinConfigDiscovery() {
    }

    public static void discover(
            final Skidfuscator skidfuscator,
            final MixinRemapperConfig config,
            final MixinRemapperContext context
    ) {
        final Set<String> explicitPaths = new LinkedHashSet<>(config.getConfigJsonPaths());
        explicitPaths.addAll(readFabricMixinPaths(skidfuscator));
        explicitPaths.addAll(readForgeMixinPaths(skidfuscator));

        for (final JarResource resource : skidfuscator.getJarContents().getResourceContents()) {
            final String path = resource.getName();
            if (!explicitPaths.contains(path) && !MixinAnnotations.looksLikeMixinConfigPath(path)) {
                continue;
            }
            registerConfig(skidfuscator, path, resource, config, context);
        }
    }

    private static Set<String> readFabricMixinPaths(final Skidfuscator skidfuscator) {
        final Set<String> paths = new LinkedHashSet<>();
        final JarResource fabricMod = findResource(skidfuscator, "fabric.mod.json");
        if (fabricMod == null) {
            return paths;
        }
        final JsonObject root = parseJson(fabricMod);
        if (root == null || !root.has("mixins")) {
            return paths;
        }
        final JsonArray mixins = root.getAsJsonArray("mixins");
        for (final JsonElement element : mixins) {
            paths.add(element.getAsString());
        }
        return paths;
    }

    private static Set<String> readForgeMixinPaths(final Skidfuscator skidfuscator) {
        final Set<String> paths = new LinkedHashSet<>();
        final JarResource modsToml = findResource(skidfuscator, "META-INF/mods.toml");
        if (modsToml == null) {
            return paths;
        }
        final String text = new String(modsToml.getData(), StandardCharsets.UTF_8);
        for (final String line : text.split("\\R")) {
            final String trimmed = line.trim();
            if (trimmed.startsWith("config=\"")) {
                final int end = trimmed.indexOf('"', 8);
                if (end > 8) {
                    paths.add(trimmed.substring(8, end));
                }
            }
        }
        return paths;
    }

    private static void registerConfig(
            final Skidfuscator skidfuscator,
            final String path,
            final JarResource resource,
            final MixinRemapperConfig config,
            final MixinRemapperContext context
    ) {
        final JsonObject root = parseJson(resource);
        if (root == null) {
            return;
        }
        final MixinConfigFormat format = resolveFormat(root, config.getConfigJsonFormat());
        context.getMixinConfigResources().put(path, resource);
        context.getDetectedFormats().put(path, format);
        if (format == MixinConfigFormat.CUSTOM) {
            context.getCustomBootstrapConfigs().add(path);
            Skidfuscator.LOGGER.warn("Mixin config '" + path + "' flagged as CUSTOM bootstrap — verify manually.");
        }
        if (root.has("refmap")) {
            final String refmapPath = root.get("refmap").getAsString();
            final JarResource refmap = findResource(skidfuscator, refmapPath);
            if (refmap != null) {
                context.getRefmapResources().put(refmapPath, refmap);
            }
        }
    }

    private static JarResource findResource(final Skidfuscator skidfuscator, final String path) {
        for (final JarResource resource : skidfuscator.getJarContents().getResourceContents()) {
            if (resource.getName().equals(path)) {
                return resource;
            }
        }
        return null;
    }

    private static JsonObject parseJson(final JarResource resource) {
        try {
            return JsonParser.parseString(new String(resource.getData(), StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (RuntimeException ex) {
            Skidfuscator.LOGGER.warn("Failed to parse mixin resource '" + resource.getName() + "': " + ex.getMessage());
            return null;
        }
    }

    public static MixinConfigFormat resolveFormat(final JsonObject root, final MixinConfigFormat configured) {
        if (configured != MixinConfigFormat.AUTO) {
            return configured;
        }
        final boolean hasPackage = root.has("package");
        final boolean hasMixinsArray = root.has("mixins") && root.get("mixins").isJsonArray();
        final boolean hasClientServer = root.has("client") || root.has("server");
        if (hasPackage && hasClientServer) {
            return MixinConfigFormat.LEGACY_FABRIC;
        }
        if (hasMixinsArray) {
            return MixinConfigFormat.MODERN;
        }
        if (hasPackage) {
            return MixinConfigFormat.LEGACY_FABRIC;
        }
        return MixinConfigFormat.CUSTOM;
    }

    public static List<String> collectMixinEntries(final JsonObject root) {
        final List<String> entries = new ArrayList<>();
        collectArray(root, "mixins", entries);
        collectArray(root, "client", entries);
        collectArray(root, "server", entries);
        return entries;
    }

    private static void collectArray(final JsonObject root, final String key, final List<String> out) {
        if (!root.has(key) || !root.get(key).isJsonArray()) {
            return;
        }
        for (final JsonElement element : root.getAsJsonArray(key)) {
            out.add(element.getAsString());
        }
    }

    public static String qualifyEntry(final JsonObject root, final String entry, final MixinConfigFormat format) {
        if (entry.contains(".") || entry.contains("/")) {
            return entry.replace('/', '.');
        }
        if (format == MixinConfigFormat.LEGACY_FABRIC && root.has("package")) {
            return root.get("package").getAsString() + '.' + entry;
        }
        return entry;
    }

    public static String toConfigEntry(final JsonObject root, final String binaryName, final MixinConfigFormat format) {
        if (format == MixinConfigFormat.LEGACY_FABRIC && root.has("package")) {
            final String pkg = root.get("package").getAsString();
            if (binaryName.startsWith(pkg + ".")) {
                return binaryName.substring(pkg.length() + 1);
            }
        }
        return binaryName;
    }
}

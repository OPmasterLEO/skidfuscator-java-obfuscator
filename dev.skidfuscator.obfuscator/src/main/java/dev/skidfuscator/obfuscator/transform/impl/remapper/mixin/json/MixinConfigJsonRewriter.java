package dev.skidfuscator.obfuscator.transform.impl.remapper.mixin.json;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.skidfuscator.obfuscator.Skidfuscator;
import dev.skidfuscator.obfuscator.transform.impl.remapper.mixin.MixinConfigFormat;
import dev.skidfuscator.obfuscator.transform.impl.remapper.mixin.MixinRemapperContext;
import dev.skidfuscator.obfuscator.transform.impl.remapper.mixin.util.MixinAnnotations;
import org.topdank.byteengineer.commons.data.JarResource;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Rewrites mixin config and refmap JSON resources after class remapping.
 */
public final class MixinConfigJsonRewriter {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String[] MIXIN_ARRAY_KEYS = {"mixins", "client", "server"};

    private MixinConfigJsonRewriter() {
    }

    public static int rewriteConfigs(final MixinRemapperContext context) {
        int updated = 0;
        for (final Map.Entry<String, JarResource> entry : context.getMixinConfigResources().entrySet()) {
            final MixinConfigFormat format = context.getDetectedFormats().getOrDefault(entry.getKey(), MixinConfigFormat.CUSTOM);
            if (format == MixinConfigFormat.CUSTOM) {
                continue;
            }
            if (rewriteConfig(entry.getValue(), format, context)) {
                updated++;
            }
        }
        updated += rewriteRefmaps(context);
        return updated;
    }

    private static boolean rewriteConfig(
            final JarResource resource,
            final MixinConfigFormat format,
            final MixinRemapperContext context
    ) {
        final JsonObject root = JsonParser.parseString(new String(resource.getData(), StandardCharsets.UTF_8)).getAsJsonObject();
        boolean changed = false;
        for (final String key : MIXIN_ARRAY_KEYS) {
            if (!root.has(key) || !root.get(key).isJsonArray()) {
                continue;
            }
            final JsonArray array = root.getAsJsonArray(key);
            for (int i = 0; i < array.size(); i++) {
                final String original = array.get(i).getAsString();
                final String qualified = MixinConfigDiscovery.qualifyEntry(root, original, format);
                final String mapped = mapQualifiedClass(qualified, context);
                final String rewritten = MixinConfigDiscovery.toConfigEntry(root, mapped, format);
                if (!original.equals(rewritten)) {
                    array.set(i, GSON.toJsonTree(rewritten));
                    changed = true;
                }
            }
        }
        if (root.has("package")) {
            final String pkg = root.get("package").getAsString().replace('.', '/');
            final String mappedPkg = context.mapClass(pkg).replace('/', '.');
            if (!root.get("package").getAsString().equals(mappedPkg)) {
                root.addProperty("package", mappedPkg);
                changed = true;
            }
        }
        if (changed) {
            resource.setData(GSON.toJson(root).getBytes(StandardCharsets.UTF_8));
        }
        return changed;
    }

    private static int rewriteRefmaps(final MixinRemapperContext context) {
        int updated = 0;
        for (final JarResource resource : context.getRefmapResources().values()) {
            if (rewriteRefmap(resource, context)) {
                updated++;
            }
        }
        return updated;
    }

    private static boolean rewriteRefmap(final JarResource resource, final MixinRemapperContext context) {
        final JsonObject root = JsonParser.parseString(new String(resource.getData(), StandardCharsets.UTF_8)).getAsJsonObject();
        boolean changed = false;
        if (root.has("mappings") && root.get("mappings").isJsonObject()) {
            changed |= rewriteRefmapMappings(root.getAsJsonObject("mappings"), context);
        }
        if (root.has("data") && root.get("data").isJsonObject()) {
            changed |= rewriteRefmapData(root.getAsJsonObject("data"), context);
        }
        if (changed) {
            resource.setData(GSON.toJson(root).getBytes(StandardCharsets.UTF_8));
        }
        return changed;
    }

    private static boolean rewriteRefmapMappings(final JsonObject mappings, final MixinRemapperContext context) {
        boolean changed = false;
        final List<String> keys = List.copyOf(mappings.keySet());
        for (final String key : keys) {
            final String mappedKey = mapQualifiedClass(key.replace('/', '.'), context);
            if (!key.equals(mappedKey.replace('.', '/'))) {
                final JsonElement value = mappings.remove(key);
                mappings.add(mappedKey.replace('.', '/'), value);
                changed = true;
            }
        }
        return changed;
    }

    private static boolean rewriteRefmapData(final JsonObject data, final MixinRemapperContext context) {
        boolean changed = false;
        for (final Map.Entry<String, JsonElement> entry : data.entrySet()) {
            if (!entry.getValue().isJsonObject()) {
                continue;
            }
            final JsonObject bucket = entry.getValue().getAsJsonObject();
            final List<String> keys = List.copyOf(bucket.keySet());
            for (final String key : keys) {
                final String mappedKey = mapQualifiedClass(key.replace('/', '.'), context);
                if (!key.equals(mappedKey.replace('.', '/'))) {
                    final JsonElement value = bucket.remove(key);
                    bucket.add(mappedKey.replace('.', '/'), value);
                    changed = true;
                }
            }
        }
        return changed;
    }

    private static String mapQualifiedClass(final String binaryName, final MixinRemapperContext context) {
        final String internal = MixinAnnotations.toInternalName(binaryName);
        return MixinAnnotations.toBinaryName(context.mapClass(internal));
    }
}

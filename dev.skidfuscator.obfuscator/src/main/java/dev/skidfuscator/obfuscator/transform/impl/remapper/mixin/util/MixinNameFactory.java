package dev.skidfuscator.obfuscator.transform.impl.remapper.mixin.util;

import dev.skidfuscator.obfuscator.Skidfuscator;
import dev.skidfuscator.obfuscator.util.RandomUtil;
import org.mapleir.deob.util.RenamingUtil;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Generates collision-free obfuscated names using the global renamer config conventions.
 */
public final class MixinNameFactory {

    private final Skidfuscator skidfuscator;
    private final Set<String> usedClassNames = new HashSet<>();
    private final Set<String> usedMemberNames = new HashSet<>();
    private int classCounter = RenamingUtil.numeric("mixin");
    private int memberCounter = RenamingUtil.numeric("shadow");

    public MixinNameFactory(final Skidfuscator skidfuscator) {
        this.skidfuscator = skidfuscator;
    }

    public String nextClassName(final String originalInternalName) {
        final String prefix = skidfuscator.getConfig().getString("classRenamer.prefix", "mix/");
        final int depth = skidfuscator.getConfig().getInt("classRenamer.depth", 3);
        String candidate;
        do {
            candidate = prefix + buildSegment(depth);
            classCounter += 17;
        } while (!usedClassNames.add(candidate));
        return candidate;
    }

    public String nextMemberName() {
        String candidate;
        do {
            candidate = RenamingUtil.createName(memberCounter++);
        } while (!usedMemberNames.add(candidate));
        return candidate;
    }

    private String buildSegment(final int depth) {
        final StringBuilder builder = new StringBuilder();
        for (int i = 0; i < depth; i++) {
            builder.append(pickChar());
        }
        return builder.toString();
    }

    private char pickChar() {
        final List<String> chars = skidfuscator.getConfig().getStringList(
                "classRenamer.chars",
                java.util.Arrays.asList("K", "oO", "o0")
        );
        final String segment = chars.get(RandomUtil.nextInt(chars.size()));
        return segment.charAt(RandomUtil.nextInt(segment.length()));
    }
}

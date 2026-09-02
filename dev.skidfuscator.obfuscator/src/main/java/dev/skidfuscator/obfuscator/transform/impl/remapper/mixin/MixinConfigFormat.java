package dev.skidfuscator.obfuscator.transform.impl.remapper.mixin;

/**
 * Supported mixin configuration JSON layouts.
 * <p>
 * {@link #AUTO} attempts detection and flags {@link #CUSTOM} when bootstrap cannot be inferred safely.
 */
public enum MixinConfigFormat {
    /** Legacy Fabric / FML (pre-1.13): {@code package} + short names in {@code mixins}/{@code client}/{@code server}. */
    LEGACY_FABRIC,
    /** Modern Forge / NeoForge / Fabric (post-1.12): fully-qualified entries and/or {@code mixins} array. */
    MODERN,
    /** Tweaker-based or manually initialized configs — not auto-rewritten unless paths are explicit. */
    CUSTOM,
    /** Detect format at runtime; unresolved cases are reported as {@link #CUSTOM}. */
    AUTO
}

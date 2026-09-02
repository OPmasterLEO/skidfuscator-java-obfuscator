# Mixin Remapper

Full-scope mixin remapping for Skidfuscator community edition, implemented fresh on `master`.

## Config (`mixinRemapper` in HOCON)

| Tier | Key | Default |
|------|-----|---------|
| Class remapping | `classRemapping.enabled` | `true` |
| Field remapping | `fieldRemapping.enabled` | `false` |
| Method remapping | `methodRemapping.enabled` | `false` |
| JSON config rewrite | `configJson.enabled` | `false` |
| Plugin handling | `configPlugin.enabled` | `false` |
| Advanced plugin remap | `configPlugin.remapPlugins` | `false` |

JSON format: `configJson.format` — `AUTO`, `LEGACY_FABRIC`, `MODERN`, or `CUSTOM`.

## Design vs PR #78

PR #78 (reference only, not merged) was class-remapping only and required manual `refmap` / `config` paths in HOCON. Known TODOs there: plugin support, field/method remapping, name tracking, same-package constraint.

This implementation:

- **Tiered flags** — each concern is independently toggled; class remapping on by default.
- **Auto-discovery** — reads `fabric.mod.json`, `META-INF/mods.toml`, and `*.mixins.json` paths instead of mandatory manual config paths.
- **Format detection** — legacy Fabric/FML (`package` + `client`/`server`), modern (`mixins` array), or `CUSTOM` (warn, skip auto-rewrite).
- **Field/method tiers** — `@Shadow` / `@Overwrite` stay aligned with targets; `@Inject`/`@At` strings updated when application classes are remapped; library/game classes untouched via `isApplicationClass`.
- **Plugins** — `IMixinConfigPlugin` implementors excluded by default; `remapPlugins: true` remaps them like mixin classes.
- **No same-package gate** — mixin classes may land in different packages; JSON rewriter handles qualified and short names.

## Manual verification

Fixtures live in `dev.skidfuscator.obfuscator/mixin-test-fixtures/` (Fabric + Forge minimal mods).

1. Build a jar from a fixture (compile sources + resources; include SpongePowered Mixin on the compile classpath).
2. Obfuscate with a config enabling desired tiers, e.g.:

```hocon
mixinRemapper {
  classRemapping { enabled: true }
  fieldRemapping { enabled: true }
  methodRemapping { enabled: true }
  configJson { enabled: true format: AUTO }
  configPlugin { enabled: true remapPlugins: false }
}
```

3. Inspect output:
   - `@Mixin` class names changed in bytecode and `mixins.json`.
   - `@Shadow` field names match remapped target fields when field tier is on.
   - `@Inject` / `@At` target strings updated when method tier is on.
   - Plugin class unchanged when `remapPlugins: false`.
4. Load the obfuscated mod in Fabric/Forge and confirm mixins still apply (no `ClassNotFoundException` for mixin entries).

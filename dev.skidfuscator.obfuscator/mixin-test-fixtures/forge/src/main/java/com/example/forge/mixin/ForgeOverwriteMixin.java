package com.example.forge.mixin;

import com.example.forge.target.ForgeTarget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(ForgeTarget.class)
public class ForgeOverwriteMixin {
    @Overwrite
    public int increment() {
        return 42;
    }
}

package com.example.forge.mixin;

import com.example.forge.target.ForgeTarget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ForgeTarget.class)
public abstract class ForgeFieldShadowMixin {
    @Shadow
    private int counter;
}

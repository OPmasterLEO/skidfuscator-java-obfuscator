package com.example.fabric.mixin;

import com.example.fabric.target.ExampleTarget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ExampleTarget.class)
public abstract class ExampleFieldShadowMixin {
    @Shadow
    private String secret;
}

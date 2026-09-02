package com.example.fabric.mixin;

import com.example.fabric.target.ExampleTarget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(ExampleTarget.class)
public class ExampleOverwriteMixin {
    @Overwrite
    public String getSecret() {
        return "obfuscated";
    }
}

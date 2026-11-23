package dev.poldi.client.mixin;

import dev.poldi.client.PoldiConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * NoFog: schiebt Start und Ende des Nebels ins Unendliche. Ziel per Name (ohne Compile-Abhaengigkeit),
 * alle Injections optional - fehlt die Methode in einer Version, passiert einfach nichts.
 */
@Pseudo
@Mixin(targets = "net.minecraft.client.renderer.fog.FogRenderer")
public abstract class FogRendererMixin {
    private static final float FAR = 1.0e7f;

    private static float far(float orig, boolean end) {
        try {
            if (PoldiConfig.get().noFog) return end ? FAR + 1f : FAR;
        } catch (Throwable ignored) {}
        return orig;
    }

    @ModifyVariable(method = "applyFog", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private float poldi$envStart(float v) { return far(v, false); }

    @ModifyVariable(method = "applyFog", at = @At("HEAD"), argsOnly = true, ordinal = 1, require = 0)
    private float poldi$envEnd(float v) { return far(v, true); }

    @ModifyVariable(method = "applyFog", at = @At("HEAD"), argsOnly = true, ordinal = 2, require = 0)
    private float poldi$rdStart(float v) { return far(v, false); }

    @ModifyVariable(method = "applyFog", at = @At("HEAD"), argsOnly = true, ordinal = 3, require = 0)
    private float poldi$rdEnd(float v) { return far(v, true); }

    @ModifyVariable(method = "applyFog", at = @At("HEAD"), argsOnly = true, ordinal = 4, require = 0)
    private float poldi$skyEnd(float v) { return far(v, true); }

    @ModifyVariable(method = "applyFog", at = @At("HEAD"), argsOnly = true, ordinal = 5, require = 0)
    private float poldi$cloudEnd(float v) { return far(v, true); }
}

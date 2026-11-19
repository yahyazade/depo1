package dev.zadeai;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import dev.zadeai.actor.AiActorRenderer;
public final class ZadeAiClient implements ClientModInitializer {
 @Override public void onInitializeClient() { EntityRendererRegistry.register(ZadeAiMod.ACTOR, AiActorRenderer::new); }
}
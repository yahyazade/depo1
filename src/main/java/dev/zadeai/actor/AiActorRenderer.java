package dev.zadeai.actor;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.util.Identifier;
public final class AiActorRenderer extends MobEntityRenderer<AiActor,PlayerEntityModel<AiActor>> {
 private static final Identifier SKIN=Identifier.of("zadeai","textures/entity/chatgpt.png");
 public AiActorRenderer(EntityRendererFactory.Context c){super(c,new PlayerEntityModel<>(c.getPart(EntityModelLayers.PLAYER),false),.5f);}
 @Override public Identifier getTexture(AiActor e){return SKIN;}
}
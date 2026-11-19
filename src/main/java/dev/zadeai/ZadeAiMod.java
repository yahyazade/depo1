package dev.zadeai;
import dev.zadeai.actor.AiActor;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import java.util.UUID;
public final class ZadeAiMod implements ModInitializer {
 public static final EntityType<AiActor> ACTOR = Registry.register(Registries.ENTITY_TYPE, Identifier.of("zadeai","chatgpt"),
   EntityType.Builder.create(AiActor::new, SpawnGroup.CREATURE).dimensions(.6f,1.8f).maxTrackingRange(64).build("zadeai:chatgpt"));
 private UUID id;
 private boolean gifted;
 private int ticks;
 @Override public void onInitialize() {
  FabricDefaultAttributeRegistry.register(ACTOR, AiActor.attributes());
  ServerTickEvents.END_SERVER_TICK.register(server -> {
   if(++ticks%40 != 0)return;
   ServerPlayerEntity p = server.getPlayerManager().getPlayerList().stream()
    .filter(x->x.getName().getString().equalsIgnoreCase("Yahya__Zade")).findFirst().orElse(null);
   if(p==null)return;
   var world=p.getServerWorld();
   AiActor bot = id==null ? null : (world.getEntity(id) instanceof AiActor a ? a:null);
   if(bot==null) {
     bot=ACTOR.create(world);
     if(bot==null)return;
     bot.refreshPositionAndAngles(p.getX()+2,p.getY(),p.getZ()+2,p.getYaw(),0);
     world.spawnEntity(bot);
     id=bot.getUuid();
   }
   if(!gifted) {
     gifted=true;
     var wood=new net.minecraft.item.ItemStack(net.minecraft.item.Items.OAK_LOG,32);
     var stone=new net.minecraft.item.ItemStack(net.minecraft.item.Items.COBBLESTONE,48);
     p.getInventory().insertStack(wood);
     p.getInventory().insertStack(stone);
   }
  });
 }
}
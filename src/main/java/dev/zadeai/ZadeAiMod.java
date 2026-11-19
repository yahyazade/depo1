package dev.zadeai;
import dev.zadeai.actor.AiActor;
import dev.zadeai.voice.VoiceBridge;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import java.util.UUID;
public final class ZadeAiMod implements ModInitializer {
 public static final EntityType<AiActor> ACTOR=Registry.register(Registries.ENTITY_TYPE,Identifier.of("zadeai","chatgpt"),EntityType.Builder.create(AiActor::new,SpawnGroup.CREATURE).dimensions(.6f,1.8f).maxTrackingRange(64).build("zadeai:chatgpt"));
 private UUID actorId;
 private boolean gifted;
 private int clock;
 @Override public void onInitialize(){
  FabricDefaultAttributeRegistry.register(ACTOR,AiActor.attributes());
  ServerTickEvents.END_SERVER_TICK.register(server->{
   VoiceBridge.tick(server);
   if(++clock%20!=0)return;
   ServerPlayerEntity p=server.getPlayerManager().getPlayerList().stream().filter(x->x.getName().getString().equalsIgnoreCase("Yahya__Zade")).findFirst().orElse(null);
   if(p==null){VoiceBridge.update(null,null);return;}
   AiActor a=actorId==null?null:(p.getServerWorld().getEntity(actorId) instanceof AiActor found?found:null);
   if(a==null){
    a=ACTOR.create(p.getServerWorld());if(a==null)return;
    a.refreshPositionAndAngles(p.getX()+2,p.getY(),p.getZ()+2,p.getYaw(),0);p.getServerWorld().spawnEntity(a);actorId=a.getUuid();
   }
   if(!gifted){
    gifted=true;
    p.getInventory().insertStack(new ItemStack(Items.OAK_LOG,32));
    p.getInventory().insertStack(new ItemStack(Items.COBBLESTONE,48));
   }
   VoiceBridge.update(p,a);
  });
 }
}
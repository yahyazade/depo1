package dev.zadeai.actor;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.world.World;
import java.util.EnumSet;
public final class AiActor extends PathAwareEntity {
 public AiActor(EntityType<? extends PathAwareEntity> type, World world) {
  super(type,world); setCustomName(Text.literal("ChatGPT"));setCustomNameVisible(false);setPersistent();
 }
 public static DefaultAttributeContainer.Builder attributes() {
  return MobEntity.createMobAttributes().add(EntityAttributes.GENERIC_MAX_HEALTH,40.0)
   .add(EntityAttributes.GENERIC_MOVEMENT_SPEED,.27).add(EntityAttributes.GENERIC_ATTACK_DAMAGE,4)
   .add(EntityAttributes.GENERIC_FOLLOW_RANGE,30);
 }
 @Override protected void initGoals() {
  goalSelector.add(0,new SwimGoal(this));
  goalSelector.add(1,new MeleeAttackGoal(this,1.0,true));
  targetSelector.add(2,new ActiveTargetGoal<>(this,HostileEntity.class,true));
  goalSelector.add(3,new FollowGoal(this));
  goalSelector.add(5,new LookAtEntityGoal(this,PlayerEntity.class,9));
  goalSelector.add(6,new WanderAroundFarGoal(this,.55));
 }
 private static final class FollowGoal extends Goal {
  private final AiActor actor;
  private PlayerEntity player;
  FollowGoal(AiActor actor){this.actor=actor;setControls(EnumSet.of(Control.MOVE));}
  @Override public boolean canStart() {
   player=actor.getWorld().getPlayers().stream()
    .filter(p->p.getName().getString().equalsIgnoreCase("Yahya__Zade") && p.isAlive()).findFirst().orElse(null);
   return player!=null && actor.squaredDistanceTo(player)>25 && actor.squaredDistanceTo(player)<2500;
  }
  @Override public boolean shouldContinue(){return player!=null && player.isAlive() && actor.squaredDistanceTo(player)>10 && actor.squaredDistanceTo(player)<2500;}
  @Override public void tick(){if(player!=null)actor.getNavigation().startMovingTo(player,1.04);}
 }
}
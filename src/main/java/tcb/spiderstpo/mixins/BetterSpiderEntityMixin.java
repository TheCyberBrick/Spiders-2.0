package tcb.spiderstpo.mixins;

import java.util.UUID;
import java.util.function.Predicate;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.entity.ai.goal.TargetGoal;
import net.minecraft.entity.monster.MonsterEntity;
import net.minecraft.entity.monster.SpiderEntity;
import net.minecraft.pathfinding.Path;
import net.minecraft.pathfinding.PathNavigator;
import net.minecraft.pathfinding.PathNodeType;
import net.minecraft.pathfinding.PathPoint;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3i;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;
import tcb.spiderstpo.common.Config;
import tcb.spiderstpo.common.ModTags;
import tcb.spiderstpo.common.entity.goal.BetterLeapAtTargetGoal;
import tcb.spiderstpo.common.entity.mob.IClimberEntity;
import tcb.spiderstpo.common.entity.mob.IMobEntityNavigatorHook;
import tcb.spiderstpo.common.entity.mob.IMobEntityRegisterGoalsHook;
import tcb.spiderstpo.common.entity.movement.BetterSpiderPathNavigator;
import tcb.spiderstpo.common.entity.movement.DirectionalPathPoint;

@Mixin(value = SpiderEntity.class, priority = 1001)
public abstract class BetterSpiderEntityMixin extends MonsterEntity implements IClimberEntity, IMobEntityRegisterGoalsHook, IMobEntityNavigatorHook {

	private static final UUID FOLLOW_RANGE_INCREASE_ID = UUID.fromString("9e815957-3a8e-4b65-afbc-eba39d2a06b4");
	private static final AttributeModifier FOLLOW_RANGE_INCREASE = new AttributeModifier(FOLLOW_RANGE_INCREASE_ID, "Spiders 2.0 follow range increase", 8.0D, AttributeModifier.Operation.ADDITION);

	private boolean pathFinderDebugPreview;

	private BetterSpiderEntityMixin(EntityType<? extends MonsterEntity> type, World worldIn) {
		super(type, worldIn);
	}

	@Inject(method = "<init>*", at = @At("RETURN"))
	private void onConstructed(CallbackInfo ci) {
		this.getAttribute(Attributes.field_233819_b_).func_233769_c_(FOLLOW_RANGE_INCREASE);
	}

	@Override
	public PathNavigator onCreateNavigator(World world) {
		BetterSpiderPathNavigator<BetterSpiderEntityMixin> navigate = new BetterSpiderPathNavigator<>(this, world, false);
		navigate.setCanSwim(true);
		return navigate;
	}

	@Inject(method = "registerData()V", at = @At("HEAD"))
	private void onRegisterData(CallbackInfo ci) {
		this.pathFinderDebugPreview = Config.PATH_FINDER_DEBUG_PREVIEW.get();
	}

	@Redirect(method = "registerGoals()V", at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/entity/ai/goal/GoalSelector;addGoal(ILnet/minecraft/entity/ai/goal/Goal;)V"
			))
	private void onAddGoal(GoalSelector selector, int priority, Goal task) {
		if(task instanceof LeapAtTargetGoal) {
			selector.addGoal(3, new BetterLeapAtTargetGoal<>(this, 0.4f));
		} else if(task instanceof TargetGoal) {
			selector.addGoal(2, ((TargetGoal) task).setUnseenMemoryTicks(200));
		} else {
			selector.addGoal(priority, task);
		}
	}

	@Override
	public boolean shouldTrackPathingTargets() {
		return this.pathFinderDebugPreview;
	}	

	@Override	
	public boolean canClimbOnBlock(BlockState state, BlockPos pos) {
		return !state.getBlock().isIn(ModTags.NON_CLIMBABLE);
	}

	@Override
	public boolean canAttachToSide(Direction side) {
		if(!this.isJumping && Config.PREVENT_CLIMBING_IN_RAIN.get() && side.getAxis() != Direction.Axis.Y && this.world.isRainingAt(new BlockPos(this.getPosX(), this.getPosY() + this.getHeight() * 0.5f,  this.getPosZ()))) {
			return false;
		}
		return true;
	}

	@Override
	public float getBlockSlipperiness(BlockPos pos) {
		BlockState offsetState = this.world.getBlockState(pos);

		float slipperiness = offsetState.getBlock().getSlipperiness(offsetState, this.world, pos, this) * 0.91f;

		if(offsetState.getBlock().isIn(ModTags.NON_CLIMBABLE)) {
			slipperiness = 1 - (1 - slipperiness) * 0.25f;
		}

		return slipperiness;
	}

	@Override
	public float getPathingMalus(IBlockReader cache, MobEntity entity, PathNodeType nodeType, BlockPos pos, Vector3i direction, Predicate<Direction> sides) {
		if(direction.getY() != 0) {
			if(Config.PREVENT_CLIMBING_IN_RAIN.get() && !sides.test(Direction.UP) && !sides.test(Direction.DOWN) && this.world.isRainingAt(pos)) {
				return -1.0f;
			}

			boolean hasClimbableNeigbor = false;

			BlockPos.Mutable offsetPos = new BlockPos.Mutable();

			for(Direction offset : Direction.values()) {
				if(sides.test(offset)) {
					offsetPos.setPos(pos.getX() + offset.getXOffset(), pos.getY() + offset.getYOffset(), pos.getZ() + offset.getZOffset());

					BlockState state = cache.getBlockState(offsetPos);

					if(this.canClimbOnBlock(state, offsetPos)) {
						hasClimbableNeigbor = true;
					}
				}
			}

			if(!hasClimbableNeigbor) {
				return -1.0f;
			}
		}

		return entity.getPathPriority(nodeType);
	}
}

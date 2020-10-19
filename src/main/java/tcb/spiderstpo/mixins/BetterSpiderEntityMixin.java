package tcb.spiderstpo.mixins;

import java.util.function.Predicate;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.block.BlockState;
import net.minecraft.entity.CreatureEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.monster.SpiderEntity;
import net.minecraft.pathfinding.PathNodeType;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;
import tcb.spiderstpo.common.Config;
import tcb.spiderstpo.common.ModTags;
import tcb.spiderstpo.common.entity.mob.IClimberEntity;

@Mixin(value = SpiderEntity.class, priority = 1001)
public abstract class BetterSpiderEntityMixin extends CreatureEntity implements IClimberEntity {
	private boolean pathFinderDebugPreview;

	private BetterSpiderEntityMixin(EntityType<? extends CreatureEntity> type, World worldIn) {
		super(type, worldIn);
	}

	@Inject(method = "registerData()V", at = @At("HEAD"))
	private void onRegisterData(CallbackInfo ci) {
		this.pathFinderDebugPreview = Config.PATH_FINDER_DEBUG_PREVIEW.get();
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
	public float getBlockSlipperiness(BlockPos pos) {
		BlockState offsetState = this.world.getBlockState(pos);

		float slipperiness = offsetState.getBlock().getSlipperiness(offsetState, this.world, pos, this) * 0.91f;

		if(offsetState.getBlock().isIn(ModTags.NON_CLIMBABLE)) {
			slipperiness = 1 - (1 - slipperiness) * 0.25f;
		}

		return slipperiness;
	}

	@Override
	public float getPathingMalus(IBlockReader cache, MobEntity entity, PathNodeType nodeType, BlockPos pos, Vec3i direction, Predicate<Direction> sides) {
		if(direction.getY() != 0) {
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

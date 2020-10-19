package tcb.spiderstpo.common.entity.mob;

import java.util.List;

import javax.annotation.Nullable;

import org.apache.commons.lang3.tuple.Pair;

import net.minecraft.block.BlockState;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import tcb.spiderstpo.common.entity.movement.IAdvancedPathFindingEntity;

public interface IClimberEntity extends IAdvancedPathFindingEntity {
	public float getAttachmentOffset(Direction.Axis axis, float partialTicks);

	public float getVerticalOffset(float partialTicks);

	public Orientation getOrientation();

	public Orientation calculateOrientation(float partialTicks);

	public void setRenderOrientation(Orientation orientation);

	@Nullable
	public Orientation getRenderOrientation();

	public float getMovementSpeed();

	public Pair<Direction, Vec3d> getGroundDirection();

	public boolean shouldTrackPathingTargets();
	
	@Nullable
	public List<BlockPos> getTrackedPathingTargets();

	public boolean canClimbOnBlock(BlockState state, BlockPos pos);

	public float getBlockSlipperiness(BlockPos pos);

	public boolean canClimberTriggerWalking();

	public boolean canClimbInWater();

	public void setCanClimbInWater(boolean value);

	public boolean canClimbInLava();

	public void setCanClimbInLava(boolean value);

	public float getCollisionsInclusionRange();

	public void setCollisionsInclusionRange(float range);

	public float getCollisionsSmoothingRange();

	public void setCollisionsSmoothingRange(float range);
}

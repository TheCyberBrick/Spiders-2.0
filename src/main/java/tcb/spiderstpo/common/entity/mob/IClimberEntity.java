package tcb.spiderstpo.common.entity.mob;

import java.util.List;

import javax.annotation.Nullable;

import org.apache.commons.lang3.tuple.Pair;

import net.minecraft.block.BlockState;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import tcb.spiderstpo.common.entity.movement.IAdvancedPathFindingEntity;

public interface IClimberEntity extends IAdvancedPathFindingEntity {
	public float getAttachmentOffset(Direction.Axis axis, float partialTicks);

	public float getVerticalOffset(float partialTicks);

	public Orientation getOrientation();

	public Orientation calculateOrientation(float partialTicks);

	public float getMovementSpeed();

	public Pair<Direction, Vector3d> getGroundDirection();

	@Nullable
	public List<BlockPos> getPathingTargets();

	public boolean canClimbOnBlock(BlockState state, BlockPos pos);

	public float getBlockSlipperiness(BlockPos pos);
}

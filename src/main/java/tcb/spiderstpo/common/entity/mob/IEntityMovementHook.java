package tcb.spiderstpo.common.entity.mob;

import javax.annotation.Nullable;

import net.minecraft.entity.MoverType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;

public interface IEntityMovementHook {
	public boolean onMove(MoverType type, Vector3d pos, boolean pre);

	@Nullable
	public BlockPos getAdjustedOnPosition(BlockPos onPosition);

	public boolean getAdjustedCanTriggerWalking(boolean canTriggerWalking);
}

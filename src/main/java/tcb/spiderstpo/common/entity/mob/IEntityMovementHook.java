package tcb.spiderstpo.common.entity.mob;

import javax.annotation.Nullable;

import net.minecraft.entity.MoverType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public interface IEntityMovementHook {
	public boolean onMove(MoverType type, Vec3d pos, boolean pre);

	@Nullable
	public BlockPos getAdjustedOnPosition(BlockPos onPosition);

	public boolean getAdjustedCanTriggerWalking(boolean canTriggerWalking);
}

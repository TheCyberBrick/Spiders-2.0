package tcb.spiderstpo.common.entity.mob;

import javax.annotation.Nullable;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public interface IEntityMovementHook {
	public boolean onMove(MoverType type, Vec3 pos, boolean pre);

	@Nullable
	public BlockPos getAdjustedOnPosition(BlockPos onPosition);

	public Entity.MovementEmission getAdjustedCanTriggerWalking(Entity.MovementEmission canTriggerWalking);
}

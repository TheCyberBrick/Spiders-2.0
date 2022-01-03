package tcb.spiderstpo.common.entity.mob;

import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;

public class PathingTarget {
	public final BlockPos pos;
	public final Direction side;
	
	public PathingTarget(BlockPos pos, Direction side) {
		this.pos = pos;
		this.side = side;
	}
}
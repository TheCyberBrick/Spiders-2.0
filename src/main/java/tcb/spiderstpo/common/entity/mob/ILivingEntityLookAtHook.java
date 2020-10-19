package tcb.spiderstpo.common.entity.mob;

import net.minecraft.command.arguments.EntityAnchorArgument;
import net.minecraft.util.math.Vec3d;

public interface ILivingEntityLookAtHook {
	public Vec3d onLookAt(EntityAnchorArgument.Type anchor, Vec3d vec);
}

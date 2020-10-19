package tcb.spiderstpo.common.entity.mob;

import net.minecraft.command.arguments.EntityAnchorArgument;
import net.minecraft.util.math.vector.Vector3d;

public interface ILivingEntityLookAtHook {
	public Vector3d onLookAt(EntityAnchorArgument.Type anchor, Vector3d vec);
}

package tcb.spiderstpo.common.entity.mob;

import net.minecraft.util.math.vector.Vector3d;

public interface ILivingEntityTravelHook {
	public boolean onTravel(Vector3d relative, boolean pre);
}

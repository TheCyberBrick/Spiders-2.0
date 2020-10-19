package tcb.spiderstpo.common.entity.mob;

import net.minecraft.util.math.Vec3d;

public interface ILivingEntityTravelHook {
	public boolean onTravel(Vec3d relative, boolean pre);
}

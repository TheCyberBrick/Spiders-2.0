package tcb.spiderstpo.common.entity.mob;

import net.minecraft.network.syncher.EntityDataAccessor;

public interface ILivingEntityDataManagerHook {
	public void onNotifyDataManagerChange(EntityDataAccessor<?> key);
}

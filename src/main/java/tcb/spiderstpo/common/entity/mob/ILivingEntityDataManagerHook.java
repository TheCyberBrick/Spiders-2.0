package tcb.spiderstpo.common.entity.mob;

import net.minecraft.network.datasync.DataParameter;

public interface ILivingEntityDataManagerHook {
	public void onNotifyDataManagerChange(DataParameter<?> key);
}

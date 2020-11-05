package tcb.spiderstpo.common.entity.mob;

import net.minecraft.nbt.CompoundNBT;

public interface IEntityReadWriteHook {
	public void onRead(CompoundNBT nbt);
	
	public void onWrite(CompoundNBT nbt);
}

package tcb.spiderstpo.common.entity.mob;

import net.minecraft.nbt.CompoundTag;

public interface IEntityReadWriteHook {
	public void onRead(CompoundTag nbt);
	
	public void onWrite(CompoundTag nbt);
}

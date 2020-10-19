package tcb.spiderstpo.common;

import net.minecraftforge.common.MinecraftForge;

public class CommonSetup {
	public static void run() {
		MinecraftForge.EVENT_BUS.register(CommonEventHandlers.class);
	}
}

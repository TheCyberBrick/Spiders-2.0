package tcb.spiderstpo.compat.mobends;

import goblinbob.mobends.core.addon.AddonHelper;
import net.minecraftforge.fml.common.Loader;

public class MoBendsCompat {
	private static boolean isEnabled = false;
	
	public static void init() {
		if(Loader.isModLoaded("mobends")) {
			MoBendsCompatRegistrar.register();
			isEnabled = true;
		}
	}
	
	public static boolean isEnabled() {
		return isEnabled;
	}
}

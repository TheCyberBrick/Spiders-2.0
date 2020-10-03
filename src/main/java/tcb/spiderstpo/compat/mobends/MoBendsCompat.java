package tcb.spiderstpo.compat.mobends;

import goblinbob.mobends.core.addon.AddonHelper;

public class MoBendsCompat {
	public static void register() {
		AddonHelper.registerAddon("spiderstpo", new BetterSpiderAddon());
	}
}

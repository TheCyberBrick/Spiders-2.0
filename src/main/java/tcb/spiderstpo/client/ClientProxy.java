package tcb.spiderstpo.client;

import tcb.spiderstpo.common.CommonProxy;

public class ClientProxy extends CommonProxy {
	@Override
	public void preInit() {
		EntityRenderers.register();
	}
}

package tcb.spiderstpo.compat.mobends;

import goblinbob.mobends.core.addon.AddonAnimationRegistry;
import goblinbob.mobends.core.addon.IAddon;
import tcb.spiderstpo.common.entity.mob.BetterSpiderEntity;

public class BetterSpiderAddon implements IAddon {

	@Override
	public void registerContent(AddonAnimationRegistry registry) {
		registry.registerNewEntity(BetterSpiderEntity.class, BetterSpiderData::new, BetterSpiderMutator::new, new BetterSpiderMutatedRenderer<>(),
				new BetterSpiderPreviewer(),
				"head", "body", "neck", "leg1", "leg2", "leg3", "leg4", "leg5", "leg6", "leg7", "leg8",
				"foreLeg1", "foreLeg2", "foreLeg3", "foreLeg4", "foreLeg5", "foreLeg6", "foreLeg7", "foreLeg8");		
	}

	@Override
	public String getDisplayName() {
		return "Spiders 2.0 Compatbility";
	}

}

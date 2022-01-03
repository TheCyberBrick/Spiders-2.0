package tcb.spiderstpo.common;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.monster.CaveSpider;
import net.minecraft.world.entity.monster.Spider;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class CommonEventHandlers {
	@SubscribeEvent
	public static void onEntitySize(EntityEvent.Size event) {
		Entity entity = event.getEntity();

		if(entity instanceof CaveSpider) {
			event.setNewSize(EntityDimensions.scalable(0.7f, 0.5f));
		} else if(entity instanceof Spider) {
			event.setNewSize(EntityDimensions.scalable(0.95f, 0.85f));
		}
	}
}

package tcb.spiderstpo.common;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntitySize;
import net.minecraft.entity.monster.CaveSpiderEntity;
import net.minecraft.entity.monster.SpiderEntity;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;

public class CommonEventHandlers {
	@SubscribeEvent
	public static void onEntitySize(EntityEvent.EyeHeight event) {
		Entity entity = event.getEntity();

		if(entity instanceof CaveSpiderEntity) {
			setSize(entity, EntitySize.flexible(0.7f, 0.5f));
		} else if(entity instanceof SpiderEntity) {
			setSize(entity, EntitySize.flexible(0.95f, 0.85f));
		}
	}

	private static void setSize(Entity entity, EntitySize size) {
		ObfuscationReflectionHelper.setPrivateValue(Entity.class, entity, size, "field_213325_aI");
	}
}
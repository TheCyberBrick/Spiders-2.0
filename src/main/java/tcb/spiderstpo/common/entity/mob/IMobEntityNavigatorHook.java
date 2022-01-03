package tcb.spiderstpo.common.entity.mob;

import javax.annotation.Nullable;

import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;

public interface IMobEntityNavigatorHook {
	@Nullable
	public PathNavigation onCreateNavigator(Level world);
}

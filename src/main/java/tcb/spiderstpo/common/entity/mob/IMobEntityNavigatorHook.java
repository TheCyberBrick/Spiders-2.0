package tcb.spiderstpo.common.entity.mob;

import javax.annotation.Nullable;

import net.minecraft.pathfinding.PathNavigator;
import net.minecraft.world.World;

public interface IMobEntityNavigatorHook {
	@Nullable
	public PathNavigator onCreateNavigator(World world);
}

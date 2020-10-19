package tcb.spiderstpo.common;

import net.minecraft.block.Block;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.Tag;
import net.minecraft.util.ResourceLocation;

public class ModTags {
	public static final Tag<Block> NON_CLIMBABLE = new BlockTags.Wrapper(new ResourceLocation("spiderstpo:non_climbable"));
}

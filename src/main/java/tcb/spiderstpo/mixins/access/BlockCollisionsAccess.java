package tcb.spiderstpo.mixins.access;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Cursor3D;
import net.minecraft.world.level.BlockCollisions;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(BlockCollisions.class)
public interface BlockCollisionsAccess {

    @Accessor
    Cursor3D getCursor();

    @Invoker
    BlockGetter invokeGetChunk(int x, int z);

    @Accessor
    BlockPos.MutableBlockPos getPos();

    @Accessor
    CollisionGetter getCollisionGetter();

    @Accessor
    CollisionContext getContext();

    @Accessor
    AABB getBox();

    @Accessor
    VoxelShape getEntityShape();
}

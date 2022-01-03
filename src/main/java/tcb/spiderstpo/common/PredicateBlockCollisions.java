package tcb.spiderstpo.common;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Cursor3D;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockCollisions;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import tcb.spiderstpo.mixins.access.BlockCollisionsAccess;

import java.util.function.BiPredicate;

// This class exists because mojang removed the predicate in the BlockCollisions/VoxelSpliterator constructor and instead hardcoded it to a boolean that lets you check if a block is air or not, so this class exists to add that predicate back.
public class PredicateBlockCollisions extends BlockCollisions {

    private final BiPredicate<BlockState, BlockPos> predicate;

    public PredicateBlockCollisions(CollisionGetter p_186402_, @Nullable Entity p_186403_, AABB p_186404_) {
        this(p_186402_, p_186403_, p_186404_, (state, pos) -> true);
    }

    public PredicateBlockCollisions(CollisionGetter p_186406_, @Nullable Entity p_186407_, AABB p_186408_, BiPredicate<BlockState, BlockPos> predicate) {
        super(p_186406_, p_186407_, p_186408_, false);
        this.predicate = predicate;
    }

    @Override
    protected VoxelShape computeNext() {
        while(true) {
            final Cursor3D cursor = ((BlockCollisionsAccess) this).getCursor();
            if (cursor.advance()) {
                int i = cursor.nextX();
                int j = cursor.nextY();
                int k = cursor.nextZ();
                int l = cursor.getNextType();
                if (l == 3) {
                    continue;
                }

                BlockGetter blockgetter = ((BlockCollisionsAccess) this).invokeGetChunk(i, k);
                if (blockgetter == null) {
                    continue;
                }

                final BlockPos.MutableBlockPos pos = ((BlockCollisionsAccess) this).getPos();
                pos.set(i, j, k);
                BlockState blockstate = blockgetter.getBlockState(pos);
                if (!this.predicate.test(blockstate, pos) || l == 1 && !blockstate.hasLargeCollisionShape() || l == 2 && !blockstate.is(Blocks.MOVING_PISTON)) {
                    continue;
                }

                VoxelShape voxelshape = blockstate.getCollisionShape(((BlockCollisionsAccess) this).getCollisionGetter(), pos, ((BlockCollisionsAccess) this).getContext());
                if (voxelshape == Shapes.block()) {
                    if (!((BlockCollisionsAccess) this).getBox().intersects((double)i, (double)j, (double)k, (double)i + 1.0D, (double)j + 1.0D, (double)k + 1.0D)) {
                        continue;
                    }

                    return voxelshape.move((double)i, (double)j, (double)k);
                }

                VoxelShape voxelshape1 = voxelshape.move((double)i, (double)j, (double)k);
                if (!Shapes.joinIsNotEmpty(voxelshape1, ((BlockCollisionsAccess) this).getEntityShape(), BooleanOp.AND)) {
                    continue;
                }

                return voxelshape1;
            }

            return this.endOfData();
        }
    }
}

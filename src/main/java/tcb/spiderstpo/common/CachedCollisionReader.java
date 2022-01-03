package tcb.spiderstpo.common;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.border.WorldBorder;
import org.jetbrains.annotations.Nullable;

public class CachedCollisionReader implements CollisionGetter {
	private final CollisionGetter collisionReader;
	private final BlockGetter[] blockReaderCache;
	private final int minChunkX, minChunkZ, width;

	public CachedCollisionReader(CollisionGetter collisionReader, AABB aabb) {
		this.collisionReader = collisionReader;

		this.minChunkX = ((Mth.floor(aabb.minX - 1.0E-7D) - 1) >> 4);
		int maxChunkX = ((Mth.floor(aabb.maxX + 1.0E-7D) + 1) >> 4);
		this.minChunkZ = ((Mth.floor(aabb.minZ - 1.0E-7D) - 1) >> 4);
		int maxChunkZ = ((Mth.floor(aabb.maxZ + 1.0E-7D) + 1) >> 4);

		this.width = maxChunkX - this.minChunkX + 1;
		int depth = maxChunkZ - this.minChunkZ + 1;

		BlockGetter[] blockReaderCache = new BlockGetter[width * depth];

		for(int cx = minChunkX; cx <= maxChunkX; cx++) {
			for(int cz = minChunkZ; cz <= maxChunkZ; cz++) {
				blockReaderCache[(cx - minChunkX) + (cz - minChunkZ) * width] = collisionReader.getChunkForCollisions(cx, cz);
			}
		}

		this.blockReaderCache = blockReaderCache;
	}

	@Override
	public BlockEntity getBlockEntity(BlockPos pos) {
		return this.collisionReader.getBlockEntity(pos);
	}

	@Override
	public BlockState getBlockState(BlockPos pos) {
		return this.collisionReader.getBlockState(pos);
	}

	@Override
	public FluidState getFluidState(BlockPos pos) {
		return this.collisionReader.getFluidState(pos);
	}

	@Override
	public WorldBorder getWorldBorder() {
		return this.collisionReader.getWorldBorder();
	}

	@Override
	public BlockGetter getChunkForCollisions(int chunkX, int chunkZ) {
		return this.blockReaderCache[(chunkX - minChunkX) + (chunkZ - minChunkZ) * width];
	}

	@Override
	public List<VoxelShape> getEntityCollisions(@Nullable Entity entity, AABB aabb) {
		return this.collisionReader.getEntityCollisions(entity, aabb);
	}

	@Override
	public int getHeight() {
		return this.collisionReader.getHeight();
	}

	@Override
	public int getMinBuildHeight() {
		return this.collisionReader.getMinBuildHeight();
	}
}

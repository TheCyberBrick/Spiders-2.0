package tcb.spiderstpo.common.entity.movement;

import java.util.EnumSet;

import javax.annotation.Nullable;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLiving;
import net.minecraft.init.Blocks;
import net.minecraft.pathfinding.PathNodeType;
import net.minecraft.pathfinding.PathPoint;
import net.minecraft.pathfinding.WalkNodeProcessor;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumFacing.Axis;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockPos.MutableBlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.IBlockAccess;

public class AdvancedWalkNodeProcessor<T extends EntityLiving & IAdvancedPathFindingEntity> extends WalkNodeProcessor {
	protected T obstructionAwareEntity;
	protected boolean startFromGround = true;
	protected boolean checkObstructions;
	protected int pathingSizeOffsetX, pathingSizeOffsetY, pathingSizeOffsetZ;
	protected EnumSet<EnumFacing> pathableFacings = EnumSet.of(EnumFacing.DOWN);

	private final Long2ObjectMap<PathNodeType> pathNodeTypeCache = new Long2ObjectOpenHashMap<>();
	private final Object2BooleanMap<AxisAlignedBB> aabbCollisionCache = new Object2BooleanOpenHashMap<>();

	protected boolean alwaysAllowDiagonals = true;

	public void setObstructionAwareEntity(T obstructionAwareEntity) {
		this.obstructionAwareEntity = obstructionAwareEntity;
	}

	public void setStartPathOnGround(boolean startFromGround) {
		this.startFromGround = startFromGround;
	}

	public void setCheckObstructions(boolean checkObstructions) {
		this.checkObstructions = checkObstructions;
	}

	public void setCanPathWalls(boolean canPathWalls) {
		if(canPathWalls) {
			this.pathableFacings.add(EnumFacing.NORTH);
			this.pathableFacings.add(EnumFacing.EAST);
			this.pathableFacings.add(EnumFacing.SOUTH);
			this.pathableFacings.add(EnumFacing.WEST);
		} else {
			this.pathableFacings.remove(EnumFacing.NORTH);
			this.pathableFacings.remove(EnumFacing.EAST);
			this.pathableFacings.remove(EnumFacing.SOUTH);
			this.pathableFacings.remove(EnumFacing.WEST);
		}
	}

	public void setCanPathCeiling(boolean canPathCeiling) {
		if(canPathCeiling) {
			this.pathableFacings.add(EnumFacing.UP);
		} else {
			this.pathableFacings.remove(EnumFacing.UP);
		}
	}

	@Override
	public void init(IBlockAccess sourceIn, EntityLiving mob) {
		super.init(sourceIn, mob);
		this.pathingSizeOffsetX = Math.max(1, MathHelper.floor(this.entity.width / 2.0f + 1));
		this.pathingSizeOffsetY = Math.max(1, MathHelper.floor(this.entity.height + 1));
		this.pathingSizeOffsetZ = Math.max(1, MathHelper.floor(this.entity.width / 2.0f + 1));
	}

	@Override
	public void postProcess() {
		super.postProcess();
		this.pathNodeTypeCache.clear();
		this.aabbCollisionCache.clear();
	}

	private boolean checkAabbCollision(AxisAlignedBB aabb) {
		return this.aabbCollisionCache.computeIfAbsent(aabb, (p_237237_2_) -> {
			return this.entity.world.collidesWithAnyBlock(aabb);
		});
	}

	@Override
	public PathPoint getStart() {
		double x = this.entity.posX;
		double y = this.entity.posY;
		double z = this.entity.posZ;

		MutableBlockPos checkPos = new MutableBlockPos();

		int by = MathHelper.floor(y);

		if(this.getCanSwim() && this.entity.isInWater()) {
            by = (int)this.entity.getEntityBoundingBox().minY;
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(MathHelper.floor(this.entity.posX), by, MathHelper.floor(this.entity.posZ));

            for(Block block = this.blockaccess.getBlockState(pos).getBlock(); block == Blocks.FLOWING_WATER || block == Blocks.WATER; block = this.blockaccess.getBlockState(pos).getBlock()) {
                ++by;
                pos.setPos(MathHelper.floor(this.entity.posX), by, MathHelper.floor(this.entity.posZ));
            }
        } else if(this.entity.onGround || !this.startFromGround) {
            by = MathHelper.floor(this.entity.getEntityBoundingBox().minY + 0.5D);
        } else {
            BlockPos pos;

            for(pos = new BlockPos(this.entity); (this.blockaccess.getBlockState(pos).getMaterial() == Material.AIR || this.blockaccess.getBlockState(pos).getBlock().isPassable(this.blockaccess, pos)) && pos.getY() > 0; pos = pos.down()) {
                ;
            }

            by = pos.up().getY();
        }

		BlockPos startPos = new BlockPos(x, y, z);

		PathNodeType startNodeType = this.getPathNodeTypeCached(this.entity, startPos.getX(), by, startPos.getZ());
		if(this.entity.getPathPriority(startNodeType) < 0.0F) {
			AxisAlignedBB aabb = this.entity.getEntityBoundingBox();

			if(this.isSafeStartingPosition(checkPos.setPos(aabb.minX, by, aabb.minZ)) || this.isSafeStartingPosition(checkPos.setPos(aabb.minX, by, aabb.maxZ)) || this.isSafeStartingPosition(checkPos.setPos(aabb.maxX, by, aabb.minZ)) || this.isSafeStartingPosition(checkPos.setPos(aabb.maxX, by, aabb.maxZ))) {
				PathPoint startPathPoint = this.openPoint(checkPos.getX(), checkPos.getY(), checkPos.getZ());
				startPathPoint.nodeType = this.getPathNodeTypeCached(this.entity, new BlockPos(startPathPoint.x, startPathPoint.y, startPathPoint.z));
				startPathPoint.costMalus = this.entity.getPathPriority(startPathPoint.nodeType);
				return startPathPoint;
			}
		}

		PathPoint startPathPoint = this.openPoint(startPos.getX(), by, startPos.getZ());
		startPathPoint.nodeType = this.getPathNodeTypeCached(this.entity, new BlockPos(startPathPoint.x, startPathPoint.y, startPathPoint.z));
		startPathPoint.costMalus = this.entity.getPathPriority(startPathPoint.nodeType);
		return startPathPoint;
	}

	private boolean isSafeStartingPosition(BlockPos pos) {
		PathNodeType pathnodetype = this.getPathNodeTypeCached(this.entity, pos);
		return this.entity.getPathPriority(pathnodetype) >= 0.0F;
	}

	private boolean allowDiagonalPathOptions(PathPoint[] options) {
		return this.alwaysAllowDiagonals || options == null || options.length == 0 || ((options[0] == null || options[0].nodeType == PathNodeType.OPEN || options[0].costMalus != 0.0F) && (options.length <= 1 || (options[1] == null || options[1].nodeType == PathNodeType.OPEN || options[1].costMalus != 0.0F)));
	}

	private boolean isPassableWithExemptions(IBlockAccess blockAccess, int x, int y, int z, @Nullable EnumSet<EnumFacing> exemptions, @Nullable EnumSet<EnumFacing> requirement, @Nullable EnumSet<EnumFacing> found) {
		if(requirement != null && found == null) {
			found = EnumSet.noneOf(EnumFacing.class);
		}

		for(int xo = 0; xo < this.entitySizeX; xo++) {
			for(int yo = 0; yo < this.entitySizeY; yo++) {
				for(int zo = 0; zo < this.entitySizeZ; zo++) {
					PathNodeType nodeType = getPathNodeTypeWithConditions(blockAccess, x + xo, y + yo, z + zo, this.pathingSizeOffsetX, this.pathingSizeOffsetY, this.pathingSizeOffsetZ, this.pathableFacings, exemptions, found);

					if(nodeType != PathNodeType.OPEN && this.entity.getPathPriority(nodeType) >= 0.0f) {
						if(requirement != null) {
							for(EnumFacing facing : requirement) {
								if(found.contains(facing)) {
									return true;
								}
							}

							return false;
						}

						return true;
					}
				}
			}
		}
		return false;
	}

	@Override
	public int findPathOptions(PathPoint[] pathOptions, PathPoint currentPoint, PathPoint targetPoint, float maxDistance) {
		int openedNodeCount = 0;
		int stepHeight = 0;

		PathNodeType nodeTypeAbove = this.getPathNodeTypeCached(this.entity, currentPoint.x, currentPoint.y + 1, currentPoint.z);

		if(this.entity.getPathPriority(nodeTypeAbove) >= 0.0F) {
			stepHeight = MathHelper.floor(Math.max(1.0F, this.entity.stepHeight));
		}

		BlockPos pos = (new BlockPos(currentPoint.x, currentPoint.y, currentPoint.z)).down();
		double height = currentPoint.y - (1.0D - this.blockaccess.getBlockState(pos).getBoundingBox(this.blockaccess, pos).maxY);
		
		PathPoint[] pathsPZ = this.getSafePoints(currentPoint.x, currentPoint.y, currentPoint.z + 1, stepHeight, height, EnumFacing.SOUTH, this.checkObstructions);
		PathPoint[] pathsNX = this.getSafePoints(currentPoint.x - 1, currentPoint.y, currentPoint.z, stepHeight, height, EnumFacing.WEST, this.checkObstructions);
		PathPoint[] pathsPX = this.getSafePoints(currentPoint.x + 1, currentPoint.y, currentPoint.z, stepHeight, height, EnumFacing.EAST, this.checkObstructions);
		PathPoint[] pathsNZ = this.getSafePoints(currentPoint.x, currentPoint.y, currentPoint.z - 1, stepHeight, height, EnumFacing.NORTH, this.checkObstructions);

		for(int k = 0; k < pathsPZ.length; k++) {
			if(isSuitablePoint(pathsPZ[k], currentPoint, this.checkObstructions)) {
				pathOptions[openedNodeCount++] = pathsPZ[k];
			}
		}

		for(int k = 0; k < pathsNX.length; k++) {
			if(isSuitablePoint(pathsNX[k], currentPoint, this.checkObstructions)) {
				pathOptions[openedNodeCount++] = pathsNX[k];
			}
		}

		for(int k = 0; k < pathsPX.length; k++) {
			if(isSuitablePoint(pathsPX[k], currentPoint, this.checkObstructions)) {
				pathOptions[openedNodeCount++] = pathsPX[k];
			}
		}

		for(int k = 0; k < pathsNZ.length; k++) {
			if(isSuitablePoint(pathsNZ[k], currentPoint, this.checkObstructions)) {
				pathOptions[openedNodeCount++] = pathsNZ[k];
			}
		}

		PathPoint[] pathsNY = null;
		if(this.checkObstructions || this.pathableFacings.size() > 1) {
			boolean hasValidPath = false;

			if(this.pathableFacings.size() > 1) {
				EnumSet<EnumFacing> found = EnumSet.noneOf(EnumFacing.class);
				this.isPassableWithExemptions(this.blockaccess, currentPoint.x, currentPoint.y - 1, currentPoint.z, EnumSet.of(EnumFacing.UP, EnumFacing.DOWN), null, found);
				hasValidPath = this.isPassableWithExemptions(this.blockaccess, currentPoint.x, currentPoint.y, currentPoint.z, EnumSet.of(EnumFacing.UP, EnumFacing.DOWN), found, null);
			}

			if(hasValidPath) {
				pathsNY = this.getSafePoints(currentPoint.x, currentPoint.y - 1, currentPoint.z, stepHeight, height, EnumFacing.DOWN, this.checkObstructions);
				for(int k = 0; k < pathsNY.length; k++) {
					if(isSuitablePoint(pathsNY[k], currentPoint, this.checkObstructions)) {
						pathOptions[openedNodeCount++] = pathsNY[k];
					}
				}
			}
		}

		PathPoint[] pathsPY = null;
		if(this.pathableFacings.size() > 1) {
			EnumSet<EnumFacing> found = EnumSet.noneOf(EnumFacing.class);
			this.isPassableWithExemptions(this.blockaccess, currentPoint.x, currentPoint.y + 1, currentPoint.z, EnumSet.of(EnumFacing.UP, EnumFacing.DOWN), null, found);

			if(this.isPassableWithExemptions(this.blockaccess, currentPoint.x, currentPoint.y, currentPoint.z, EnumSet.of(EnumFacing.UP, EnumFacing.DOWN), found, null)) {
				pathsPY = this.getSafePoints(currentPoint.x, currentPoint.y + 1, currentPoint.z, stepHeight, height, EnumFacing.UP, this.checkObstructions);
				for(int k = 0; k < pathsPY.length; k++) {
					if(isSuitablePoint(pathsPY[k], currentPoint, this.checkObstructions)) {
						pathOptions[openedNodeCount++] = pathsPY[k];
					}
				}
			}
		}

		boolean allowDiagonalNZ = this.allowDiagonalPathOptions(pathsNZ);
		boolean allowDiagonalPZ = this.allowDiagonalPathOptions(pathsPZ);
		boolean allowDiagonalPX = this.allowDiagonalPathOptions(pathsPX);
		boolean allowDiagonalNX = this.allowDiagonalPathOptions(pathsNX);

		boolean fitsThroughPoles = this.entity.width < 0.5f;

		boolean allowOuterCorners = this.pathableFacings.size() >= 3;

		if(allowDiagonalNZ && allowDiagonalNX) {
			PathPoint[] pathsNXNZ = this.getSafePoints(currentPoint.x - this.entitySizeX, currentPoint.y, currentPoint.z - 1, stepHeight, height, EnumFacing.NORTH, this.checkObstructions);

			boolean foundDiagonal = false;

			for(int k = 0; k < pathsNXNZ.length; k++) {
				if(isSuitablePoint(pathsNX, pathsNZ, pathsNXNZ[k], this.checkObstructions, fitsThroughPoles, allowOuterCorners)) {
					pathOptions[openedNodeCount++] = pathsNXNZ[k];
					foundDiagonal = true;
				}
			}

			if(!foundDiagonal && (this.entitySizeX != 1 || this.entitySizeZ != 1)) {
				pathsNXNZ = this.getSafePoints(currentPoint.x - 1, currentPoint.y, currentPoint.z - this.entitySizeZ, stepHeight, height, EnumFacing.NORTH, this.checkObstructions);

				for(int k = 0; k < pathsNXNZ.length; k++) {
					if(isSuitablePoint(pathsNX, pathsNZ, pathsNXNZ[k], this.checkObstructions, fitsThroughPoles, allowOuterCorners)) {
						pathOptions[openedNodeCount++] = pathsNXNZ[k];
					}
				}
			}
		}

		if(allowDiagonalNZ && allowDiagonalPX) {
			PathPoint[] pathsPXNZ = this.getSafePoints(currentPoint.x + 1, currentPoint.y, currentPoint.z - 1, stepHeight, height, EnumFacing.NORTH, this.checkObstructions);

			for(int k = 0; k < pathsPXNZ.length; k++) {
				if(isSuitablePoint(pathsPX, pathsNZ, pathsPXNZ[k], this.checkObstructions, fitsThroughPoles, allowOuterCorners)) {
					pathOptions[openedNodeCount++] = pathsPXNZ[k];
				}
			}
		}

		if(allowDiagonalPZ && allowDiagonalNX) {
			PathPoint[] pathsNXPZ = this.getSafePoints(currentPoint.x - 1, currentPoint.y, currentPoint.z + 1, stepHeight, height, EnumFacing.SOUTH, this.checkObstructions);

			for(int k = 0; k < pathsNXPZ.length; k++) {
				if(isSuitablePoint(pathsNX, pathsPZ, pathsNXPZ[k], this.checkObstructions, fitsThroughPoles, allowOuterCorners)) {
					pathOptions[openedNodeCount++] = pathsNXPZ[k];
				}
			}
		}

		if(allowDiagonalPZ && allowDiagonalPX) {
			PathPoint[] pathsPXPZ = this.getSafePoints(currentPoint.x + this.entitySizeX, currentPoint.y, currentPoint.z + 1, stepHeight, height, EnumFacing.SOUTH, this.checkObstructions);

			boolean foundDiagonal = false;

			for(int k = 0; k < pathsPXPZ.length; k++) {
				if(isSuitablePoint(pathsPX, pathsPZ, pathsPXPZ[k], this.checkObstructions, fitsThroughPoles, allowOuterCorners)) {
					pathOptions[openedNodeCount++] = pathsPXPZ[k];
					foundDiagonal = true;
				}
			}

			if(!foundDiagonal && (this.entitySizeX != 1 || this.entitySizeZ != 1)) {
				pathsPXPZ = this.getSafePoints(currentPoint.x + 1, currentPoint.y, currentPoint.z + this.entitySizeZ, stepHeight, height, EnumFacing.SOUTH, this.checkObstructions);

				for(int k = 0; k < pathsPXPZ.length; k++) {
					if(isSuitablePoint(pathsPX, pathsPZ, pathsPXPZ[k], this.checkObstructions, fitsThroughPoles, allowOuterCorners)) {
						pathOptions[openedNodeCount++] = pathsPXPZ[k];
					}
				}
			}
		}

		if(this.pathableFacings.size() > 1) {
			boolean allowDiagonalPY = this.allowDiagonalPathOptions(pathsPY);
			boolean allowDiagonalNY = this.allowDiagonalPathOptions(pathsNY);

			if(allowDiagonalNY && allowDiagonalNX && this.isPassableWithExemptions(this.blockaccess, currentPoint.x, currentPoint.y, currentPoint.z, EnumSet.of(EnumFacing.UP, EnumFacing.EAST), null, null)) {
				PathPoint[] pathsNYNX = this.getSafePoints(currentPoint.x - this.entitySizeX, currentPoint.y - 1, currentPoint.z, stepHeight, height, EnumFacing.WEST, this.checkObstructions);

				boolean foundDiagonal = false;

				for(int k = 0; k < pathsNYNX.length; k++) {
					if(isSuitablePoint(pathsNY, pathsNX, pathsNYNX[k], this.checkObstructions, fitsThroughPoles, allowOuterCorners)) {
						pathOptions[openedNodeCount++] = pathsNYNX[k];
						foundDiagonal = true;
					}
				}

				if(!foundDiagonal && (this.entitySizeX != 1 || this.entitySizeY != 1)) {
					pathsNYNX = this.getSafePoints(currentPoint.x - 1, currentPoint.y - this.entitySizeY, currentPoint.z, stepHeight, height, EnumFacing.WEST, this.checkObstructions);

					for(int k = 0; k < pathsNYNX.length; k++) {
						if(isSuitablePoint(pathsNY, pathsNX, pathsNYNX[k], this.checkObstructions, fitsThroughPoles, allowOuterCorners)) {
							pathOptions[openedNodeCount++] = pathsNYNX[k];
						}
					}
				}
			}

			if(allowDiagonalNY && allowDiagonalPX && this.isPassableWithExemptions(this.blockaccess, currentPoint.x, currentPoint.y, currentPoint.z, EnumSet.of(EnumFacing.UP, EnumFacing.WEST), null, null)) {
				PathPoint[] pathsNYPX = this.getSafePoints(currentPoint.x + 1, currentPoint.y - 1, currentPoint.z, stepHeight, height, EnumFacing.EAST, this.checkObstructions);

				for(int k = 0; k < pathsNYPX.length; k++) {
					if(isSuitablePoint(pathsNY, pathsPX, pathsNYPX[k], this.checkObstructions, fitsThroughPoles, allowOuterCorners)) {
						pathOptions[openedNodeCount++] = pathsNYPX[k];
					}
				}
			}

			if(allowDiagonalNY && allowDiagonalNZ && this.isPassableWithExemptions(this.blockaccess, currentPoint.x, currentPoint.y, currentPoint.z, EnumSet.of(EnumFacing.UP, EnumFacing.SOUTH), null, null)) {
				PathPoint[] pathsNYNZ = this.getSafePoints(currentPoint.x, currentPoint.y - this.entitySizeY, currentPoint.z - 1, stepHeight, height, EnumFacing.NORTH, this.checkObstructions);

				boolean foundDiagonal = false;

				for(int k = 0; k < pathsNYNZ.length; k++) {
					if(isSuitablePoint(pathsNY, pathsNZ, pathsNYNZ[k], this.checkObstructions, fitsThroughPoles, allowOuterCorners)) {
						pathOptions[openedNodeCount++] = pathsNYNZ[k];
						foundDiagonal = true;
					}
				}

				if(!foundDiagonal && (this.entitySizeY != 1 || this.entitySizeZ != 1)) {
					pathsNYNZ = this.getSafePoints(currentPoint.x, currentPoint.y - 1, currentPoint.z - this.entitySizeZ, stepHeight, height, EnumFacing.NORTH, this.checkObstructions);

					for(int k = 0; k < pathsNYNZ.length; k++) {
						if(isSuitablePoint(pathsNY, pathsNZ, pathsNYNZ[k], this.checkObstructions, fitsThroughPoles, allowOuterCorners)) {
							pathOptions[openedNodeCount++] = pathsNYNZ[k];
						}
					}
				}
			}

			if(allowDiagonalNY && allowDiagonalPZ && this.isPassableWithExemptions(this.blockaccess, currentPoint.x, currentPoint.y, currentPoint.z, EnumSet.of(EnumFacing.UP, EnumFacing.NORTH), null, null)) {
				PathPoint[] pathsNYPZ = this.getSafePoints(currentPoint.x, currentPoint.y - 1, currentPoint.z + 1, stepHeight, height, EnumFacing.SOUTH, this.checkObstructions);

				for(int k = 0; k < pathsNYPZ.length; k++) {
					if(isSuitablePoint(pathsNY, pathsPZ, pathsNYPZ[k], this.checkObstructions, fitsThroughPoles, allowOuterCorners)) {
						pathOptions[openedNodeCount++] = pathsNYPZ[k];
					}
				}
			}

			if(allowDiagonalPY && allowDiagonalNX && this.isPassableWithExemptions(this.blockaccess, currentPoint.x, currentPoint.y, currentPoint.z, EnumSet.of(EnumFacing.DOWN, EnumFacing.EAST), null, null)) {
				PathPoint[] pathsPYNX = this.getSafePoints(currentPoint.x - 1, currentPoint.y + 1, currentPoint.z, stepHeight, height, EnumFacing.WEST, this.checkObstructions);

				for(int k = 0; k < pathsPYNX.length; k++) {
					if(isSuitablePoint(pathsPY, pathsNZ, pathsPYNX[k], this.checkObstructions, fitsThroughPoles, allowOuterCorners)) {
						pathOptions[openedNodeCount++] = pathsPYNX[k];
					}
				}
			}

			if(allowDiagonalPY && allowDiagonalPX && this.isPassableWithExemptions(this.blockaccess, currentPoint.x, currentPoint.y, currentPoint.z, EnumSet.of(EnumFacing.DOWN, EnumFacing.WEST), null, null)) {
				PathPoint[] pathsPYPX = this.getSafePoints(currentPoint.x + this.entitySizeX, currentPoint.y + 1, currentPoint.z, stepHeight, height, EnumFacing.EAST, this.checkObstructions);

				boolean foundDiagonal = false;

				for(int k = 0; k < pathsPYPX.length; k++) {
					if(isSuitablePoint(pathsPY, pathsPX, pathsPYPX[k], this.checkObstructions, fitsThroughPoles, allowOuterCorners)) {
						pathOptions[openedNodeCount++] = pathsPYPX[k];
						foundDiagonal = true;
					}
				}

				if(!foundDiagonal && (this.entitySizeX != 1 || this.entitySizeY != 1)) {
					pathsPYPX = this.getSafePoints(currentPoint.x + 1, currentPoint.y + this.entitySizeY, currentPoint.z, stepHeight, height, EnumFacing.EAST, this.checkObstructions);

					for(int k = 0; k < pathsPYPX.length; k++) {
						if(isSuitablePoint(pathsPY, pathsPX, pathsPYPX[k], this.checkObstructions, fitsThroughPoles, allowOuterCorners)) {
							pathOptions[openedNodeCount++] = pathsPYPX[k];
						}
					}
				}
			}

			if(allowDiagonalPY && allowDiagonalNZ && this.isPassableWithExemptions(this.blockaccess, currentPoint.x, currentPoint.y, currentPoint.z, EnumSet.of(EnumFacing.DOWN, EnumFacing.SOUTH), null, null)) {
				PathPoint[] pathsPYNZ = this.getSafePoints(currentPoint.x, currentPoint.y + 1, currentPoint.z - 1, stepHeight, height, EnumFacing.NORTH, this.checkObstructions);

				for(int k = 0; k < pathsPYNZ.length; k++) {
					if(isSuitablePoint(pathsPY, pathsNZ, pathsPYNZ[k], this.checkObstructions, fitsThroughPoles, allowOuterCorners)) {
						pathOptions[openedNodeCount++] = pathsPYNZ[k];
					}
				}
			}

			if(allowDiagonalPY && allowDiagonalPZ && this.isPassableWithExemptions(this.blockaccess, currentPoint.x, currentPoint.y, currentPoint.z, EnumSet.of(EnumFacing.DOWN, EnumFacing.NORTH), null, null)) {
				PathPoint[] pathsPYPZ = this.getSafePoints(currentPoint.x, currentPoint.y + this.entitySizeY, currentPoint.z + 1, stepHeight, height, EnumFacing.SOUTH, this.checkObstructions);

				boolean foundDiagonal = false;

				for(int k = 0; k < pathsPYPZ.length; k++) {
					if(isSuitablePoint(pathsPY, pathsPZ, pathsPYPZ[k], this.checkObstructions, fitsThroughPoles, allowOuterCorners)) {
						pathOptions[openedNodeCount++] = pathsPYPZ[k];
						foundDiagonal = true;
					}
				}

				if(!foundDiagonal && (this.entitySizeY != 1 || this.entitySizeZ != 1)) {
					pathsPYPZ = this.getSafePoints(currentPoint.x, currentPoint.y + 1, currentPoint.z + this.entitySizeZ, stepHeight, height, EnumFacing.SOUTH, this.checkObstructions);

					for(int k = 0; k < pathsPYPZ.length; k++) {
						if(isSuitablePoint(pathsPY, pathsPZ, pathsPYPZ[k], this.checkObstructions, fitsThroughPoles, allowOuterCorners)) {
							pathOptions[openedNodeCount++] = pathsPYPZ[k];
						}
					}
				}
			}
		}

		return openedNodeCount;
	}

	private static boolean isSuitablePoint(@Nullable PathPoint newPoint, PathPoint currentPoint, boolean allowObstructions) {
		return newPoint != null && !newPoint.visited && (allowObstructions || newPoint.costMalus >= 0.0F || currentPoint.costMalus < 0.0F);
	}

	private static boolean isSuitablePoint(@Nullable PathPoint[] newPoints1, @Nullable PathPoint[] newPoints2, @Nullable PathPoint newPointDiagonal, boolean allowObstructions, boolean fitsThroughPoles, boolean allowOuterCorners) {
		if(!allowOuterCorners) {
			if(newPointDiagonal != null && !newPointDiagonal.visited && newPoints2 != null && newPoints2.length > 0 && (newPoints2[0] != null || (newPoints2.length > 1 && newPoints2[1] != null)) && newPoints1 != null && newPoints1.length > 0 && (newPoints1[0] != null || (newPoints1.length > 1 && newPoints1[1] != null))) {
				if((newPoints1[0] == null || newPoints1[0].nodeType != PathNodeType.DOOR_OPEN) && (newPoints2[0] == null || newPoints2[0].nodeType != PathNodeType.DOOR_OPEN) && newPointDiagonal.nodeType != PathNodeType.DOOR_OPEN) {
					boolean canPassPoleDiagonally = newPoints2[0] != null && newPoints2[0].nodeType == PathNodeType.FENCE && newPoints1[0] != null && newPoints1[0].nodeType == PathNodeType.FENCE && fitsThroughPoles;
					return (allowObstructions || newPointDiagonal.costMalus >= 0.0F) &&
							(canPassPoleDiagonally || (
									((newPoints2[0] != null && (allowObstructions || newPoints2[0].costMalus >= 0.0F)) || (newPoints2.length > 1 && newPoints2[1] != null && (allowObstructions || newPoints2[1].costMalus >= 0.0F))) &&
									((newPoints1[0] != null && (allowObstructions || newPoints1[0].costMalus >= 0.0F)) || (newPoints1.length > 1 && newPoints1[1] != null && (allowObstructions || newPoints1[1].costMalus >= 0.0F)))
									));
				} else {
					return false;
				}
			} else {
				return false;
			}
		} else {
			if(newPointDiagonal != null && !newPointDiagonal.visited) {
				return (allowObstructions || newPointDiagonal.costMalus >= 0.0F) && (
						((newPoints2 == null || (newPoints2.length > 0 && (newPoints2[0] == null || (allowObstructions || newPoints2[0].costMalus >= 0.0F || newPoints2[0].nodeType == PathNodeType.OPEN)))) || (newPoints2 == null || (newPoints2.length > 1 && (newPoints2[1] == null || (allowObstructions || newPoints2[1].costMalus >= 0.0F || newPoints2[1].nodeType == PathNodeType.OPEN))))) &&
						((newPoints1 == null || (newPoints1.length > 0 && (newPoints1[0] == null || (allowObstructions || newPoints1[0].costMalus >= 0.0F || newPoints1[0].nodeType == PathNodeType.OPEN)))) || (newPoints1 == null || (newPoints1.length > 1 && (newPoints1[1] == null || (allowObstructions || newPoints1[1].costMalus >= 0.0F || newPoints1[1].nodeType == PathNodeType.OPEN)))))
						);
			} else {
				return false;
			}
		}
	}

	@Nullable
	private PathPoint[] getSafePoints(int x, int y, int z, int stepHeight, double height, EnumFacing facing, boolean allowBlocked) {
		PathPoint directPathPoint = null;

		BlockPos pos = new BlockPos(x, y, z);

        BlockPos posDown = pos.down();
        double blockHeight = y - (1.0D - this.blockaccess.getBlockState(posDown).getBoundingBox(this.blockaccess, posDown).maxY);

		if (blockHeight - height > 1.125D) {
			return new PathPoint[0];
		} else {
			PathNodeType nodeType = this.getPathNodeTypeCached(this.entity, x, y, z);

			float malus = this.obstructionAwareEntity.getPathingMalus(this.obstructionAwareEntity, nodeType, pos); //Replaces EntityLiving#getPathPriority

			double halfWidth = (double)this.entity.width / 2.0D;

			PathPoint[] result = new PathPoint[1];

			if(malus >= 0.0F && (allowBlocked || nodeType != PathNodeType.BLOCKED)) {
				directPathPoint = this.openPoint(x, y, z);
				directPathPoint.nodeType = nodeType;
				directPathPoint.costMalus = Math.max(directPathPoint.costMalus, malus);

				//Allow other nodes than this obstructed node to also be considered, otherwise jumping/pathing up steps does no longer work
				if(directPathPoint.nodeType == PathNodeType.BLOCKED) {
					result = new PathPoint[2];
					result[1] = directPathPoint;
					directPathPoint = null;
				}
			}

			if(nodeType == PathNodeType.WALKABLE) {
				result[0] = directPathPoint;
				return result;
			} else {
				if (directPathPoint == null && stepHeight > 0 && nodeType != PathNodeType.FENCE && nodeType != PathNodeType.TRAPDOOR && facing.getAxis() != EnumFacing.Axis.Y) {
					PathPoint[] pointsAbove = this.getSafePoints(x, y + 1, z, stepHeight - 1, height, facing, false);
					directPathPoint = pointsAbove.length > 0 ? pointsAbove[0] : null;

					if(directPathPoint != null && (directPathPoint.nodeType == PathNodeType.OPEN || directPathPoint.nodeType == PathNodeType.WALKABLE) && this.entity.width < 1.0F) {
						double jumpX = (double)(x - facing.getFrontOffsetX()) + 0.5D;
                        double jumpZ = (double)(z - facing.getFrontOffsetZ()) + 0.5D;
                        AxisAlignedBB jumpBox = new AxisAlignedBB(jumpX - halfWidth, (double)y + 0.001D, jumpZ - halfWidth, jumpX + halfWidth, (double)((float)y + this.entity.height), jumpZ + halfWidth);
                        AxisAlignedBB blockBox = this.blockaccess.getBlockState(pos).getBoundingBox(this.blockaccess, pos);
                        AxisAlignedBB checkBox = jumpBox.expand(0.0D, blockBox.maxY - 0.002D, 0.0D);

                        if(this.checkAabbCollision(checkBox)) {
                        	directPathPoint = null;
                        }
					}
				}

				if(nodeType == PathNodeType.OPEN) {
					AxisAlignedBB checkAabb = new AxisAlignedBB((double)x - halfWidth + 0.5D, (double)y + 0.001D, (double)z - halfWidth + 0.5D, (double)x + halfWidth + 0.5D, (double)((float)y + this.entity.height), (double)z + halfWidth + 0.5D);

					if(this.checkAabbCollision(checkAabb)) {
						result[0] = null;
						return result;
					}

					if(this.entity.width >= 1.0F) {
						for(EnumFacing pathableFacing : this.pathableFacings) {
							PathNodeType nodeTypeAtFacing = this.getPathNodeTypeCached(this.entity, x + pathableFacing.getFrontOffsetX() * this.pathingSizeOffsetX, y + (pathableFacing == EnumFacing.DOWN ? -1 : pathableFacing == EnumFacing.UP ? this.pathingSizeOffsetY : 0), z + pathableFacing.getFrontOffsetZ() * this.pathingSizeOffsetZ);

							if(nodeTypeAtFacing == PathNodeType.BLOCKED) {
								directPathPoint = this.openPoint(x, y, z);
								directPathPoint.nodeType = PathNodeType.WALKABLE;
								directPathPoint.costMalus = Math.max(directPathPoint.costMalus, malus);
								result[0] = directPathPoint;
								return result;
							}
						}
					}


					boolean cancelFallDown = false;
					PathPoint fallPathPoint = null;

					int fallDistance = 0;
					int preFallY = y;

					while(y > 0 && nodeType == PathNodeType.OPEN) {
						--y;

						if(fallDistance++ >= this.entity.getMaxFallHeight() || y == 0) {
							cancelFallDown = true;
							break;
						}

						nodeType = this.getPathNodeTypeCached(this.entity, x, y, z);
						malus = this.entity.getPathPriority(nodeType);

						if(nodeType != PathNodeType.OPEN && malus >= 0.0F) {
							fallPathPoint = this.openPoint(x, y, z);
							fallPathPoint.nodeType = nodeType;
							fallPathPoint.costMalus = Math.max(fallPathPoint.costMalus, malus);
							break;
						}

						if(malus < 0.0F) {
							cancelFallDown = true;
						}
					}

					boolean hasPathUp = false;

					if(this.pathableFacings.size() > 1) {
						nodeType = this.getPathNodeTypeCached(this.entity, x, preFallY, z);
						malus = this.entity.getPathPriority(nodeType);

						if(nodeType != PathNodeType.OPEN && malus >= 0.0F) {
							if(fallPathPoint != null) {
								result = new PathPoint[2];
								result[1] = fallPathPoint;
							}

							result[0] = directPathPoint = this.openPoint(x, preFallY, z);
							directPathPoint.nodeType = nodeType;
							directPathPoint.costMalus = Math.max(directPathPoint.costMalus, malus);
							hasPathUp = true;
						}
					}

					if(fallPathPoint != null) {
						if(!hasPathUp) {
							result[0] = directPathPoint = fallPathPoint;
						} else {
							result = new PathPoint[2];
							result[0] = directPathPoint;
							result[1] = fallPathPoint;
						}
					}

					if(fallPathPoint != null) {
						float bridingMalus = this.obstructionAwareEntity.getBridgePathingMalus(this.obstructionAwareEntity, new BlockPos(x, preFallY, z), fallPathPoint);

						if(bridingMalus >= 0.0f) {
							result = new PathPoint[2];
							result[0] = directPathPoint;

							PathPoint bridgePathPoint = this.openPoint(x, preFallY, z);
							bridgePathPoint.nodeType = PathNodeType.WALKABLE;
							bridgePathPoint.costMalus = Math.max(bridgePathPoint.costMalus, bridingMalus);
							result[1] = bridgePathPoint;
						}
					}

					if(cancelFallDown && !hasPathUp) {
						result[0] = null;
						if(result.length == 2) {
							result[1] = null;
						}
						return result;
					}
				}

				result[0] = directPathPoint;
				return result;
			}
		}
	}

	private PathNodeType getPathNodeTypeCached(EntityLiving entitylivingIn, BlockPos pos) {
		return this.getPathNodeType(this.blockaccess, pos.getX(), pos.getY(), pos.getZ());
	}

	private PathNodeType getPathNodeTypeCached(EntityLiving entitylivingIn, int x, int y, int z) {
		return this.pathNodeTypeCache.computeIfAbsent(new BlockPos(x, y, z).toLong(), (key) -> {
			return this.getPathNodeType(this.blockaccess, x, y, z, entitylivingIn, this.entitySizeX, this.entitySizeY, this.entitySizeZ, this.getCanOpenDoors(), this.getCanEnterDoors());
		});
	}

	@Override
	public PathNodeType getPathNodeType(IBlockAccess blockaccessIn, int x, int y, int z, EntityLiving entity, int xSize, int ySize, int zSize, boolean canBreakDoorsIn, boolean canEnterDoorsIn) {
		BlockPos pos = new BlockPos(entity.getPositionVector());

		EnumSet<PathNodeType> applicablePathNodeTypes = EnumSet.noneOf(PathNodeType.class);
		PathNodeType centerPathNodeType = this.getPathNodeType(blockaccessIn, x, y, z, xSize, ySize, zSize, canBreakDoorsIn, canEnterDoorsIn, applicablePathNodeTypes, PathNodeType.BLOCKED, pos);

		if(applicablePathNodeTypes.contains(PathNodeType.FENCE)) {
			return PathNodeType.FENCE;
		} else {
			PathNodeType selectedPathNodeType = PathNodeType.BLOCKED;

			for(PathNodeType applicablePathNodeType : applicablePathNodeTypes) {
				if(entity.getPathPriority(applicablePathNodeType) < 0.0F) {
					return applicablePathNodeType;
				}

				float p1 = entity.getPathPriority(applicablePathNodeType);
				float p2 = entity.getPathPriority(selectedPathNodeType);
				if(p1 > p2 || (p1 == p2 && !(selectedPathNodeType == PathNodeType.WALKABLE && applicablePathNodeType == PathNodeType.OPEN)) || (p1 == p2 && selectedPathNodeType == PathNodeType.OPEN && applicablePathNodeType == PathNodeType.WALKABLE)) {
					selectedPathNodeType = applicablePathNodeType;
				}
			}

			if(centerPathNodeType == PathNodeType.OPEN && entity.getPathPriority(selectedPathNodeType) == 0.0F) {
				return PathNodeType.OPEN;
			} else {
				return selectedPathNodeType;
			}
		}
	}

	@Override
	public PathNodeType getPathNodeType(IBlockAccess blockaccessIn, int x, int y, int z) {
		return getPathNodeTypeWithConditions(blockaccessIn, x, y, z, this.pathingSizeOffsetX, this.pathingSizeOffsetY, this.pathingSizeOffsetZ, this.pathableFacings, EnumSet.noneOf(EnumFacing.class), null);
	}

	protected PathNodeType getPathNodeTypeWithConditions(IBlockAccess blockaccessIn, int x, int y, int z, int pathingSizeOffsetX, int pathingSizeOffsetY, int pathingSizeOffsetZ, EnumSet<EnumFacing> pathableFacings, @Nullable EnumSet<EnumFacing> exemptions, @Nullable EnumSet<EnumFacing> found) {
		MutableBlockPos pos = new MutableBlockPos();

		PathNodeType nodeType = this.getPathNodeTypeRaw(blockaccessIn, x, y, z);

		if(nodeType == PathNodeType.OPEN && y >= 1) {
			facings: for(EnumFacing pathableFacing : pathableFacings) {
				if(exemptions == null || !exemptions.contains(pathableFacing)) {
					int checkHeight = pathableFacing.getAxis() != Axis.Y ? Math.min(4, pathingSizeOffsetY - 1) : 0;

					int cx = x + pathableFacing.getFrontOffsetX() * pathingSizeOffsetX;
					int cy = y + (pathableFacing == EnumFacing.DOWN ? -1 : pathableFacing == EnumFacing.UP ? pathingSizeOffsetY : 0);
					int cz = z + pathableFacing.getFrontOffsetZ() * pathingSizeOffsetZ;

					for(int yo = 0; yo <= checkHeight; yo++) {
						pos.setPos(cx, cy + yo, cz);

						PathNodeType offsetNodeType = this.getPathNodeTypeRaw(blockaccessIn, pos.getX(), pos.getY(), pos.getZ());
						nodeType = offsetNodeType != PathNodeType.WALKABLE && offsetNodeType != PathNodeType.OPEN && offsetNodeType != PathNodeType.WATER && offsetNodeType != PathNodeType.LAVA ? PathNodeType.WALKABLE : PathNodeType.OPEN;

						if(offsetNodeType == PathNodeType.DAMAGE_FIRE) {
							nodeType = PathNodeType.DAMAGE_FIRE;
						}

						if(offsetNodeType == PathNodeType.DAMAGE_CACTUS) {
							nodeType = PathNodeType.DAMAGE_CACTUS;
						}

						if(offsetNodeType == PathNodeType.DAMAGE_OTHER) {
							nodeType = PathNodeType.DAMAGE_OTHER;
						}

						if(nodeType == PathNodeType.WALKABLE) {
							if(found != null) {
								found.add(pathableFacing);
							}
							break facings;
						}
					}
				}
			}
		}

		if(nodeType == PathNodeType.WALKABLE) {
			nodeType = this.checkNeighborBlocks(blockaccessIn, x, y, z, nodeType);
		}

		return nodeType;
	}
}
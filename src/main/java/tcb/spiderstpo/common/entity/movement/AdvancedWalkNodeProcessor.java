package tcb.spiderstpo.common.entity.movement;

import java.util.EnumSet;

import javax.annotation.Nullable;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.MobEntity;
import net.minecraft.fluid.Fluids;
import net.minecraft.pathfinding.PathNodeType;
import net.minecraft.pathfinding.PathPoint;
import net.minecraft.pathfinding.PathType;
import net.minecraft.pathfinding.WalkNodeProcessor;
import net.minecraft.util.Direction;
import net.minecraft.util.Direction.Axis;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3i;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.Region;

public class AdvancedWalkNodeProcessor extends WalkNodeProcessor {
	protected static final Vector3i PX = new Vector3i(1, 0, 0);
	protected static final Vector3i NX = new Vector3i(-1, 0, 0);
	protected static final Vector3i PY = new Vector3i(0, 1, 0);
	protected static final Vector3i NY = new Vector3i(0, -1, 0);
	protected static final Vector3i PZ = new Vector3i(0, 0, 1);
	protected static final Vector3i NZ = new Vector3i(0, 0, -1);

	protected static final Vector3i PXPY = new Vector3i(1, 1, 0);
	protected static final Vector3i NXPY = new Vector3i(-1, 1, 0);
	protected static final Vector3i PXNY = new Vector3i(1, -1, 0);
	protected static final Vector3i NXNY = new Vector3i(-1, -1, 0);

	protected static final Vector3i PXPZ = new Vector3i(1, 0, 1);
	protected static final Vector3i NXPZ = new Vector3i(-1, 0, 1);
	protected static final Vector3i PXNZ = new Vector3i(1, 0, -1);
	protected static final Vector3i NXNZ = new Vector3i(-1, 0, -1);

	protected static final Vector3i PYPZ = new Vector3i(0, 1, 1);
	protected static final Vector3i NYPZ = new Vector3i(0, -1, 1);
	protected static final Vector3i PYNZ = new Vector3i(0, 1, -1);
	protected static final Vector3i NYNZ = new Vector3i(0, -1, -1);

	protected IAdvancedPathFindingEntity advancedPathFindingEntity;
	protected boolean startFromGround = true;
	protected boolean checkObstructions;
	protected int pathingSizeOffsetX, pathingSizeOffsetY, pathingSizeOffsetZ;
	protected EnumSet<Direction> pathableFacings = EnumSet.of(Direction.DOWN);

	private final Long2ObjectMap<PathNodeType> pathNodeTypeCache = new Long2ObjectOpenHashMap<>();
	private final Object2BooleanMap<AxisAlignedBB> aabbCollisionCache = new Object2BooleanOpenHashMap<>();

	protected boolean alwaysAllowDiagonals = true;

	public void setStartPathOnGround(boolean startFromGround) {
		this.startFromGround = startFromGround;
	}

	public void setCheckObstructions(boolean checkObstructions) {
		this.checkObstructions = checkObstructions;
	}

	public void setCanPathWalls(boolean canPathWalls) {
		if(canPathWalls) {
			this.pathableFacings.add(Direction.NORTH);
			this.pathableFacings.add(Direction.EAST);
			this.pathableFacings.add(Direction.SOUTH);
			this.pathableFacings.add(Direction.WEST);
		} else {
			this.pathableFacings.remove(Direction.NORTH);
			this.pathableFacings.remove(Direction.EAST);
			this.pathableFacings.remove(Direction.SOUTH);
			this.pathableFacings.remove(Direction.WEST);
		}
	}

	public void setCanPathCeiling(boolean canPathCeiling) {
		if(canPathCeiling) {
			this.pathableFacings.add(Direction.UP);
		} else {
			this.pathableFacings.remove(Direction.UP);
		}
	}

	@Override
	public void func_225578_a_(Region sourceIn, MobEntity mob) {
		super.func_225578_a_(sourceIn, mob);

		if(mob instanceof IAdvancedPathFindingEntity) {
			this.advancedPathFindingEntity = (IAdvancedPathFindingEntity) mob;
		} else {
			throw new IllegalArgumentException("Only mobs that extend " + IAdvancedPathFindingEntity.class.getSimpleName() + " are supported. Received: " + mob.getClass().getName());
		}

		this.pathingSizeOffsetX = Math.max(1, MathHelper.floor(this.entity.getWidth() / 2.0f + 1));
		this.pathingSizeOffsetY = Math.max(1, MathHelper.floor(this.entity.getHeight() + 1));
		this.pathingSizeOffsetZ = Math.max(1, MathHelper.floor(this.entity.getWidth() / 2.0f + 1));
	}

	@Override
	public void postProcess() {
		super.postProcess();
		this.pathNodeTypeCache.clear();
		this.aabbCollisionCache.clear();
		this.advancedPathFindingEntity.pathFinderCleanup();
	}

	private boolean checkAabbCollision(AxisAlignedBB aabb) {
		return this.aabbCollisionCache.computeIfAbsent(aabb, (p_237237_2_) -> {
			return !this.blockaccess.hasNoCollisions(this.entity, aabb);
		});
	}

	@Override
	public PathPoint getStart() {
		double x = this.entity.getPosX();
		double y = this.entity.getPosY();
		double z = this.entity.getPosZ();

		BlockPos.Mutable checkPos = new BlockPos.Mutable();

		int by = MathHelper.floor(y);

		BlockState state = this.blockaccess.getBlockState(checkPos.setPos(x, by, z));

		if(!this.entity.func_230285_a_(state.getFluidState().getFluid())) {
			if(this.getCanSwim() && this.entity.isInWater()) {
				while(true) {
					if(state.getBlock() != Blocks.WATER && state.getFluidState() != Fluids.WATER.getStillFluidState(false)) {
						--by;
						break;
					}

					++by;
					state = this.blockaccess.getBlockState(checkPos.setPos(x, by, z));
				}
			} else if(this.entity.func_233570_aj_() || !this.startFromGround) {
				by = MathHelper.floor(y + 0.5D);
			} else {
				BlockPos blockpos;
				for(blockpos = this.entity.func_233580_cy_(); (this.blockaccess.getBlockState(blockpos).isAir() || this.blockaccess.getBlockState(blockpos).allowsMovement(this.blockaccess, blockpos, PathType.LAND)) && blockpos.getY() > 0; blockpos = blockpos.down()) { }

				by = blockpos.up().getY();
			}
		} else {
			while(this.entity.func_230285_a_(state.getFluidState().getFluid())) {
				++by;
				state = this.blockaccess.getBlockState(checkPos.setPos(x, by, z));
			}

			--by;
		}

		BlockPos startPos = new BlockPos(x, y, z);

		PathNodeType startNodeType = this.getPathNodeTypeCached(this.entity, startPos.getX(), by, startPos.getZ());
		if(this.entity.getPathPriority(startNodeType) < 0.0F) {
			AxisAlignedBB aabb = this.entity.getBoundingBox();

			if(this.isSafeStartingPosition(checkPos.setPos(aabb.minX, by, aabb.minZ)) || this.isSafeStartingPosition(checkPos.setPos(aabb.minX, by, aabb.maxZ)) || this.isSafeStartingPosition(checkPos.setPos(aabb.maxX, by, aabb.minZ)) || this.isSafeStartingPosition(checkPos.setPos(aabb.maxX, by, aabb.maxZ))) {
				PathPoint startPathPoint = this.func_237223_a_(checkPos);
				startPathPoint.nodeType = this.getPathNodeTypeCached(this.entity, startPathPoint.func_224759_a());
				startPathPoint.costMalus = this.entity.getPathPriority(startPathPoint.nodeType);
				return startPathPoint;
			}
		}

		PathPoint startPathPoint = this.openPoint(startPos.getX(), by, startPos.getZ());
		startPathPoint.nodeType = this.getPathNodeTypeCached(this.entity, startPathPoint.func_224759_a());
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

	private boolean isPassableWithExemptions(IBlockReader blockAccess, int x, int y, int z, @Nullable EnumSet<Direction> exemptions, @Nullable EnumSet<Direction> requirement, @Nullable EnumSet<Direction> found) {
		if(requirement != null && found == null) {
			found = EnumSet.noneOf(Direction.class);
		}

		for(int xo = 0; xo < this.entitySizeX; xo++) {
			for(int yo = 0; yo < this.entitySizeY; yo++) {
				for(int zo = 0; zo < this.entitySizeZ; zo++) {
					PathNodeType nodeType = getPathNodeTypeWithConditions(blockAccess, x + xo, y + yo, z + zo, this.pathingSizeOffsetX, this.pathingSizeOffsetY, this.pathingSizeOffsetZ, this.pathableFacings, exemptions, found);

					if(nodeType != PathNodeType.OPEN && this.entity.getPathPriority(nodeType) >= 0.0f) {
						if(requirement != null) {
							for(Direction facing : requirement) {
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
	public int func_222859_a(PathPoint[] pathOptions, PathPoint currentPoint) {
		int openedNodeCount = 0;
		int stepHeight = 0;

		PathNodeType nodeTypeAbove = this.getPathNodeTypeCached(this.entity, currentPoint.x, currentPoint.y + 1, currentPoint.z);

		if(this.entity.getPathPriority(nodeTypeAbove) >= 0.0F) {
			stepHeight = MathHelper.floor(Math.max(1.0F, this.entity.stepHeight));
		}

		double height = currentPoint.y - getGroundY(this.blockaccess, new BlockPos(currentPoint.x, currentPoint.y, currentPoint.z));

		PathPoint[] pathsPZ = this.getSafePoints(currentPoint.x, currentPoint.y, currentPoint.z + 1, stepHeight, height, PZ, this.checkObstructions);
		PathPoint[] pathsNX = this.getSafePoints(currentPoint.x - 1, currentPoint.y, currentPoint.z, stepHeight, height, NX, this.checkObstructions);
		PathPoint[] pathsPX = this.getSafePoints(currentPoint.x + 1, currentPoint.y, currentPoint.z, stepHeight, height, PX, this.checkObstructions);
		PathPoint[] pathsNZ = this.getSafePoints(currentPoint.x, currentPoint.y, currentPoint.z - 1, stepHeight, height, NZ, this.checkObstructions);

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
				EnumSet<Direction> found = EnumSet.noneOf(Direction.class);
				this.isPassableWithExemptions(this.blockaccess, currentPoint.x, currentPoint.y - 1, currentPoint.z, EnumSet.of(Direction.UP, Direction.DOWN), null, found);
				hasValidPath = this.isPassableWithExemptions(this.blockaccess, currentPoint.x, currentPoint.y, currentPoint.z, EnumSet.of(Direction.UP, Direction.DOWN), found, null);
			}

			if(hasValidPath) {
				pathsNY = this.getSafePoints(currentPoint.x, currentPoint.y - 1, currentPoint.z, stepHeight, height, NY, this.checkObstructions);
				for(int k = 0; k < pathsNY.length; k++) {
					if(isSuitablePoint(pathsNY[k], currentPoint, this.checkObstructions)) {
						pathOptions[openedNodeCount++] = pathsNY[k];
					}
				}
			}
		}

		PathPoint[] pathsPY = null;
		if(this.pathableFacings.size() > 1) {
			EnumSet<Direction> found = EnumSet.noneOf(Direction.class);
			this.isPassableWithExemptions(this.blockaccess, currentPoint.x, currentPoint.y + 1, currentPoint.z, EnumSet.of(Direction.UP, Direction.DOWN), null, found);

			if(this.isPassableWithExemptions(this.blockaccess, currentPoint.x, currentPoint.y, currentPoint.z, EnumSet.of(Direction.UP, Direction.DOWN), found, null)) {
				pathsPY = this.getSafePoints(currentPoint.x, currentPoint.y + 1, currentPoint.z, stepHeight, height, PY, this.checkObstructions);
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

		boolean fitsThroughPoles = this.entity.getWidth() < 0.5f;

		boolean allowOuterCorners = this.pathableFacings.size() >= 3;

		if(allowDiagonalNZ && allowDiagonalNX) {
			PathPoint[] pathsNXNZ = this.getSafePoints(currentPoint.x - this.entitySizeX, currentPoint.y, currentPoint.z - 1, stepHeight, height, NXNZ, this.checkObstructions);

			boolean foundDiagonal = false;

			for(int k = 0; k < pathsNXNZ.length; k++) {
				if(isSuitablePoint(pathsNX, currentPoint.x - 1, currentPoint.y, currentPoint.z, pathsNZ, currentPoint.x, currentPoint.y, currentPoint.z - 1, pathsNXNZ[k], this.checkObstructions, fitsThroughPoles, allowOuterCorners)) {
					pathOptions[openedNodeCount++] = pathsNXNZ[k];
					foundDiagonal = true;
				}
			}

			if(!foundDiagonal && (this.entitySizeX != 1 || this.entitySizeZ != 1)) {
				pathsNXNZ = this.getSafePoints(currentPoint.x - 1, currentPoint.y, currentPoint.z - this.entitySizeZ, stepHeight, height, NXNZ, this.checkObstructions);

				for(int k = 0; k < pathsNXNZ.length; k++) {
					if(isSuitablePoint(pathsNX, currentPoint.x - 1, currentPoint.y, currentPoint.z, pathsNZ, currentPoint.x, currentPoint.y, currentPoint.z - 1, pathsNXNZ[k], this.checkObstructions, fitsThroughPoles, allowOuterCorners)) {
						pathOptions[openedNodeCount++] = pathsNXNZ[k];
					}
				}
			}
		}

		if(allowDiagonalNZ && allowDiagonalPX) {
			PathPoint[] pathsPXNZ = this.getSafePoints(currentPoint.x + 1, currentPoint.y, currentPoint.z - 1, stepHeight, height, PXNZ, this.checkObstructions);

			for(int k = 0; k < pathsPXNZ.length; k++) {
				if(isSuitablePoint(pathsPX, currentPoint.x + 1, currentPoint.y, currentPoint.z, pathsNZ, currentPoint.x, currentPoint.y, currentPoint.z - 1, pathsPXNZ[k], this.checkObstructions, fitsThroughPoles, allowOuterCorners)) {
					pathOptions[openedNodeCount++] = pathsPXNZ[k];
				}
			}
		}

		if(allowDiagonalPZ && allowDiagonalNX) {
			PathPoint[] pathsNXPZ = this.getSafePoints(currentPoint.x - 1, currentPoint.y, currentPoint.z + 1, stepHeight, height, NXPZ, this.checkObstructions);

			for(int k = 0; k < pathsNXPZ.length; k++) {
				if(isSuitablePoint(pathsNX, currentPoint.x - 1, currentPoint.y, currentPoint.z, pathsPZ, currentPoint.x, currentPoint.y, currentPoint.z + 1, pathsNXPZ[k], this.checkObstructions, fitsThroughPoles, allowOuterCorners)) {
					pathOptions[openedNodeCount++] = pathsNXPZ[k];
				}
			}
		}

		if(allowDiagonalPZ && allowDiagonalPX) {
			PathPoint[] pathsPXPZ = this.getSafePoints(currentPoint.x + this.entitySizeX, currentPoint.y, currentPoint.z + 1, stepHeight, height, PXPZ, this.checkObstructions);

			boolean foundDiagonal = false;

			for(int k = 0; k < pathsPXPZ.length; k++) {
				if(isSuitablePoint(pathsPX, currentPoint.x + 1, currentPoint.y, currentPoint.z, pathsPZ, currentPoint.x, currentPoint.y, currentPoint.z - 1, pathsPXPZ[k], this.checkObstructions, fitsThroughPoles, allowOuterCorners)) {
					pathOptions[openedNodeCount++] = pathsPXPZ[k];
					foundDiagonal = true;
				}
			}

			if(!foundDiagonal && (this.entitySizeX != 1 || this.entitySizeZ != 1)) {
				pathsPXPZ = this.getSafePoints(currentPoint.x + 1, currentPoint.y, currentPoint.z + this.entitySizeZ, stepHeight, height, PXPZ, this.checkObstructions);

				for(int k = 0; k < pathsPXPZ.length; k++) {
					if(isSuitablePoint(pathsPX, currentPoint.x + 1, currentPoint.y, currentPoint.z, pathsPZ, currentPoint.x, currentPoint.y, currentPoint.z + 1, pathsPXPZ[k], this.checkObstructions, fitsThroughPoles, allowOuterCorners)) {
						pathOptions[openedNodeCount++] = pathsPXPZ[k];
					}
				}
			}
		}

		if(this.pathableFacings.size() > 1) {
			boolean allowDiagonalPY = this.allowDiagonalPathOptions(pathsPY);
			boolean allowDiagonalNY = this.allowDiagonalPathOptions(pathsNY);

			if(allowDiagonalNY && allowDiagonalNX && this.isPassableWithExemptions(this.blockaccess, currentPoint.x, currentPoint.y, currentPoint.z, EnumSet.of(Direction.UP, Direction.EAST), null, null)) {
				PathPoint[] pathsNYNX = this.getSafePoints(currentPoint.x - this.entitySizeX, currentPoint.y - 1, currentPoint.z, stepHeight, height, NXNY, this.checkObstructions);

				boolean foundDiagonal = false;

				for(int k = 0; k < pathsNYNX.length; k++) {
					if(isSuitablePoint(pathsNY, currentPoint.x, currentPoint.y - 1, currentPoint.z, pathsNX, currentPoint.x - 1, currentPoint.y, currentPoint.z, pathsNYNX[k], this.checkObstructions, fitsThroughPoles, allowOuterCorners)) {
						pathOptions[openedNodeCount++] = pathsNYNX[k];
						foundDiagonal = true;
					}
				}

				if(!foundDiagonal && (this.entitySizeX != 1 || this.entitySizeY != 1)) {
					pathsNYNX = this.getSafePoints(currentPoint.x - 1, currentPoint.y - this.entitySizeY, currentPoint.z, stepHeight, height, NXNY, this.checkObstructions);

					for(int k = 0; k < pathsNYNX.length; k++) {
						if(isSuitablePoint(pathsNY, currentPoint.x, currentPoint.y - 1, currentPoint.z, pathsNX, currentPoint.x - 1, currentPoint.y, currentPoint.z, pathsNYNX[k], this.checkObstructions, fitsThroughPoles, allowOuterCorners)) {
							pathOptions[openedNodeCount++] = pathsNYNX[k];
						}
					}
				}
			}

			if(allowDiagonalNY && allowDiagonalPX && this.isPassableWithExemptions(this.blockaccess, currentPoint.x, currentPoint.y, currentPoint.z, EnumSet.of(Direction.UP, Direction.WEST), null, null)) {
				PathPoint[] pathsNYPX = this.getSafePoints(currentPoint.x + 1, currentPoint.y - 1, currentPoint.z, stepHeight, height, PXNY, this.checkObstructions);

				for(int k = 0; k < pathsNYPX.length; k++) {
					if(isSuitablePoint(pathsNY, currentPoint.x, currentPoint.y - 1, currentPoint.z, pathsPX, currentPoint.x + 1, currentPoint.y, currentPoint.z, pathsNYPX[k], this.checkObstructions, fitsThroughPoles, allowOuterCorners)) {
						pathOptions[openedNodeCount++] = pathsNYPX[k];
					}
				}
			}

			if(allowDiagonalNY && allowDiagonalNZ && this.isPassableWithExemptions(this.blockaccess, currentPoint.x, currentPoint.y, currentPoint.z, EnumSet.of(Direction.UP, Direction.SOUTH), null, null)) {
				PathPoint[] pathsNYNZ = this.getSafePoints(currentPoint.x, currentPoint.y - this.entitySizeY, currentPoint.z - 1, stepHeight, height, NYNZ, this.checkObstructions);

				boolean foundDiagonal = false;

				for(int k = 0; k < pathsNYNZ.length; k++) {
					if(isSuitablePoint(pathsNY, currentPoint.x, currentPoint.y - 1, currentPoint.z, pathsNZ, currentPoint.x, currentPoint.y, currentPoint.z - 1, pathsNYNZ[k], this.checkObstructions, fitsThroughPoles, allowOuterCorners)) {
						pathOptions[openedNodeCount++] = pathsNYNZ[k];
						foundDiagonal = true;
					}
				}

				if(!foundDiagonal && (this.entitySizeY != 1 || this.entitySizeZ != 1)) {
					pathsNYNZ = this.getSafePoints(currentPoint.x, currentPoint.y - 1, currentPoint.z - this.entitySizeZ, stepHeight, height, NYNZ, this.checkObstructions);

					for(int k = 0; k < pathsNYNZ.length; k++) {
						if(isSuitablePoint(pathsNY, currentPoint.x, currentPoint.y - 1, currentPoint.z, pathsNZ, currentPoint.x, currentPoint.y, currentPoint.z - 1, pathsNYNZ[k], this.checkObstructions, fitsThroughPoles, allowOuterCorners)) {
							pathOptions[openedNodeCount++] = pathsNYNZ[k];
						}
					}
				}
			}

			if(allowDiagonalNY && allowDiagonalPZ && this.isPassableWithExemptions(this.blockaccess, currentPoint.x, currentPoint.y, currentPoint.z, EnumSet.of(Direction.UP, Direction.NORTH), null, null)) {
				PathPoint[] pathsNYPZ = this.getSafePoints(currentPoint.x, currentPoint.y - 1, currentPoint.z + 1, stepHeight, height, NYPZ, this.checkObstructions);

				for(int k = 0; k < pathsNYPZ.length; k++) {
					if(isSuitablePoint(pathsNY, currentPoint.x, currentPoint.y - 1, currentPoint.z, pathsPZ, currentPoint.x, currentPoint.y, currentPoint.z + 1, pathsNYPZ[k], this.checkObstructions, fitsThroughPoles, allowOuterCorners)) {
						pathOptions[openedNodeCount++] = pathsNYPZ[k];
					}
				}
			}

			if(allowDiagonalPY && allowDiagonalNX && this.isPassableWithExemptions(this.blockaccess, currentPoint.x, currentPoint.y, currentPoint.z, EnumSet.of(Direction.DOWN, Direction.EAST), null, null)) {
				PathPoint[] pathsPYNX = this.getSafePoints(currentPoint.x - 1, currentPoint.y + 1, currentPoint.z, stepHeight, height, NXPY, this.checkObstructions);

				for(int k = 0; k < pathsPYNX.length; k++) {
					if(isSuitablePoint(pathsPY, currentPoint.x, currentPoint.y - 1, currentPoint.z, pathsNZ, currentPoint.x, currentPoint.y, currentPoint.z - 1, pathsPYNX[k], this.checkObstructions, fitsThroughPoles, allowOuterCorners)) {
						pathOptions[openedNodeCount++] = pathsPYNX[k];
					}
				}
			}

			if(allowDiagonalPY && allowDiagonalPX && this.isPassableWithExemptions(this.blockaccess, currentPoint.x, currentPoint.y, currentPoint.z, EnumSet.of(Direction.DOWN, Direction.WEST), null, null)) {
				PathPoint[] pathsPYPX = this.getSafePoints(currentPoint.x + this.entitySizeX, currentPoint.y + 1, currentPoint.z, stepHeight, height, PXPY, this.checkObstructions);

				boolean foundDiagonal = false;

				for(int k = 0; k < pathsPYPX.length; k++) {
					if(isSuitablePoint(pathsPY, currentPoint.x, currentPoint.y + 1, currentPoint.z, pathsPX, currentPoint.x + 1, currentPoint.y, currentPoint.z, pathsPYPX[k], this.checkObstructions, fitsThroughPoles, allowOuterCorners)) {
						pathOptions[openedNodeCount++] = pathsPYPX[k];
						foundDiagonal = true;
					}
				}

				if(!foundDiagonal && (this.entitySizeX != 1 || this.entitySizeY != 1)) {
					pathsPYPX = this.getSafePoints(currentPoint.x + 1, currentPoint.y + this.entitySizeY, currentPoint.z, stepHeight, height, PXPY, this.checkObstructions);

					for(int k = 0; k < pathsPYPX.length; k++) {
						if(isSuitablePoint(pathsPY, currentPoint.x, currentPoint.y - 1, currentPoint.z, pathsPX, currentPoint.x + 1, currentPoint.y, currentPoint.z, pathsPYPX[k], this.checkObstructions, fitsThroughPoles, allowOuterCorners)) {
							pathOptions[openedNodeCount++] = pathsPYPX[k];
						}
					}
				}
			}

			if(allowDiagonalPY && allowDiagonalNZ && this.isPassableWithExemptions(this.blockaccess, currentPoint.x, currentPoint.y, currentPoint.z, EnumSet.of(Direction.DOWN, Direction.SOUTH), null, null)) {
				PathPoint[] pathsPYNZ = this.getSafePoints(currentPoint.x, currentPoint.y + 1, currentPoint.z - 1, stepHeight, height, PYNZ, this.checkObstructions);

				for(int k = 0; k < pathsPYNZ.length; k++) {
					if(isSuitablePoint(pathsPY, currentPoint.x, currentPoint.y + 1, currentPoint.z, pathsNZ, currentPoint.x, currentPoint.y, currentPoint.z - 1, pathsPYNZ[k], this.checkObstructions, fitsThroughPoles, allowOuterCorners)) {
						pathOptions[openedNodeCount++] = pathsPYNZ[k];
					}
				}
			}

			if(allowDiagonalPY && allowDiagonalPZ && this.isPassableWithExemptions(this.blockaccess, currentPoint.x, currentPoint.y, currentPoint.z, EnumSet.of(Direction.DOWN, Direction.NORTH), null, null)) {
				PathPoint[] pathsPYPZ = this.getSafePoints(currentPoint.x, currentPoint.y + this.entitySizeY, currentPoint.z + 1, stepHeight, height, PYPZ, this.checkObstructions);

				boolean foundDiagonal = false;

				for(int k = 0; k < pathsPYPZ.length; k++) {
					if(isSuitablePoint(pathsPY, currentPoint.x, currentPoint.y + 1, currentPoint.z, pathsPZ, currentPoint.x, currentPoint.y, currentPoint.z + 1, pathsPYPZ[k], this.checkObstructions, fitsThroughPoles, allowOuterCorners)) {
						pathOptions[openedNodeCount++] = pathsPYPZ[k];
						foundDiagonal = true;
					}
				}

				if(!foundDiagonal && (this.entitySizeY != 1 || this.entitySizeZ != 1)) {
					pathsPYPZ = this.getSafePoints(currentPoint.x, currentPoint.y + 1, currentPoint.z + this.entitySizeZ, stepHeight, height, PYPZ, this.checkObstructions);

					for(int k = 0; k < pathsPYPZ.length; k++) {
						if(isSuitablePoint(pathsPY, currentPoint.x, currentPoint.y + 1, currentPoint.z, pathsPZ, currentPoint.x, currentPoint.y, currentPoint.z + 1, pathsPYPZ[k], this.checkObstructions, fitsThroughPoles, allowOuterCorners)) {
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

	private boolean isSuitablePoint(@Nullable PathPoint[] newPoints1, int np1x, int np1y, int np1z, @Nullable PathPoint[] newPoints2, int np2x, int np2y, int np2z, @Nullable PathPoint newPointDiagonal, boolean allowObstructions, boolean fitsThroughPoles, boolean allowOuterCorners) {
		if(!allowOuterCorners) {
			if(newPointDiagonal != null && !newPointDiagonal.visited && newPoints2 != null && newPoints2.length > 0 && (newPoints2[0] != null || (newPoints2.length > 1 && newPoints2[1] != null)) && newPoints1 != null && newPoints1.length > 0 && (newPoints1[0] != null || (newPoints1.length > 1 && newPoints1[1] != null))) {
				if((newPoints1[0] == null || newPoints1[0].nodeType != PathNodeType.WALKABLE_DOOR) && (newPoints2[0] == null || newPoints2[0].nodeType != PathNodeType.WALKABLE_DOOR) && newPointDiagonal.nodeType != PathNodeType.WALKABLE_DOOR) {
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
				PathPoint newPoint21 = newPoints2 != null && newPoints2.length >= 1 ? newPoints2[0] : null;
				PathPoint newPoint22 = newPoints2 != null && newPoints2.length >= 2 ? newPoints2[1] : null;
				PathPoint newPoint11 = newPoints1 != null && newPoints1.length >= 1 ? newPoints1[0] : null;
				PathPoint newPoint12 = newPoints1 != null && newPoints1.length >= 2 ? newPoints1[1] : null;

				if(allowObstructions || (newPointDiagonal.costMalus >= 0.0f && (
						(newPoint21 != null && (newPoint21.costMalus >= 0.0f || newPoint21.nodeType == PathNodeType.OPEN)) ||
						(newPoint22 != null && (newPoint22.costMalus >= 0.0f || newPoint22.nodeType == PathNodeType.OPEN)) ||
						(newPoint11 != null && (newPoint11.costMalus >= 0.0f || newPoint11.nodeType == PathNodeType.OPEN)) ||
						(newPoint12 != null && (newPoint12.costMalus >= 0.0f || newPoint12.nodeType == PathNodeType.OPEN))
						))) {
					return true;
				}

				PathNodeType pathNodeType2 = this.getPathNodeTypeCached(this.entity, np2x, np2y, np2z);
				if(pathNodeType2 == PathNodeType.OPEN || pathNodeType2 == PathNodeType.WALKABLE) {
					return true;
				}

				PathNodeType pathNodeType1 = this.getPathNodeTypeCached(this.entity, np1x, np1y, np1z);
				if(pathNodeType1 == PathNodeType.OPEN || pathNodeType2 == PathNodeType.WALKABLE) {
					return true;
				}

				return false;
			} else {
				return false;
			}
		}
	}

	@Nullable
	private PathPoint[] getSafePoints(int x, int y, int z, int stepHeight, double height, Vector3i direction, boolean allowBlocked) {
		PathPoint directPathPoint = null;

		BlockPos pos = new BlockPos(x, y, z);

		double blockHeight = y - getGroundY(this.blockaccess, new BlockPos(x, y, z));

		if (blockHeight - height > 1.125D) {
			return new PathPoint[0];
		} else {
			PathNodeType nodeType = this.getPathNodeTypeCached(this.entity, x, y, z);

			float malus = this.advancedPathFindingEntity.getPathingMalus(this.blockaccess, this.entity, nodeType, pos, direction); //Replaces EntityLiving#getPathPriority

			double halfWidth = (double)this.entity.getWidth() / 2.0D;

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
				if (directPathPoint == null && stepHeight > 0 && nodeType != PathNodeType.FENCE && nodeType != PathNodeType.TRAPDOOR && direction.getY() == 0 && Math.abs(direction.getX()) + Math.abs(direction.getY()) + Math.abs(direction.getZ()) == 1) {
					PathPoint[] pointsAbove = this.getSafePoints(x, y + 1, z, stepHeight - 1, height, direction, false);
					directPathPoint = pointsAbove.length > 0 ? pointsAbove[0] : null;

					if(directPathPoint != null && (directPathPoint.nodeType == PathNodeType.OPEN || directPathPoint.nodeType == PathNodeType.WALKABLE) && this.entity.getWidth() < 1.0F) {
						double offsetX = (x - direction.getX()) + 0.5D;
						double offsetZ = (z - direction.getY()) + 0.5D;

						AxisAlignedBB enclosingAabb = new AxisAlignedBB(
								offsetX - halfWidth,
								getGroundY(this.blockaccess, new BlockPos(offsetX, (double)(y + 1), offsetZ)) + 0.001D,
								offsetZ - halfWidth,
								offsetX + halfWidth,
								(double)this.entity.getHeight() + getGroundY(this.blockaccess, new BlockPos(directPathPoint.x, directPathPoint.y, directPathPoint.z)) - 0.002D,
								offsetZ + halfWidth);
						if (this.checkAabbCollision(enclosingAabb)) {
							directPathPoint = null;
						}
					}
				}

				if(nodeType == PathNodeType.OPEN) {
					directPathPoint = null;

					AxisAlignedBB checkAabb = new AxisAlignedBB((double)x - halfWidth + 0.5D, (double)y + 0.001D, (double)z - halfWidth + 0.5D, (double)x + halfWidth + 0.5D, (double)((float)y + this.entity.getHeight()), (double)z + halfWidth + 0.5D);

					if(this.checkAabbCollision(checkAabb)) {
						result[0] = null;
						return result;
					}

					if(this.entity.getWidth() >= 1.0F) {
						for(Direction pathableFacing : this.pathableFacings) {
							PathNodeType nodeTypeAtFacing = this.getPathNodeTypeCached(this.entity, x + pathableFacing.getXOffset() * this.pathingSizeOffsetX, y + (pathableFacing == Direction.DOWN ? -1 : pathableFacing == Direction.UP ? this.pathingSizeOffsetY : 0), z + pathableFacing.getZOffset() * this.pathingSizeOffsetZ);

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

						if(fallDistance++ >= Math.max(1, this.entity.getMaxFallHeight()) /*at least one chance is required for swimming*/ || y == 0) {
							cancelFallDown = true;
							break;
						}

						nodeType = this.getPathNodeTypeCached(this.entity, x, y, z);
						malus = this.entity.getPathPriority(nodeType);

						if(((this.entity.getMaxFallHeight() > 0 && nodeType != PathNodeType.OPEN) || nodeType == PathNodeType.WATER || nodeType == PathNodeType.LAVA) && malus >= 0.0F) {
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
						float bridingMalus = this.advancedPathFindingEntity.getBridgePathingMalus(this.entity, new BlockPos(x, preFallY, z), fallPathPoint);

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

	private PathNodeType getPathNodeTypeCached(MobEntity entitylivingIn, PathPoint point) {
		return this.getPathNodeType(this.blockaccess, point.x, point.y, point.z);
	}

	private PathNodeType getPathNodeTypeCached(MobEntity entitylivingIn, BlockPos pos) {
		return this.getPathNodeType(this.blockaccess, pos.getX(), pos.getY(), pos.getZ());
	}

	private PathNodeType getPathNodeTypeCached(MobEntity entitylivingIn, int x, int y, int z) {
		return this.pathNodeTypeCache.computeIfAbsent(BlockPos.pack(x, y, z), (key) -> {
			return this.getPathNodeType(this.blockaccess, x, y, z, entitylivingIn, this.entitySizeX, this.entitySizeY, this.entitySizeZ, this.getCanOpenDoors(), this.getCanEnterDoors());
		});
	}

	@Override
	public PathNodeType getPathNodeType(IBlockReader blockaccessIn, int x, int y, int z, MobEntity entity, int xSize, int ySize, int zSize, boolean canBreakDoorsIn, boolean canEnterDoorsIn) {
		BlockPos pos = new BlockPos(entity.getPositionVec());

		EnumSet<PathNodeType> applicablePathNodeTypes = EnumSet.noneOf(PathNodeType.class);
		PathNodeType centerPathNodeType = this.getPathNodeType(blockaccessIn, x, y, z, xSize, ySize, zSize, canBreakDoorsIn, canEnterDoorsIn, applicablePathNodeTypes, PathNodeType.BLOCKED, pos);

		if(applicablePathNodeTypes.contains(PathNodeType.FENCE)) {
			return PathNodeType.FENCE;
		} else if(applicablePathNodeTypes.contains(PathNodeType.UNPASSABLE_RAIL)) {
			return PathNodeType.UNPASSABLE_RAIL;
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
	public PathNodeType getPathNodeType(IBlockReader blockaccessIn, int x, int y, int z) {
		return getPathNodeTypeWithConditions(blockaccessIn, x, y, z, this.pathingSizeOffsetX, this.pathingSizeOffsetY, this.pathingSizeOffsetZ, this.pathableFacings, EnumSet.noneOf(Direction.class), null);
	}

	protected static PathNodeType getPathNodeTypeWithConditions(IBlockReader blockaccessIn, int x, int y, int z, int pathingSizeOffsetX, int pathingSizeOffsetY, int pathingSizeOffsetZ, EnumSet<Direction> pathableFacings, @Nullable EnumSet<Direction> exemptions, @Nullable EnumSet<Direction> found) {
		BlockPos.Mutable pos = new BlockPos.Mutable();

		PathNodeType nodeType = func_237238_b_(blockaccessIn, pos.setPos(x, y, z)); //getPathNodeTypeRaw

		if(nodeType == PathNodeType.OPEN && y >= 1) {
			facings: for(Direction pathableFacing : pathableFacings) {
				if(exemptions == null || !exemptions.contains(pathableFacing)) {
					int checkHeight = pathableFacing.getAxis() != Axis.Y ? Math.min(4, pathingSizeOffsetY - 1) : 0;

					int cx = x + pathableFacing.getXOffset() * pathingSizeOffsetX;
					int cy = y + (pathableFacing == Direction.DOWN ? -1 : pathableFacing == Direction.UP ? pathingSizeOffsetY : 0);
					int cz = z + pathableFacing.getZOffset() * pathingSizeOffsetZ;

					for(int yo = 0; yo <= checkHeight; yo++) {
						pos.setPos(cx, cy + yo, cz);

						PathNodeType offsetNodeType = func_237238_b_(blockaccessIn, pos); //getPathNodeTypeRaw
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

						if(offsetNodeType == PathNodeType.STICKY_HONEY) {
							nodeType = PathNodeType.STICKY_HONEY;
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
			nodeType = func_237232_a_(blockaccessIn, pos.setPos(x, y, z), nodeType); //checkNeighborBlocks
		}

		return nodeType;
	}
}
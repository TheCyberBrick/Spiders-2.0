package tcb.spiderstpo.common.entity.movement;

import java.util.EnumSet;

import javax.annotation.Nullable;

import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
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
	protected static final PathNodeType[] PATH_NODE_TYPES = PathNodeType.values();
	protected static final Direction[] DIRECTIONS = Direction.values();

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
	protected Direction[] pathableFacingsArray;

	private final Long2LongMap pathNodeTypeCache = new Long2LongOpenHashMap();
	private final Long2ObjectMap<PathNodeType> rawPathNodeTypeCache = new Long2ObjectOpenHashMap<>();
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

		this.pathableFacingsArray = this.pathableFacings.toArray(new Direction[0]);
	}

	@Override
	public void postProcess() {
		super.postProcess();
		this.pathNodeTypeCache.clear();
		this.rawPathNodeTypeCache.clear();
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
				by = MathHelper.floor(y + Math.min(0.5D, Math.max(this.entity.getHeight() - 0.1f, 0.0D)));
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

		final BlockPos initialStartPos = new BlockPos(x, by, z);
		BlockPos startPos = initialStartPos;

		long packed = this.removeNonStartingSides(this.getDirectionalPathNodeTypeCached(this.entity, startPos.getX(), startPos.getY(), startPos.getZ()));
		DirectionalPathPoint startPathPoint = this.openPoint(startPos.getX(), startPos.getY(), startPos.getZ(), packed, false);
		startPathPoint.nodeType = unpackNodeType(packed);
		startPathPoint.costMalus = this.entity.getPathPriority(startPathPoint.nodeType);

		startPos = this.findSuitableStartingPosition(startPos, startPathPoint);

		if(!initialStartPos.equals(startPos)) {
			packed = this.removeNonStartingSides(this.getDirectionalPathNodeTypeCached(this.entity, startPos.getX(), startPos.getY(), startPos.getZ()));
			startPathPoint = this.openPoint(startPos.getX(), startPos.getY(), startPos.getZ(), packed, false);
			startPathPoint.nodeType = unpackNodeType(packed);
			startPathPoint.costMalus = this.entity.getPathPriority(startPathPoint.nodeType);
		}

		if(this.entity.getPathPriority(startPathPoint.nodeType) < 0.0F) {
			AxisAlignedBB aabb = this.entity.getBoundingBox();

			if(this.isSafeStartingPosition(checkPos.setPos(aabb.minX, by, aabb.minZ)) || this.isSafeStartingPosition(checkPos.setPos(aabb.minX, by, aabb.maxZ)) || this.isSafeStartingPosition(checkPos.setPos(aabb.maxX, by, aabb.minZ)) || this.isSafeStartingPosition(checkPos.setPos(aabb.maxX, by, aabb.maxZ))) {
				packed = this.removeNonStartingSides(this.getDirectionalPathNodeTypeCached(this.entity, checkPos.getX(), checkPos.getY(), checkPos.getZ()));
				startPathPoint = this.openPoint(checkPos.getX(), checkPos.getY(), checkPos.getZ(), packed, false);
				startPathPoint.nodeType = unpackNodeType(packed);
				startPathPoint.costMalus = this.entity.getPathPriority(startPathPoint.nodeType);
			}
		}

		return startPathPoint;
	}

	private long removeNonStartingSides(long packed) {
		long newPacked = packed & ~0xFFFFFFFFL;

		for(Direction side : DIRECTIONS) {
			if(unpackDirection(side, packed) && this.isValidStartingSide(side)) {
				newPacked = packDirection(side, newPacked);
			}
		}

		return newPacked;
	}

	protected boolean isValidStartingSide(Direction side) {
		Direction groundSide = this.advancedPathFindingEntity.getGroundSide();
		return side == groundSide || side.getAxis() != groundSide.getAxis();
	}

	protected BlockPos findSuitableStartingPosition(BlockPos pos, DirectionalPathPoint startPathPoint) {
		if(startPathPoint.getPathableSides().length == 0) {
			Direction avoidedOffset = this.advancedPathFindingEntity.getGroundSide().getOpposite();

			for(int xo = -1; xo <= 1; xo++) {
				for(int yo = -1; yo <= 1; yo++) {
					for(int zo = -1; zo <= 1; zo++) {
						if(xo != avoidedOffset.getXOffset() && yo != avoidedOffset.getYOffset() && zo != avoidedOffset.getZOffset()) {
							BlockPos offsetPos = pos.add(xo, yo, zo);

							long packed = this.getDirectionalPathNodeTypeCached(this.entity, offsetPos.getX(), offsetPos.getY(), offsetPos.getZ());
							PathNodeType nodeType = unpackNodeType(packed);

							if(nodeType == PathNodeType.WALKABLE && unpackDirection(packed)) {
								return offsetPos;
							}
						}
					}
				}
			}
		}

		return pos;
	}

	private boolean isSafeStartingPosition(BlockPos pos) {
		PathNodeType pathnodetype = unpackNodeType(this.getDirectionalPathNodeTypeCached(this.entity, pos.getX(), pos.getY(), pos.getZ()));
		return this.entity.getPathPriority(pathnodetype) >= 0.0F;
	}

	private boolean allowDiagonalPathOptions(PathPoint[] options) {
		return this.alwaysAllowDiagonals || options == null || options.length == 0 || ((options[0] == null || options[0].nodeType == PathNodeType.OPEN || options[0].costMalus != 0.0F) && (options.length <= 1 || (options[1] == null || options[1].nodeType == PathNodeType.OPEN || options[1].costMalus != 0.0F)));
	}

	@Override
	public int func_222859_a(PathPoint[] pathOptions, PathPoint currentPointIn) {
		DirectionalPathPoint currentPoint;
		if(currentPointIn instanceof DirectionalPathPoint) {
			currentPoint = (DirectionalPathPoint) currentPointIn;
		} else {
			currentPoint = new DirectionalPathPoint(currentPointIn);
		}

		int openedNodeCount = 0;
		int stepHeight = 0;

		PathNodeType nodeTypeAbove = unpackNodeType(this.getDirectionalPathNodeTypeCached(this.entity, currentPoint.x, currentPoint.y + 1, currentPoint.z));

		if(this.entity.getPathPriority(nodeTypeAbove) >= 0.0F) {
			stepHeight = MathHelper.floor(Math.max(1.0F, this.entity.stepHeight));
		}

		double height = currentPoint.y - getGroundY(this.blockaccess, new BlockPos(currentPoint.x, currentPoint.y, currentPoint.z));

		DirectionalPathPoint[] pathsPZ = this.getSafePoints(currentPoint.x, currentPoint.y, currentPoint.z + 1, stepHeight, height, PZ, this.checkObstructions);
		DirectionalPathPoint[] pathsNX = this.getSafePoints(currentPoint.x - 1, currentPoint.y, currentPoint.z, stepHeight, height, NX, this.checkObstructions);
		DirectionalPathPoint[] pathsPX = this.getSafePoints(currentPoint.x + 1, currentPoint.y, currentPoint.z, stepHeight, height, PX, this.checkObstructions);
		DirectionalPathPoint[] pathsNZ = this.getSafePoints(currentPoint.x, currentPoint.y, currentPoint.z - 1, stepHeight, height, NZ, this.checkObstructions);

		for(int k = 0; k < pathsPZ.length; k++) {
			if(this.isSuitablePoint(pathsPZ[k], currentPoint, this.checkObstructions)) {
				pathOptions[openedNodeCount++] = pathsPZ[k];
			}
		}

		for(int k = 0; k < pathsNX.length; k++) {
			if(this.isSuitablePoint(pathsNX[k], currentPoint, this.checkObstructions)) {
				pathOptions[openedNodeCount++] = pathsNX[k];
			}
		}

		for(int k = 0; k < pathsPX.length; k++) {
			if(this.isSuitablePoint(pathsPX[k], currentPoint, this.checkObstructions)) {
				pathOptions[openedNodeCount++] = pathsPX[k];
			}
		}

		for(int k = 0; k < pathsNZ.length; k++) {
			if(this.isSuitablePoint(pathsNZ[k], currentPoint, this.checkObstructions)) {
				pathOptions[openedNodeCount++] = pathsNZ[k];
			}
		}

		DirectionalPathPoint[] pathsNY = null;
		if(this.checkObstructions || this.pathableFacings.size() > 1) {
			pathsNY = this.getSafePoints(currentPoint.x, currentPoint.y - 1, currentPoint.z, stepHeight, height, NY, this.checkObstructions);

			for(int k = 0; k < pathsNY.length; k++) {
				if(this.isSuitablePoint(pathsNY[k], currentPoint, this.checkObstructions)) {
					pathOptions[openedNodeCount++] = pathsNY[k];
				}
			}
		}

		DirectionalPathPoint[] pathsPY = null;
		if(this.pathableFacings.size() > 1) {
			pathsPY = this.getSafePoints(currentPoint.x, currentPoint.y + 1, currentPoint.z, stepHeight, height, PY, this.checkObstructions);

			for(int k = 0; k < pathsPY.length; k++) {
				if(this.isSuitablePoint(pathsPY[k], currentPoint, this.checkObstructions)) {
					pathOptions[openedNodeCount++] = pathsPY[k];
				}
			}
		}

		boolean allowDiagonalNZ = this.allowDiagonalPathOptions(pathsNZ);
		boolean allowDiagonalPZ = this.allowDiagonalPathOptions(pathsPZ);
		boolean allowDiagonalPX = this.allowDiagonalPathOptions(pathsPX);
		boolean allowDiagonalNX = this.allowDiagonalPathOptions(pathsNX);

		boolean fitsThroughPoles = this.entity.getWidth() < 0.5f;

		boolean is3DPathing = this.pathableFacings.size() >= 3;

		if(allowDiagonalNZ && allowDiagonalNX) {
			DirectionalPathPoint[] pathsNXNZ = this.getSafePoints(currentPoint.x - this.entitySizeX, currentPoint.y, currentPoint.z - 1, stepHeight, height, NXNZ, this.checkObstructions);

			boolean foundDiagonal = false;

			for(int k = 0; k < pathsNXNZ.length; k++) {
				if(this.isSuitablePoint(pathsNX, currentPoint.x - 1, currentPoint.y, currentPoint.z, pathsNZ, currentPoint.x, currentPoint.y, currentPoint.z - 1, pathsNXNZ[k], currentPoint, this.checkObstructions, fitsThroughPoles, is3DPathing)) {
					pathOptions[openedNodeCount++] = pathsNXNZ[k];
					foundDiagonal = true;
				}
			}

			if(!foundDiagonal && (this.entitySizeX != 1 || this.entitySizeZ != 1)) {
				pathsNXNZ = this.getSafePoints(currentPoint.x - 1, currentPoint.y, currentPoint.z - this.entitySizeZ, stepHeight, height, NXNZ, this.checkObstructions);

				for(int k = 0; k < pathsNXNZ.length; k++) {
					if(this.isSuitablePoint(pathsNX, currentPoint.x - 1, currentPoint.y, currentPoint.z, pathsNZ, currentPoint.x, currentPoint.y, currentPoint.z - 1, pathsNXNZ[k], currentPoint, this.checkObstructions, fitsThroughPoles, is3DPathing)) {
						pathOptions[openedNodeCount++] = pathsNXNZ[k];
					}
				}
			}
		}

		if(allowDiagonalNZ && allowDiagonalPX) {
			DirectionalPathPoint[] pathsPXNZ = this.getSafePoints(currentPoint.x + 1, currentPoint.y, currentPoint.z - 1, stepHeight, height, PXNZ, this.checkObstructions);

			for(int k = 0; k < pathsPXNZ.length; k++) {
				if(this.isSuitablePoint(pathsPX, currentPoint.x + 1, currentPoint.y, currentPoint.z, pathsNZ, currentPoint.x, currentPoint.y, currentPoint.z - 1, pathsPXNZ[k], currentPoint, this.checkObstructions, fitsThroughPoles, is3DPathing)) {
					pathOptions[openedNodeCount++] = pathsPXNZ[k];
				}
			}
		}

		if(allowDiagonalPZ && allowDiagonalNX) {
			DirectionalPathPoint[] pathsNXPZ = this.getSafePoints(currentPoint.x - 1, currentPoint.y, currentPoint.z + 1, stepHeight, height, NXPZ, this.checkObstructions);

			for(int k = 0; k < pathsNXPZ.length; k++) {
				if(this.isSuitablePoint(pathsNX, currentPoint.x - 1, currentPoint.y, currentPoint.z, pathsPZ, currentPoint.x, currentPoint.y, currentPoint.z + 1, pathsNXPZ[k], currentPoint, this.checkObstructions, fitsThroughPoles, is3DPathing)) {
					pathOptions[openedNodeCount++] = pathsNXPZ[k];
				}
			}
		}

		if(allowDiagonalPZ && allowDiagonalPX) {
			DirectionalPathPoint[] pathsPXPZ = this.getSafePoints(currentPoint.x + this.entitySizeX, currentPoint.y, currentPoint.z + 1, stepHeight, height, PXPZ, this.checkObstructions);

			boolean foundDiagonal = false;

			for(int k = 0; k < pathsPXPZ.length; k++) {
				if(this.isSuitablePoint(pathsPX, currentPoint.x + 1, currentPoint.y, currentPoint.z, pathsPZ, currentPoint.x, currentPoint.y, currentPoint.z + 1, pathsPXPZ[k], currentPoint, this.checkObstructions, fitsThroughPoles, is3DPathing)) {
					pathOptions[openedNodeCount++] = pathsPXPZ[k];
					foundDiagonal = true;
				}
			}

			if(!foundDiagonal && (this.entitySizeX != 1 || this.entitySizeZ != 1)) {
				pathsPXPZ = this.getSafePoints(currentPoint.x + 1, currentPoint.y, currentPoint.z + this.entitySizeZ, stepHeight, height, PXPZ, this.checkObstructions);

				for(int k = 0; k < pathsPXPZ.length; k++) {
					if(this.isSuitablePoint(pathsPX, currentPoint.x + 1, currentPoint.y, currentPoint.z, pathsPZ, currentPoint.x, currentPoint.y, currentPoint.z + 1, pathsPXPZ[k], currentPoint, this.checkObstructions, fitsThroughPoles, is3DPathing)) {
						pathOptions[openedNodeCount++] = pathsPXPZ[k];
					}
				}
			}
		}

		if(this.pathableFacings.size() > 1) {
			boolean allowDiagonalPY = this.allowDiagonalPathOptions(pathsPY);
			boolean allowDiagonalNY = this.allowDiagonalPathOptions(pathsNY);

			if(allowDiagonalNY && allowDiagonalNX) {
				DirectionalPathPoint[] pathsNYNX = this.getSafePoints(currentPoint.x - this.entitySizeX, currentPoint.y - 1, currentPoint.z, stepHeight, height, NXNY, this.checkObstructions);

				boolean foundDiagonal = false;

				for(int k = 0; k < pathsNYNX.length; k++) {
					if(this.isSuitablePoint(pathsNY, currentPoint.x, currentPoint.y - 1, currentPoint.z, pathsNX, currentPoint.x - 1, currentPoint.y, currentPoint.z, pathsNYNX[k], currentPoint, this.checkObstructions, fitsThroughPoles, is3DPathing)) {
						pathOptions[openedNodeCount++] = pathsNYNX[k];
						foundDiagonal = true;
					}
				}

				if(!foundDiagonal && (this.entitySizeX != 1 || this.entitySizeY != 1)) {
					pathsNYNX = this.getSafePoints(currentPoint.x - 1, currentPoint.y - this.entitySizeY, currentPoint.z, stepHeight, height, NXNY, this.checkObstructions);

					for(int k = 0; k < pathsNYNX.length; k++) {
						if(this.isSuitablePoint(pathsNY, currentPoint.x, currentPoint.y - 1, currentPoint.z, pathsNX, currentPoint.x - 1, currentPoint.y, currentPoint.z, pathsNYNX[k], currentPoint, this.checkObstructions, fitsThroughPoles, is3DPathing)) {
							pathOptions[openedNodeCount++] = pathsNYNX[k];
						}
					}
				}
			}

			if(allowDiagonalNY && allowDiagonalPX) {
				DirectionalPathPoint[] pathsNYPX = this.getSafePoints(currentPoint.x + 1, currentPoint.y - 1, currentPoint.z, stepHeight, height, PXNY, this.checkObstructions);

				for(int k = 0; k < pathsNYPX.length; k++) {
					if(this.isSuitablePoint(pathsNY, currentPoint.x, currentPoint.y - 1, currentPoint.z, pathsPX, currentPoint.x + 1, currentPoint.y, currentPoint.z, pathsNYPX[k], currentPoint, this.checkObstructions, fitsThroughPoles, is3DPathing)) {
						pathOptions[openedNodeCount++] = pathsNYPX[k];
					}
				}
			}

			if(allowDiagonalNY && allowDiagonalNZ) {
				DirectionalPathPoint[] pathsNYNZ = this.getSafePoints(currentPoint.x, currentPoint.y - this.entitySizeY, currentPoint.z - 1, stepHeight, height, NYNZ, this.checkObstructions);

				boolean foundDiagonal = false;

				for(int k = 0; k < pathsNYNZ.length; k++) {
					if(this.isSuitablePoint(pathsNY, currentPoint.x, currentPoint.y - 1, currentPoint.z, pathsNZ, currentPoint.x, currentPoint.y, currentPoint.z - 1, pathsNYNZ[k], currentPoint, this.checkObstructions, fitsThroughPoles, is3DPathing)) {
						pathOptions[openedNodeCount++] = pathsNYNZ[k];
						foundDiagonal = true;
					}
				}

				if(!foundDiagonal && (this.entitySizeY != 1 || this.entitySizeZ != 1)) {
					pathsNYNZ = this.getSafePoints(currentPoint.x, currentPoint.y - 1, currentPoint.z - this.entitySizeZ, stepHeight, height, NYNZ, this.checkObstructions);

					for(int k = 0; k < pathsNYNZ.length; k++) {
						if(this.isSuitablePoint(pathsNY, currentPoint.x, currentPoint.y - 1, currentPoint.z, pathsNZ, currentPoint.x, currentPoint.y, currentPoint.z - 1, pathsNYNZ[k], currentPoint, this.checkObstructions, fitsThroughPoles, is3DPathing)) {
							pathOptions[openedNodeCount++] = pathsNYNZ[k];
						}
					}
				}
			}

			if(allowDiagonalNY && allowDiagonalPZ) {
				DirectionalPathPoint[] pathsNYPZ = this.getSafePoints(currentPoint.x, currentPoint.y - 1, currentPoint.z + 1, stepHeight, height, NYPZ, this.checkObstructions);

				for(int k = 0; k < pathsNYPZ.length; k++) {
					if(this.isSuitablePoint(pathsNY, currentPoint.x, currentPoint.y - 1, currentPoint.z, pathsPZ, currentPoint.x, currentPoint.y, currentPoint.z + 1, pathsNYPZ[k], currentPoint, this.checkObstructions, fitsThroughPoles, is3DPathing)) {
						pathOptions[openedNodeCount++] = pathsNYPZ[k];
					}
				}
			}

			if(allowDiagonalPY && allowDiagonalNX) {
				DirectionalPathPoint[] pathsPYNX = this.getSafePoints(currentPoint.x - 1, currentPoint.y + 1, currentPoint.z, stepHeight, height, NXPY, this.checkObstructions);

				for(int k = 0; k < pathsPYNX.length; k++) {
					if(this.isSuitablePoint(pathsPY, currentPoint.x, currentPoint.y + 1, currentPoint.z, pathsNZ, currentPoint.x - 1, currentPoint.y, currentPoint.z, pathsPYNX[k], currentPoint, this.checkObstructions, fitsThroughPoles, is3DPathing)) {
						pathOptions[openedNodeCount++] = pathsPYNX[k];
					}
				}
			}

			if(allowDiagonalPY && allowDiagonalPX) {
				DirectionalPathPoint[] pathsPYPX = this.getSafePoints(currentPoint.x + this.entitySizeX, currentPoint.y + 1, currentPoint.z, stepHeight, height, PXPY, this.checkObstructions);

				boolean foundDiagonal = false;

				for(int k = 0; k < pathsPYPX.length; k++) {
					if(this.isSuitablePoint(pathsPY, currentPoint.x, currentPoint.y + 1, currentPoint.z, pathsPX, currentPoint.x + 1, currentPoint.y, currentPoint.z, pathsPYPX[k], currentPoint, this.checkObstructions, fitsThroughPoles, is3DPathing)) {
						pathOptions[openedNodeCount++] = pathsPYPX[k];
						foundDiagonal = true;
					}
				}

				if(!foundDiagonal && (this.entitySizeX != 1 || this.entitySizeY != 1)) {
					pathsPYPX = this.getSafePoints(currentPoint.x + 1, currentPoint.y + this.entitySizeY, currentPoint.z, stepHeight, height, PXPY, this.checkObstructions);

					for(int k = 0; k < pathsPYPX.length; k++) {
						if(this.isSuitablePoint(pathsPY, currentPoint.x, currentPoint.y + 1, currentPoint.z, pathsPX, currentPoint.x + 1, currentPoint.y, currentPoint.z, pathsPYPX[k], currentPoint, this.checkObstructions, fitsThroughPoles, is3DPathing)) {
							pathOptions[openedNodeCount++] = pathsPYPX[k];
						}
					}
				}
			}

			if(allowDiagonalPY && allowDiagonalNZ) {
				DirectionalPathPoint[] pathsPYNZ = this.getSafePoints(currentPoint.x, currentPoint.y + 1, currentPoint.z - 1, stepHeight, height, PYNZ, this.checkObstructions);

				for(int k = 0; k < pathsPYNZ.length; k++) {
					if(this.isSuitablePoint(pathsPY, currentPoint.x, currentPoint.y + 1, currentPoint.z, pathsNZ, currentPoint.x, currentPoint.y, currentPoint.z - 1, pathsPYNZ[k], currentPoint, this.checkObstructions, fitsThroughPoles, is3DPathing)) {
						pathOptions[openedNodeCount++] = pathsPYNZ[k];
					}
				}
			}

			if(allowDiagonalPY && allowDiagonalPZ) {
				DirectionalPathPoint[] pathsPYPZ = this.getSafePoints(currentPoint.x, currentPoint.y + this.entitySizeY, currentPoint.z + 1, stepHeight, height, PYPZ, this.checkObstructions);

				boolean foundDiagonal = false;

				for(int k = 0; k < pathsPYPZ.length; k++) {
					if(this.isSuitablePoint(pathsPY, currentPoint.x, currentPoint.y + 1, currentPoint.z, pathsPZ, currentPoint.x, currentPoint.y, currentPoint.z + 1, pathsPYPZ[k], currentPoint, this.checkObstructions, fitsThroughPoles, is3DPathing)) {
						pathOptions[openedNodeCount++] = pathsPYPZ[k];
						foundDiagonal = true;
					}
				}

				if(!foundDiagonal && (this.entitySizeY != 1 || this.entitySizeZ != 1)) {
					pathsPYPZ = this.getSafePoints(currentPoint.x, currentPoint.y + 1, currentPoint.z + this.entitySizeZ, stepHeight, height, PYPZ, this.checkObstructions);

					for(int k = 0; k < pathsPYPZ.length; k++) {
						if(this.isSuitablePoint(pathsPY, currentPoint.x, currentPoint.y + 1, currentPoint.z, pathsPZ, currentPoint.x, currentPoint.y, currentPoint.z + 1, pathsPYPZ[k], currentPoint, this.checkObstructions, fitsThroughPoles, is3DPathing)) {
							pathOptions[openedNodeCount++] = pathsPYPZ[k];
						}
					}
				}
			}
		}

		return openedNodeCount;
	}

	protected boolean isTraversible(DirectionalPathPoint from, DirectionalPathPoint to) {
		if(this.getCanSwim() && (from.nodeType == PathNodeType.WATER || from.nodeType == PathNodeType.WATER_BORDER || from.nodeType == PathNodeType.LAVA || to.nodeType == PathNodeType.WATER || to.nodeType == PathNodeType.WATER_BORDER || to.nodeType == PathNodeType.LAVA)) {
			//When swimming it can always reach any side
			return true;
		}

		boolean dx = (to.x - from.x) != 0;
		boolean dy = (to.y - from.y) != 0;
		boolean dz = (to.z - from.z) != 0;

		boolean isDiagonal = (dx ? 1 : 0) + (dy ? 1 : 0) + (dz ? 1 : 0) > 1;

		Direction[] fromDirections = from.getPathableSides();
		Direction[] toDirections = to.getPathableSides();

		for(int i = 0; i < fromDirections.length; i++) {
			Direction d1 = fromDirections[i];

			for(int j = 0; j < toDirections.length; j++) {
				Direction d2 = toDirections[j];

				if(d1 == d2) {
					return true;
				} else if(isDiagonal) {
					Axis a1 = d1.getAxis();
					Axis a2 = d2.getAxis();

					if((a1 == Axis.X && a2 == Axis.Y) || (a1 == Axis.Y && a2 == Axis.X)) {
						return !dz;
					} else if((a1 == Axis.X && a2 == Axis.Z) || (a1 == Axis.Z && a2 == Axis.X)) {
						return !dy;
					} else if((a1 == Axis.Z && a2 == Axis.Y) || (a1 == Axis.Y && a2 == Axis.Z)) {
						return !dx;
					}
				}
			}
		}

		return false;
	}

	protected static boolean isSharingDirection(DirectionalPathPoint from, DirectionalPathPoint to) {
		Direction[] fromDirections = from.getPathableSides();
		Direction[] toDirections = to.getPathableSides();

		for(int i = 0; i < fromDirections.length; i++) {
			Direction d1 = fromDirections[i];

			for(int j = 0; j < toDirections.length; j++) {
				Direction d2 = toDirections[j];

				if(d1 == d2) {
					return true;
				}
			}
		}

		return false;
	}

	protected boolean isSuitablePoint(@Nullable DirectionalPathPoint newPoint, DirectionalPathPoint currentPoint, boolean allowObstructions) {
		return newPoint != null && !newPoint.visited && (allowObstructions || newPoint.costMalus >= 0.0F || currentPoint.costMalus < 0.0F) && this.isTraversible(currentPoint, newPoint);
	}

	protected boolean isSuitablePoint(@Nullable DirectionalPathPoint[] newPoints1, int np1x, int np1y, int np1z, @Nullable DirectionalPathPoint[] newPoints2, int np2x, int np2y, int np2z, @Nullable DirectionalPathPoint newPointDiagonal, DirectionalPathPoint currentPoint, boolean allowObstructions, boolean fitsThroughPoles, boolean is3DPathing) {
		if(!is3DPathing) {
			if(newPointDiagonal != null && !newPointDiagonal.visited && newPoints2 != null && newPoints2.length > 0 && (newPoints2[0] != null || (newPoints2.length > 1 && newPoints2[1] != null)) && newPoints1 != null && newPoints1.length > 0 && (newPoints1[0] != null || (newPoints1.length > 1 && newPoints1[1] != null))) {
				if((newPoints1[0] == null || newPoints1[0].nodeType != PathNodeType.DOOR_OPEN) && (newPoints2[0] == null || newPoints2[0].nodeType != PathNodeType.DOOR_OPEN) && newPointDiagonal.nodeType != PathNodeType.DOOR_OPEN) {
					boolean canPassPoleDiagonally = newPoints2[0] != null && newPoints2[0].nodeType == PathNodeType.FENCE && newPoints1[0] != null && newPoints1[0].nodeType == PathNodeType.FENCE && fitsThroughPoles;
					return (allowObstructions || newPointDiagonal.costMalus >= 0.0F) &&
							(canPassPoleDiagonally || (
									((newPoints2[0] != null && (allowObstructions || newPoints2[0].costMalus >= 0.0F)) || (newPoints2.length > 1 && newPoints2[1] != null && (allowObstructions || newPoints2[1].costMalus >= 0.0F))) &&
									((newPoints1[0] != null && (allowObstructions || newPoints1[0].costMalus >= 0.0F)) || (newPoints1.length > 1 && newPoints1[1] != null && (allowObstructions || newPoints1[1].costMalus >= 0.0F)))
									));
				}
			}
		} else {
			if(newPointDiagonal != null && !newPointDiagonal.visited && this.isTraversible(currentPoint, newPointDiagonal)) {
				long packed2 = this.getDirectionalPathNodeTypeCached(this.entity, np2x, np2y, np2z);
				PathNodeType pathNodeType2 = unpackNodeType(packed2);
				boolean open2 = (pathNodeType2 == PathNodeType.OPEN || pathNodeType2 == PathNodeType.WALKABLE);

				long packed1 = this.getDirectionalPathNodeTypeCached(this.entity, np1x, np1y, np1z);
				PathNodeType pathNodeType1 = unpackNodeType(packed1);
				boolean open1 = (pathNodeType1 == PathNodeType.OPEN || pathNodeType1 == PathNodeType.WALKABLE);

				return (open1 != open2) || (open1 == true && open2 == true && isSharingDirection(newPointDiagonal, currentPoint));
			}
		}

		return false;
	}

	protected DirectionalPathPoint openPoint(int x, int y, int z, long packed, boolean isDrop) {
		int hash = PathPoint.makeHash(x, y, z);

		PathPoint point = this.pointMap.computeIfAbsent(hash, (key) -> {
			return new DirectionalPathPoint(x, y, z, packed, isDrop);
		});

		if(point instanceof DirectionalPathPoint == false) {
			point = new DirectionalPathPoint(point);
			this.pointMap.put(hash, point);
		}

		return (DirectionalPathPoint) point;
	}

	@Nullable
	private DirectionalPathPoint[] getSafePoints(int x, int y, int z, int stepHeight, double height, Vector3i direction, boolean allowBlocked) {
		DirectionalPathPoint directPathPoint = null;

		BlockPos pos = new BlockPos(x, y, z);

		double blockHeight = y - getGroundY(this.blockaccess, new BlockPos(x, y, z));

		if (blockHeight - height > 1.125D) {
			return new DirectionalPathPoint[0];
		} else {
			final long initialPacked = this.getDirectionalPathNodeTypeCached(this.entity, x, y, z);
			long packed = initialPacked;
			PathNodeType nodeType = unpackNodeType(packed);

			float malus = this.advancedPathFindingEntity.getPathingMalus(this.blockaccess, this.entity, nodeType, pos, direction, dir -> unpackDirection(dir, initialPacked)); //Replaces EntityLiving#getPathPriority

			double halfWidth = (double)this.entity.getWidth() / 2.0D;

			DirectionalPathPoint[] result = new DirectionalPathPoint[1];

			if(malus >= 0.0F && (allowBlocked || nodeType != PathNodeType.BLOCKED)) {
				directPathPoint = this.openPoint(x, y, z, packed, false);
				directPathPoint.nodeType = nodeType;
				directPathPoint.costMalus = Math.max(directPathPoint.costMalus, malus);

				//Allow other nodes than this obstructed node to also be considered, otherwise jumping/pathing up steps does no longer work
				if(directPathPoint.nodeType == PathNodeType.BLOCKED) {
					result = new DirectionalPathPoint[2];
					result[1] = directPathPoint;
					directPathPoint = null;
				}
			}

			if(nodeType == PathNodeType.WALKABLE) {
				result[0] = directPathPoint;
				return result;
			} else {
				if (directPathPoint == null && stepHeight > 0 && nodeType != PathNodeType.FENCE && nodeType != PathNodeType.UNPASSABLE_RAIL && nodeType != PathNodeType.TRAPDOOR && direction.getY() == 0 && Math.abs(direction.getX()) + Math.abs(direction.getY()) + Math.abs(direction.getZ()) == 1) {
					DirectionalPathPoint[] pointsAbove = this.getSafePoints(x, y + 1, z, stepHeight - 1, height, direction, false);
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
						for(int i = 0; i < this.pathableFacingsArray.length; i++) {
							Direction pathableFacing = this.pathableFacingsArray[i];

							long packedAtFacing = this.getDirectionalPathNodeTypeCached(this.entity, x + pathableFacing.getXOffset() * this.pathingSizeOffsetX, y + (pathableFacing == Direction.DOWN ? -1 : pathableFacing == Direction.UP ? this.pathingSizeOffsetY : 0), z + pathableFacing.getZOffset() * this.pathingSizeOffsetZ);
							PathNodeType nodeTypeAtFacing = unpackNodeType(packedAtFacing);

							if(nodeTypeAtFacing == PathNodeType.BLOCKED) {
								directPathPoint = this.openPoint(x, y, z, packedAtFacing, false);
								directPathPoint.nodeType = PathNodeType.WALKABLE;
								directPathPoint.costMalus = Math.max(directPathPoint.costMalus, malus);
								result[0] = directPathPoint;
								return result;
							}
						}
					}


					boolean cancelFallDown = false;
					DirectionalPathPoint fallPathPoint = null;

					int fallDistance = 0;
					int preFallY = y;

					while(y > 0 && nodeType == PathNodeType.OPEN) {
						--y;

						if(fallDistance++ >= Math.max(1, this.entity.getMaxFallHeight()) /*at least one chance is required for swimming*/ || y == 0) {
							cancelFallDown = true;
							break;
						}

						packed = this.getDirectionalPathNodeTypeCached(this.entity, x, y, z);
						nodeType = unpackNodeType(packed);

						malus = this.entity.getPathPriority(nodeType);

						if(((this.entity.getMaxFallHeight() > 0 && nodeType != PathNodeType.OPEN) || nodeType == PathNodeType.WATER || nodeType == PathNodeType.LAVA) && malus >= 0.0F) {
							fallPathPoint = this.openPoint(x, y, z, packed, true);
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
						packed = this.getDirectionalPathNodeTypeCached(this.entity, x, preFallY, z);
						nodeType = unpackNodeType(packed);

						malus = this.entity.getPathPriority(nodeType);

						if(nodeType != PathNodeType.OPEN && malus >= 0.0F) {
							if(fallPathPoint != null) {
								result = new DirectionalPathPoint[2];
								result[1] = fallPathPoint;
							}

							result[0] = directPathPoint = this.openPoint(x, preFallY, z, packed, false);
							directPathPoint.nodeType = nodeType;
							directPathPoint.costMalus = Math.max(directPathPoint.costMalus, malus);
							hasPathUp = true;
						}
					}

					if(fallPathPoint != null) {
						if(!hasPathUp) {
							result[0] = directPathPoint = fallPathPoint;
						} else {
							result = new DirectionalPathPoint[2];
							result[0] = directPathPoint;
							result[1] = fallPathPoint;
						}
					}

					if(fallPathPoint != null) {
						float bridingMalus = this.advancedPathFindingEntity.getBridgePathingMalus(this.entity, new BlockPos(x, preFallY, z), fallPathPoint);

						if(bridingMalus >= 0.0f) {
							result = new DirectionalPathPoint[2];
							result[0] = directPathPoint;

							DirectionalPathPoint bridgePathPoint = this.openPoint(x, preFallY, z, packed, false);
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

				if(nodeType == PathNodeType.FENCE) {
					directPathPoint = this.openPoint(x, y, z, packed, false);
					directPathPoint.visited = true;
					directPathPoint.nodeType = nodeType;
					directPathPoint.costMalus = nodeType.getPriority();
				}

				result[0] = directPathPoint;
				return result;
			}
		}
	}

	protected long getDirectionalPathNodeTypeCached(MobEntity entitylivingIn, int x, int y, int z) {
		return this.pathNodeTypeCache.computeIfAbsent(BlockPos.pack(x, y, z), (key) -> {
			return this.getDirectionalPathNodeType(this.blockaccess, x, y, z, entitylivingIn, this.entitySizeX, this.entitySizeY, this.entitySizeZ, this.getCanOpenDoors(), this.getCanEnterDoors());
		});
	}

	static long packDirection(Direction facing, long packed) {
		return packed | (1L << facing.ordinal());
	}

	static long packDirection(long packed1, long packed2) {
		return (packed1 & ~0xFFFFFFFFL) | (packed1 & 0xFFFFFFFFL) | (packed2 & 0xFFFFFFFFL);
	}

	static boolean unpackDirection(Direction facing, long packed) {
		return (packed & (1L << facing.ordinal())) != 0;
	}

	static boolean unpackDirection(long packed) {
		return (packed & 0xFFFFFFFFL) != 0;
	}

	static long packNodeType(PathNodeType type, long packed) {
		return ((long) type.ordinal() << 32) | (packed & 0xFFFFFFFFL);
	}

	static PathNodeType unpackNodeType(long packed) {
		return PATH_NODE_TYPES[(int) (packed >> 32)];
	}

	@Override
	public PathNodeType getPathNodeType(IBlockReader blockaccessIn, int x, int y, int z, MobEntity entity, int xSize, int ySize, int zSize, boolean canBreakDoorsIn, boolean canEnterDoorsIn) {
		return unpackNodeType(this.getDirectionalPathNodeType(blockaccessIn, x, y, z, entity, xSize, ySize, zSize, canBreakDoorsIn, canEnterDoorsIn));
	}

	protected long getDirectionalPathNodeType(IBlockReader blockaccessIn, int x, int y, int z, MobEntity entity, int xSize, int ySize, int zSize, boolean canBreakDoorsIn, boolean canEnterDoorsIn) {
		BlockPos pos = new BlockPos(entity.getPositionVec());

		EnumSet<PathNodeType> applicablePathNodeTypes = EnumSet.noneOf(PathNodeType.class);

		long centerPacked = this.getDirectionalPathNodeType(blockaccessIn, x, y, z, xSize, ySize, zSize, canBreakDoorsIn, canEnterDoorsIn, applicablePathNodeTypes, PathNodeType.BLOCKED, pos);
		PathNodeType centerPathNodeType = unpackNodeType(centerPacked);

		if(applicablePathNodeTypes.contains(PathNodeType.FENCE)) {
			return packNodeType(PathNodeType.FENCE, centerPacked);
		} else if(applicablePathNodeTypes.contains(PathNodeType.UNPASSABLE_RAIL)) {
			return packNodeType(PathNodeType.UNPASSABLE_RAIL, centerPacked);
		} else {
			PathNodeType selectedPathNodeType = PathNodeType.BLOCKED;

			for(PathNodeType applicablePathNodeType : applicablePathNodeTypes) {
				if(entity.getPathPriority(applicablePathNodeType) < 0.0F) {
					return packNodeType(applicablePathNodeType, centerPacked);
				}

				float p1 = entity.getPathPriority(applicablePathNodeType);
				float p2 = entity.getPathPriority(selectedPathNodeType);
				if(p1 > p2 || (p1 == p2 && !(selectedPathNodeType == PathNodeType.WALKABLE && applicablePathNodeType == PathNodeType.OPEN)) || (p1 == p2 && selectedPathNodeType == PathNodeType.OPEN && applicablePathNodeType == PathNodeType.WALKABLE)) {
					selectedPathNodeType = applicablePathNodeType;
				}
			}

			if(centerPathNodeType == PathNodeType.OPEN && entity.getPathPriority(selectedPathNodeType) == 0.0F) {
				return packNodeType(PathNodeType.OPEN, 0L);
			} else {
				return packNodeType(selectedPathNodeType, centerPacked);
			}
		}
	}

	protected long getDirectionalPathNodeType(IBlockReader blockaccessIn, int x, int y, int z, int xSize, int ySize, int zSize, boolean canOpenDoorsIn, boolean canEnterDoorsIn, EnumSet<PathNodeType> nodeTypeEnum, PathNodeType nodeType, BlockPos pos) {
		long packed = 0L;

		for(int ox = 0; ox < xSize; ++ox) {
			for(int oy = 0; oy < ySize; ++oy) {
				for(int oz = 0; oz < zSize; ++oz) {
					int bx = ox + x;
					int by = oy + y;
					int bz = oz + z;

					long packedAdjusted = this.getDirectionalPathNodeType(blockaccessIn, bx, by, bz);
					PathNodeType adjustedNodeType = unpackNodeType(packedAdjusted);

					adjustedNodeType = this.func_215744_a(blockaccessIn, canOpenDoorsIn, canEnterDoorsIn, pos, adjustedNodeType);

					if (ox == 0 && oy == 0 && oz == 0) {
						packed = packNodeType(adjustedNodeType, packedAdjusted);
					}

					nodeTypeEnum.add(adjustedNodeType);
				}
			}
		}

		return packed;
	}

	@Override
	public PathNodeType getPathNodeType(IBlockReader blockaccessIn, int x, int y, int z) {
		return unpackNodeType(this.getDirectionalPathNodeType(blockaccessIn, x, y, z));
	}

	protected long getDirectionalPathNodeType(IBlockReader blockaccessIn, int x, int y, int z) {
		return getDirectionalPathNodeType(this.rawPathNodeTypeCache, blockaccessIn, x, y, z, this.pathingSizeOffsetX, this.pathingSizeOffsetY, this.pathingSizeOffsetZ, this.pathableFacingsArray);
	}

	protected static PathNodeType getRawPathNodeTypeCached(Long2ObjectMap<PathNodeType> cache, IBlockReader blockaccessIn, BlockPos.Mutable pos) {
		return cache.computeIfAbsent(BlockPos.pack(pos.getX(), pos.getY(), pos.getZ()), (key) -> {
			return func_237238_b_(blockaccessIn, pos); //getPathNodeTypeRaw
		});
	}

	protected static long getDirectionalPathNodeType(Long2ObjectMap<PathNodeType> rawPathNodeTypeCache, IBlockReader blockaccessIn, int x, int y, int z, int pathingSizeOffsetX, int pathingSizeOffsetY, int pathingSizeOffsetZ, Direction[] pathableFacings) {
		long packed = 0L;

		BlockPos.Mutable pos = new BlockPos.Mutable();

		PathNodeType nodeType = getRawPathNodeTypeCached(rawPathNodeTypeCache, blockaccessIn, pos.setPos(x, y, z));
		boolean isWalkable = false;

		if(nodeType == PathNodeType.OPEN && y >= 1) {
			for(int i = 0; i < pathableFacings.length; i++) {
				Direction pathableFacing = pathableFacings[i];

				int checkHeight = pathableFacing.getAxis() != Axis.Y ? Math.min(4, pathingSizeOffsetY - 1) : 0;

				int cx = x + pathableFacing.getXOffset() * pathingSizeOffsetX;
				int cy = y + (pathableFacing == Direction.DOWN ? -1 : pathableFacing == Direction.UP ? pathingSizeOffsetY : 0);
				int cz = z + pathableFacing.getZOffset() * pathingSizeOffsetZ;

				for(int yo = 0; yo <= checkHeight; yo++) {
					pos.setPos(cx, cy + yo, cz);

					PathNodeType offsetNodeType = getRawPathNodeTypeCached(rawPathNodeTypeCache, blockaccessIn, pos); 
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
						if(isColliderNodeType(offsetNodeType)) {
							packed = packDirection(pathableFacing, packed);
						}
						isWalkable = true;
					}
				}
			}
		}

		if(isWalkable) {
			nodeType = func_237232_a_(blockaccessIn, pos.setPos(x, y, z), PathNodeType.WALKABLE); //checkNeighborBlocks
		}

		return packNodeType(nodeType, packed);
	}

	protected static boolean isColliderNodeType(PathNodeType type) {
		return type == PathNodeType.BLOCKED || type == PathNodeType.TRAPDOOR || type == PathNodeType.FENCE ||
				type == PathNodeType.DOOR_WOOD_CLOSED || type == PathNodeType.DOOR_IRON_CLOSED || type == PathNodeType.LEAVES ||
				type == PathNodeType.STICKY_HONEY || type == PathNodeType.COCOA;
	}
}
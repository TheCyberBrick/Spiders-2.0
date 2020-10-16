package tcb.spiderstpo.common.entity.mob;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.annotation.Nullable;

import org.apache.commons.lang3.tuple.Pair;

import com.google.common.collect.ImmutableList;

import net.minecraft.block.BlockState;
import net.minecraft.command.arguments.EntityAnchorArgument.Type;
import net.minecraft.entity.CreatureEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.MoverType;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.ai.attributes.ModifiableAttributeInstance;
import net.minecraft.fluid.FluidState;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.pathfinding.Path;
import net.minecraft.pathfinding.PathNavigator;
import net.minecraft.pathfinding.PathPoint;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Rotations;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.shapes.VoxelShapeSpliterator;
import net.minecraft.util.math.shapes.VoxelShapes;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.ICollisionReader;
import net.minecraft.world.World;
import net.minecraft.world.border.WorldBorder;
import net.minecraft.world.server.ChunkManager.EntityTracker;
import net.minecraft.world.server.ServerWorld;
import tcb.spiderstpo.common.CollisionSmoothingUtil;
import tcb.spiderstpo.common.Config;
import tcb.spiderstpo.common.Matrix4f;
import tcb.spiderstpo.common.entity.movement.AdvancedClimberPathNavigator;
import tcb.spiderstpo.common.entity.movement.AdvancedGroundPathNavigator;
import tcb.spiderstpo.common.entity.movement.ClimberLookController;
import tcb.spiderstpo.common.entity.movement.ClimberMoveController;
import tcb.spiderstpo.common.entity.movement.IAdvancedPathFindingEntity;

public abstract class AbstractClimberEntity extends CreatureEntity implements IAdvancedPathFindingEntity {
	public boolean pathFinderDebugPreview;

	public static final ImmutableList<DataParameter<Optional<BlockPos>>> PATHING_TARGETS = ImmutableList.of(EntityDataManager.createKey(AbstractClimberEntity.class, DataSerializers.OPTIONAL_BLOCK_POS), EntityDataManager.createKey(AbstractClimberEntity.class, DataSerializers.OPTIONAL_BLOCK_POS), EntityDataManager.createKey(AbstractClimberEntity.class, DataSerializers.OPTIONAL_BLOCK_POS), EntityDataManager.createKey(AbstractClimberEntity.class, DataSerializers.OPTIONAL_BLOCK_POS), EntityDataManager.createKey(AbstractClimberEntity.class, DataSerializers.OPTIONAL_BLOCK_POS), EntityDataManager.createKey(AbstractClimberEntity.class, DataSerializers.OPTIONAL_BLOCK_POS), EntityDataManager.createKey(AbstractClimberEntity.class, DataSerializers.OPTIONAL_BLOCK_POS), EntityDataManager.createKey(AbstractClimberEntity.class, DataSerializers.OPTIONAL_BLOCK_POS));

	public static final DataParameter<Rotations> ROTATION_BODY = EntityDataManager.createKey(AbstractClimberEntity.class, DataSerializers.ROTATIONS);
	public static final DataParameter<Rotations> ROTATION_HEAD = EntityDataManager.createKey(AbstractClimberEntity.class, DataSerializers.ROTATIONS);

	public double prevStickingOffsetX, prevStickingOffsetY, prevStickingOffsetZ;
	public double stickingOffsetX, stickingOffsetY, stickingOffsetZ;

	public Vector3d orientationNormal = new Vector3d(0, 1, 0);
	public Vector3d prevOrientationNormal = new Vector3d(0, 1, 0);

	public float prevOrientationYawDelta;
	public float orientationYawDelta;

	protected double attachedStickingOffsetX, attachedStickingOffsetY, attachedStickingOffsetZ;
	protected Vector3d attachedOrientationNormal = new Vector3d(0, 1, 0);

	protected int attachedTicks = 5;

	protected Vector3d attachedSides = new Vector3d(0, 0, 0);
	protected Vector3d prevAttachedSides = new Vector3d(0, 0, 0);

	protected boolean canClimbInWater = false;
	protected boolean canClimbInLava = false;

	protected boolean isTravelingInFluid = false;

	protected float collisionsInclusionRange = 2.0f;
	protected float collisionsSmoothingRange = 1.25f;

	protected Orientation orientation;
	protected Pair<Direction, Vector3d> walkingSide;

	private float nextStepDistance, nextFlap;
	private Vector3d preWalkingPosition;

	public AbstractClimberEntity(EntityType<? extends AbstractClimberEntity> type, World world) {
		super(type, world);
		this.stepHeight = 0.1f;
		this.orientation = this.calculateOrientation(1);
		this.walkingSide = this.getWalkingSide();
		this.moveController = new ClimberMoveController(this);
		this.lookController = new ClimberLookController(this);
	}

	@Override
	protected void registerData() {
		super.registerData();

		this.pathFinderDebugPreview = Config.PATH_FINDER_DEBUG_PREVIEW.get();

		if(this.pathFinderDebugPreview) {
			for(DataParameter<Optional<BlockPos>> pathingTarget : PATHING_TARGETS) {
				this.dataManager.register(pathingTarget, Optional.empty());
			}
		}

		this.dataManager.register(ROTATION_BODY, new Rotations(0, 0, 0));

		this.dataManager.register(ROTATION_HEAD, new Rotations(0, 0, 0));
	}

	@Override
	protected PathNavigator createNavigator(World worldIn) {
		AdvancedGroundPathNavigator<AbstractClimberEntity> navigate = new AdvancedClimberPathNavigator<AbstractClimberEntity>(this, worldIn, false, true, true);
		navigate.setCanSwim(true);
		return navigate;
	}

	@Override
	public float getBridgePathingMalus(MobEntity entity, BlockPos pos, PathPoint fallPathPoint) {
		return -1.0f;
	}

	@Override
	public void onPathingObstructed(Direction facing) {

	}

	@Override
	public int getMaxFallHeight() {
		return 0;
	}

	public float getMovementSpeed() {
		ModifiableAttributeInstance attribute = this.getAttribute(Attributes.field_233821_d_); //MOVEMENT_SPEED
		return attribute != null ? (float) attribute.getValue() : 1.0f;
	}

	private static double calculateXOffset(AxisAlignedBB aabb, AxisAlignedBB other, double offsetX) {
		if(other.maxY > aabb.minY && other.minY < aabb.maxY && other.maxZ > aabb.minZ && other.minZ < aabb.maxZ) {
			if(offsetX > 0.0D && other.maxX <= aabb.minX) {
				double dx = aabb.minX - other.maxX;

				if(dx < offsetX) {
					offsetX = dx;
				}
			} else if(offsetX < 0.0D && other.minX >= aabb.maxX) {
				double dx = aabb.maxX - other.minX;

				if(dx > offsetX) {
					offsetX = dx;
				}
			}

			return offsetX;
		} else {
			return offsetX;
		}
	}

	private static double calculateYOffset(AxisAlignedBB aabb, AxisAlignedBB other, double offsetY) {
		if(other.maxX > aabb.minX && other.minX < aabb.maxX && other.maxZ > aabb.minZ && other.minZ < aabb.maxZ) {
			if(offsetY > 0.0D && other.maxY <= aabb.minY) {
				double dy = aabb.minY - other.maxY;

				if(dy < offsetY) {
					offsetY = dy;
				}
			} else if(offsetY < 0.0D && other.minY >= aabb.maxY) {
				double dy = aabb.maxY - other.minY;

				if(dy > offsetY) {
					offsetY = dy;
				}
			}

			return offsetY;
		} else {
			return offsetY;
		}
	}

	private static double calculateZOffset(AxisAlignedBB aabb, AxisAlignedBB other, double offsetZ) {
		if(other.maxX > aabb.minX && other.minX < aabb.maxX && other.maxY > aabb.minY && other.minY < aabb.maxY) {
			if(offsetZ > 0.0D && other.maxZ <= aabb.minZ) {
				double dz = aabb.minZ - other.maxZ;

				if(dz < offsetZ) {
					offsetZ = dz;
				}
			} else if(offsetZ < 0.0D && other.minZ >= aabb.maxZ) {
				double dz = aabb.maxZ - other.minZ;

				if(dz > offsetZ) {
					offsetZ = dz;
				}
			}

			return offsetZ;
		} else {
			return offsetZ;
		}
	}

	protected void updateWalkingSide() {
		Direction avoidPathingFacing = null;

		AxisAlignedBB entityBox = this.getBoundingBox();

		double closestFacingDst = Double.MAX_VALUE;
		Direction closestFacing = null;

		Vector3d weighting = new Vector3d(0, 0, 0);

		float stickingDistance = this.moveForward != 0 ? 1.5f : 0.1f;

		for(Direction facing : Direction.values()) {
			if(avoidPathingFacing == facing) {
				continue;
			}

			List<AxisAlignedBB> collisionBoxes = this.getCollisionBoxes(entityBox.grow(0.2f).expand(facing.getXOffset() * stickingDistance, facing.getYOffset() * stickingDistance, facing.getZOffset() * stickingDistance));

			double closestDst = Double.MAX_VALUE;

			for(AxisAlignedBB collisionBox : collisionBoxes) {
				switch(facing) {
				case EAST:
				case WEST:
					closestDst = Math.min(closestDst, Math.abs(calculateXOffset(entityBox, collisionBox, -facing.getXOffset() * stickingDistance)));
					break;
				case UP:
				case DOWN:
					closestDst = Math.min(closestDst, Math.abs(calculateYOffset(entityBox, collisionBox, -facing.getYOffset() * stickingDistance)));
					break;
				case NORTH:
				case SOUTH:
					closestDst = Math.min(closestDst, Math.abs(calculateZOffset(entityBox, collisionBox, -facing.getZOffset() * stickingDistance)));
					break;
				}
			}

			if(closestDst < closestFacingDst) {
				closestFacingDst = closestDst;
				closestFacing = facing;
			}

			if(closestDst < Double.MAX_VALUE) {
				weighting = weighting.add(new Vector3d(facing.getXOffset(), facing.getYOffset(), facing.getZOffset()).scale(1 - Math.min(closestDst, stickingDistance) / stickingDistance));
			}
		}

		if(closestFacing == null) {
			this.walkingSide = Pair.of(Direction.DOWN, new Vector3d(0, -1, 0));
		} else {
			this.walkingSide = Pair.of(closestFacing, weighting.normalize().add(0, -0.001f, 0).normalize());
		}
	}

	public Pair<Direction, Vector3d> getWalkingSide() {
		return this.walkingSide;
	}

	public static class Orientation {
		public final Vector3d normal, localZ, localY, localX;
		public final float componentZ, componentY, componentX, yaw, pitch;

		private Orientation(Vector3d normal, Vector3d localZ, Vector3d localY, Vector3d localX, float componentZ, float componentY, float componentX, float yaw, float pitch) {
			this.normal = normal;
			this.localZ = localZ;
			this.localY = localY;
			this.localX = localX;
			this.componentZ = componentZ;
			this.componentY = componentY;
			this.componentX = componentX;
			this.yaw = yaw;
			this.pitch = pitch;
		}

		public Vector3d getDirection(Vector3d local) {
			return this.localX.scale(local.x).add(this.localY.scale(local.y)).add(this.localZ.scale(local.z));
		}

		public Vector3d getDirection(float yaw, float pitch) {
			float cy = MathHelper.cos(yaw * 0.017453292F);
			float sy = MathHelper.sin(yaw * 0.017453292F);
			float cp = -MathHelper.cos(-pitch * 0.017453292F);
			float sp = MathHelper.sin(-pitch * 0.017453292F);
			return this.localX.scale(sy * cp).add(this.localY.scale(sp)).add(this.localZ.scale(cy * cp));
		}

		public Vector3d getLocal(Vector3d global) {
			return new Vector3d(this.localX.dotProduct(global), this.localY.dotProduct(global), this.localZ.dotProduct(global));
		}

		public Pair<Float, Float> getRotation(Vector3d global) {
			Vector3d local = this.getLocal(global);

			float yaw = (float) Math.toDegrees(MathHelper.atan2(local.x, local.z)) + 180.0f;
			float pitch = (float) -Math.toDegrees(MathHelper.atan2(local.y, MathHelper.sqrt(local.x * local.x + local.z * local.z)));

			return Pair.of(yaw, pitch);
		}
	}

	public Orientation getOrientation() {
		return this.orientation;
	}

	public Orientation calculateOrientation(float partialTicks) {
		Vector3d orientationNormal = this.prevOrientationNormal.add(this.orientationNormal.subtract(this.prevOrientationNormal).scale(partialTicks));

		Vector3d localZ = new Vector3d(0, 0, 1);
		Vector3d localY = new Vector3d(0, 1, 0);
		Vector3d localX = new Vector3d(1, 0, 0);

		float componentZ = (float) localZ.dotProduct(orientationNormal);
		float componentY;
		float componentX = (float) localX.dotProduct(orientationNormal);

		float yaw = (float) Math.toDegrees(MathHelper.atan2(componentX, componentZ));

		localZ = new Vector3d(Math.sin(Math.toRadians(yaw)), 0, Math.cos(Math.toRadians(yaw)));
		localY = new Vector3d(0, 1, 0);
		localX = new Vector3d(Math.sin(Math.toRadians(yaw - 90)), 0, Math.cos(Math.toRadians(yaw - 90)));

		componentZ = (float) localZ.dotProduct(orientationNormal);
		componentY = (float) localY.dotProduct(orientationNormal);
		componentX = (float) localX.dotProduct(orientationNormal);

		float pitch = (float) Math.toDegrees(MathHelper.atan2(MathHelper.sqrt(componentX * componentX + componentZ * componentZ), componentY));

		Matrix4f m = new Matrix4f();

		m.multiply(new Matrix4f((float) Math.toRadians(yaw), 0, 1, 0));
		m.multiply(new Matrix4f((float) Math.toRadians(pitch), 1, 0, 0));
		m.multiply(new Matrix4f((float) Math.toRadians((float) Math.signum(0.5f - componentY - componentZ - componentX) * yaw), 0, 1, 0));

		localZ = m.multiply(new Vector3d(0, 0, -1));
		localY = m.multiply(new Vector3d(0, 1, 0));
		localX = m.multiply(new Vector3d(1, 0, 0));

		return new Orientation(orientationNormal, localZ, localY, localX, componentZ, componentY, componentX, yaw, pitch);
	}

	@Override
	public void lookAt(Type anchor, Vector3d pos) {
		Vector3d dir = pos.subtract(this.getPositionVec());
		dir = this.getOrientation().getLocal(dir);
		super.lookAt(anchor, this.getPositionVec().add(dir));
	}

	@Override
	public void tick() {
		super.tick();

		if(!this.world.isRemote && this.world instanceof ServerWorld) {
			EntityTracker entityTracker = ((ServerWorld) this.world).getChunkProvider().chunkManager.entities.get(this.getEntityId());

			//Prevent premature syncing of position causing overly smoothed movement
			if(entityTracker != null && entityTracker.entry.updateCounter % entityTracker.entry.updateFrequency == 0) {
				Orientation orientation = this.getOrientation();

				Vector3d look = orientation.getDirection(this.rotationYaw, this.rotationPitch);
				this.dataManager.set(ROTATION_BODY, new Rotations((float) look.x, (float) look.y, (float) look.z));

				look = orientation.getDirection(this.rotationYawHead, 0.0f);
				this.dataManager.set(ROTATION_HEAD, new Rotations((float) look.x, (float) look.y, (float) look.z));

				if(this.pathFinderDebugPreview) {
					Path path = this.getNavigator().getPath();
					if(path != null) {
						int i = 0;
						for(DataParameter<Optional<BlockPos>> pathingTarget : PATHING_TARGETS) {
							if(path.getCurrentPathIndex() + i < path.getCurrentPathLength()) {
								PathPoint point = path.getPathPointFromIndex(path.getCurrentPathIndex() + i);
								this.dataManager.set(pathingTarget, Optional.of(new BlockPos(point.x, point.y, point.z)));
							} else {
								this.dataManager.set(pathingTarget, Optional.empty());
							}
							i++;
						}
					} else {
						for(DataParameter<Optional<BlockPos>> pathingTarget : PATHING_TARGETS) {
							this.dataManager.set(pathingTarget, Optional.empty());
						}
					}
				}
			}
		}
	}

	@Override
	public void livingTick() {
		this.updateWalkingSide();

		super.livingTick();
	}

	@Nullable
	public BlockPos getPathingTarget(int i) {
		if(this.pathFinderDebugPreview) {
			return this.dataManager.get(PATHING_TARGETS.get(i)).orElse(null);
		}
		return null;
	}

	public float getVerticalOffset(float partialTicks) {
		return 0.075f;
	}

	protected void forEachCollisonBox(AxisAlignedBB aabb, VoxelShapes.ILineConsumer action) {
		int minChunkX = ((MathHelper.floor(aabb.minX - 1.0E-7D) - 1) >> 4);
		int maxChunkX = ((MathHelper.floor(aabb.maxX + 1.0E-7D) + 1) >> 4);
		int minChunkZ = ((MathHelper.floor(aabb.minZ - 1.0E-7D) - 1) >> 4);
		int maxChunkZ = ((MathHelper.floor(aabb.maxZ + 1.0E-7D) + 1) >> 4);

		int width = maxChunkX - minChunkX + 1;
		int depth = maxChunkZ - minChunkZ + 1;

		IBlockReader[] blockReaderCache = new IBlockReader[width * depth];

		ICollisionReader collisionReader = this.world;

		for(int cx = minChunkX; cx <= maxChunkX; cx++) {
			for(int cz = minChunkZ; cz <= maxChunkZ; cz++) {
				blockReaderCache[(cx - minChunkX) + (cz - minChunkZ) * width] = collisionReader.getBlockReader(cx, cz);
			}
		}

		ICollisionReader cachedCollisionReader = new ICollisionReader() {
			@Override
			public TileEntity getTileEntity(BlockPos pos) {
				return collisionReader.getTileEntity(pos);
			}

			@Override
			public BlockState getBlockState(BlockPos pos) {
				return collisionReader.getBlockState(pos);
			}

			@Override
			public FluidState getFluidState(BlockPos pos) {
				return collisionReader.getFluidState(pos);
			}

			@Override
			public WorldBorder getWorldBorder() {
				return collisionReader.getWorldBorder();
			}

			@Override
			public Stream<VoxelShape> func_230318_c_(Entity entity, AxisAlignedBB aabb, Predicate<Entity> predicate) {
				return collisionReader.func_230318_c_(entity, aabb, predicate);
			}

			@Override
			public IBlockReader getBlockReader(int chunkX, int chunkZ) {
				return blockReaderCache[(chunkX - minChunkX) + (chunkZ - minChunkZ) * width];
			}
		};

		Stream<VoxelShape> shapes = StreamSupport.stream(new VoxelShapeSpliterator(cachedCollisionReader, this, aabb, this::canClimbOnBlock), false);

		shapes.forEach(shape -> shape.forEachBox(action));
	}

	protected List<AxisAlignedBB> getCollisionBoxes(AxisAlignedBB aabb) {
		List<AxisAlignedBB> boxes = new ArrayList<>();
		this.forEachCollisonBox(aabb, (minX, minY, minZ, maxX, maxY, maxZ) -> boxes.add(new AxisAlignedBB(minX, minY, minZ, maxX, maxY, maxZ)));
		return boxes;
	}

	protected boolean canClimbOnBlock(BlockState state, BlockPos pos) {
		return true;
	}

	protected float getBlockSlipperiness(BlockPos pos) {
		BlockState offsetState = this.world.getBlockState(pos);
		return offsetState.getBlock().getSlipperiness(offsetState, this.world, pos, this) * 0.91f;
	}

	protected void updateOffsetsAndOrientation() {
		Vector3d direction = this.getOrientation().getDirection(this.rotationYaw, this.rotationPitch);

		boolean isAttached = false;

		double baseStickingOffsetX = 0.0f;
		double baseStickingOffsetY = this.getVerticalOffset(1);
		double baseStickingOffsetZ = 0.0f;
		Vector3d baseOrientationNormal = new Vector3d(0, 1, 0);

		if(!this.isTravelingInFluid && this.onGround && this.getRidingEntity() == null) {
			Vector3d p = this.getPositionVec();

			Vector3d s = p.add(0, this.getHeight() * 0.5f, 0);
			AxisAlignedBB inclusionBox = new AxisAlignedBB(s.x, s.y, s.z, s.x, s.y, s.z).grow(this.collisionsInclusionRange);

			Pair<Vector3d, Vector3d> attachmentPoint = CollisionSmoothingUtil.findClosestPoint(consumer -> this.forEachCollisonBox(inclusionBox, consumer), s, this.orientationNormal.scale(-1), this.collisionsSmoothingRange, 1.0f, 0.001f, 20, 0.05f, s);

			AxisAlignedBB entityBox = this.getBoundingBox();

			if(attachmentPoint != null) {
				Vector3d attachmentPos = attachmentPoint.getLeft();

				double dx = Math.max(entityBox.minX - attachmentPos.x, attachmentPos.x - entityBox.maxX);
				double dy = Math.max(entityBox.minY - attachmentPos.y, attachmentPos.y - entityBox.maxY);
				double dz = Math.max(entityBox.minZ - attachmentPos.z, attachmentPos.z - entityBox.maxZ);

				if(Math.max(dx, Math.max(dy, dz)) < 0.5f) {
					isAttached = true;

					this.attachedStickingOffsetX = MathHelper.clamp(attachmentPos.x - p.x, -this.getWidth() / 2, this.getWidth() / 2);
					this.attachedStickingOffsetY = MathHelper.clamp(attachmentPos.y - p.y, 0, this.getHeight());
					this.attachedStickingOffsetZ = MathHelper.clamp(attachmentPos.z - p.z, -this.getWidth() / 2, this.getWidth() / 2);
					this.attachedOrientationNormal = attachmentPoint.getRight();
				}
			}
		}

		this.prevStickingOffsetX = this.stickingOffsetX;
		this.prevStickingOffsetY = this.stickingOffsetY;
		this.prevStickingOffsetZ = this.stickingOffsetZ;
		this.prevOrientationNormal = this.orientationNormal;

		float attachmentBlend = this.attachedTicks * 0.2f;

		this.stickingOffsetX = baseStickingOffsetX + (this.attachedStickingOffsetX - baseStickingOffsetX) * attachmentBlend;
		this.stickingOffsetY = baseStickingOffsetY + (this.attachedStickingOffsetY - baseStickingOffsetY) * attachmentBlend;
		this.stickingOffsetZ = baseStickingOffsetZ + (this.attachedStickingOffsetZ - baseStickingOffsetZ) * attachmentBlend;
		this.orientationNormal = baseOrientationNormal.add(this.attachedOrientationNormal.subtract(baseOrientationNormal).scale(attachmentBlend)).normalize();

		if(!isAttached) {
			this.attachedTicks = Math.max(0, this.attachedTicks - 1);
		} else {
			this.attachedTicks = Math.min(5, this.attachedTicks + 1);
		}

		this.orientation = this.calculateOrientation(1);

		Pair<Float, Float> newRotations = this.getOrientation().getRotation(direction);

		float yawDelta = newRotations.getLeft() - this.rotationYaw;
		float pitchDelta = newRotations.getRight() - this.rotationPitch;

		this.prevOrientationYawDelta = this.orientationYawDelta;
		this.orientationYawDelta = yawDelta;

		this.rotationYaw = MathHelper.wrapDegrees(this.rotationYaw + yawDelta);
		this.prevRotationYaw = this.wrapAngleInRange(this.prevRotationYaw/* + yawDelta*/, this.rotationYaw);
		this.interpTargetYaw = MathHelper.wrapDegrees(this.interpTargetYaw + yawDelta);

		this.renderYawOffset = MathHelper.wrapDegrees(this.renderYawOffset + yawDelta);
		this.prevRenderYawOffset = this.wrapAngleInRange(this.prevRenderYawOffset/* + yawDelta*/, this.renderYawOffset);

		this.rotationYawHead = MathHelper.wrapDegrees(this.rotationYawHead + yawDelta);
		this.prevRotationYawHead = this.wrapAngleInRange(this.prevRotationYawHead/* + yawDelta*/, this.rotationYawHead);
		this.interpTargetHeadYaw = MathHelper.wrapDegrees(this.interpTargetHeadYaw + yawDelta);

		this.rotationPitch = MathHelper.wrapDegrees(this.rotationPitch + pitchDelta);
		this.prevRotationPitch = this.wrapAngleInRange(this.prevRotationPitch/* + pitchDelta*/, this.rotationPitch);
		this.interpTargetPitch = MathHelper.wrapDegrees(this.interpTargetPitch + pitchDelta);
	}

	private float wrapAngleInRange(float angle, float target) {
		while(target - angle < -180.0F) {
			angle -= 360.0F;
		}

		while(target - angle >= 180.0F) {
			angle += 360.0F;
		}

		return angle;
	}

	@Override
	public void setPositionAndRotation(double x, double y, double z, float yaw, float pitch) {
		super.setPositionAndRotation(x, y, z, yaw, pitch);
	}

	@Override
	public void setPositionAndRotationDirect(double x, double y, double z, float yaw, float pitch, int posRotationIncrements, boolean teleport) {
		super.setPositionAndRotationDirect(x, y, z, (float) this.interpTargetYaw, (float) this.interpTargetPitch, posRotationIncrements, teleport);
	}

	@Override
	public void setHeadRotation(float yaw, int rotationIncrements) {
		super.setHeadRotation((float) this.interpTargetHeadYaw, rotationIncrements);
	}

	@Override
	public void notifyDataManagerChange(DataParameter<?> key) {
		super.notifyDataManagerChange(key);

		if(ROTATION_BODY.equals(key)) {
			Rotations rotation = this.dataManager.get(ROTATION_BODY);
			Vector3d look = new Vector3d(rotation.getX(), rotation.getY(), rotation.getZ());

			Pair<Float, Float> rotations = this.getOrientation().getRotation(look);

			this.interpTargetYaw = rotations.getLeft();
			this.interpTargetPitch = rotations.getRight();
		} else if(ROTATION_HEAD.equals(key)) {
			Rotations rotation = this.dataManager.get(ROTATION_HEAD);
			Vector3d look = new Vector3d(rotation.getX(), rotation.getY(), rotation.getZ());

			Pair<Float, Float> rotations = this.getOrientation().getRotation(look);

			this.interpTargetHeadYaw = rotations.getLeft();
			this.interpTicksHead = 3;
		}
	}

	public Vector3d getStickingForce(Pair<Direction, Vector3d> walkingSide) {
		if(!this.hasNoGravity()) { //TODO Forge gravity attribute
			return walkingSide.getRight().scale(0.08f);
		}

		return new Vector3d(0, 0, 0);
	}

	@Override
	public void travel(Vector3d relative) {
		boolean canTravel = this.isServerWorld() || this.canPassengerSteer();

		this.isTravelingInFluid = false;

		FluidState fluidState = this.world.getFluidState(this.func_233580_cy_());

		if(!this.canClimbInWater && this.isInWater() && this.func_241208_cS_() && !this.func_230285_a_(fluidState.getFluid())) {
			this.isTravelingInFluid = true;

			if(canTravel) {
				super.travel(relative);
			}
		} else if(!this.canClimbInLava && this.isInLava() && this.func_241208_cS_() && !this.func_230285_a_(fluidState.getFluid())) {
			this.isTravelingInFluid = true;

			if(canTravel) {
				super.travel(relative);
			}
		} else if(canTravel) {
			this.travelOnGround(relative);
		}

		if(!canTravel) {
			this.func_233629_a_(this, true);
		}

		this.updateOffsetsAndOrientation();
	}

	protected void travelOnGround(Vector3d relative) {
		Orientation orientation = this.getOrientation();

		Vector3d forwardVector = orientation.getDirection(this.rotationYaw, 0);
		Vector3d upVector = orientation.getDirection(this.rotationYaw, -90);

		Pair<Direction, Vector3d> walkingSide = this.getWalkingSide();

		Vector3d stickingForce = this.getStickingForce(walkingSide);

		float forward = (float) relative.z;

		if(forward != 0) {
			float slipperiness = 0.91f;

			if(this.onGround) {
				BlockPos offsetPos = new BlockPos(this.getPositionVec()).offset(walkingSide.getLeft());
				slipperiness = this.getBlockSlipperiness(offsetPos);
			}

			float friction = forward * 0.16277136F / (slipperiness * slipperiness * slipperiness);

			float f = forward * forward;
			if(f >= 1.0E-4F) {
				f = Math.max(MathHelper.sqrt(f), 1.0f);
				f = friction / f;

				Vector3d forwardOffset = new Vector3d(forwardVector.x * forward * f, forwardVector.y * forward * f, forwardVector.z * forward * f);

				double px = this.getPosX();
				double py = this.getPosY();
				double pz = this.getPosZ();
				Vector3d motion = this.getMotion();
				AxisAlignedBB aabb = this.getBoundingBox();

				//Probe actual movement vector
				this.move(MoverType.SELF, forwardOffset);

				Vector3d movementDir = new Vector3d(this.getPosX() - px, this.getPosY() - py, this.getPosZ() - pz).normalize();

				this.setBoundingBox(aabb);
				this.resetPositionToBB();
				this.setMotion(motion);

				//Probe collision normal
				Vector3d probeVector = new Vector3d(Math.abs(movementDir.x) < 0.001D ? -Math.signum(upVector.x) : 0, Math.abs(movementDir.y) < 0.001D ? -Math.signum(upVector.y) : 0, Math.abs(movementDir.z) < 0.001D ? -Math.signum(upVector.z) : 0).normalize().scale(0.0001D);
				this.move(MoverType.SELF, probeVector);

				Vector3d collisionNormal = new Vector3d(Math.abs(this.getPosX() - px - probeVector.x) > 0.000001D ? Math.signum(-probeVector.x) : 0, Math.abs(this.getPosY() - py - probeVector.y) > 0.000001D ? Math.signum(-probeVector.y) : 0, Math.abs(this.getPosZ() - pz - probeVector.z) > 0.000001D ? Math.signum(-probeVector.z) : 0).normalize();

				this.setBoundingBox(aabb);
				this.resetPositionToBB();
				this.setMotion(motion);

				//Movement vector projected to surface
				Vector3d surfaceMovementDir = movementDir.subtract(collisionNormal.scale(collisionNormal.dotProduct(movementDir))).normalize();

				boolean isInnerCorner = Math.abs(collisionNormal.x) + Math.abs(collisionNormal.y) + Math.abs(collisionNormal.z) > 1.0001f;

				//Only project movement vector to surface if not moving across inner corner, otherwise it'd get stuck in the corner
				if(!isInnerCorner) {
					movementDir = surfaceMovementDir;
				}

				//Nullify sticking force along movement vector projected to surface
				stickingForce = stickingForce.subtract(surfaceMovementDir.scale(surfaceMovementDir.normalize().dotProduct(stickingForce)));

				float moveSpeed = forward * f;
				this.setMotion(this.getMotion().add(movementDir.scale(moveSpeed)));
			}
		}

		this.setMotion(this.getMotion().add(stickingForce));

		double px = this.getPosX();
		double py = this.getPosY();
		double pz = this.getPosZ();
		Vector3d motion = this.getMotion();

		this.move(MoverType.SELF, motion);

		this.prevAttachedSides = this.attachedSides;
		this.attachedSides = new Vector3d(Math.abs(this.getPosX() - px - motion.x) > 0.001D ? -Math.signum(motion.x) : 0, Math.abs(this.getPosY() - py - motion.y) > 0.001D ? -Math.signum(motion.y) : 0, Math.abs(this.getPosZ() - pz - motion.z) > 0.001D ? -Math.signum(motion.z) : 0);

		float slipperiness = 0.91f;

		if(this.onGround) {
			this.fallDistance = 0;

			BlockPos offsetPos = new BlockPos(this.getPositionVec()).offset(walkingSide.getLeft());
			slipperiness = this.getBlockSlipperiness(offsetPos);
		}

		motion = this.getMotion();
		Vector3d orthogonalMotion = upVector.scale(upVector.dotProduct(motion));
		Vector3d tangentialMotion = motion.subtract(orthogonalMotion);

		this.setMotion(tangentialMotion.x * slipperiness + orthogonalMotion.x * 0.98f, tangentialMotion.y * slipperiness + orthogonalMotion.y * 0.98f, tangentialMotion.z * slipperiness + orthogonalMotion.z * 0.98f);

		boolean detachedX = this.attachedSides.x != this.prevAttachedSides.x && Math.abs(this.attachedSides.x) < 0.001D;
		boolean detachedY = this.attachedSides.y != this.prevAttachedSides.y && Math.abs(this.attachedSides.y) < 0.001D;
		boolean detachedZ = this.attachedSides.z != this.prevAttachedSides.z && Math.abs(this.attachedSides.z) < 0.001D;

		if(detachedX || detachedY || detachedZ) {
			float stepHeight = this.stepHeight;
			this.stepHeight = 0;

			boolean prevOnGround = this.onGround;
			boolean prevCollidedHorizontally = this.collidedHorizontally;
			boolean prevCollidedVertically = this.collidedVertically;

			//Offset so that AABB is moved above the new surface
			this.move(MoverType.SELF, new Vector3d(detachedX ? -this.prevAttachedSides.x * 0.25f : 0, detachedY ? -this.prevAttachedSides.y * 0.25f : 0, detachedZ ? -this.prevAttachedSides.z * 0.25f : 0));

			Vector3d axis = this.prevAttachedSides.normalize();
			Vector3d attachVector = upVector.scale(-1);
			attachVector = attachVector.subtract(axis.scale(axis.dotProduct(attachVector)));

			if(Math.abs(attachVector.x) > Math.abs(attachVector.y) && Math.abs(attachVector.x) > Math.abs(attachVector.z)) {
				attachVector = new Vector3d(Math.signum(attachVector.x), 0, 0);
			} else if(Math.abs(attachVector.y) > Math.abs(attachVector.z)) {
				attachVector = new Vector3d(0, Math.signum(attachVector.y), 0);
			} else {
				attachVector = new Vector3d(0, 0, Math.signum(attachVector.z));
			}

			double attachDst = motion.length() + 0.1f;

			AxisAlignedBB aabb = this.getBoundingBox();
			motion = this.getMotion();

			//Offset AABB towards new surface until it touches
			for(int i = 0; i < 2 && !this.onGround; i++) {
				this.move(MoverType.SELF, attachVector.scale(attachDst));
			}

			this.stepHeight = stepHeight;

			//Attaching failed, fall back to previous position
			if(!this.onGround) {
				this.setBoundingBox(aabb);
				this.resetPositionToBB();
				this.setMotion(motion);
				this.onGround = prevOnGround;
				this.collidedHorizontally = prevCollidedHorizontally;
				this.collidedVertically = prevCollidedVertically;
			} else {
				this.setMotion(Vector3d.ZERO);
			}
		}

		this.func_233629_a_(this, true);
	}

	@Override
	public void func_233629_a_(LivingEntity entity, boolean includeY) {
		entity.prevLimbSwingAmount = entity.limbSwingAmount;
		double dx = entity.getPosX() - entity.prevPosX;
		double dy = includeY ? entity.getPosY() - entity.prevPosY : 0.0D;
		double dz = entity.getPosZ() - entity.prevPosZ;
		float f = MathHelper.sqrt(dx * dx + dy * dy + dz * dz) * 4.0F;
		if(f > 1.0F) {
			f = 1.0F;
		}

		entity.limbSwingAmount += (f - entity.limbSwingAmount) * 0.4F;
		entity.limbSwing += entity.limbSwingAmount;
	}

	@Override
	public void move(MoverType type, Vector3d pos) {
		this.preWalkingPosition = this.getPositionVec();

		double py = this.getPosY();

		super.move(type, pos);

		if(Math.abs(this.getPosY() - py - pos.y) > 0.000001D) {
			this.setMotion(this.getMotion().mul(1, 0, 1));
		}

		this.onGround |= this.collidedHorizontally || this.collidedVertically;
	}

	@Override
	protected BlockPos getOnPosition() {
		float verticalOffset = this.getVerticalOffset(1);

		int x = MathHelper.floor(this.getPosX() + this.stickingOffsetX - (float) this.orientationNormal.x * (verticalOffset + 0.2f));
		int y = MathHelper.floor(this.getPosY() + this.stickingOffsetY - (float) this.orientationNormal.y * (verticalOffset + 0.2f));
		int z = MathHelper.floor(this.getPosZ() + this.stickingOffsetZ - (float) this.orientationNormal.z * (verticalOffset + 0.2f));
		BlockPos pos = new BlockPos(x, y, z);

		if(this.world.isAirBlock(pos) && this.orientationNormal.y < 0.0f) {
			BlockPos posDown = pos.down();
			BlockState stateDown = this.world.getBlockState(posDown);

			if(stateDown.collisionExtendsVertically(this.world, posDown, this)) {
				return posDown;
			}
		}

		return pos;
	}

	@Override
	protected final boolean canTriggerWalking() {
		if(this.preWalkingPosition != null && this.canActuallyTriggerWalking() && !this.isPassenger()) {
			Vector3d moved = this.getPositionVec().subtract(this.preWalkingPosition);
			this.preWalkingPosition = null;

			BlockPos pos = this.getOnPosition();
			BlockState state = this.world.getBlockState(pos);

			double dx = moved.x;
			double dy = moved.y;
			double dz = moved.z;

			Vector3d tangentialMovement = moved.subtract(this.orientationNormal.scale(this.orientationNormal.dotProduct(moved)));

			this.distanceWalkedModified = (float) ((double) this.distanceWalkedModified + tangentialMovement.length() * 0.6D);

			this.distanceWalkedOnStepModified = (float) ((double) this.distanceWalkedOnStepModified + (double) MathHelper.sqrt(dx * dx + dy * dy + dz * dz) * 0.6D);

			if(this.distanceWalkedOnStepModified > this.nextStepDistance && !state.isAir(this.world, pos)) {
				this.nextStepDistance = this.determineNextStepDistance();

				if(this.isInWater()) {
					Entity controller = this.isBeingRidden() && this.getControllingPassenger() != null ? this.getControllingPassenger() : this;

					float multiplier = controller == this ? 0.35F : 0.4F;

					Vector3d motion = controller.getMotion();

					float swimStrength = MathHelper.sqrt(motion.x * motion.x * (double) 0.2F + motion.y * motion.y + motion.z * motion.z * 0.2F) * multiplier;
					if(swimStrength > 1.0F) {
						swimStrength = 1.0F;
					}

					this.playSwimSound(swimStrength);
				} else {
					this.playStepSound(pos, state);
				}
			} else if(this.distanceWalkedOnStepModified > this.nextFlap && this.makeFlySound() && state.isAir(this.world, pos)) {
				this.nextFlap = this.playFlySound(this.distanceWalkedOnStepModified);
			}
		}

		return false;
	}

	protected boolean canActuallyTriggerWalking() {
		return true;
	}
}
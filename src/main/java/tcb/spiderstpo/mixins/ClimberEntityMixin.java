package tcb.spiderstpo.mixins;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Spliterators.AbstractSpliterator;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.annotation.Nullable;

import org.apache.commons.lang3.tuple.Pair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.google.common.collect.ImmutableList;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.command.arguments.EntityAnchorArgument.Type;
import net.minecraft.entity.CreatureEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.MoverType;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.monster.SpiderEntity;
import net.minecraft.fluid.IFluidState;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.pathfinding.Path;
import net.minecraft.pathfinding.PathNavigator;
import net.minecraft.pathfinding.PathPoint;
import net.minecraft.potion.Effects;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.CubeCoordinateIterator;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Rotations;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.shapes.IBooleanFunction;
import net.minecraft.util.math.shapes.ISelectionContext;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.shapes.VoxelShapes;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.ICollisionReader;
import net.minecraft.world.World;
import net.minecraft.world.border.WorldBorder;
import net.minecraft.world.server.ChunkManager.EntityTracker;
import net.minecraft.world.server.ServerWorld;
import tcb.spiderstpo.common.CollisionSmoothingUtil;
import tcb.spiderstpo.common.Matrix4f;
import tcb.spiderstpo.common.entity.mob.IClimberEntity;
import tcb.spiderstpo.common.entity.mob.IEntityMovementHook;
import tcb.spiderstpo.common.entity.mob.ILivingEntityDataManagerHook;
import tcb.spiderstpo.common.entity.mob.ILivingEntityLookAtHook;
import tcb.spiderstpo.common.entity.mob.ILivingEntityRotationHook;
import tcb.spiderstpo.common.entity.mob.ILivingEntityTravelHook;
import tcb.spiderstpo.common.entity.mob.IMobEntityLivingTickHook;
import tcb.spiderstpo.common.entity.mob.IMobEntityTickHook;
import tcb.spiderstpo.common.entity.mob.Orientation;
import tcb.spiderstpo.common.entity.movement.AdvancedClimberPathNavigator;
import tcb.spiderstpo.common.entity.movement.AdvancedGroundPathNavigator;
import tcb.spiderstpo.common.entity.movement.ClimberLookController;
import tcb.spiderstpo.common.entity.movement.ClimberMoveController;

@Mixin(value = { SpiderEntity.class })
public abstract class ClimberEntityMixin extends CreatureEntity implements IClimberEntity, IMobEntityLivingTickHook, ILivingEntityLookAtHook, IMobEntityTickHook, ILivingEntityRotationHook, ILivingEntityDataManagerHook, ILivingEntityTravelHook, IEntityMovementHook {

	//Copy from LivingEntity
	private static final UUID SLOW_FALLING_ID = UUID.fromString("A5B6CF2A-2F7C-31EF-9022-7C3E7D5E6ABA");
	private static final AttributeModifier SLOW_FALLING = new AttributeModifier(SLOW_FALLING_ID, "Slow falling acceleration reduction", -0.07, AttributeModifier.Operation.ADDITION);

	private static final ImmutableList<DataParameter<Optional<BlockPos>>> PATHING_TARGETS;

	private static final DataParameter<Rotations> ROTATION_BODY;
	private static final DataParameter<Rotations> ROTATION_HEAD;

	static {
		@SuppressWarnings("unchecked")
		Class<Entity> cls = (Class<Entity>) MethodHandles.lookup().lookupClass();

		ImmutableList.Builder<DataParameter<Optional<BlockPos>>> pathingTargets = ImmutableList.builder();
		for(int i = 0; i < 8; i++) {
			pathingTargets.add(EntityDataManager.createKey(cls, DataSerializers.OPTIONAL_BLOCK_POS));
		}
		PATHING_TARGETS = pathingTargets.build();

		ROTATION_BODY = EntityDataManager.createKey(cls, DataSerializers.ROTATIONS);
		ROTATION_HEAD = EntityDataManager.createKey(cls, DataSerializers.ROTATIONS);
	}

	private double prevAttachmentOffsetX, prevAttachmentOffsetY, prevAttachmentOffsetZ;
	private double attachmentOffsetX, attachmentOffsetY, attachmentOffsetZ;

	private Vec3d attachmentNormal = new Vec3d(0, 1, 0);
	private Vec3d prevAttachmentNormal = new Vec3d(0, 1, 0);

	private float prevOrientationYawDelta;
	private float orientationYawDelta;

	private double lastAttachmentOffsetX, lastAttachmentOffsetY, lastAttachmentOffsetZ;
	private Vec3d lastAttachmentOrientationNormal = new Vec3d(0, 1, 0);

	private int attachedTicks = 5;

	private Vec3d attachedSides = new Vec3d(0, 0, 0);
	private Vec3d prevAttachedSides = new Vec3d(0, 0, 0);

	private boolean canClimbInWater = false;
	private boolean canClimbInLava = false;

	private boolean isTravelingInFluid = false;

	private float collisionsInclusionRange = 2.0f;
	private float collisionsSmoothingRange = 1.25f;

	private Orientation orientation;
	private Pair<Direction, Vec3d> groundDirecton;

	private Orientation renderOrientation;

	private float nextStepDistance, nextFlap;
	private Vec3d preWalkingPosition;

	private double preMoveY;

	private ClimberEntityMixin(EntityType<? extends CreatureEntity> type, World worldIn) {
		super(type, worldIn);
	}

	@Inject(method = "<init>*", at = @At("RETURN"))
	private void onConstructed(CallbackInfo ci) {
		this.stepHeight = 0.1f;
		this.orientation = this.calculateOrientation(1);
		this.groundDirecton = this.getGroundDirection();
		this.moveController = new ClimberMoveController<>(this);
		this.lookController = new ClimberLookController<>(this);
	}

	@Inject(method = "registerData()V", at = @At("RETURN"))
	private void onRegisterData(CallbackInfo ci) {
		if(this.shouldTrackPathingTargets()) {
			for(DataParameter<Optional<BlockPos>> pathingTarget : PATHING_TARGETS) {
				this.dataManager.register(pathingTarget, Optional.empty());
			}
		}

		this.dataManager.register(ROTATION_BODY, new Rotations(0, 0, 0));

		this.dataManager.register(ROTATION_HEAD, new Rotations(0, 0, 0));
	}

	@Inject(method = "createNavigator(Lnet/minecraft/world/World;)Lnet/minecraft/pathfinding/PathNavigator;", at = @At("HEAD"), cancellable = true)
	private void onCreateNavigator(World world, CallbackInfoReturnable<PathNavigator> ci) {
		AdvancedGroundPathNavigator<ClimberEntityMixin> navigate = new AdvancedClimberPathNavigator<ClimberEntityMixin>(this, world, false, true, true);
		navigate.setCanSwim(true);
		ci.setReturnValue(navigate);
	}

	@Override
	public boolean canClimbInWater() {
		return this.canClimbInWater;
	}

	@Override
	public void setCanClimbInWater(boolean value) {
		this.canClimbInWater = value;
	}

	@Override
	public boolean canClimbInLava() {
		return this.canClimbInLava;
	}

	@Override
	public void setCanClimbInLava(boolean value) {
		this.canClimbInLava = value;
	}

	@Override
	public float getCollisionsInclusionRange() {
		return this.collisionsInclusionRange;
	}

	@Override
	public void setCollisionsInclusionRange(float range) {
		this.collisionsInclusionRange = range;
	}

	@Override
	public float getCollisionsSmoothingRange() {
		return this.collisionsSmoothingRange;
	}

	@Override
	public void setCollisionsSmoothingRange(float range) {
		this.collisionsSmoothingRange = range;
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

	@Override
	public float getMovementSpeed() {
		IAttributeInstance attribute = this.getAttribute(SharedMonsterAttributes.MOVEMENT_SPEED);
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

	private void updateWalkingSide() {
		Direction avoidPathingFacing = null;

		AxisAlignedBB entityBox = this.getBoundingBox();

		double closestFacingDst = Double.MAX_VALUE;
		Direction closestFacing = null;

		Vec3d weighting = new Vec3d(0, 0, 0);

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
				weighting = weighting.add(new Vec3d(facing.getXOffset(), facing.getYOffset(), facing.getZOffset()).scale(1 - Math.min(closestDst, stickingDistance) / stickingDistance));
			}
		}

		if(closestFacing == null) {
			this.groundDirecton = Pair.of(Direction.DOWN, new Vec3d(0, -1, 0));
		} else {
			this.groundDirecton = Pair.of(closestFacing, weighting.normalize().add(0, -0.001f, 0).normalize());
		}
	}

	@Override
	public Pair<Direction, Vec3d> getGroundDirection() {
		return this.groundDirecton;
	}

	@Override
	public Direction getGroundSide() {
		return this.groundDirecton.getKey();
	}

	@Override
	public Orientation getOrientation() {
		return this.orientation;
	}

	@Override
	public void setRenderOrientation(Orientation orientation) {
		this.renderOrientation = orientation;
	}

	@Override
	public Orientation getRenderOrientation() {
		return this.renderOrientation;
	}

	@Override
	public float getAttachmentOffset(Direction.Axis axis, float partialTicks) {
		switch(axis) {
		default:
		case X:
			return (float) (this.prevAttachmentOffsetX + (this.attachmentOffsetX - this.prevAttachmentOffsetX) * partialTicks);
		case Y:
			return (float) (this.prevAttachmentOffsetY + (this.attachmentOffsetY - this.prevAttachmentOffsetY) * partialTicks);
		case Z:
			return (float) (this.prevAttachmentOffsetZ + (this.attachmentOffsetZ - this.prevAttachmentOffsetZ) * partialTicks);
		}
	}

	@Override
	public Vec3d onLookAt(Type anchor, Vec3d vec) {
		Vec3d dir = vec.subtract(this.getPositionVec());
		dir = this.getOrientation().getLocal(dir);
		return dir;
	}

	@Override
	public void onTick() {
		if(!this.world.isRemote && this.world instanceof ServerWorld) {
			EntityTracker entityTracker = ((ServerWorld) this.world).getChunkProvider().chunkManager.entities.get(this.getEntityId());

			//Prevent premature syncing of position causing overly smoothed movement
			if(entityTracker != null && entityTracker.entry.updateCounter % entityTracker.entry.updateFrequency == 0) {
				Orientation orientation = this.getOrientation();

				Vec3d look = orientation.getGlobal(this.rotationYaw, this.rotationPitch);
				this.dataManager.set(ROTATION_BODY, new Rotations((float) look.x, (float) look.y, (float) look.z));

				look = orientation.getGlobal(this.rotationYawHead, 0.0f);
				this.dataManager.set(ROTATION_HEAD, new Rotations((float) look.x, (float) look.y, (float) look.z));

				if(this.shouldTrackPathingTargets()) {
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
	public void onLivingTick() {
		this.updateWalkingSide();
	}

	@Override
	public boolean isOnLadder() {
		return false;
	}

	@Override
	@Nullable
	public List<BlockPos> getTrackedPathingTargets() {
		if(this.shouldTrackPathingTargets()) {
			List<BlockPos> pathingTargets = new ArrayList<>(PATHING_TARGETS.size());

			for(DataParameter<Optional<BlockPos>> key : PATHING_TARGETS) {
				BlockPos pos = this.dataManager.get(key).orElse(null);

				if(pos != null) {
					pathingTargets.add(pos);
				}
			}

			return pathingTargets;
		}

		return null;
	}

	@Override
	public float getVerticalOffset(float partialTicks) {
		return 0.075f;
	}

	private void forEachCollisonBox(AxisAlignedBB aabb, VoxelShapes.ILineConsumer action) {
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
			public IFluidState getFluidState(BlockPos pos) {
				return collisionReader.getFluidState(pos);
			}

			@Override
			public WorldBorder getWorldBorder() {
				return collisionReader.getWorldBorder();
			}

			@Override
			public IBlockReader getBlockReader(int chunkX, int chunkZ) {
				return blockReaderCache[(chunkX - minChunkX) + (chunkZ - minChunkZ) * width];
			}
		};

		Stream<VoxelShape> shapes = this.getCollisionShapes(cachedCollisionReader, this, aabb, this::canClimbOnBlock);

		shapes.forEach(shape -> shape.forEachBox(action));
	}

	private Stream<VoxelShape> getCollisionShapes(final ICollisionReader collisionReader, @Nullable final Entity entity, AxisAlignedBB aabb, BiPredicate<BlockState, BlockPos> predicate) {
		int minX = MathHelper.floor(aabb.minX - 1.0E-7D) - 1;
		int maxX = MathHelper.floor(aabb.maxX + 1.0E-7D) + 1;
		int minY = MathHelper.floor(aabb.minY - 1.0E-7D) - 1;
		int maxY = MathHelper.floor(aabb.maxY + 1.0E-7D) + 1;
		int minZ = MathHelper.floor(aabb.minZ - 1.0E-7D) - 1;
		int maxZ = MathHelper.floor(aabb.maxZ + 1.0E-7D) + 1;

		final ISelectionContext selectionContext = entity == null ? ISelectionContext.dummy() : ISelectionContext.forEntity(entity);
		final CubeCoordinateIterator coordinateIterator = new CubeCoordinateIterator(minX, minY, minZ, maxX, maxY, maxZ);
		final BlockPos.Mutable pos = new BlockPos.Mutable();
		final VoxelShape aabbShape = VoxelShapes.create(aabb);

		return StreamSupport.stream(new AbstractSpliterator<VoxelShape>(Long.MAX_VALUE, 1280) {
			boolean isEntityNull = entity == null;

			@Override
			public boolean tryAdvance(Consumer<? super VoxelShape> consumer) {
				if(!this.isEntityNull) {
					this.isEntityNull = true;

					VoxelShape worldBorderShape = collisionReader.getWorldBorder().getShape();

					boolean flag = VoxelShapes.compare(worldBorderShape, VoxelShapes.create(entity.getBoundingBox().shrink(1.0E-7D)), IBooleanFunction.AND);
					boolean flag1 = VoxelShapes.compare(worldBorderShape, VoxelShapes.create(entity.getBoundingBox().grow(1.0E-7D)), IBooleanFunction.AND);

					if(!flag && flag1) {
						consumer.accept(worldBorderShape);
						return true;
					}
				}

				VoxelShape shape;
				while(true) {
					if(!coordinateIterator.hasNext()) {
						return false;
					}

					int x = coordinateIterator.getX();
					int y = coordinateIterator.getY();
					int z = coordinateIterator.getZ();
					int numBoundariesTouched = coordinateIterator.numBoundariesTouched();

					if(numBoundariesTouched != 3) {
						int cx = (x >> 4);
						int cz = (z >> 4);

						IBlockReader blockReader = collisionReader.getBlockReader(cx, cz);

						if(blockReader != null) {
							pos.setPos(x, y, z);

							BlockState state = blockReader.getBlockState(pos);

							if((numBoundariesTouched != 1 || state.isCollisionShapeLargerThanFullBlock()) && (numBoundariesTouched != 2 || state.getBlock() == Blocks.MOVING_PISTON) && predicate.test(state, pos)) {
								VoxelShape blockShape = state.getCollisionShape(collisionReader, pos, selectionContext);
								shape = blockShape.withOffset((double)x, (double)y, (double)z);
								if(VoxelShapes.compare(aabbShape, shape, IBooleanFunction.AND)) {
									break;
								}
							}
						}
					}
				}

				consumer.accept(shape);
				return true;
			}
		}, false);
	}

	private List<AxisAlignedBB> getCollisionBoxes(AxisAlignedBB aabb) {
		List<AxisAlignedBB> boxes = new ArrayList<>();
		this.forEachCollisonBox(aabb, (minX, minY, minZ, maxX, maxY, maxZ) -> boxes.add(new AxisAlignedBB(minX, minY, minZ, maxX, maxY, maxZ)));
		return boxes;
	}

	@Override
	public boolean canClimbOnBlock(BlockState state, BlockPos pos) {
		return true;
	}

	@Override
	public float getBlockSlipperiness(BlockPos pos) {
		BlockState offsetState = this.world.getBlockState(pos);
		return offsetState.getBlock().getSlipperiness(offsetState, this.world, pos, this) * 0.91f;
	}

	private void updateOffsetsAndOrientation() {
		Vec3d direction = this.getOrientation().getGlobal(this.rotationYaw, this.rotationPitch);

		boolean isAttached = false;

		double baseStickingOffsetX = 0.0f;
		double baseStickingOffsetY = this.getVerticalOffset(1);
		double baseStickingOffsetZ = 0.0f;
		Vec3d baseOrientationNormal = new Vec3d(0, 1, 0);

		if(!this.isTravelingInFluid && this.onGround && this.getRidingEntity() == null) {
			Vec3d p = this.getPositionVec();

			Vec3d s = p.add(0, this.getHeight() * 0.5f, 0);
			AxisAlignedBB inclusionBox = new AxisAlignedBB(s.x, s.y, s.z, s.x, s.y, s.z).grow(this.collisionsInclusionRange);

			Pair<Vec3d, Vec3d> attachmentPoint = CollisionSmoothingUtil.findClosestPoint(consumer -> this.forEachCollisonBox(inclusionBox, consumer), s, this.attachmentNormal.scale(-1), this.collisionsSmoothingRange, 1.0f, 0.001f, 20, 0.05f, s);

			AxisAlignedBB entityBox = this.getBoundingBox();

			if(attachmentPoint != null) {
				Vec3d attachmentPos = attachmentPoint.getLeft();

				double dx = Math.max(entityBox.minX - attachmentPos.x, attachmentPos.x - entityBox.maxX);
				double dy = Math.max(entityBox.minY - attachmentPos.y, attachmentPos.y - entityBox.maxY);
				double dz = Math.max(entityBox.minZ - attachmentPos.z, attachmentPos.z - entityBox.maxZ);

				if(Math.max(dx, Math.max(dy, dz)) < 0.5f) {
					isAttached = true;

					this.lastAttachmentOffsetX = MathHelper.clamp(attachmentPos.x - p.x, -this.getWidth() / 2, this.getWidth() / 2);
					this.lastAttachmentOffsetY = MathHelper.clamp(attachmentPos.y - p.y, 0, this.getHeight());
					this.lastAttachmentOffsetZ = MathHelper.clamp(attachmentPos.z - p.z, -this.getWidth() / 2, this.getWidth() / 2);
					this.lastAttachmentOrientationNormal = attachmentPoint.getRight();
				}
			}
		}

		this.prevAttachmentOffsetX = this.attachmentOffsetX;
		this.prevAttachmentOffsetY = this.attachmentOffsetY;
		this.prevAttachmentOffsetZ = this.attachmentOffsetZ;
		this.prevAttachmentNormal = this.attachmentNormal;

		float attachmentBlend = this.attachedTicks * 0.2f;

		this.attachmentOffsetX = baseStickingOffsetX + (this.lastAttachmentOffsetX - baseStickingOffsetX) * attachmentBlend;
		this.attachmentOffsetY = baseStickingOffsetY + (this.lastAttachmentOffsetY - baseStickingOffsetY) * attachmentBlend;
		this.attachmentOffsetZ = baseStickingOffsetZ + (this.lastAttachmentOffsetZ - baseStickingOffsetZ) * attachmentBlend;
		this.attachmentNormal = baseOrientationNormal.add(this.lastAttachmentOrientationNormal.subtract(baseOrientationNormal).scale(attachmentBlend)).normalize();

		if(!isAttached) {
			this.attachedTicks = Math.max(0, this.attachedTicks - 1);
		} else {
			this.attachedTicks = Math.min(5, this.attachedTicks + 1);
		}

		this.orientation = this.calculateOrientation(1);

		Pair<Float, Float> newRotations = this.getOrientation().getLocalRotation(direction);

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
	public Orientation calculateOrientation(float partialTicks) {
		Vec3d attachmentNormal = this.prevAttachmentNormal.add(this.attachmentNormal.subtract(this.prevAttachmentNormal).scale(partialTicks));

		Vec3d localZ = new Vec3d(0, 0, 1);
		Vec3d localY = new Vec3d(0, 1, 0);
		Vec3d localX = new Vec3d(1, 0, 0);

		float componentZ = (float) localZ.dotProduct(attachmentNormal);
		float componentY;
		float componentX = (float) localX.dotProduct(attachmentNormal);

		float yaw = (float) Math.toDegrees(MathHelper.atan2(componentX, componentZ));

		localZ = new Vec3d(Math.sin(Math.toRadians(yaw)), 0, Math.cos(Math.toRadians(yaw)));
		localY = new Vec3d(0, 1, 0);
		localX = new Vec3d(Math.sin(Math.toRadians(yaw - 90)), 0, Math.cos(Math.toRadians(yaw - 90)));

		componentZ = (float) localZ.dotProduct(attachmentNormal);
		componentY = (float) localY.dotProduct(attachmentNormal);
		componentX = (float) localX.dotProduct(attachmentNormal);

		float pitch = (float) Math.toDegrees(MathHelper.atan2(MathHelper.sqrt(componentX * componentX + componentZ * componentZ), componentY));

		Matrix4f m = new Matrix4f();

		m.multiply(new Matrix4f((float) Math.toRadians(yaw), 0, 1, 0));
		m.multiply(new Matrix4f((float) Math.toRadians(pitch), 1, 0, 0));
		m.multiply(new Matrix4f((float) Math.toRadians((float) Math.signum(0.5f - componentY - componentZ - componentX) * yaw), 0, 1, 0));

		localZ = m.multiply(new Vec3d(0, 0, -1));
		localY = m.multiply(new Vec3d(0, 1, 0));
		localX = m.multiply(new Vec3d(1, 0, 0));

		return new Orientation(attachmentNormal, localZ, localY, localX, componentZ, componentY, componentX, yaw, pitch);
	}

	@Override
	public float getTargetYaw(double x, double y, double z, float yaw, float pitch, int posRotationIncrements, boolean teleport) {
		return (float) this.interpTargetYaw;
	}

	@Override
	public float getTargetPitch(double x, double y, double z, float yaw, float pitch, int posRotationIncrements, boolean teleport) {
		return (float) this.interpTargetPitch;
	}

	@Override
	public float getTargetHeadYaw(float yaw, int rotationIncrements) {
		return (float) this.interpTargetHeadYaw;
	}

	@Override
	public void onNotifyDataManagerChange(DataParameter<?> key) {
		if(ROTATION_BODY.equals(key)) {
			Rotations rotation = this.dataManager.get(ROTATION_BODY);
			Vec3d look = new Vec3d(rotation.getX(), rotation.getY(), rotation.getZ());

			Pair<Float, Float> rotations = this.getOrientation().getLocalRotation(look);

			this.interpTargetYaw = rotations.getLeft();
			this.interpTargetPitch = rotations.getRight();
		} else if(ROTATION_HEAD.equals(key)) {
			Rotations rotation = this.dataManager.get(ROTATION_HEAD);
			Vec3d look = new Vec3d(rotation.getX(), rotation.getY(), rotation.getZ());

			Pair<Float, Float> rotations = this.getOrientation().getLocalRotation(look);

			this.interpTargetHeadYaw = rotations.getLeft();
			this.interpTicksHead = 3;
		}
	}

	private double getGravity() {
		if(this.hasNoGravity()) {
			return 0;
		}

		IAttributeInstance gravity = this.getAttribute(LivingEntity.ENTITY_GRAVITY);

		boolean isFalling = this.getMotion().y <= 0.0D;

		if(isFalling && this.isPotionActive(Effects.SLOW_FALLING)) {
			if(!gravity.hasModifier(SLOW_FALLING)) {
				gravity.applyModifier(SLOW_FALLING);
			}
		} else if(gravity.hasModifier(SLOW_FALLING)) {
			gravity.removeModifier(SLOW_FALLING);
		}

		return gravity.getValue();
	}

	private Vec3d getStickingForce(Pair<Direction, Vec3d> walkingSide) {
		double uprightness = Math.max(this.attachmentNormal.y, 0);
		double gravity = this.getGravity();
		double stickingForce = gravity * uprightness + 0.08D * (1 - uprightness);
		return walkingSide.getRight().scale(stickingForce);
	}

	@Override
	public boolean onTravel(Vec3d relative, boolean pre) {
		if(pre) {
			boolean canTravel = this.isServerWorld() || this.canPassengerSteer();

			this.isTravelingInFluid = false;

			if(!this.canClimbInWater && this.isInWater()) {
				this.isTravelingInFluid = true;

				if(canTravel) {
					return false;
				}
			} else if(!this.canClimbInLava && this.isInLava()) {
				this.isTravelingInFluid = true;

				if(canTravel) {
					return false;
				}
			} else if(canTravel) {
				this.travelOnGround(relative);
			}

			if(!canTravel) {
				this.applyLimbSwing();
			}

			this.updateOffsetsAndOrientation();
			return true;
		} else {
			this.updateOffsetsAndOrientation();
			return false;
		}
	}

	private void applyLimbSwing() {
		this.prevLimbSwingAmount = this.limbSwingAmount;
		double dx = this.getPosX() - this.prevPosX;
		double dy = this.getPosZ() - this.prevPosZ;
		double dz = this.getPosY() - this.prevPosY;
		float d = MathHelper.sqrt(dx * dx + dz * dz + dy * dy) * 4.0F;
		if(d > 1.0F) {
			d = 1.0F;
		}

		this.limbSwingAmount += (d - this.limbSwingAmount) * 0.4F;
		this.limbSwing += this.limbSwingAmount;
	}

	private void travelOnGround(Vec3d relative) {
		Orientation orientation = this.getOrientation();

		Vec3d forwardVector = orientation.getGlobal(this.rotationYaw, 0);
		Vec3d upVector = orientation.getGlobal(this.rotationYaw, -90);

		Pair<Direction, Vec3d> groundDirection = this.getGroundDirection();

		Vec3d stickingForce = this.getStickingForce(groundDirection);

		boolean isFalling = this.getMotion().y <= 0.0D;

		if(isFalling && this.isPotionActive(Effects.SLOW_FALLING)) {
			this.fallDistance = 0;
		}

		float forward = (float) relative.z;

		if(forward != 0) {
			float slipperiness = 0.91f;

			if(this.onGround) {
				BlockPos offsetPos = new BlockPos(this.getPositionVec()).offset(groundDirection.getLeft());
				slipperiness = this.getBlockSlipperiness(offsetPos);
			}

			float friction = forward * 0.16277136F / (slipperiness * slipperiness * slipperiness);

			float f = forward * forward;
			if(f >= 1.0E-4F) {
				f = Math.max(MathHelper.sqrt(f), 1.0f);
				f = friction / f;

				Vec3d forwardOffset = new Vec3d(forwardVector.x * forward * f, forwardVector.y * forward * f, forwardVector.z * forward * f);

				double px = this.getPosX();
				double py = this.getPosY();
				double pz = this.getPosZ();
				Vec3d motion = this.getMotion();
				AxisAlignedBB aabb = this.getBoundingBox();

				//Probe actual movement vector
				this.move(MoverType.SELF, forwardOffset);

				Vec3d movementDir = new Vec3d(this.getPosX() - px, this.getPosY() - py, this.getPosZ() - pz).normalize();

				this.setBoundingBox(aabb);
				this.resetPositionToBB();
				this.setMotion(motion);

				//Probe collision normal
				Vec3d probeVector = new Vec3d(Math.abs(movementDir.x) < 0.001D ? -Math.signum(upVector.x) : 0, Math.abs(movementDir.y) < 0.001D ? -Math.signum(upVector.y) : 0, Math.abs(movementDir.z) < 0.001D ? -Math.signum(upVector.z) : 0).normalize().scale(0.0001D);
				this.move(MoverType.SELF, probeVector);

				Vec3d collisionNormal = new Vec3d(Math.abs(this.getPosX() - px - probeVector.x) > 0.000001D ? Math.signum(-probeVector.x) : 0, Math.abs(this.getPosY() - py - probeVector.y) > 0.000001D ? Math.signum(-probeVector.y) : 0, Math.abs(this.getPosZ() - pz - probeVector.z) > 0.000001D ? Math.signum(-probeVector.z) : 0).normalize();

				this.setBoundingBox(aabb);
				this.resetPositionToBB();
				this.setMotion(motion);

				//Movement vector projected to surface
				Vec3d surfaceMovementDir = movementDir.subtract(collisionNormal.scale(collisionNormal.dotProduct(movementDir))).normalize();

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
		Vec3d motion = this.getMotion();

		this.move(MoverType.SELF, motion);

		this.prevAttachedSides = this.attachedSides;
		this.attachedSides = new Vec3d(Math.abs(this.getPosX() - px - motion.x) > 0.001D ? -Math.signum(motion.x) : 0, Math.abs(this.getPosY() - py - motion.y) > 0.001D ? -Math.signum(motion.y) : 0, Math.abs(this.getPosZ() - pz - motion.z) > 0.001D ? -Math.signum(motion.z) : 0);

		float slipperiness = 0.91f;

		if(this.onGround) {
			this.fallDistance = 0;

			BlockPos offsetPos = new BlockPos(this.getPositionVec()).offset(groundDirection.getLeft());
			slipperiness = this.getBlockSlipperiness(offsetPos);
		}

		motion = this.getMotion();
		Vec3d orthogonalMotion = upVector.scale(upVector.dotProduct(motion));
		Vec3d tangentialMotion = motion.subtract(orthogonalMotion);

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
			this.move(MoverType.SELF, new Vec3d(detachedX ? -this.prevAttachedSides.x * 0.25f : 0, detachedY ? -this.prevAttachedSides.y * 0.25f : 0, detachedZ ? -this.prevAttachedSides.z * 0.25f : 0));

			Vec3d axis = this.prevAttachedSides.normalize();
			Vec3d attachVector = upVector.scale(-1);
			attachVector = attachVector.subtract(axis.scale(axis.dotProduct(attachVector)));

			if(Math.abs(attachVector.x) > Math.abs(attachVector.y) && Math.abs(attachVector.x) > Math.abs(attachVector.z)) {
				attachVector = new Vec3d(Math.signum(attachVector.x), 0, 0);
			} else if(Math.abs(attachVector.y) > Math.abs(attachVector.z)) {
				attachVector = new Vec3d(0, Math.signum(attachVector.y), 0);
			} else {
				attachVector = new Vec3d(0, 0, Math.signum(attachVector.z));
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
				this.setMotion(Vec3d.ZERO);
			}
		}

		this.applyLimbSwing();
	}

	@Override
	public boolean onMove(MoverType type, Vec3d pos, boolean pre) {
		if(pre) {
			this.preWalkingPosition = this.getPositionVec();
			this.preMoveY = this.getPosY();
		} else {
			if(Math.abs(this.getPosY() - this.preMoveY - pos.y) > 0.000001D) {
				this.setMotion(this.getMotion().mul(1, 0, 1));
			}

			this.onGround |= this.collidedHorizontally || this.collidedVertically;
		}

		return false;
	}

	@Override
	public BlockPos getAdjustedOnPosition(BlockPos onPosition) {
		float verticalOffset = this.getVerticalOffset(1);

		int x = MathHelper.floor(this.getPosX() + this.attachmentOffsetX - (float) this.attachmentNormal.x * (verticalOffset + 0.2f));
		int y = MathHelper.floor(this.getPosY() + this.attachmentOffsetY - (float) this.attachmentNormal.y * (verticalOffset + 0.2f));
		int z = MathHelper.floor(this.getPosZ() + this.attachmentOffsetZ - (float) this.attachmentNormal.z * (verticalOffset + 0.2f));
		BlockPos pos = new BlockPos(x, y, z);

		if(this.world.isAirBlock(pos) && this.attachmentNormal.y < 0.0f) {
			BlockPos posDown = pos.down();
			BlockState stateDown = this.world.getBlockState(posDown);

			if(stateDown.collisionExtendsVertically(this.world, posDown, this)) {
				return posDown;
			}
		}

		return pos;
	}

	@Override
	public boolean getAdjustedCanTriggerWalking(boolean canTriggerWalking) {
		if(this.preWalkingPosition != null && this.canClimberTriggerWalking() && !this.isPassenger()) {
			Vec3d moved = this.getPositionVec().subtract(this.preWalkingPosition);
			this.preWalkingPosition = null;

			BlockPos pos = this.getOnPosition();
			BlockState state = this.world.getBlockState(pos);

			double dx = moved.x;
			double dy = moved.y;
			double dz = moved.z;

			Vec3d tangentialMovement = moved.subtract(this.attachmentNormal.scale(this.attachmentNormal.dotProduct(moved)));

			this.distanceWalkedModified = (float) ((double) this.distanceWalkedModified + tangentialMovement.length() * 0.6D);

			this.distanceWalkedOnStepModified = (float) ((double) this.distanceWalkedOnStepModified + (double) MathHelper.sqrt(dx * dx + dy * dy + dz * dz) * 0.6D);

			if(this.distanceWalkedOnStepModified > this.nextStepDistance && !state.isAir(this.world, pos)) {
				this.nextStepDistance = this.determineNextStepDistance();

				if(this.isInWater()) {
					Entity controller = this.isBeingRidden() && this.getControllingPassenger() != null ? this.getControllingPassenger() : this;

					float multiplier = controller == this ? 0.35F : 0.4F;

					Vec3d motion = controller.getMotion();

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

	@Override
	public boolean canClimberTriggerWalking() {
		return true;
	}
}

package tcb.spiderstpo.mixins;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
import net.minecraft.command.arguments.EntityAnchorArgument.Type;
import net.minecraft.entity.CreatureEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.MoverType;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.ai.attributes.ModifiableAttributeInstance;
import net.minecraft.entity.monster.SpiderEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.pathfinding.Path;
import net.minecraft.pathfinding.PathNavigator;
import net.minecraft.pathfinding.PathPoint;
import net.minecraft.potion.Effects;
import net.minecraft.util.Direction;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Rotations;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.shapes.VoxelShapeSpliterator;
import net.minecraft.util.math.shapes.VoxelShapes;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.ICollisionReader;
import net.minecraft.world.World;
import net.minecraft.world.server.ChunkManager.EntityTracker;
import net.minecraft.world.server.ServerWorld;
import tcb.spiderstpo.common.CachedCollisionReader;
import tcb.spiderstpo.common.CollisionSmoothingUtil;
import tcb.spiderstpo.common.Matrix4f;
import tcb.spiderstpo.common.entity.mob.IClimberEntity;
import tcb.spiderstpo.common.entity.mob.IEntityMovementHook;
import tcb.spiderstpo.common.entity.mob.IEntityReadWriteHook;
import tcb.spiderstpo.common.entity.mob.IEntityRegisterDataHook;
import tcb.spiderstpo.common.entity.mob.ILivingEntityDataManagerHook;
import tcb.spiderstpo.common.entity.mob.ILivingEntityJumpHook;
import tcb.spiderstpo.common.entity.mob.ILivingEntityLookAtHook;
import tcb.spiderstpo.common.entity.mob.ILivingEntityRotationHook;
import tcb.spiderstpo.common.entity.mob.ILivingEntityTravelHook;
import tcb.spiderstpo.common.entity.mob.IMobEntityLivingTickHook;
import tcb.spiderstpo.common.entity.mob.IMobEntityNavigatorHook;
import tcb.spiderstpo.common.entity.mob.IMobEntityTickHook;
import tcb.spiderstpo.common.entity.mob.Orientation;
import tcb.spiderstpo.common.entity.mob.PathingTarget;
import tcb.spiderstpo.common.entity.movement.AdvancedClimberPathNavigator;
import tcb.spiderstpo.common.entity.movement.ClimberJumpController;
import tcb.spiderstpo.common.entity.movement.ClimberLookController;
import tcb.spiderstpo.common.entity.movement.ClimberMoveController;
import tcb.spiderstpo.common.entity.movement.DirectionalPathPoint;

@Mixin(value = { SpiderEntity.class })
public abstract class ClimberEntityMixin extends CreatureEntity implements IClimberEntity, IMobEntityLivingTickHook, ILivingEntityLookAtHook, IMobEntityTickHook, ILivingEntityRotationHook, ILivingEntityDataManagerHook, ILivingEntityTravelHook, IEntityMovementHook, IEntityReadWriteHook, IEntityRegisterDataHook, ILivingEntityJumpHook, IMobEntityNavigatorHook {

	//Copy from LivingEntity
	private static final UUID SLOW_FALLING_ID = UUID.fromString("A5B6CF2A-2F7C-31EF-9022-7C3E7D5E6ABA");
	private static final AttributeModifier SLOW_FALLING = new AttributeModifier(SLOW_FALLING_ID, "Slow falling acceleration reduction", -0.07, AttributeModifier.Operation.ADDITION);

	private static final DataParameter<Float> MOVEMENT_TARGET_X;
	private static final DataParameter<Float> MOVEMENT_TARGET_Y;
	private static final DataParameter<Float> MOVEMENT_TARGET_Z;
	private static final ImmutableList<DataParameter<Optional<BlockPos>>> PATHING_TARGETS;
	private static final ImmutableList<DataParameter<Direction>> PATHING_SIDES;

	private static final DataParameter<Rotations> ROTATION_BODY;
	private static final DataParameter<Rotations> ROTATION_HEAD;

	static {
		@SuppressWarnings("unchecked")
		Class<Entity> cls = (Class<Entity>) MethodHandles.lookup().lookupClass();

		MOVEMENT_TARGET_X = EntityDataManager.createKey(cls, DataSerializers.FLOAT);
		MOVEMENT_TARGET_Y = EntityDataManager.createKey(cls, DataSerializers.FLOAT);
		MOVEMENT_TARGET_Z = EntityDataManager.createKey(cls, DataSerializers.FLOAT);

		ImmutableList.Builder<DataParameter<Optional<BlockPos>>> pathingTargets = ImmutableList.builder();
		ImmutableList.Builder<DataParameter<Direction>> pathingSides = ImmutableList.builder();
		for(int i = 0; i < 8; i++) {
			pathingTargets.add(EntityDataManager.createKey(cls, DataSerializers.OPTIONAL_BLOCK_POS));
			pathingSides.add(EntityDataManager.createKey(cls, DataSerializers.DIRECTION));
		}
		PATHING_TARGETS = pathingTargets.build();
		PATHING_SIDES = pathingSides.build();

		ROTATION_BODY = EntityDataManager.createKey(cls, DataSerializers.ROTATIONS);
		ROTATION_HEAD = EntityDataManager.createKey(cls, DataSerializers.ROTATIONS);
	}

	private double prevAttachmentOffsetX, prevAttachmentOffsetY, prevAttachmentOffsetZ;
	private double attachmentOffsetX, attachmentOffsetY, attachmentOffsetZ;

	private Vector3d attachmentNormal = new Vector3d(0, 1, 0);
	private Vector3d prevAttachmentNormal = new Vector3d(0, 1, 0);

	private float prevOrientationYawDelta;
	private float orientationYawDelta;

	private double lastAttachmentOffsetX, lastAttachmentOffsetY, lastAttachmentOffsetZ;
	private Vector3d lastAttachmentOrientationNormal = new Vector3d(0, 1, 0);

	private int attachedTicks = 5;

	private Vector3d attachedSides = new Vector3d(0, 0, 0);
	private Vector3d prevAttachedSides = new Vector3d(0, 0, 0);

	private boolean canClimbInWater = false;
	private boolean canClimbInLava = false;

	private boolean isClimbingDisabled = false;

	private float collisionsInclusionRange = 2.0f;
	private float collisionsSmoothingRange = 1.25f;

	private Orientation orientation;
	private Pair<Direction, Vector3d> groundDirecton;

	private Orientation renderOrientation;

	private float nextStepDistance, nextFlap;
	private Vector3d preWalkingPosition;

	private double preMoveY;

	private Vector3d jumpDir;

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
		this.jumpController = new ClimberJumpController<>(this);
		this.prevAttachmentOffsetY = this.attachmentOffsetY = this.lastAttachmentOffsetY = this.getVerticalOffset(1);
	}

	//createNavigator overrides usually don't call super.createNavigator so this ensures that onCreateNavigator
	//still gets called in such cases
	@Inject(method = "createNavigator(Lnet/minecraft/world/World;)Lnet/minecraft/pathfinding/PathNavigator;", at = @At("HEAD"), cancellable = true, require = 0, expect = 0)
	private void onCreateNavigator(World world, CallbackInfoReturnable<PathNavigator> ci) {
		PathNavigator navigator = this.onCreateNavigator(world);
		if(navigator != null) {
			ci.setReturnValue(navigator);
		}
	}

	@Override
	public PathNavigator onCreateNavigator(World world) {
		AdvancedClimberPathNavigator<ClimberEntityMixin> navigate = new AdvancedClimberPathNavigator<>(this, world, false, true, true);
		navigate.setCanSwim(true);
		return navigate;
	}

	@Override
	public void onRegisterData() {
		if(this.shouldTrackPathingTargets()) {
			this.dataManager.register(MOVEMENT_TARGET_X, 0.0f);
			this.dataManager.register(MOVEMENT_TARGET_Y, 0.0f);
			this.dataManager.register(MOVEMENT_TARGET_Z, 0.0f);

			for(DataParameter<Optional<BlockPos>> pathingTarget : PATHING_TARGETS) {
				this.dataManager.register(pathingTarget, Optional.empty());
			}

			for(DataParameter<Direction> pathingSide : PATHING_SIDES) {
				this.dataManager.register(pathingSide, Direction.DOWN);
			}
		}

		this.dataManager.register(ROTATION_BODY, new Rotations(0, 0, 0));

		this.dataManager.register(ROTATION_HEAD, new Rotations(0, 0, 0));
	}

	@Override
	public void onWrite(CompoundNBT nbt) {
		nbt.putDouble("SpidersTPO.AttachmentNormalX", this.attachmentNormal.x);
		nbt.putDouble("SpidersTPO.AttachmentNormalY", this.attachmentNormal.y);
		nbt.putDouble("SpidersTPO.AttachmentNormalZ", this.attachmentNormal.z);

		nbt.putInt("SpidersTPO.AttachedTicks", this.attachedTicks);
	}

	@Override
	public void onRead(CompoundNBT nbt) {
		this.prevAttachmentNormal = this.attachmentNormal = new Vector3d(
				nbt.getDouble("SpidersTPO.AttachmentNormalX"),
				nbt.getDouble("SpidersTPO.AttachmentNormalY"),
				nbt.getDouble("SpidersTPO.AttachmentNormalZ")
				);

		this.attachedTicks = nbt.getInt("SpidersTPO.AttachedTicks");

		this.orientation = this.calculateOrientation(1);
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

	private void updateWalkingSide() {
		Direction avoidPathingFacing = null;

		AxisAlignedBB entityBox = this.getBoundingBox();

		double closestFacingDst = Double.MAX_VALUE;
		Direction closestFacing = null;

		Vector3d weighting = new Vector3d(0, 0, 0);

		float stickingDistance = this.moveForward != 0 ? 1.5f : 0.1f;

		for(Direction facing : Direction.values()) {
			if(avoidPathingFacing == facing || !this.canAttachToSide(facing)) {
				continue;
			}

			List<AxisAlignedBB> collisionBoxes = this.getClimbableCollisionBoxes(entityBox.grow(0.2f).expand(facing.getXOffset() * stickingDistance, facing.getYOffset() * stickingDistance, facing.getZOffset() * stickingDistance));

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
			this.groundDirecton = Pair.of(Direction.DOWN, new Vector3d(0, -1, 0));
		} else {
			this.groundDirecton = Pair.of(closestFacing, weighting.normalize().add(0, -0.001f, 0).normalize());
		}
	}

	@Override
	public boolean canAttachToSide(Direction side) {
		return true;
	}

	@Override
	public Pair<Direction, Vector3d> getGroundDirection() {
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
	public Vector3d onLookAt(Type anchor, Vector3d vec) {
		Vector3d dir = vec.subtract(this.getPositionVec());
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

				Vector3d look = orientation.getGlobal(this.rotationYaw, this.rotationPitch);
				this.dataManager.set(ROTATION_BODY, new Rotations((float) look.x, (float) look.y, (float) look.z));

				look = orientation.getGlobal(this.rotationYawHead, 0.0f);
				this.dataManager.set(ROTATION_HEAD, new Rotations((float) look.x, (float) look.y, (float) look.z));

				if(this.shouldTrackPathingTargets()) {
					if(this.moveStrafing != 0) {
						Vector3d forwardVector = orientation.getGlobal(this.rotationYaw, 0);
						Vector3d strafeVector = orientation.getGlobal(this.rotationYaw - 90.0f, 0);

						Vector3d offset = forwardVector.scale(this.moveForward).add(strafeVector.scale(this.moveStrafing)).normalize();

						this.dataManager.set(MOVEMENT_TARGET_X, (float) (this.getPosX() + offset.x));
						this.dataManager.set(MOVEMENT_TARGET_Y, (float) (this.getPosY() + this.getHeight() * 0.5f + offset.y));
						this.dataManager.set(MOVEMENT_TARGET_Z, (float) (this.getPosZ() + offset.z));
					} else {
						this.dataManager.set(MOVEMENT_TARGET_X, (float) this.getMoveHelper().getX());
						this.dataManager.set(MOVEMENT_TARGET_Y, (float) this.getMoveHelper().getY());
						this.dataManager.set(MOVEMENT_TARGET_Z, (float) this.getMoveHelper().getZ());
					}

					Path path = this.getNavigator().getPath();
					if(path != null) {
						int i = 0;

						for(DataParameter<Optional<BlockPos>> pathingTarget : PATHING_TARGETS) {
							DataParameter<Direction> pathingSide = PATHING_SIDES.get(i);

							if(path.getCurrentPathIndex() + i < path.getCurrentPathLength()) {
								PathPoint point = path.getPathPointFromIndex(path.getCurrentPathIndex() + i);

								this.dataManager.set(pathingTarget, Optional.of(new BlockPos(point.x, point.y, point.z)));

								if(point instanceof DirectionalPathPoint) {
									Direction dir = ((DirectionalPathPoint) point).getPathSide();

									if(dir != null) {
										this.dataManager.set(pathingSide, dir);
									} else {
										this.dataManager.set(pathingSide, Direction.DOWN);
									}
								}

							} else {
								this.dataManager.set(pathingTarget, Optional.empty());
								this.dataManager.set(pathingSide, Direction.DOWN);
							}

							i++;
						}
					} else {
						for(DataParameter<Optional<BlockPos>> pathingTarget : PATHING_TARGETS) {
							this.dataManager.set(pathingTarget, Optional.empty());
						}

						for(DataParameter<Direction> pathingSide : PATHING_SIDES) {
							this.dataManager.set(pathingSide, Direction.DOWN);
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
	public Vector3d getTrackedMovementTarget() {
		if(this.shouldTrackPathingTargets()) {
			return new Vector3d(this.dataManager.get(MOVEMENT_TARGET_X), this.dataManager.get(MOVEMENT_TARGET_Y), this.dataManager.get(MOVEMENT_TARGET_Z));
		}

		return null;
	}

	@Override
	@Nullable
	public List<PathingTarget> getTrackedPathingTargets() {
		if(this.shouldTrackPathingTargets()) {
			List<PathingTarget> pathingTargets = new ArrayList<>(PATHING_TARGETS.size());

			int i = 0;
			for(DataParameter<Optional<BlockPos>> key : PATHING_TARGETS) {
				BlockPos pos = this.dataManager.get(key).orElse(null);

				if(pos != null) {
					pathingTargets.add(new PathingTarget(pos, this.dataManager.get(PATHING_SIDES.get(i))));
				}

				i++;
			}

			return pathingTargets;
		}

		return null;
	}

	@Override
	public boolean shouldTrackPathingTargets() {
		return true;
	}

	@Override
	public float getVerticalOffset(float partialTicks) {
		return 0.4f;
	}

	private void forEachClimbableCollisonBox(AxisAlignedBB aabb, VoxelShapes.ILineConsumer action) {
		ICollisionReader cachedCollisionReader = new CachedCollisionReader(this.world, aabb);

		Stream<VoxelShape> shapes = StreamSupport.stream(new VoxelShapeSpliterator(cachedCollisionReader, this, aabb, this::canClimbOnBlock), false);

		shapes.forEach(shape -> shape.forEachBox(action));
	}

	private List<AxisAlignedBB> getClimbableCollisionBoxes(AxisAlignedBB aabb) {
		List<AxisAlignedBB> boxes = new ArrayList<>();
		this.forEachClimbableCollisonBox(aabb, (minX, minY, minZ, maxX, maxY, maxZ) -> boxes.add(new AxisAlignedBB(minX, minY, minZ, maxX, maxY, maxZ)));
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
		Vector3d direction = this.getOrientation().getGlobal(this.rotationYaw, this.rotationPitch);

		boolean isAttached = false;

		double baseStickingOffsetX = 0.0f;
		double baseStickingOffsetY = this.getVerticalOffset(1);
		double baseStickingOffsetZ = 0.0f;
		Vector3d baseOrientationNormal = new Vector3d(0, 1, 0);

		if(!this.isClimbingDisabled && this.onGround && this.getRidingEntity() == null) {
			Vector3d p = this.getPositionVec();

			Vector3d s = p.add(0, this.getHeight() * 0.5f, 0);
			Vector3d pp = s;
			Vector3d pn = this.attachmentNormal.scale(-1);

			//Give nudge towards ground direction so that the climber doesn't
			//get stuck in an incorrect orientation
			if(this.groundDirecton != null) {
				double groundDirectionBlend = 0.25D;
				Vector3d scaledGroundDirection = this.groundDirecton.getValue().scale(groundDirectionBlend);
				pp = pp.add(scaledGroundDirection.scale(-1));
				pn = pn.scale(1.0D - groundDirectionBlend).add(scaledGroundDirection);
			}

			AxisAlignedBB inclusionBox = new AxisAlignedBB(s.x, s.y, s.z, s.x, s.y, s.z).grow(this.collisionsInclusionRange);

			Pair<Vector3d, Vector3d> attachmentPoint = CollisionSmoothingUtil.findClosestPoint(consumer -> this.forEachClimbableCollisonBox(inclusionBox, consumer), pp, pn, this.collisionsSmoothingRange, 0.5f, 1.0f, 0.001f, 20, 0.05f, s);

			AxisAlignedBB entityBox = this.getBoundingBox();

			if(attachmentPoint != null) {
				Vector3d attachmentPos = attachmentPoint.getLeft();

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
		Vector3d attachmentNormal = this.prevAttachmentNormal.add(this.attachmentNormal.subtract(this.prevAttachmentNormal).scale(partialTicks));

		Vector3d localZ = new Vector3d(0, 0, 1);
		Vector3d localY = new Vector3d(0, 1, 0);
		Vector3d localX = new Vector3d(1, 0, 0);

		float componentZ = (float) localZ.dotProduct(attachmentNormal);
		float componentY;
		float componentX = (float) localX.dotProduct(attachmentNormal);

		float yaw = (float) Math.toDegrees(MathHelper.atan2(componentX, componentZ));

		localZ = new Vector3d(Math.sin(Math.toRadians(yaw)), 0, Math.cos(Math.toRadians(yaw)));
		localY = new Vector3d(0, 1, 0);
		localX = new Vector3d(Math.sin(Math.toRadians(yaw - 90)), 0, Math.cos(Math.toRadians(yaw - 90)));

		componentZ = (float) localZ.dotProduct(attachmentNormal);
		componentY = (float) localY.dotProduct(attachmentNormal);
		componentX = (float) localX.dotProduct(attachmentNormal);

		float pitch = (float) Math.toDegrees(MathHelper.atan2(MathHelper.sqrt(componentX * componentX + componentZ * componentZ), componentY));

		Matrix4f m = new Matrix4f();

		m.multiply(new Matrix4f((float) Math.toRadians(yaw), 0, 1, 0));
		m.multiply(new Matrix4f((float) Math.toRadians(pitch), 1, 0, 0));
		m.multiply(new Matrix4f((float) Math.toRadians((float) Math.signum(0.5f - componentY - componentZ - componentX) * yaw), 0, 1, 0));

		localZ = m.multiply(new Vector3d(0, 0, -1));
		localY = m.multiply(new Vector3d(0, 1, 0));
		localX = m.multiply(new Vector3d(1, 0, 0));

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
			Vector3d look = new Vector3d(rotation.getX(), rotation.getY(), rotation.getZ());

			Pair<Float, Float> rotations = this.getOrientation().getLocalRotation(look);

			this.interpTargetYaw = rotations.getLeft();
			this.interpTargetPitch = rotations.getRight();
		} else if(ROTATION_HEAD.equals(key)) {
			Rotations rotation = this.dataManager.get(ROTATION_HEAD);
			Vector3d look = new Vector3d(rotation.getX(), rotation.getY(), rotation.getZ());

			Pair<Float, Float> rotations = this.getOrientation().getLocalRotation(look);

			this.interpTargetHeadYaw = rotations.getLeft();
			this.interpTicksHead = 3;
		}
	}

	private double getGravity() {
		if(this.hasNoGravity()) {
			return 0;
		}

		ModifiableAttributeInstance gravity = this.getAttribute(net.minecraftforge.common.ForgeMod.ENTITY_GRAVITY.get());

		boolean isFalling = this.getMotion().y <= 0.0D;

		if(isFalling && this.isPotionActive(Effects.SLOW_FALLING)) {
			if(!gravity.hasModifier(SLOW_FALLING)) {
				gravity.func_233767_b_(SLOW_FALLING);
			}
		} else if(gravity.hasModifier(SLOW_FALLING)) {
			gravity.removeModifier(SLOW_FALLING);
		}

		return gravity.getValue();
	}

	private Vector3d getStickingForce(Pair<Direction, Vector3d> walkingSide) {
		double uprightness = Math.max(this.attachmentNormal.y, 0);
		double gravity = this.getGravity();
		double stickingForce = gravity * uprightness + 0.08D * (1 - uprightness);
		return walkingSide.getRight().scale(stickingForce);
	}

	@Override
	public void setJumpDirection(Vector3d dir) {
		this.jumpDir = dir != null ? dir.normalize() : null;
	}

	@Override
	public boolean onJump() {
		if(this.jumpDir != null) {
			float jumpStrength = this.getJumpUpwardsMotion();
			if(this.isPotionActive(Effects.JUMP_BOOST)) {
				jumpStrength += 0.1F * (float)(this.getActivePotionEffect(Effects.JUMP_BOOST).getAmplifier() + 1);
			}

			Vector3d motion = this.getMotion();

			Vector3d orthogonalMotion = this.jumpDir.scale(this.jumpDir.dotProduct(motion));
			Vector3d tangentialMotion = motion.subtract(orthogonalMotion);

			this.setMotion(tangentialMotion.x + this.jumpDir.x * jumpStrength, tangentialMotion.y + this.jumpDir.y * jumpStrength, tangentialMotion.z + this.jumpDir.z * jumpStrength);

			if(this.isSprinting()) {
				Vector3d boost = this.getOrientation().getGlobal(this.rotationYaw, 0).scale(0.2f);
				this.setMotion(this.getMotion().add(boost));
			}

			this.isAirBorne = true;
			net.minecraftforge.common.ForgeHooks.onLivingJump(this);

			return true;
		}

		return false;
	}

	@Override
	public boolean onTravel(Vector3d relative, boolean pre) {
		if(pre) {
			boolean canTravel = this.isServerWorld() || this.canPassengerSteer();

			this.isClimbingDisabled = false;

			FluidState fluidState = this.world.getFluidState(this.func_233580_cy_());

			if(!this.canClimbInWater && this.isInWater() && this.func_241208_cS_() && !this.func_230285_a_(fluidState.getFluid())) {
				this.isClimbingDisabled = true;

				if(canTravel) {
					return false;
				}
			} else if(!this.canClimbInLava && this.isInLava() && this.func_241208_cS_() && !this.func_230285_a_(fluidState.getFluid())) {
				this.isClimbingDisabled = true;

				if(canTravel) {
					return false;
				}
			} else if(canTravel) {
				this.travelOnGround(relative);
			}

			if(!canTravel) {
				this.func_233629_a_(this, true);
			}

			this.updateOffsetsAndOrientation();
			return true;
		} else {
			this.updateOffsetsAndOrientation();
			return false;
		}
	}

	private float getRelevantMoveFactor(float slipperiness) {
		return this.onGround ? this.getAIMoveSpeed() * (0.16277136F / (slipperiness * slipperiness * slipperiness)) : this.jumpMovementFactor;
	}

	private void travelOnGround(Vector3d relative) {
		Orientation orientation = this.getOrientation();

		Vector3d forwardVector = orientation.getGlobal(this.rotationYaw, 0);
		Vector3d strafeVector = orientation.getGlobal(this.rotationYaw - 90.0f, 0);
		Vector3d upVector = orientation.getGlobal(this.rotationYaw, -90.0f);

		Pair<Direction, Vector3d> groundDirection = this.getGroundDirection();

		Vector3d stickingForce = this.getStickingForce(groundDirection);

		boolean isFalling = this.getMotion().y <= 0.0D;

		if(isFalling && this.isPotionActive(Effects.SLOW_FALLING)) {
			this.fallDistance = 0;
		}

		float forward = (float) relative.z;
		float strafe = (float) relative.x;

		if(forward != 0 || strafe != 0) {
			float slipperiness = 0.91f;

			if(this.onGround) {
				BlockPos offsetPos = new BlockPos(this.getPositionVec()).offset(groundDirection.getLeft());
				slipperiness = this.getBlockSlipperiness(offsetPos);
			}

			float f = forward * forward + strafe * strafe;
			if(f >= 1.0E-4F) {
				f = Math.max(MathHelper.sqrt(f), 1.0f);
				f = this.getRelevantMoveFactor(slipperiness) / f;
				forward *= f;
				strafe *= f;

				Vector3d movementOffset = new Vector3d(forwardVector.x * forward + strafeVector.x * strafe, forwardVector.y * forward + strafeVector.y * strafe, forwardVector.z * forward + strafeVector.z * strafe);

				double px = this.getPosX();
				double py = this.getPosY();
				double pz = this.getPosZ();
				Vector3d motion = this.getMotion();
				AxisAlignedBB aabb = this.getBoundingBox();

				//Probe actual movement vector
				this.move(MoverType.SELF, movementOffset);

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

				float moveSpeed = MathHelper.sqrt(forward * forward + strafe * strafe);
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

			BlockPos offsetPos = new BlockPos(this.getPositionVec()).offset(groundDirection.getLeft());
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
	public boolean onMove(MoverType type, Vector3d pos, boolean pre) {
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
			Vector3d moved = this.getPositionVec().subtract(this.preWalkingPosition);
			this.preWalkingPosition = null;

			BlockPos pos = this.getOnPosition();
			BlockState state = this.world.getBlockState(pos);

			double dx = moved.x;
			double dy = moved.y;
			double dz = moved.z;

			Vector3d tangentialMovement = moved.subtract(this.attachmentNormal.scale(this.attachmentNormal.dotProduct(moved)));

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

	@Override
	public boolean canClimberTriggerWalking() {
		return true;
	}
}

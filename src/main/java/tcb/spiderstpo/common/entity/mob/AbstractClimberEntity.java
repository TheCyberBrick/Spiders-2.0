package tcb.spiderstpo.common.entity.mob;

import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.apache.commons.lang3.tuple.Pair;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.MoverType;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.ai.attributes.ModifiableAttributeInstance;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.pathfinding.Path;
import net.minecraft.pathfinding.PathNavigate;
import net.minecraft.pathfinding.PathPoint;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Rotations;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import tcb.spiderstpo.common.CollisionSmoothingUtil;
import tcb.spiderstpo.common.Matrix4f;
import tcb.spiderstpo.common.SpiderMod;
import tcb.spiderstpo.common.entity.movement.AdvancedClimberPathNavigator;
import tcb.spiderstpo.common.entity.movement.AdvancedGroundPathNavigator;
import tcb.spiderstpo.common.entity.movement.ClimberLookController;
import tcb.spiderstpo.common.entity.movement.ClimberMoveController;
import tcb.spiderstpo.common.entity.movement.IAdvancedPathFindingEntity;


public abstract class AbstractClimberEntity extends EntityCreature implements IAdvancedPathFindingEntity {
	public static final ImmutableList<DataParameter<Optional<BlockPos>>> PATHING_TARGETS = ImmutableList.of(
			EntityDataManager.createKey(AbstractClimberEntity.class, DataSerializers.OPTIONAL_BLOCK_POS),
			EntityDataManager.createKey(AbstractClimberEntity.class, DataSerializers.OPTIONAL_BLOCK_POS),
			EntityDataManager.createKey(AbstractClimberEntity.class, DataSerializers.OPTIONAL_BLOCK_POS),
			EntityDataManager.createKey(AbstractClimberEntity.class, DataSerializers.OPTIONAL_BLOCK_POS),
			EntityDataManager.createKey(AbstractClimberEntity.class, DataSerializers.OPTIONAL_BLOCK_POS),
			EntityDataManager.createKey(AbstractClimberEntity.class, DataSerializers.OPTIONAL_BLOCK_POS),
			EntityDataManager.createKey(AbstractClimberEntity.class, DataSerializers.OPTIONAL_BLOCK_POS),
			EntityDataManager.createKey(AbstractClimberEntity.class, DataSerializers.OPTIONAL_BLOCK_POS)
			);

	public static final DataParameter<Rotations> ROTATION_BODY = EntityDataManager.createKey(AbstractClimberEntity.class, DataSerializers.ROTATIONS);
	public static final DataParameter<Rotations> ROTATION_HEAD = EntityDataManager.createKey(AbstractClimberEntity.class, DataSerializers.ROTATIONS);

	public double prevStickingOffsetX, prevStickingOffsetY, prevStickingOffsetZ;
	public double stickingOffsetX, stickingOffsetY, stickingOffsetZ;

	public Vec3d orientationNormal = new Vec3d(0, 1, 0);
	public Vec3d prevOrientationNormal = new Vec3d(0, 1, 0);

	public float prevOrientationYawDelta;
	public float orientationYawDelta;

	protected double attachedStickingOffsetX, attachedStickingOffsetY, attachedStickingOffsetZ;
	protected Vec3d attachedOrientationNormal = new Vec3d(0, 1, 0);

	protected int attachedTicks = 5;

	protected Vec3d attachedSides = new Vec3d(0, 0, 0);
	protected Vec3d prevAttachedSides = new Vec3d(0, 0, 0);

	protected boolean canClimbInWater = false;
	protected boolean canClimbInLava = false;

	protected boolean isTravelingInFluid = false;

	protected float collisionsInclusionRange = 2.0f;
	protected float collisionsSmoothingRange = 1.25f;

	public AbstractClimberEntity(World world) {
		super(world);
		this.stepHeight = 0.1f;
		this.moveHelper = new ClimberMoveController(this);
		ObfuscationReflectionHelper.setPrivateValue(EntityLiving.class, this, new ClimberLookController(this), "field_70749_g"); //lookHelper
	}

	@Override
	protected void entityInit() {
		super.entityInit();

		if(SpiderMod.DEBUG) {
			for(DataParameter<Optional<BlockPos>> pathingTarget : PATHING_TARGETS) {
				this.dataManager.register(pathingTarget, Optional.absent());
			}
		}

		this.dataManager.register(ROTATION_BODY, new Rotations(0, 0, 0));

		this.dataManager.register(ROTATION_HEAD, new Rotations(0, 0, 0));
	}

	@Override
	protected PathNavigate createNavigator(World worldIn) {
		AdvancedGroundPathNavigator<AbstractClimberEntity> navigate = new AdvancedClimberPathNavigator<AbstractClimberEntity>(this, worldIn, false, true, true);
		navigate.setCanSwim(true);
		return navigate;
	}

	@Override
	public float getBridgePathingMalus(EntityLiving entity, BlockPos pos, PathPoint fallPathPoint) {
		return -1.0f;
	}

	@Override
	public void onPathingObstructed(EnumFacing facing) {

	}

	@Override
	public int getMaxFallHeight() {
		return 0;
	}

	public float getMovementSpeed() {
		IAttributeInstance attribute = this.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED);
		return attribute != null ? (float) attribute.getAttributeValue() : 1.0f;
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

	public Pair<EnumFacing, Vec3d> getWalkingSide() {
		EnumFacing avoidPathingFacing = null;

		/*Path path = this.getNavigator().getPath();
		if(path != null) {
			int index = path.getCurrentPathIndex();

			if(index < path.getCurrentPathLength()) {
				PathPoint point = path.getPathPointFromIndex(index);

				double maxDist = 0;

				for(EnumFacing facing : EnumFacing.values()) {
					double posEntity = Math.abs(facing.getFrontOffsetX()) * this.posX + Math.abs(facing.getFrontOffsetY()) * this.posY + Math.abs(facing.getFrontOffsetZ()) * this.posZ;
					double posPath = Math.abs(facing.getFrontOffsetX()) * point.x + Math.abs(facing.getFrontOffsetY()) * point.y + Math.abs(facing.getFrontOffsetZ()) * point.z;

					double distSigned = posPath + 0.5f - posEntity;
					if(distSigned * (facing.getFrontOffsetX() + facing.getFrontOffsetY() + facing.getFrontOffsetZ()) > 0) {
						double dist = Math.abs(distSigned) - (facing.getAxis().isHorizontal() ? this.width / 2 : (facing == EnumFacing.DOWN ? 0 : this.height));

						if(dist > maxDist) {
							maxDist = dist;

							if(dist < 1.732f) {
								avoidPathingFacing = facing.getOpposite();
							} else {
								//Don't avoid facing if further away than 1 block diagonal, otherwise it could start floating around
								//if next path point is still too far away
								avoidPathingFacing = null;
							}
						}
					}
				}
			}
		}*/

		AxisAlignedBB entityBox = this.getEntityBoundingBox();

		double closestFacingDst = Double.MAX_VALUE;
		EnumFacing closestFacing = null;

		Vec3d weighting = new Vec3d(0, 0, 0);

		float stickingDistance = this.moveForward != 0 ? 1.5f : 0.1f;

		for(EnumFacing facing : EnumFacing.values()) {
			if(avoidPathingFacing == facing) {
				continue;
			}

			List<AxisAlignedBB> collisionBoxes = this.world.getCollisionBoxes(this, entityBox.grow(0.2f).expand(facing.getFrontOffsetX() * stickingDistance, facing.getFrontOffsetY() * stickingDistance, facing.getFrontOffsetZ() * stickingDistance));

			double closestDst = Double.MAX_VALUE;

			for(AxisAlignedBB collisionBox : collisionBoxes) {
				switch(facing) {
				case EAST:
				case WEST:
					closestDst = Math.min(closestDst, Math.abs(calculateXOffset(entityBox, collisionBox, -facing.getFrontOffsetX() * stickingDistance)));
					break;
				case UP:
				case DOWN:
					closestDst = Math.min(closestDst, Math.abs(calculateYOffset(entityBox, collisionBox, -facing.getFrontOffsetY() * stickingDistance)));
					break;
				case NORTH:
				case SOUTH:
					closestDst = Math.min(closestDst, Math.abs(calculateZOffset(entityBox, collisionBox, -facing.getFrontOffsetZ() * stickingDistance)));
					break;
				}
			}

			if(closestDst < closestFacingDst) {
				closestFacingDst = closestDst;
				closestFacing = facing;
			}

			if(closestDst < Double.MAX_VALUE) {
				weighting = weighting.add(new Vec3d(facing.getFrontOffsetX(), facing.getFrontOffsetY(), facing.getFrontOffsetZ()).scale(1 - Math.min(closestDst, stickingDistance) / stickingDistance));
			}
		}

		if(closestFacing == null) {
			return Pair.of(EnumFacing.DOWN, new Vec3d(0, -1, 0));
		}

		return Pair.of(closestFacing, weighting.normalize().addVector(0, -0.001f, 0).normalize());
	}

	public static class Orientation {
		public final Vec3d normal, localZ, localY, localX;
		public final float componentZ, componentY, componentX, yaw, pitch;

		private Orientation(Vec3d normal, Vec3d localZ, Vec3d localY, Vec3d localX, float componentZ, float componentY, float componentX, float yaw, float pitch) {
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

		public Vec3d getDirection(Vec3d local) {
			return this.localX.scale(local.x).add(this.localY.scale(local.y)).add(this.localZ.scale(local.z));
		}

		public Vec3d getDirection(float yaw, float pitch) {
			float cy = MathHelper.cos(yaw * 0.017453292F);
			float sy = MathHelper.sin(yaw * 0.017453292F);
			float cp = -MathHelper.cos(-pitch * 0.017453292F);
			float sp = MathHelper.sin(-pitch * 0.017453292F);
			return this.localX.scale(sy * cp).add(this.localY.scale(sp)).add(this.localZ.scale(cy * cp));
		}

		public Vec3d getLocal(Vec3d global) {
			return new Vec3d(this.localX.dotProduct(global), this.localY.dotProduct(global), this.localZ.dotProduct(global));
		}

		public Pair<Float, Float> getRotation(Vec3d global) {
			Vec3d local = this.getLocal(global);

			float yaw = (float)Math.toDegrees(Math.atan2(local.x, local.z)) + 180.0f;
			float pitch = (float)-Math.toDegrees(Math.atan2(local.y, MathHelper.sqrt(local.x * local.x + local.z * local.z)));

			return Pair.of(yaw, pitch);
		}
	}

	public Orientation getOrientation(float partialTicks) {
		Vec3d orientationNormal = this.prevOrientationNormal.add(this.orientationNormal.subtract(this.prevOrientationNormal).scale(partialTicks));

		Vec3d localZ = new Vec3d(0, 0, 1);
		Vec3d localY = new Vec3d(0, 1, 0);
		Vec3d localX = new Vec3d(1, 0, 0);

		float componentZ = (float)localZ.dotProduct(orientationNormal);
		float componentY;
		float componentX = (float)localX.dotProduct(orientationNormal);

		float yaw = (float)Math.toDegrees(Math.atan2(componentX, componentZ));

		localZ = new Vec3d(Math.sin(Math.toRadians(yaw)), 0, Math.cos(Math.toRadians(yaw)));
		localY = new Vec3d(0, 1, 0);
		localX = new Vec3d(Math.sin(Math.toRadians(yaw - 90)), 0, Math.cos(Math.toRadians(yaw - 90)));

		componentZ = (float)localZ.dotProduct(orientationNormal);
		componentY = (float)localY.dotProduct(orientationNormal);
		componentX = (float)localX.dotProduct(orientationNormal);

		float pitch = (float)Math.toDegrees(Math.atan2(MathHelper.sqrt(componentX * componentX + componentZ * componentZ), componentY));

		Matrix4f m = new Matrix4f();

		m.multiply(new Matrix4f((float)Math.toRadians(yaw), 0, 1, 0));
		m.multiply(new Matrix4f((float)Math.toRadians(pitch), 1, 0, 0));
		m.multiply(new Matrix4f((float)Math.toRadians((float)Math.signum(0.5f - componentY - componentZ - componentX) * yaw), 0, 1, 0));

		localZ = m.multiply(new Vec3d(0, 0, -1));
		localY = m.multiply(new Vec3d(0, 1, 0));
		localX = m.multiply(new Vec3d(1, 0, 0));

		return new Orientation(orientationNormal, localZ, localY, localX, componentZ, componentY, componentX, yaw, pitch);
	}

	@Override
	public void onUpdate() {
		super.onUpdate();

		if(!this.world.isRemote) {
			Vec3d look = this.getOrientation(1).getDirection(this.rotationYaw, this.rotationPitch);
			this.dataManager.set(ROTATION_BODY, new Rotations((float) look.x, (float) look.y, (float) look.z));

			look = this.getOrientation(1).getDirection(this.rotationYawHead, 0.0f);
			this.dataManager.set(ROTATION_HEAD, new Rotations((float) look.x, (float) look.y, (float) look.z));

			if(SpiderMod.DEBUG) {
				Path path = this.getNavigator().getPath();
				if(path != null) {
					int i = 0;
					for(DataParameter<Optional<BlockPos>> pathingTarget : PATHING_TARGETS) {
						if(path.getCurrentPathIndex() + i < path.getCurrentPathLength()) {
							PathPoint point = path.getPathPointFromIndex(path.getCurrentPathIndex() + i);
							this.dataManager.set(pathingTarget, Optional.of(new BlockPos(point.x, point.y, point.z)));
						} else {
							this.dataManager.set(pathingTarget, Optional.absent());
						}
						i++;
					}
				} else {
					for(DataParameter<Optional<BlockPos>> pathingTarget : PATHING_TARGETS) {
						this.dataManager.set(pathingTarget, Optional.absent());
					}
				}
			}
		}
	}

	@Nullable
	public BlockPos getPathingTarget(int i) {
		if(SpiderMod.DEBUG) {
			return this.dataManager.get(PATHING_TARGETS.get(i)).or((BlockPos)null);
		}
		return null;
	}

	public float getVerticalOffset(float partialTicks) {
		return 0.45f;
	}

	protected void updateOffsetsAndOrientation() {
		Vec3d direction = this.getOrientation(1).getDirection(this.rotationYaw, this.rotationPitch);

		boolean isAttached = false;

		double baseStickingOffsetX = 0.0f;
		double baseStickingOffsetY = this.getVerticalOffset(1);
		double baseStickingOffsetZ = 0.0f;
		Vec3d baseOrientationNormal = new Vec3d(0, 1, 0);

		if(!this.isTravelingInFluid && this.onGround && this.getRidingEntity() == null) {
			Vec3d p = this.getPositionVector();

			Vec3d s = p.addVector(0, this.height / 2, 0);
			AxisAlignedBB inclusionBox = new AxisAlignedBB(s.x, s.y, s.z, s.x, s.y, s.z).grow(this.collisionsInclusionRange);

			List<AxisAlignedBB> boxes = this.world.getCollisionBoxes(this, inclusionBox);

			Pair<Vec3d, Vec3d> attachmentPoint = CollisionSmoothingUtil.findClosestPoint(boxes, this.collisionsSmoothingRange, 1.0f, 0.005f, 20, 0.05f, s);

			if(attachmentPoint != null) {
				isAttached = true;

				this.attachedStickingOffsetX = MathHelper.clamp(attachmentPoint.getLeft().x - p.x, -this.width / 2, this.width / 2);
				this.attachedStickingOffsetY = MathHelper.clamp(attachmentPoint.getLeft().y - p.y, 0, this.height);
				this.attachedStickingOffsetZ = MathHelper.clamp(attachmentPoint.getLeft().z - p.z, -this.width / 2, this.width / 2);
				this.attachedOrientationNormal = attachmentPoint.getRight();
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

		Pair<Float, Float> newRotations = this.getOrientation(1).getRotation(direction);

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
		//this.interpTargetHeadYaw = MathHelper.wrapDegrees(this.interpTargetHeadYaw + yawDelta);

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
		super.setPositionAndRotationDirect(x, y, z, (float)this.interpTargetYaw, (float)this.interpTargetPitch, posRotationIncrements, teleport);
	}

	@Override
	public void setRotationYawHead(float rotation) {
	}

	@Override
	public void notifyDataManagerChange(DataParameter<?> key) {
		super.notifyDataManagerChange(key);

		if(ROTATION_BODY.equals(key)) {
			Rotations rotation = this.dataManager.get(ROTATION_BODY);
			Vec3d look = new Vec3d(rotation.getX(), rotation.getY(), rotation.getZ());

			Pair<Float, Float> rotations = this.getOrientation(1).getRotation(look);

			this.interpTargetYaw = rotations.getLeft();
			this.interpTargetPitch = rotations.getRight();
		} else if(ROTATION_HEAD.equals(key)) {
			Rotations rotation = this.dataManager.get(ROTATION_HEAD);
			Vec3d look = new Vec3d(rotation.getX(), rotation.getY(), rotation.getZ());

			Pair<Float, Float> rotations = this.getOrientation(1).getRotation(look);

			this.rotationYawHead = rotations.getLeft();
		}
	}

	public Vec3d getStickingForce(Pair<EnumFacing, Vec3d> walkingSide) {
		if(!this.hasNoGravity()) { //TODO Forge gravity attribute
			return walkingSide.getRight().scale(0.08f);
		}

		return new Vec3d(0, 0, 0);
	}

	@Override
	public void travel(float strafe, float vertical, float forward) {
		boolean canTravel = this.isServerWorld() || this.canPassengerSteer();

		this.isTravelingInFluid = false;

		if(!this.canClimbInWater && this.isInWater()) {
			this.isTravelingInFluid = true;

			if(canTravel) {
				super.travel(strafe, vertical, forward);
			}
		} else if(!this.canClimbInLava && this.isInLava()) {
			this.isTravelingInFluid = true;

			if(canTravel) {
				super.travel(strafe, vertical, forward);
			}
		} else if(canTravel) {
			this.travelOnGround(strafe, vertical, forward);
		}

		if(!canTravel) {
			this.updateLimbSwing(this, true);
		}

		this.updateOffsetsAndOrientation();
	}

	protected void travelOnGround(float strafe, float vertical, float forward) {
		Orientation orientation = this.getOrientation(1);

		Vec3d forwardVector = orientation.getDirection(this.rotationYaw, 0);
		Vec3d upVector = orientation.getDirection(this.rotationYaw, -90);

		//TODO Do some testing
		/*long nano = System.nanoTime();

		System.out.println(this.world.getGameTime() + " " + ((((System.nanoTime() - nano) / 1000) % 1000000) / 1000.0f));*/

		Pair<EnumFacing, Vec3d> walkingSide = this.getWalkingSide();

		Vec3d stickingForce = this.getStickingForce(walkingSide);

		if(forward != 0) {
			float slipperiness = 0.91f;

			if(this.onGround) {
				BlockPos offsetPos = new BlockPos(this.getPositionVector()).offset(walkingSide.getLeft());
				IBlockState offsetState = this.world.getBlockState(offsetPos);
				slipperiness = offsetState.getBlock().getSlipperiness(offsetState, this.world, offsetPos, this) * 0.91f;
			}

			float friction = forward * 0.16277136F / (slipperiness * slipperiness * slipperiness);

			float f = forward * forward;
			if(f >= 1.0E-4F) {
				f = Math.max(MathHelper.sqrt(f), 1.0f);
				f = friction / f;

				Vec3d forwardOffset = new Vec3d(forwardVector.x * forward * f, forwardVector.y * forward * f, forwardVector.z * forward * f);

				double px = this.posX;
				double py = this.posY;
				double pz = this.posZ;
				Vec3d motion = new Vec3d(this.motionX, this.motionY, this.motionZ);
				AxisAlignedBB aabb = this.getEntityBoundingBox();

				//Probe actual movement vector
				this.move(MoverType.SELF, forwardOffset.x, forwardOffset.y, forwardOffset.z);

				Vec3d movementDir = new Vec3d(this.posX - px, this.posY - py, this.posZ - pz).normalize();

				this.setEntityBoundingBox(aabb);
				this.resetPositionToBB();
				this.motionX = motion.x;
				this.motionY = motion.y;
				this.motionZ = motion.z;

				//Probe collision normal
				Vec3d probeVector = new Vec3d(Math.abs(movementDir.x) < 0.001D ? -Math.signum(upVector.x) : 0, Math.abs(movementDir.y) < 0.001D ? -Math.signum(upVector.y) : 0, Math.abs(movementDir.z) < 0.001D ? -Math.signum(upVector.z) : 0).normalize().scale(0.0001D);
				this.move(MoverType.SELF, probeVector.x, probeVector.y, probeVector.z);

				Vec3d collisionNormal = new Vec3d(Math.abs(this.posX - px - probeVector.x) > 0.000001D ? Math.signum(-probeVector.x) : 0, Math.abs(this.posY - py - probeVector.y) > 0.000001D ? Math.signum(-probeVector.y) : 0, Math.abs(this.posZ - pz - probeVector.z) > 0.000001D ? Math.signum(-probeVector.z) : 0).normalize();

				this.setEntityBoundingBox(aabb);
				this.resetPositionToBB();
				this.motionX = motion.x;
				this.motionY = motion.y;
				this.motionZ = motion.z;

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
				this.motionX += movementDir.x * moveSpeed;
				this.motionY += movementDir.y * moveSpeed;
				this.motionZ += movementDir.z * moveSpeed;
			}
		}

		this.motionX += stickingForce.x;
		this.motionY += stickingForce.y;
		this.motionZ += stickingForce.z;

		double px = this.posX;
		double py = this.posY;
		double pz = this.posZ;
		Vec3d motion = new Vec3d(this.motionX, this.motionY, this.motionZ);

		this.move(MoverType.SELF, motion.x, motion.y, motion.z);

		this.prevAttachedSides = this.attachedSides;
		this.attachedSides = new Vec3d(Math.abs(this.posX - px - motion.x) > 0.001D ? -Math.signum(motion.x) : 0, Math.abs(this.posY - py - motion.y) > 0.001D ? -Math.signum(motion.y) : 0, Math.abs(this.posZ - pz - motion.z) > 0.001D ? -Math.signum(motion.z) : 0);

		float slipperiness = 0.91f;

		if(this.onGround) {
			this.fallDistance = 0;

			BlockPos offsetPos = new BlockPos(this.getPositionVector()).offset(walkingSide.getLeft());
			IBlockState offsetState = this.world.getBlockState(offsetPos);
			slipperiness = offsetState.getBlock().getSlipperiness(offsetState, this.world, offsetPos, this) * 0.91F;
		}

		motion = new Vec3d(this.motionX, this.motionY, this.motionZ);
		Vec3d orthogonalMotion = upVector.scale(upVector.dotProduct(motion));
		Vec3d tangentialMotion = motion.subtract(orthogonalMotion);

		this.motionX = tangentialMotion.x * slipperiness + orthogonalMotion.x * 0.98f; 
		this.motionY = tangentialMotion.y * slipperiness + orthogonalMotion.y * 0.98f; 
		this.motionZ = tangentialMotion.z * slipperiness + orthogonalMotion.z * 0.98f;

		boolean detachedX = this.attachedSides.x != this.prevAttachedSides.x && Math.abs(this.attachedSides.x) < 0.001D;
		boolean detachedY = this.attachedSides.y != this.prevAttachedSides.y && Math.abs(this.attachedSides.y) < 0.001D;
		boolean detachedZ = this.attachedSides.z != this.prevAttachedSides.z && Math.abs(this.attachedSides.z) < 0.001D;

		if(detachedX || detachedY || detachedZ) {
			float stepHeight = this.stepHeight;
			this.stepHeight = 0;

			//Offset so that AABB is moved above the new surface
			this.move(MoverType.SELF, detachedX ? -this.prevAttachedSides.x * 0.25f : 0, detachedY ? -this.prevAttachedSides.y * 0.25f : 0, detachedZ ? -this.prevAttachedSides.z * 0.25f : 0);

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

			double attachDst = motion.lengthVector() + 0.1f;

			AxisAlignedBB aabb = this.getEntityBoundingBox();
			motion = new Vec3d(this.motionX, this.motionY, this.motionZ);

			//Offset AABB towards new surface until it touches
			for(int i = 0; i < 2 && !this.onGround; i++) {
				this.move(MoverType.SELF, attachVector.x * attachDst, attachVector.y * attachDst, attachVector.z * attachDst);
			}

			this.stepHeight = stepHeight;

			//Attaching failed, fall back to previous position
			if(!this.onGround) {
				this.setEntityBoundingBox(aabb);
				this.resetPositionToBB();
				this.motionX = motion.x;
				this.motionY = motion.y;
				this.motionZ = motion.z;
			} else {
				this.motionX = this.motionY = this.motionZ = 0;
			}
		}

		this.updateLimbSwing(this, true);
	}

	public void updateLimbSwing(EntityLiving entity, boolean includeY) {
		this.prevLimbSwingAmount = this.limbSwingAmount;
		double dx = this.posX - this.prevPosX;
		double dy = this.posY - this.prevPosY;
		double dz = this.posZ - this.prevPosZ;
		float f = MathHelper.sqrt(dx * dx + dy * dy + dz * dz) * 4.0F;
		if(f > 1.0F) {
			f = 1.0F;
		}

		this.limbSwingAmount += (f - this.limbSwingAmount) * 0.4F;
		this.limbSwing += this.limbSwingAmount;
	}

	@Override
	public void move(MoverType type, double x, double y, double z) {
		double py = this.posY;

		super.move(type, x, y, z);

		if(Math.abs(this.posY - py - y) > 0.000001D) {
			this.motionY = 0;
		}

		this.onGround |= this.collidedHorizontally || this.collidedVertically;
	}
}
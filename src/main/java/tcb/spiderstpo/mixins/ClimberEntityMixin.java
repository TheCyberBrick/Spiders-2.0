package tcb.spiderstpo.mixins;

import com.google.common.collect.ImmutableList;
import net.minecraft.commands.arguments.EntityAnchorArgument.Anchor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Rotations;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.level.BlockCollisions;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.apache.commons.lang3.tuple.Pair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import tcb.spiderstpo.common.CachedCollisionReader;
import tcb.spiderstpo.common.CollisionSmoothingUtil;
import tcb.spiderstpo.common.Matrix4f;
import tcb.spiderstpo.common.PredicateBlockCollisions;
import tcb.spiderstpo.common.entity.mob.*;
import tcb.spiderstpo.common.entity.movement.*;

import javax.annotation.Nullable;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Mixin(value = {Spider.class})
public abstract class ClimberEntityMixin extends PathfinderMob implements IClimberEntity, IMobEntityLivingTickHook, ILivingEntityLookAtHook, IMobEntityTickHook, ILivingEntityRotationHook, ILivingEntityDataManagerHook, ILivingEntityTravelHook, IEntityMovementHook, IEntityReadWriteHook, IEntityRegisterDataHook, ILivingEntityJumpHook, IMobEntityNavigatorHook {

    //Copy from LivingEntity
    private static final UUID SLOW_FALLING_ID = UUID.fromString("A5B6CF2A-2F7C-31EF-9022-7C3E7D5E6ABA");
    private static final AttributeModifier SLOW_FALLING = new AttributeModifier(SLOW_FALLING_ID, "Slow falling acceleration reduction", -0.07, AttributeModifier.Operation.ADDITION);

    private static final EntityDataAccessor<Float> MOVEMENT_TARGET_X;
    private static final EntityDataAccessor<Float> MOVEMENT_TARGET_Y;
    private static final EntityDataAccessor<Float> MOVEMENT_TARGET_Z;
    private static final ImmutableList<EntityDataAccessor<Optional<BlockPos>>> PATHING_TARGETS;
    private static final ImmutableList<EntityDataAccessor<Direction>> PATHING_SIDES;

    private static final EntityDataAccessor<Rotations> ROTATION_BODY;
    private static final EntityDataAccessor<Rotations> ROTATION_HEAD;

    static {
        @SuppressWarnings("unchecked")
        Class<Entity> cls = (Class<Entity>) MethodHandles.lookup().lookupClass();

        MOVEMENT_TARGET_X = SynchedEntityData.defineId(cls, EntityDataSerializers.FLOAT);
        MOVEMENT_TARGET_Y = SynchedEntityData.defineId(cls, EntityDataSerializers.FLOAT);
        MOVEMENT_TARGET_Z = SynchedEntityData.defineId(cls, EntityDataSerializers.FLOAT);

        ImmutableList.Builder<EntityDataAccessor<Optional<BlockPos>>> pathingTargets = ImmutableList.builder();
        ImmutableList.Builder<EntityDataAccessor<Direction>> pathingSides = ImmutableList.builder();
        for (int i = 0; i < 8; i++) {
            pathingTargets.add(SynchedEntityData.defineId(cls, EntityDataSerializers.OPTIONAL_BLOCK_POS));
            pathingSides.add(SynchedEntityData.defineId(cls, EntityDataSerializers.DIRECTION));
        }
        PATHING_TARGETS = pathingTargets.build();
        PATHING_SIDES = pathingSides.build();

        ROTATION_BODY = SynchedEntityData.defineId(cls, EntityDataSerializers.ROTATIONS);
        ROTATION_HEAD = SynchedEntityData.defineId(cls, EntityDataSerializers.ROTATIONS);
    }

    private double prevAttachmentOffsetX, prevAttachmentOffsetY, prevAttachmentOffsetZ;
    private double attachmentOffsetX, attachmentOffsetY, attachmentOffsetZ;

    private Vec3 attachmentNormal = new Vec3(0, 1, 0);
    private Vec3 prevAttachmentNormal = new Vec3(0, 1, 0);

    private float prevOrientationYawDelta;
    private float orientationYawDelta;

    private double lastAttachmentOffsetX, lastAttachmentOffsetY, lastAttachmentOffsetZ;
    private Vec3 lastAttachmentOrientationNormal = new Vec3(0, 1, 0);

    private int attachedTicks = 5;

    private Vec3 attachedSides = new Vec3(0, 0, 0);
    private Vec3 prevAttachedSides = new Vec3(0, 0, 0);

    private boolean canClimbInWater = false;
    private boolean canClimbInLava = false;

    private boolean isClimbingDisabled = false;

    private float collisionsInclusionRange = 2.0f;
    private float collisionsSmoothingRange = 1.25f;

    private Orientation orientation;
    private Pair<Direction, Vec3> groundDirecton;

    private Orientation renderOrientation;

    private float nextStepDistance, nextFlap;
    private Vec3 preWalkingPosition;

    private double preMoveY;

    private Vec3 jumpDir;

    private ClimberEntityMixin(EntityType<? extends PathfinderMob> type, Level worldIn) {
        super(type, worldIn);
    }

    @Inject(method = "<init>*", at = @At("RETURN"))
    private void onConstructed(CallbackInfo ci) {
        this.maxUpStep = 0.1f;
        this.orientation = this.calculateOrientation(1);
        this.groundDirecton = this.getGroundDirection();
        this.moveControl = new ClimberMoveController<>(this);
        this.lookControl = new ClimberLookController<>(this);
        this.jumpControl = new ClimberJumpController<>(this);
        this.prevAttachmentOffsetY = this.attachmentOffsetY = this.lastAttachmentOffsetY = this.getVerticalOffset(1);
    }

    //createNavigator overrides usually don't call super.createNavigator so this ensures that onCreateNavigator
    //still gets called in such cases
    @Inject(method = "createNavigation", at = @At("HEAD"), cancellable = true, require = 0, expect = 0)
    private void onCreateNavigator(Level world, CallbackInfoReturnable<PathNavigation> ci) {
        PathNavigation navigator = this.onCreateNavigator(world);
        if (navigator != null) {
            ci.setReturnValue(navigator);
        }
    }

    @Override
    public PathNavigation onCreateNavigator(Level world) {
        AdvancedClimberPathNavigator<ClimberEntityMixin> navigate = new AdvancedClimberPathNavigator<>(this, world, false, true, true);
        navigate.setCanFloat(true);
        return navigate;
    }

    @Override
    public void onRegisterData() {
        if (this.shouldTrackPathingTargets()) {
            this.entityData.define(MOVEMENT_TARGET_X, 0.0f);
            this.entityData.define(MOVEMENT_TARGET_Y, 0.0f);
            this.entityData.define(MOVEMENT_TARGET_Z, 0.0f);

            for (EntityDataAccessor<Optional<BlockPos>> pathingTarget : PATHING_TARGETS) {
                this.entityData.define(pathingTarget, Optional.empty());
            }

            for (EntityDataAccessor<Direction> pathingSide : PATHING_SIDES) {
                this.entityData.define(pathingSide, Direction.DOWN);
            }
        }

        this.entityData.define(ROTATION_BODY, new Rotations(0, 0, 0));

        this.entityData.define(ROTATION_HEAD, new Rotations(0, 0, 0));
    }

    @Override
    public void onWrite(CompoundTag nbt) {
        nbt.putDouble("SpidersTPO.AttachmentNormalX", this.attachmentNormal.x);
        nbt.putDouble("SpidersTPO.AttachmentNormalY", this.attachmentNormal.y);
        nbt.putDouble("SpidersTPO.AttachmentNormalZ", this.attachmentNormal.z);

        nbt.putInt("SpidersTPO.AttachedTicks", this.attachedTicks);
    }

    @Override
    public void onRead(CompoundTag nbt) {
        this.prevAttachmentNormal = this.attachmentNormal = new Vec3(
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
    public float getBridgePathingMalus(Mob entity, BlockPos pos, Node fallPathPoint) {
        return -1.0f;
    }

    @Override
    public void onPathingObstructed(Direction facing) {

    }

    @Override
    public int getMaxFallDistance() {
        return 0;
    }

    @Override
    public float getMovementSpeed() {
        AttributeInstance attribute = this.getAttribute(Attributes.MOVEMENT_SPEED); //MOVEMENT_SPEED
        return attribute != null ? (float) attribute.getValue() : 1.0f;
    }

    private static double calculateXOffset(AABB aabb, AABB other, double offsetX) {
        if (other.maxY > aabb.minY && other.minY < aabb.maxY && other.maxZ > aabb.minZ && other.minZ < aabb.maxZ) {
            if (offsetX > 0.0D && other.maxX <= aabb.minX) {
                double dx = aabb.minX - other.maxX;

                if (dx < offsetX) {
                    offsetX = dx;
                }
            } else if (offsetX < 0.0D && other.minX >= aabb.maxX) {
                double dx = aabb.maxX - other.minX;

                if (dx > offsetX) {
                    offsetX = dx;
                }
            }

            return offsetX;
        } else {
            return offsetX;
        }
    }

    private static double calculateYOffset(AABB aabb, AABB other, double offsetY) {
        if (other.maxX > aabb.minX && other.minX < aabb.maxX && other.maxZ > aabb.minZ && other.minZ < aabb.maxZ) {
            if (offsetY > 0.0D && other.maxY <= aabb.minY) {
                double dy = aabb.minY - other.maxY;

                if (dy < offsetY) {
                    offsetY = dy;
                }
            } else if (offsetY < 0.0D && other.minY >= aabb.maxY) {
                double dy = aabb.maxY - other.minY;

                if (dy > offsetY) {
                    offsetY = dy;
                }
            }

            return offsetY;
        } else {
            return offsetY;
        }
    }

    private static double calculateZOffset(AABB aabb, AABB other, double offsetZ) {
        if (other.maxX > aabb.minX && other.minX < aabb.maxX && other.maxY > aabb.minY && other.minY < aabb.maxY) {
            if (offsetZ > 0.0D && other.maxZ <= aabb.minZ) {
                double dz = aabb.minZ - other.maxZ;

                if (dz < offsetZ) {
                    offsetZ = dz;
                }
            } else if (offsetZ < 0.0D && other.minZ >= aabb.maxZ) {
                double dz = aabb.maxZ - other.minZ;

                if (dz > offsetZ) {
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

        AABB entityBox = this.getBoundingBox();

        double closestFacingDst = Double.MAX_VALUE;
        Direction closestFacing = null;

        Vec3 weighting = new Vec3(0, 0, 0);

        float stickingDistance = this.zza != 0 ? 1.5f : 0.1f;

        for (Direction facing : Direction.values()) {
            if (avoidPathingFacing == facing || !this.canAttachToSide(facing)) {
                continue;
            }

            List<AABB> collisionBoxes = this.getClimbableCollisionBoxes(entityBox.inflate(0.2f).expandTowards(facing.getStepX() * stickingDistance, facing.getStepY() * stickingDistance, facing.getStepZ() * stickingDistance));

            double closestDst = Double.MAX_VALUE;

            for (AABB collisionBox : collisionBoxes) {
                switch (facing) {
                    case EAST:
                    case WEST:
                        closestDst = Math.min(closestDst, Math.abs(calculateXOffset(entityBox, collisionBox, -facing.getStepX() * stickingDistance)));
                        break;
                    case UP:
                    case DOWN:
                        closestDst = Math.min(closestDst, Math.abs(calculateYOffset(entityBox, collisionBox, -facing.getStepY() * stickingDistance)));
                        break;
                    case NORTH:
                    case SOUTH:
                        closestDst = Math.min(closestDst, Math.abs(calculateZOffset(entityBox, collisionBox, -facing.getStepZ() * stickingDistance)));
                        break;
                }
            }

            if (closestDst < closestFacingDst) {
                closestFacingDst = closestDst;
                closestFacing = facing;
            }

            if (closestDst < Double.MAX_VALUE) {
                weighting = weighting.add(new Vec3(facing.getStepX(), facing.getStepY(), facing.getStepZ()).scale(1 - Math.min(closestDst, stickingDistance) / stickingDistance));
            }
        }

        if (closestFacing == null) {
            this.groundDirecton = Pair.of(Direction.DOWN, new Vec3(0, -1, 0));
        } else {
            this.groundDirecton = Pair.of(closestFacing, weighting.normalize().add(0, -0.001f, 0).normalize());
        }
    }

    @Override
    public boolean canAttachToSide(Direction side) {
        return true;
    }

    @Override
    public Pair<Direction, Vec3> getGroundDirection() {
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
        switch (axis) {
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
    public Vec3 onLookAt(Anchor anchor, Vec3 vec) {
        Vec3 dir = vec.subtract(this.position());
        dir = this.getOrientation().getLocal(dir);
        return dir;
    }

    @Override
    public void onTick() {
        if (!this.level.isClientSide && this.level instanceof ServerLevel) {
            ChunkMap.TrackedEntity entityTracker = ((ServerLevel) this.level).getChunkSource().chunkMap.entityMap.get(this.getId());

            //Prevent premature syncing of position causing overly smoothed movement
            if (entityTracker != null && entityTracker.serverEntity.tickCount % entityTracker.serverEntity.updateInterval == 0) {
                Orientation orientation = this.getOrientation();

                Vec3 look = orientation.getGlobal(this.getYRot(), this.getXRot());
                this.entityData.set(ROTATION_BODY, new Rotations((float) look.x, (float) look.y, (float) look.z));

                look = orientation.getGlobal(this.yHeadRot, 0.0f);
                this.entityData.set(ROTATION_HEAD, new Rotations((float) look.x, (float) look.y, (float) look.z));

                if (this.shouldTrackPathingTargets()) {
                    if (this.xxa != 0) {
                        Vec3 forwardVector = orientation.getGlobal(this.getYRot(), 0);
                        Vec3 strafeVector = orientation.getGlobal(this.getYRot() - 90.0f, 0);

                        Vec3 offset = forwardVector.scale(this.zza).add(strafeVector.scale(this.xxa)).normalize();

                        this.entityData.set(MOVEMENT_TARGET_X, (float) (this.getX() + offset.x));
                        this.entityData.set(MOVEMENT_TARGET_Y, (float) (this.getY() + this.getBbHeight() * 0.5f + offset.y));
                        this.entityData.set(MOVEMENT_TARGET_Z, (float) (this.getZ() + offset.z));
                    } else {
                        this.entityData.set(MOVEMENT_TARGET_X, (float) this.getMoveControl().getWantedX());
                        this.entityData.set(MOVEMENT_TARGET_Y, (float) this.getMoveControl().getWantedY());
                        this.entityData.set(MOVEMENT_TARGET_Z, (float) this.getMoveControl().getWantedZ());
                    }

                    Path path = this.getNavigation().getPath();
                    if (path != null) {
                        int i = 0;

                        for (EntityDataAccessor<Optional<BlockPos>> pathingTarget : PATHING_TARGETS) {
                            EntityDataAccessor<Direction> pathingSide = PATHING_SIDES.get(i);

                            if (path.getNextNodeIndex() + i < path.getNodeCount()) {
                                Node point = path.getNode(path.getNextNodeIndex() + i);

                                this.entityData.set(pathingTarget, Optional.of(new BlockPos(point.x, point.y, point.z)));

                                if (point instanceof DirectionalPathPoint) {
                                    Direction dir = ((DirectionalPathPoint) point).getPathSide();

                                    if (dir != null) {
                                        this.entityData.set(pathingSide, dir);
                                    } else {
                                        this.entityData.set(pathingSide, Direction.DOWN);
                                    }
                                }

                            } else {
                                this.entityData.set(pathingTarget, Optional.empty());
                                this.entityData.set(pathingSide, Direction.DOWN);
                            }

                            i++;
                        }
                    } else {
                        for (EntityDataAccessor<Optional<BlockPos>> pathingTarget : PATHING_TARGETS) {
                            this.entityData.set(pathingTarget, Optional.empty());
                        }

                        for (EntityDataAccessor<Direction> pathingSide : PATHING_SIDES) {
                            this.entityData.set(pathingSide, Direction.DOWN);
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
    public boolean onClimbable() {
        return false;
    }

    @Override
    @Nullable
    public Vec3 getTrackedMovementTarget() {
        if (this.shouldTrackPathingTargets()) {
            return new Vec3(this.entityData.get(MOVEMENT_TARGET_X), this.entityData.get(MOVEMENT_TARGET_Y), this.entityData.get(MOVEMENT_TARGET_Z));
        }

        return null;
    }

    @Override
    @Nullable
    public List<PathingTarget> getTrackedPathingTargets() {
        if (this.shouldTrackPathingTargets()) {
            List<PathingTarget> pathingTargets = new ArrayList<>(PATHING_TARGETS.size());

            int i = 0;
            for (EntityDataAccessor<Optional<BlockPos>> key : PATHING_TARGETS) {
                BlockPos pos = this.entityData.get(key).orElse(null);

                if (pos != null) {
                    pathingTargets.add(new PathingTarget(pos, this.entityData.get(PATHING_SIDES.get(i))));
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

    private void forEachClimbableCollisonBox(AABB aabb, Shapes.DoubleLineConsumer action) {
        CollisionGetter cachedCollisionReader = new CachedCollisionReader(this.level, aabb);

        Iterable<VoxelShape> shapes = () -> new PredicateBlockCollisions(cachedCollisionReader, this, aabb, this::canClimbOnBlock); cachedCollisionReader.getBlockCollisions(this, aabb);

        shapes.forEach(shape -> shape.forAllBoxes(action));
    }

    private List<AABB> getClimbableCollisionBoxes(AABB aabb) {
        List<AABB> boxes = new ArrayList<>();
        this.forEachClimbableCollisonBox(aabb, (minX, minY, minZ, maxX, maxY, maxZ) -> boxes.add(new AABB(minX, minY, minZ, maxX, maxY, maxZ)));
        return boxes;
    }

    @Override
    public boolean canClimbOnBlock(BlockState state, BlockPos pos) {
        return true;
    }

    @Override
    public float getBlockSlipperiness(BlockPos pos) {
        BlockState offsetState = this.level.getBlockState(pos);
        return offsetState.getBlock().getFriction(offsetState, this.level, pos, this) * 0.91f;
    }

    private void updateOffsetsAndOrientation() {
        Vec3 direction = this.getOrientation().getGlobal(this.getYRot(), this.getXRot());

        boolean isAttached = false;

        double baseStickingOffsetX = 0.0f;
        double baseStickingOffsetY = this.getVerticalOffset(1);
        double baseStickingOffsetZ = 0.0f;
        Vec3 baseOrientationNormal = new Vec3(0, 1, 0);

        if (!this.isClimbingDisabled && this.onGround && this.getVehicle() == null) {
            Vec3 p = this.position();

            Vec3 s = p.add(0, this.getBbHeight() * 0.5f, 0);
            Vec3 pp = s;
            Vec3 pn = this.attachmentNormal.scale(-1);

            //Give nudge towards ground direction so that the climber doesn't
            //get stuck in an incorrect orientation
            if (this.groundDirecton != null) {
                double groundDirectionBlend = 0.25D;
                Vec3 scaledGroundDirection = this.groundDirecton.getValue().scale(groundDirectionBlend);
                pp = pp.add(scaledGroundDirection.scale(-1));
                pn = pn.scale(1.0D - groundDirectionBlend).add(scaledGroundDirection);
            }

            AABB inclusionBox = new AABB(s.x, s.y, s.z, s.x, s.y, s.z).inflate(this.collisionsInclusionRange);

            Pair<Vec3, Vec3> attachmentPoint = CollisionSmoothingUtil.findClosestPoint(consumer -> this.forEachClimbableCollisonBox(inclusionBox, consumer), pp, pn, this.collisionsSmoothingRange, 0.5f, 1.0f, 0.001f, 20, 0.05f, s);

            AABB entityBox = this.getBoundingBox();

            if (attachmentPoint != null) {
                Vec3 attachmentPos = attachmentPoint.getLeft();

                double dx = Math.max(entityBox.minX - attachmentPos.x, attachmentPos.x - entityBox.maxX);
                double dy = Math.max(entityBox.minY - attachmentPos.y, attachmentPos.y - entityBox.maxY);
                double dz = Math.max(entityBox.minZ - attachmentPos.z, attachmentPos.z - entityBox.maxZ);

                if (Math.max(dx, Math.max(dy, dz)) < 0.5f) {
                    isAttached = true;

                    this.lastAttachmentOffsetX = Mth.clamp(attachmentPos.x - p.x, -this.getBbWidth() / 2, this.getBbWidth() / 2);
                    this.lastAttachmentOffsetY = Mth.clamp(attachmentPos.y - p.y, 0, this.getBbHeight());
                    this.lastAttachmentOffsetZ = Mth.clamp(attachmentPos.z - p.z, -this.getBbWidth() / 2, this.getBbWidth() / 2);
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

        if (!isAttached) {
            this.attachedTicks = Math.max(0, this.attachedTicks - 1);
        } else {
            this.attachedTicks = Math.min(5, this.attachedTicks + 1);
        }

        this.orientation = this.calculateOrientation(1);

        Pair<Float, Float> newRotations = this.getOrientation().getLocalRotation(direction);

        float yawDelta = newRotations.getLeft() - this.getYRot();
        float pitchDelta = newRotations.getRight() - this.getXRot();

        this.prevOrientationYawDelta = this.orientationYawDelta;
        this.orientationYawDelta = yawDelta;

        this.setYRot(Mth.wrapDegrees(this.getYRot() + yawDelta));
        this.yRotO = this.wrapAngleInRange(this.yRotO/* + yawDelta*/, this.getYRot());
        this.lerpYRot = Mth.wrapDegrees(this.lerpYRot + yawDelta);

        this.yBodyRot = Mth.wrapDegrees(this.yBodyRot + yawDelta);
        this.yBodyRotO = this.wrapAngleInRange(this.yBodyRotO/* + yawDelta*/, this.yBodyRot);

        this.yHeadRot = Mth.wrapDegrees(this.yHeadRot + yawDelta);
        this.yHeadRotO = this.wrapAngleInRange(this.yHeadRotO/* + yawDelta*/, this.yHeadRot);
        this.lyHeadRot = Mth.wrapDegrees(this.lyHeadRot + yawDelta);

        this.setXRot(Mth.wrapDegrees(this.getXRot() + pitchDelta));
        this.xRotO = this.wrapAngleInRange(this.xRotO/* + pitchDelta*/, this.getXRot());
        this.lerpXRot = Mth.wrapDegrees(this.lerpXRot + pitchDelta);
    }

    private float wrapAngleInRange(float angle, float target) {
        while (target - angle < -180.0F) {
            angle -= 360.0F;
        }

        while (target - angle >= 180.0F) {
            angle += 360.0F;
        }

        return angle;
    }

    @Override
    public Orientation calculateOrientation(float partialTicks) {
        Vec3 attachmentNormal = this.prevAttachmentNormal.add(this.attachmentNormal.subtract(this.prevAttachmentNormal).scale(partialTicks));

        Vec3 localZ = new Vec3(0, 0, 1);
        Vec3 localY = new Vec3(0, 1, 0);
        Vec3 localX = new Vec3(1, 0, 0);

        float componentZ = (float) localZ.dot(attachmentNormal);
        float componentY;
        float componentX = (float) localX.dot(attachmentNormal);

        float yaw = (float) Math.toDegrees(Mth.atan2(componentX, componentZ));

        localZ = new Vec3(Math.sin(Math.toRadians(yaw)), 0, Math.cos(Math.toRadians(yaw)));
        localY = new Vec3(0, 1, 0);
        localX = new Vec3(Math.sin(Math.toRadians(yaw - 90)), 0, Math.cos(Math.toRadians(yaw - 90)));

        componentZ = (float) localZ.dot(attachmentNormal);
        componentY = (float) localY.dot(attachmentNormal);
        componentX = (float) localX.dot(attachmentNormal);

        float pitch = (float) Math.toDegrees(Mth.atan2(Mth.sqrt(componentX * componentX + componentZ * componentZ), componentY));

        Matrix4f m = new Matrix4f();

        m.multiply(new Matrix4f((float) Math.toRadians(yaw), 0, 1, 0));
        m.multiply(new Matrix4f((float) Math.toRadians(pitch), 1, 0, 0));
        m.multiply(new Matrix4f((float) Math.toRadians((float) Math.signum(0.5f - componentY - componentZ - componentX) * yaw), 0, 1, 0));

        localZ = m.multiply(new Vec3(0, 0, -1));
        localY = m.multiply(new Vec3(0, 1, 0));
        localX = m.multiply(new Vec3(1, 0, 0));

        return new Orientation(attachmentNormal, localZ, localY, localX, componentZ, componentY, componentX, yaw, pitch);
    }

    @Override
    public float getTargetYaw(double x, double y, double z, float yaw, float pitch, int posRotationIncrements, boolean teleport) {
        return (float) this.lerpYRot;
    }

    @Override
    public float getTargetPitch(double x, double y, double z, float yaw, float pitch, int posRotationIncrements, boolean teleport) {
        return (float) this.lerpXRot;
    }

    @Override
    public float getTargetHeadYaw(float yaw, int rotationIncrements) {
        return (float) this.lyHeadRot;
    }

    @Override
    public void onNotifyDataManagerChange(EntityDataAccessor<?> key) {
        if (ROTATION_BODY.equals(key)) {
            Rotations rotation = this.entityData.get(ROTATION_BODY);
            Vec3 look = new Vec3(rotation.getX(), rotation.getY(), rotation.getZ());

            Pair<Float, Float> rotations = this.getOrientation().getLocalRotation(look);

            this.lerpYRot = rotations.getLeft();
            this.lerpXRot = rotations.getRight();
        } else if (ROTATION_HEAD.equals(key)) {
            Rotations rotation = this.entityData.get(ROTATION_HEAD);
            Vec3 look = new Vec3(rotation.getX(), rotation.getY(), rotation.getZ());

            Pair<Float, Float> rotations = this.getOrientation().getLocalRotation(look);

            this.lyHeadRot = rotations.getLeft();
            this.lerpHeadSteps = 3;
        }
    }

    private double getGravity() {
        if (this.isNoGravity()) {
            return 0;
        }

        AttributeInstance gravity = this.getAttribute(net.minecraftforge.common.ForgeMod.ENTITY_GRAVITY.get());

        boolean isFalling = this.getDeltaMovement().y <= 0.0D;

        if (isFalling && this.hasEffect(MobEffects.SLOW_FALLING)) {
            if (!gravity.hasModifier(SLOW_FALLING)) {
                gravity.addTransientModifier(SLOW_FALLING);
            }
        } else if (gravity.hasModifier(SLOW_FALLING)) {
            gravity.removeModifier(SLOW_FALLING);
        }

        return gravity.getValue();
    }

    private Vec3 getStickingForce(Pair<Direction, Vec3> walkingSide) {
        double uprightness = Math.max(this.attachmentNormal.y, 0);
        double gravity = this.getGravity();
        double stickingForce = gravity * uprightness + 0.08D * (1 - uprightness);
        return walkingSide.getRight().scale(stickingForce);
    }

    @Override
    public void setJumpDirection(Vec3 dir) {
        this.jumpDir = dir != null ? dir.normalize() : null;
    }

    @Override
    public boolean onJump() {
        if (this.jumpDir != null) {
            float jumpStrength = this.getJumpPower();
            if (this.hasEffect(MobEffects.JUMP)) {
                jumpStrength += 0.1F * (float) (this.getEffect(MobEffects.JUMP).getAmplifier() + 1);
            }

            Vec3 motion = this.getDeltaMovement();

            Vec3 orthogonalMotion = this.jumpDir.scale(this.jumpDir.dot(motion));
            Vec3 tangentialMotion = motion.subtract(orthogonalMotion);

            this.setDeltaMovement(tangentialMotion.x + this.jumpDir.x * jumpStrength, tangentialMotion.y + this.jumpDir.y * jumpStrength, tangentialMotion.z + this.jumpDir.z * jumpStrength);

            if (this.isSprinting()) {
                Vec3 boost = this.getOrientation().getGlobal(this.getYRot(), 0).scale(0.2f);
                this.setDeltaMovement(this.getDeltaMovement().add(boost));
            }

            this.hasImpulse = true;
            net.minecraftforge.common.ForgeHooks.onLivingJump(this);

            return true;
        }

        return false;
    }

    @Override
    public boolean onTravel(Vec3 relative, boolean pre) {
        if (pre) {
            boolean canTravel = this.isEffectiveAi() || this.isControlledByLocalInstance();

            this.isClimbingDisabled = false;

            FluidState fluidState = this.level.getFluidState(this.blockPosition());

            if (!this.canClimbInWater && this.isInWater() && this.isAffectedByFluids() && !this.canStandOnFluid(fluidState.getType())) {
                this.isClimbingDisabled = true;

                if (canTravel) {
                    return false;
                }
            } else if (!this.canClimbInLava && this.isInLava() && this.isAffectedByFluids() && !this.canStandOnFluid(fluidState.getType())) {
                this.isClimbingDisabled = true;

                if (canTravel) {
                    return false;
                }
            } else if (canTravel) {
                this.travelOnGround(relative);
            }

            if (!canTravel) {
                this.calculateEntityAnimation(this, true);
            }

            this.updateOffsetsAndOrientation();
            return true;
        } else {
            this.updateOffsetsAndOrientation();
            return false;
        }
    }

    private float getRelevantMoveFactor(float slipperiness) {
        return this.onGround ? this.getSpeed() * (0.16277136F / (slipperiness * slipperiness * slipperiness)) : this.flyingSpeed;
    }

    private void travelOnGround(Vec3 relative) {
        Orientation orientation = this.getOrientation();

        Vec3 forwardVector = orientation.getGlobal(this.getYRot(), 0);
        Vec3 strafeVector = orientation.getGlobal(this.getYRot() - 90.0f, 0);
        Vec3 upVector = orientation.getGlobal(this.getYRot(), -90.0f);

        Pair<Direction, Vec3> groundDirection = this.getGroundDirection();

        Vec3 stickingForce = this.getStickingForce(groundDirection);

        boolean isFalling = this.getDeltaMovement().y <= 0.0D;

        if (isFalling && this.hasEffect(MobEffects.SLOW_FALLING)) {
            this.fallDistance = 0;
        }

        float forward = (float) relative.z;
        float strafe = (float) relative.x;

        if (forward != 0 || strafe != 0) {
            float slipperiness = 0.91f;

            if (this.onGround) {
                BlockPos offsetPos = new BlockPos(this.position()).relative(groundDirection.getLeft());
                slipperiness = this.getBlockSlipperiness(offsetPos);
            }

            float f = forward * forward + strafe * strafe;
            if (f >= 1.0E-4F) {
                f = Math.max(Mth.sqrt(f), 1.0f);
                f = this.getRelevantMoveFactor(slipperiness) / f;
                forward *= f;
                strafe *= f;

                Vec3 movementOffset = new Vec3(forwardVector.x * forward + strafeVector.x * strafe, forwardVector.y * forward + strafeVector.y * strafe, forwardVector.z * forward + strafeVector.z * strafe);

                double px = this.getX();
                double py = this.getY();
                double pz = this.getZ();
                Vec3 motion = this.getDeltaMovement();
                AABB aabb = this.getBoundingBox();

                //Probe actual movement vector
                this.move(MoverType.SELF, movementOffset);

                Vec3 movementDir = new Vec3(this.getX() - px, this.getY() - py, this.getZ() - pz).normalize();

                this.setBoundingBox(aabb);
                this.setLocationFromBoundingbox();

                this.setDeltaMovement(motion);

                //Probe collision normal
                Vec3 probeVector = new Vec3(Math.abs(movementDir.x) < 0.001D ? -Math.signum(upVector.x) : 0, Math.abs(movementDir.y) < 0.001D ? -Math.signum(upVector.y) : 0, Math.abs(movementDir.z) < 0.001D ? -Math.signum(upVector.z) : 0).normalize().scale(0.0001D);
                this.move(MoverType.SELF, probeVector);

                Vec3 collisionNormal = new Vec3(Math.abs(this.getX() - px - probeVector.x) > 0.000001D ? Math.signum(-probeVector.x) : 0, Math.abs(this.getY() - py - probeVector.y) > 0.000001D ? Math.signum(-probeVector.y) : 0, Math.abs(this.getZ() - pz - probeVector.z) > 0.000001D ? Math.signum(-probeVector.z) : 0).normalize();

                this.setBoundingBox(aabb);
                this.setLocationFromBoundingbox();

                this.setDeltaMovement(motion);

                //Movement vector projected to surface
                Vec3 surfaceMovementDir = movementDir.subtract(collisionNormal.scale(collisionNormal.dot(movementDir))).normalize();

                boolean isInnerCorner = Math.abs(collisionNormal.x) + Math.abs(collisionNormal.y) + Math.abs(collisionNormal.z) > 1.0001f;

                //Only project movement vector to surface if not moving across inner corner, otherwise it'd get stuck in the corner
                if (!isInnerCorner) {
                    movementDir = surfaceMovementDir;
                }

                //Nullify sticking force along movement vector projected to surface
                stickingForce = stickingForce.subtract(surfaceMovementDir.scale(surfaceMovementDir.normalize().dot(stickingForce)));

                float moveSpeed = Mth.sqrt(forward * forward + strafe * strafe);
                this.setDeltaMovement(this.getDeltaMovement().add(movementDir.scale(moveSpeed)));
            }
        }

        this.setDeltaMovement(this.getDeltaMovement().add(stickingForce));

        double px = this.getX();
        double py = this.getY();
        double pz = this.getZ();
        Vec3 motion = this.getDeltaMovement();

        this.move(MoverType.SELF, motion);

        this.prevAttachedSides = this.attachedSides;
        this.attachedSides = new Vec3(Math.abs(this.getX() - px - motion.x) > 0.001D ? -Math.signum(motion.x) : 0, Math.abs(this.getY() - py - motion.y) > 0.001D ? -Math.signum(motion.y) : 0, Math.abs(this.getZ() - pz - motion.z) > 0.001D ? -Math.signum(motion.z) : 0);

        float slipperiness = 0.91f;

        if (this.onGround) {
            this.fallDistance = 0;

            BlockPos offsetPos = new BlockPos(this.position()).relative(groundDirection.getLeft());
            slipperiness = this.getBlockSlipperiness(offsetPos);
        }

        motion = this.getDeltaMovement();
        Vec3 orthogonalMotion = upVector.scale(upVector.dot(motion));
        Vec3 tangentialMotion = motion.subtract(orthogonalMotion);

        this.setDeltaMovement(tangentialMotion.x * slipperiness + orthogonalMotion.x * 0.98f, tangentialMotion.y * slipperiness + orthogonalMotion.y * 0.98f, tangentialMotion.z * slipperiness + orthogonalMotion.z * 0.98f);

        boolean detachedX = this.attachedSides.x != this.prevAttachedSides.x && Math.abs(this.attachedSides.x) < 0.001D;
        boolean detachedY = this.attachedSides.y != this.prevAttachedSides.y && Math.abs(this.attachedSides.y) < 0.001D;
        boolean detachedZ = this.attachedSides.z != this.prevAttachedSides.z && Math.abs(this.attachedSides.z) < 0.001D;

        if (detachedX || detachedY || detachedZ) {
            float stepHeight = this.maxUpStep;
            this.maxUpStep = 0;

            boolean prevOnGround = this.onGround;
            boolean prevCollidedHorizontally = this.horizontalCollision;
            boolean prevCollidedVertically = this.verticalCollision;

            //Offset so that AABB is moved above the new surface
            this.move(MoverType.SELF, new Vec3(detachedX ? -this.prevAttachedSides.x * 0.25f : 0, detachedY ? -this.prevAttachedSides.y * 0.25f : 0, detachedZ ? -this.prevAttachedSides.z * 0.25f : 0));

            Vec3 axis = this.prevAttachedSides.normalize();
            Vec3 attachVector = upVector.scale(-1);
            attachVector = attachVector.subtract(axis.scale(axis.dot(attachVector)));

            if (Math.abs(attachVector.x) > Math.abs(attachVector.y) && Math.abs(attachVector.x) > Math.abs(attachVector.z)) {
                attachVector = new Vec3(Math.signum(attachVector.x), 0, 0);
            } else if (Math.abs(attachVector.y) > Math.abs(attachVector.z)) {
                attachVector = new Vec3(0, Math.signum(attachVector.y), 0);
            } else {
                attachVector = new Vec3(0, 0, Math.signum(attachVector.z));
            }

            double attachDst = motion.length() + 0.1f;

            AABB aabb = this.getBoundingBox();
            motion = this.getDeltaMovement();

            //Offset AABB towards new surface until it touches
            for (int i = 0; i < 2 && !this.onGround; i++) {
                this.move(MoverType.SELF, attachVector.scale(attachDst));
            }

            this.maxUpStep = stepHeight;

            //Attaching failed, fall back to previous position
            if (!this.onGround) {
                this.setBoundingBox(aabb);
                this.setLocationFromBoundingbox();

                this.setDeltaMovement(motion);
                this.onGround = prevOnGround;
                this.horizontalCollision = prevCollidedHorizontally;
                this.verticalCollision = prevCollidedVertically;
            } else {
                this.setDeltaMovement(Vec3.ZERO);
            }
        }

        this.calculateEntityAnimation(this, true);
    }
    public void setLocationFromBoundingbox() {
        AABB axisalignedbb = this.getBoundingBox();
        this.setPosRaw((axisalignedbb.minX + axisalignedbb.maxX) / 2.0D, axisalignedbb.minY, (axisalignedbb.minZ + axisalignedbb.maxZ) / 2.0D);
    }


    @Override
    public boolean onMove(MoverType type, Vec3 pos, boolean pre) {
        if (pre) {
            this.preWalkingPosition = this.position();
            this.preMoveY = this.getY();
        } else {
            if (Math.abs(this.getY() - this.preMoveY - pos.y) > 0.000001D) {
                this.setDeltaMovement(this.getDeltaMovement().multiply(1, 0, 1));
            }

            this.onGround |= this.horizontalCollision || this.verticalCollision;
        }

        return false;
    }

    @Override
    public BlockPos getAdjustedOnPosition(BlockPos onPosition) {
        float verticalOffset = this.getVerticalOffset(1);

        int x = Mth.floor(this.getX() + this.attachmentOffsetX - (float) this.attachmentNormal.x * (verticalOffset + 0.2f));
        int y = Mth.floor(this.getY() + this.attachmentOffsetY - (float) this.attachmentNormal.y * (verticalOffset + 0.2f));
        int z = Mth.floor(this.getZ() + this.attachmentOffsetZ - (float) this.attachmentNormal.z * (verticalOffset + 0.2f));
        BlockPos pos = new BlockPos(x, y, z);

        if (this.level.isEmptyBlock(pos) && this.attachmentNormal.y < 0.0f) {
            BlockPos posDown = pos.below();
            BlockState stateDown = this.level.getBlockState(posDown);

            if (stateDown.collisionExtendsVertically(this.level, posDown, this)) {
                return posDown;
            }
        }

        return pos;
    }

    @Override
    public MovementEmission getAdjustedCanTriggerWalking(MovementEmission canTriggerWalking) {
        if (this.preWalkingPosition != null && this.canClimberTriggerWalking() && !this.isPassenger()) {
            Vec3 moved = this.position().subtract(this.preWalkingPosition);
            this.preWalkingPosition = null;

            BlockPos pos = this.getOnPos();
            BlockState state = this.level.getBlockState(pos);

            double dx = moved.x;
            double dy = moved.y;
            double dz = moved.z;

            Vec3 tangentialMovement = moved.subtract(this.attachmentNormal.scale(this.attachmentNormal.dot(moved)));

            this.walkDist = (float) ((double) this.walkDist + tangentialMovement.length() * 0.6D);

            this.moveDist = (float) ((double) this.moveDist + (double) Mth.sqrt((float) (dx * dx + dy * dy + dz * dz)) * 0.6D);

            if (this.moveDist > this.nextStepDistance && !state.isAir()) {
                this.nextStepDistance = this.nextStep();

                if (this.isInWater()) {
                    Entity controller = this.isVehicle() && this.getControllingPassenger() != null ? this.getControllingPassenger() : this;

                    float multiplier = controller == this ? 0.35F : 0.4F;

                    Vec3 motion = controller.getDeltaMovement();

                    float swimStrength = Mth.sqrt((float) (motion.x * motion.x * (double) 0.2F + motion.y * motion.y + motion.z * motion.z * 0.2F)) * multiplier;
                    if (swimStrength > 1.0F) {
                        swimStrength = 1.0F;
                    }

                    this.playSwimSound(swimStrength);
                } else {
                    this.playStepSound(pos, state);
                }
            } else if (state.isAir()) {
                this.processFlappingMovement();
            }
        }

        return MovementEmission.ALL;
    }

    @Override
    public boolean canClimberTriggerWalking() {
        return true;
    }
}

package tcb.spiderstpo.common.entity.movement;

import net.minecraft.entity.Entity;
import net.minecraft.entity.MobEntity;
import net.minecraft.pathfinding.Path;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import tcb.spiderstpo.common.entity.mob.IClimberEntity;

public class BetterSpiderPathNavigator<T extends MobEntity & IClimberEntity> extends AdvancedClimberPathNavigator<T> {
	private boolean useVanillaBehaviour;
	private BlockPos targetPosition;

	public BetterSpiderPathNavigator(T entity, World worldIn, boolean useVanillaBehaviour) {
		super(entity, worldIn, false, true, true);
		this.useVanillaBehaviour = useVanillaBehaviour;
	}

	@Override
	public Path getPathToPos(BlockPos pos, int p_179680_2_) {
		this.targetPosition = pos;
		return super.getPathToPos(pos, p_179680_2_);
	}

	@Override
	public Path getPathToEntity(Entity entityIn, int p_75494_2_) {
		this.targetPosition = entityIn.getPosition();
		return super.getPathToEntity(entityIn, p_75494_2_);
	}

	@Override
	public boolean tryMoveToEntityLiving(Entity entityIn, double speedIn) {
		Path path = this.getPathToEntity(entityIn, 0);
		if(path != null) {
			return this.setPath(path, speedIn);
		} else {
			this.targetPosition = entityIn.getPosition();
			this.speed = speedIn;
			return true;
		}
	}

	@Override
	public void tick() {
		if(!this.noPath()) {
			super.tick();
		} else {
			if(this.targetPosition != null && this.useVanillaBehaviour) {
				// FORGE: Fix MC-94054
				if(!this.targetPosition.withinDistance(this.entity.getPositionVec(), Math.max((double) this.entity.getWidth(), 1.0D)) && (!(this.entity.getPosY() > (double) this.targetPosition.getY()) || !(new BlockPos((double) this.targetPosition.getX(), this.entity.getPosY(), (double) this.targetPosition.getZ())).withinDistance(this.entity.getPositionVec(), Math.max((double) this.entity.getWidth(), 1.0D)))) {
					this.entity.getMoveHelper().setMoveTo((double) this.targetPosition.getX(), (double) this.targetPosition.getY(), (double) this.targetPosition.getZ(), this.speed);
				} else {
					this.targetPosition = null;
				}
			}

		}
	}
}

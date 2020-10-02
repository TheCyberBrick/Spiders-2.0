package tcb.spiderstpo.client;

import net.minecraft.client.model.ModelBase;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderLiving;
import net.minecraft.client.renderer.entity.RenderManager;
import tcb.spiderstpo.common.entity.mob.AbstractClimberEntity;

public abstract class AbstractClimberRenderer<T extends AbstractClimberEntity> extends RenderLiving<T> {
	public AbstractClimberRenderer(RenderManager rendermanagerIn, ModelBase modelbaseIn, float shadowsizeIn) {
		super(rendermanagerIn, modelbaseIn, shadowsizeIn);
	}

	@Override
	public void doRender(T entity, double x, double y, double z, float entityYaw, float partialTicks) {
		AbstractClimberEntity.Orientation orientation = entity.getOrientation(partialTicks);

		float verticalOffset = entity.getVerticalOffset(partialTicks);

		float rox = (float) (entity.prevStickingOffsetX + (entity.stickingOffsetX - entity.prevStickingOffsetX) * partialTicks) - (float) orientation.normal.x * verticalOffset;
		float roy = (float) (entity.prevStickingOffsetY + (entity.stickingOffsetY - entity.prevStickingOffsetY) * partialTicks) - (float) orientation.normal.y * verticalOffset;
		float roz = (float) (entity.prevStickingOffsetZ + (entity.stickingOffsetZ - entity.prevStickingOffsetZ) * partialTicks) - (float) orientation.normal.z * verticalOffset;

		x += rox;
		y += roy;
		z += roz;

		super.doRender(entity, x, y, z, entityYaw, partialTicks);
	}

	@Override
	protected void applyRotations(T entity, float ageInTicks, float rotationYaw, float partialTicks) {
		AbstractClimberEntity.Orientation orientation = entity.getOrientation(1);

		AbstractClimberEntity.Orientation interpolatedOrientation = entity.getOrientation(partialTicks);

		GlStateManager.rotate(interpolatedOrientation.yaw, 0, 1, 0);
		GlStateManager.rotate(interpolatedOrientation.pitch, 1, 0, 0);
		GlStateManager.rotate((float)Math.signum(0.5f - orientation.componentY - orientation.componentZ - orientation.componentX) * interpolatedOrientation.yaw, 0, 1, 0);

		this.applyLocalRotations(entity, ageInTicks, rotationYaw, partialTicks);
	}

	protected void applyLocalRotations(T entity, float ageInTicks, float rotationYaw, float partialTicks) {
		super.applyRotations(entity, ageInTicks, rotationYaw, partialTicks);
	}
}

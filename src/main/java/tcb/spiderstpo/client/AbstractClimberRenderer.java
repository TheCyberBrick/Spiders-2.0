package tcb.spiderstpo.client;

import org.lwjgl.opengl.GL11;

import net.minecraft.client.model.ModelBase;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderLiving;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import tcb.spiderstpo.common.entity.mob.AbstractClimberEntity;
import tcb.spiderstpo.compat.mobends.MoBendsCompat;

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

		if(this.getRenderManager().isDebugBoundingBox()) {
			GlStateManager.pushMatrix();
			GlStateManager.translate(x, y, z);

			GlStateManager.disableTexture2D();
			GlStateManager.color(1, 1, 1, 1);
			
			RenderGlobal.drawSelectionBoundingBox(new AxisAlignedBB(0, 0, 0, 0, 0, 0).grow(0.2f), 1.0f, 1.0f, 1.0f, 1.0f);

			for(int i = 0; i < AbstractClimberEntity.PATHING_TARGETS.size(); i++) {
				BlockPos pathingTarget = entity.getPathingTarget(i);
				if(pathingTarget != null) {
					double rx = entity.prevPosX + (entity.posX - entity.prevPosX) * partialTicks;
					double ry = entity.prevPosY + (entity.posY - entity.prevPosY) * partialTicks;
					double rz = entity.prevPosZ + (entity.posZ - entity.prevPosZ) * partialTicks;

					if(i == 0) {
						RenderGlobal.drawSelectionBoundingBox(new AxisAlignedBB(pathingTarget).offset(-rx - rox, -ry - roy, -rz - roz), 1.0f, 0.0f, 0.0f, 1.0f);
					} else {
						RenderGlobal.drawSelectionBoundingBox(new AxisAlignedBB(pathingTarget).offset(-rx - rox, -ry - roy, -rz - roz), 1.0f, i / (float)(AbstractClimberEntity.PATHING_TARGETS.size() - 1), 0.0f, 1.0f);
					}
				}
			}

			Tessellator tessellator = Tessellator.getInstance();
			BufferBuilder builder = tessellator.getBuffer();

			builder.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
			builder.pos(0, 0, 0).color(0, 1, 1, 1).endVertex();
			builder.pos((float) orientation.normal.x * 2, (float) orientation.normal.y * 2, (float) orientation.normal.z * 2).color(1.0f, 0.0f, 1.0f, 1.0f).endVertex();
			tessellator.draw();

			RenderGlobal.drawSelectionBoundingBox(new AxisAlignedBB(0, 0, 0, 0, 0, 0).offset((float) orientation.normal.x * 2, (float) orientation.normal.y * 2, (float) orientation.normal.z * 2).grow(0.025f), 1.0f, 0.0f, 1.0f, 1.0f);

			GlStateManager.pushMatrix();

			GlStateManager.translate(-rox, -roy, -roz);

			builder.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
			builder.pos(0, entity.height * 0.5f, 0).color(0, 1, 1, 1).endVertex();
			builder.pos((float) orientation.localX.x, entity.height * 0.5f + (float) orientation.localX.y, (float) orientation.localX.z).color(1.0f, 0.0f, 0.0f, 1.0f).endVertex();
			tessellator.draw();

			RenderGlobal.drawSelectionBoundingBox(new AxisAlignedBB(0, 0, 0, 0, 0, 0).offset((float) orientation.localX.x, entity.height * 0.5f + (float) orientation.localX.y, (float) orientation.localX.z).grow(0.025f), 1.0f, 0.0f, 0.0f, 1.0f);

			builder.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
			builder.pos(0, entity.height * 0.5f, 0).color(0, 1, 1, 1).endVertex();
			builder.pos((float) orientation.localY.x, entity.height * 0.5f + (float) orientation.localY.y, (float) orientation.localY.z).color(0.0f, 1.0f, 0.0f, 1.0f).endVertex();
			tessellator.draw();

			RenderGlobal.drawSelectionBoundingBox(new AxisAlignedBB(0, 0, 0, 0, 0, 0).offset((float) orientation.localY.x, entity.height * 0.5f + (float) orientation.localY.y, (float) orientation.localY.z).grow(0.025f), 0.0f, 1.0f, 0.0f, 1.0f);

			builder.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
			builder.pos(0, entity.height * 0.5f, 0).color(0, 1, 1, 1).endVertex();
			builder.pos((float) orientation.localZ.x, entity.height * 0.5f + (float) orientation.localZ.y, (float) orientation.localZ.z).color(0.0f, 0.0f, 1.0f, 1.0f).endVertex();
			tessellator.draw();

			RenderGlobal.drawSelectionBoundingBox(new AxisAlignedBB(0, 0, 0, 0, 0, 0).offset((float) orientation.localZ.x, entity.height * 0.5f + (float) orientation.localZ.y, (float) orientation.localZ.z).grow(0.025f), 0.0f, 0.0f, 1.0f, 1.0f);

			GlStateManager.popMatrix();

			GlStateManager.color(1, 1, 1, 1);
			GlStateManager.enableTexture2D();
			
			GlStateManager.popMatrix();
		}

	}

	@Override
	protected void applyRotations(T entity, float ageInTicks, float rotationYaw, float partialTicks) {
		AbstractClimberEntity.Orientation orientation = entity.getOrientation(1);

		AbstractClimberEntity.Orientation interpolatedOrientation = entity.getOrientation(partialTicks);

		GlStateManager.rotate(interpolatedOrientation.yaw, 0, 1, 0);
		GlStateManager.rotate(interpolatedOrientation.pitch, 1, 0, 0);
		GlStateManager.rotate((float)Math.signum(0.5f - orientation.componentY - orientation.componentZ - orientation.componentX) * interpolatedOrientation.yaw, 0, 1, 0);

		if(MoBendsCompat.isEnabled()) {
			GlStateManager.translate(0, (1.0f - Math.abs(interpolatedOrientation.normal.y)) * -0.2f + (MathHelper.clamp(interpolatedOrientation.normal.y, -1.0f, -0.5f) + 0.5f) * 0.25f, 0);
		}

		this.applyLocalRotations(entity, ageInTicks, rotationYaw, partialTicks);
	}

	protected void applyLocalRotations(T entity, float ageInTicks, float rotationYaw, float partialTicks) {
		super.applyRotations(entity, ageInTicks, rotationYaw, partialTicks);
	}
}

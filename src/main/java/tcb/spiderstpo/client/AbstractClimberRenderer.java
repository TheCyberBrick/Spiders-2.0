package tcb.spiderstpo.client;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.model.EntityModel;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import tcb.spiderstpo.common.entity.mob.AbstractClimberEntity;

public abstract class AbstractClimberRenderer<T extends AbstractClimberEntity, M extends EntityModel<T>> extends MobRenderer<T, M> {
	public AbstractClimberRenderer(EntityRendererManager renderManagerIn, M entityModelIn, float shadowSizeIn) {
		super(renderManagerIn, entityModelIn, shadowSizeIn);
	}

	@Override
	public void render(T entity, float entityYaw, float partialTicks, MatrixStack matrixStackIn, IRenderTypeBuffer bufferIn, int packedLightIn) {
		AbstractClimberEntity.Orientation orientation = entity.getOrientation(partialTicks);

		float verticalOffset = entity.getVerticalOffset(partialTicks);

		float rox = (float) (entity.prevStickingOffsetX + (entity.stickingOffsetX - entity.prevStickingOffsetX) * partialTicks) - (float) orientation.normal.x * verticalOffset;
		float roy = (float) (entity.prevStickingOffsetY + (entity.stickingOffsetY - entity.prevStickingOffsetY) * partialTicks) - (float) orientation.normal.y * verticalOffset;
		float roz = (float) (entity.prevStickingOffsetZ + (entity.stickingOffsetZ - entity.prevStickingOffsetZ) * partialTicks) - (float) orientation.normal.z * verticalOffset;

		matrixStackIn.push();

		matrixStackIn.translate(rox, roy, roz);

		super.render(entity, entityYaw, partialTicks, matrixStackIn, bufferIn, packedLightIn);

		if (this.getRenderManager().isDebugBoundingBox()) {
			WorldRenderer.drawBoundingBox(matrixStackIn, bufferIn.getBuffer(RenderType.LINES), new AxisAlignedBB(0, 0, 0, 0, 0, 0).grow(0.2f), 1.0f, 1.0f, 1.0f, 1.0f);

			for (int i = 0; i < AbstractClimberEntity.PATHING_TARGETS.size(); i++) {
				BlockPos pathingTarget = entity.getPathingTarget(i);
				if (pathingTarget != null) {
					double rx = entity.prevPosX + (entity.getPosX() - entity.prevPosX) * partialTicks;
					double ry = entity.prevPosY + (entity.getPosY() - entity.prevPosY) * partialTicks;
					double rz = entity.prevPosZ + (entity.getPosZ() - entity.prevPosZ) * partialTicks;

					if (i == 0) {
						WorldRenderer.drawBoundingBox(matrixStackIn, bufferIn.getBuffer(RenderType.LINES), new AxisAlignedBB(pathingTarget).offset(-rx - rox, -ry - roy, -rz - roz), 1.0f, 0.0f, 0.0f, 1.0f);
					} else {
						WorldRenderer.drawBoundingBox(matrixStackIn, bufferIn.getBuffer(RenderType.LINES), new AxisAlignedBB(pathingTarget).offset(-rx - rox, -ry - roy, -rz - roz), 1.0f, i / (float) (AbstractClimberEntity.PATHING_TARGETS.size() - 1), 0.0f, 1.0f);
					}
				}
			}

			Matrix4f matrix4f = matrixStackIn.getLast().getMatrix();
			IVertexBuilder builder = bufferIn.getBuffer(RenderType.LINES);

			builder.pos(matrix4f, 0, 0, 0).color(0, 1, 1, 1).endVertex();
			builder.pos(matrix4f, (float) orientation.normal.x * 2, (float) orientation.normal.y * 2, (float) orientation.normal.z * 2).color(1.0f, 0.0f, 1.0f, 1.0f).endVertex();

			WorldRenderer.drawBoundingBox(matrixStackIn, bufferIn.getBuffer(RenderType.LINES), new AxisAlignedBB(0, 0, 0, 0, 0, 0).offset((float) orientation.normal.x * 2, (float) orientation.normal.y * 2, (float) orientation.normal.z * 2).grow(0.025f), 1.0f, 0.0f, 1.0f, 1.0f);

			matrixStackIn.push();

			matrixStackIn.translate(-rox, -roy, -roz);

			matrix4f = matrixStackIn.getLast().getMatrix();

			builder.pos(matrix4f, 0, entity.getHeight() * 0.5f, 0).color(0, 1, 1, 1).endVertex();
			builder.pos(matrix4f, (float) orientation.localX.x, entity.getHeight() * 0.5f + (float) orientation.localX.y, (float) orientation.localX.z).color(1.0f, 0.0f, 0.0f, 1.0f).endVertex();

			WorldRenderer.drawBoundingBox(matrixStackIn, bufferIn.getBuffer(RenderType.LINES), new AxisAlignedBB(0, 0, 0, 0, 0, 0).offset((float) orientation.localX.x, entity.getHeight() * 0.5f + (float) orientation.localX.y, (float) orientation.localX.z).grow(0.025f), 1.0f, 0.0f, 0.0f, 1.0f);

			builder.pos(matrix4f, 0, entity.getHeight() * 0.5f, 0).color(0, 1, 1, 1).endVertex();
			builder.pos(matrix4f, (float) orientation.localY.x, entity.getHeight() * 0.5f + (float) orientation.localY.y, (float) orientation.localY.z).color(0.0f, 1.0f, 0.0f, 1.0f).endVertex();

			WorldRenderer.drawBoundingBox(matrixStackIn, bufferIn.getBuffer(RenderType.LINES), new AxisAlignedBB(0, 0, 0, 0, 0, 0).offset((float) orientation.localY.x, entity.getHeight() * 0.5f + (float) orientation.localY.y, (float) orientation.localY.z).grow(0.025f), 0.0f, 1.0f, 0.0f, 1.0f);

			builder.pos(matrix4f, 0, entity.getHeight() * 0.5f, 0).color(0, 1, 1, 1).endVertex();
			builder.pos(matrix4f, (float) orientation.localZ.x, entity.getHeight() * 0.5f + (float) orientation.localZ.y, (float) orientation.localZ.z).color(0.0f, 0.0f, 1.0f, 1.0f).endVertex();

			WorldRenderer.drawBoundingBox(matrixStackIn, bufferIn.getBuffer(RenderType.LINES), new AxisAlignedBB(0, 0, 0, 0, 0, 0).offset((float) orientation.localZ.x, entity.getHeight() * 0.5f + (float) orientation.localZ.y, (float) orientation.localZ.z).grow(0.025f), 0.0f, 0.0f, 1.0f, 1.0f);

			matrixStackIn.pop();
		}

		matrixStackIn.pop();
	}

	@Override
	protected void applyRotations(T entity, MatrixStack matrixStackIn, float ageInTicks, float rotationYaw, float partialTicks) {
		AbstractClimberEntity.Orientation orientation = entity.getOrientation(1);

		AbstractClimberEntity.Orientation interpolatedOrientation = entity.getOrientation(partialTicks);

		matrixStackIn.rotate(Vector3f.YP.rotationDegrees(interpolatedOrientation.yaw));
		matrixStackIn.rotate(Vector3f.XP.rotationDegrees(interpolatedOrientation.pitch));
		matrixStackIn.rotate(Vector3f.YP.rotationDegrees(Math.signum(0.5f - orientation.componentY - orientation.componentZ - orientation.componentX) * interpolatedOrientation.yaw));

		this.applyLocalRotations(entity, matrixStackIn, ageInTicks, rotationYaw, partialTicks);
	}

	protected void applyLocalRotations(T entity, MatrixStack matrixStackIn, float ageInTicks, float rotationYaw, float partialTicks) {
		super.applyRotations(entity, matrixStackIn, ageInTicks, rotationYaw, partialTicks);
	}
}

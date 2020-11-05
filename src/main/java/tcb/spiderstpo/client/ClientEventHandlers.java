package tcb.spiderstpo.client;

import java.util.List;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Matrix4f;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.math.vector.Vector3f;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import tcb.spiderstpo.common.entity.mob.IClimberEntity;
import tcb.spiderstpo.common.entity.mob.Orientation;
import tcb.spiderstpo.common.entity.mob.PathingTarget;

public class ClientEventHandlers {
	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onPreRenderLiving(RenderLivingEvent.Pre<?, ?> event) {
		LivingEntity entity = event.getEntity();

		if(entity instanceof IClimberEntity) {
			IClimberEntity climber = (IClimberEntity) entity;

			float partialTicks = event.getPartialRenderTick();
			MatrixStack matrixStack = event.getMatrixStack();

			Orientation orientation = climber.getOrientation();
			Orientation renderOrientation = climber.calculateOrientation(partialTicks);
			climber.setRenderOrientation(renderOrientation);

			float verticalOffset = climber.getVerticalOffset(partialTicks);

			float x = climber.getAttachmentOffset(Direction.Axis.X, partialTicks) - (float) renderOrientation.normal.x * verticalOffset;
			float y = climber.getAttachmentOffset(Direction.Axis.Y, partialTicks) - (float) renderOrientation.normal.y * verticalOffset;
			float z = climber.getAttachmentOffset(Direction.Axis.Z, partialTicks) - (float) renderOrientation.normal.z * verticalOffset;

			matrixStack.translate(x, y, z);

			matrixStack.rotate(Vector3f.YP.rotationDegrees(renderOrientation.yaw));
			matrixStack.rotate(Vector3f.XP.rotationDegrees(renderOrientation.pitch));
			matrixStack.rotate(Vector3f.YP.rotationDegrees((float) Math.signum(0.5f - orientation.componentY - orientation.componentZ - orientation.componentX) * renderOrientation.yaw));
		}
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void onPostRenderLiving(RenderLivingEvent.Post<?, ?> event) {
		LivingEntity entity = event.getEntity();

		if(entity instanceof IClimberEntity) {
			IClimberEntity climber = (IClimberEntity) entity;

			float partialTicks = event.getPartialRenderTick();
			MatrixStack matrixStack = event.getMatrixStack();
			IRenderTypeBuffer bufferIn = event.getBuffers();

			Orientation orientation = climber.getOrientation();
			Orientation renderOrientation = climber.getRenderOrientation();

			if(renderOrientation != null) {
				float verticalOffset = climber.getVerticalOffset(partialTicks);

				float x = climber.getAttachmentOffset(Direction.Axis.X, partialTicks) - (float) renderOrientation.normal.x * verticalOffset;
				float y = climber.getAttachmentOffset(Direction.Axis.Y, partialTicks) - (float) renderOrientation.normal.y * verticalOffset;
				float z = climber.getAttachmentOffset(Direction.Axis.Z, partialTicks) - (float) renderOrientation.normal.z * verticalOffset;

				matrixStack.rotate(Vector3f.YP.rotationDegrees(-(float) Math.signum(0.5f - orientation.componentY - orientation.componentZ - orientation.componentX) * renderOrientation.yaw));
				matrixStack.rotate(Vector3f.XP.rotationDegrees(-renderOrientation.pitch));
				matrixStack.rotate(Vector3f.YP.rotationDegrees(-renderOrientation.yaw));

				if(Minecraft.getInstance().getRenderManager().isDebugBoundingBox()) {
					WorldRenderer.drawBoundingBox(matrixStack, bufferIn.getBuffer(RenderType.LINES), new AxisAlignedBB(0, 0, 0, 0, 0, 0).grow(0.2f), 1.0f, 1.0f, 1.0f, 1.0f);

					double rx = entity.prevPosX + (entity.getPosX() - entity.prevPosX) * partialTicks;
					double ry = entity.prevPosY + (entity.getPosY() - entity.prevPosY) * partialTicks;
					double rz = entity.prevPosZ + (entity.getPosZ() - entity.prevPosZ) * partialTicks;

					Vector3d movementTarget = climber.getTrackedMovementTarget();

					if(movementTarget != null) {
						WorldRenderer.drawBoundingBox(matrixStack, bufferIn.getBuffer(RenderType.LINES), new AxisAlignedBB(movementTarget.getX() - 0.25f, movementTarget.getY() - 0.25f, movementTarget.getZ() - 0.25f, movementTarget.getX() + 0.25f, movementTarget.getY() + 0.25f, movementTarget.getZ() + 0.25f).offset(-rx - x, -ry - y, -rz - z), 0.0f, 1.0f, 1.0f, 1.0f);
					}

					List<PathingTarget> pathingTargets = climber.getTrackedPathingTargets();

					if(pathingTargets != null) {
						int i = 0;

						for(PathingTarget pathingTarget : pathingTargets) {
							BlockPos pos = pathingTarget.pos;

                            WorldRenderer.drawBoundingBox(matrixStack, bufferIn.getBuffer(RenderType.LINES), new AxisAlignedBB(pos).offset(-rx - x, -ry - y, -rz - z), 1.0f, i / (float) (pathingTargets.size() - 1), 0.0f, 0.15f);
							
							matrixStack.push();
							matrixStack.translate(pos.getX() + 0.5D - rx - x, pos.getY() + 0.5D - ry - y, pos.getZ() + 0.5D - rz - z);

							matrixStack.rotate(pathingTarget.side.getOpposite().getRotation());

							WorldRenderer.drawBoundingBox(matrixStack, bufferIn.getBuffer(RenderType.LINES), new AxisAlignedBB(-0.501D, -0.501D, -0.501D, 0.501D, -0.45D, 0.501D), 1.0f, i / (float) (pathingTargets.size() - 1), 0.0f, 1.0f);

							Matrix4f matrix4f = matrixStack.getLast().getMatrix();
							IVertexBuilder builder = bufferIn.getBuffer(RenderType.LINES);

							builder.pos(matrix4f, -0.501f, -0.45f, -0.501f).color(1.0f, i / (float) (pathingTargets.size() - 1), 0.0f, 1.0f).endVertex();
							builder.pos(matrix4f, 0.501f, -0.45f, 0.501f).color(1.0f, i / (float) (pathingTargets.size() - 1), 0.0f, 1.0f).endVertex();
							builder.pos(matrix4f, -0.501f, -0.45f, 0.501f).color(1.0f, i / (float) (pathingTargets.size() - 1), 0.0f, 1.0f).endVertex();
							builder.pos(matrix4f, 0.501f, -0.45f, -0.501f).color(1.0f, i / (float) (pathingTargets.size() - 1), 0.0f, 1.0f).endVertex();

							matrixStack.pop();

							i++;
						}
					}

					Matrix4f matrix4f = matrixStack.getLast().getMatrix();
					IVertexBuilder builder = bufferIn.getBuffer(RenderType.LINES);

					builder.pos(matrix4f, 0, 0, 0).color(0, 1, 1, 1).endVertex();
					builder.pos(matrix4f, (float) orientation.normal.x * 2, (float) orientation.normal.y * 2, (float) orientation.normal.z * 2).color(1.0f, 0.0f, 1.0f, 1.0f).endVertex();

					WorldRenderer.drawBoundingBox(matrixStack, bufferIn.getBuffer(RenderType.LINES), new AxisAlignedBB(0, 0, 0, 0, 0, 0).offset((float) orientation.normal.x * 2, (float) orientation.normal.y * 2, (float) orientation.normal.z * 2).grow(0.025f), 1.0f, 0.0f, 1.0f, 1.0f);

					matrixStack.push();

					matrixStack.translate(-x, -y, -z);

					matrix4f = matrixStack.getLast().getMatrix();

					builder.pos(matrix4f, 0, entity.getHeight() * 0.5f, 0).color(0, 1, 1, 1).endVertex();
					builder.pos(matrix4f, (float) orientation.localX.x, entity.getHeight() * 0.5f + (float) orientation.localX.y, (float) orientation.localX.z).color(1.0f, 0.0f, 0.0f, 1.0f).endVertex();

					WorldRenderer.drawBoundingBox(matrixStack, bufferIn.getBuffer(RenderType.LINES), new AxisAlignedBB(0, 0, 0, 0, 0, 0).offset((float) orientation.localX.x, entity.getHeight() * 0.5f + (float) orientation.localX.y, (float) orientation.localX.z).grow(0.025f), 1.0f, 0.0f, 0.0f, 1.0f);

					builder.pos(matrix4f, 0, entity.getHeight() * 0.5f, 0).color(0, 1, 1, 1).endVertex();
					builder.pos(matrix4f, (float) orientation.localY.x, entity.getHeight() * 0.5f + (float) orientation.localY.y, (float) orientation.localY.z).color(0.0f, 1.0f, 0.0f, 1.0f).endVertex();

					WorldRenderer.drawBoundingBox(matrixStack, bufferIn.getBuffer(RenderType.LINES), new AxisAlignedBB(0, 0, 0, 0, 0, 0).offset((float) orientation.localY.x, entity.getHeight() * 0.5f + (float) orientation.localY.y, (float) orientation.localY.z).grow(0.025f), 0.0f, 1.0f, 0.0f, 1.0f);

					builder.pos(matrix4f, 0, entity.getHeight() * 0.5f, 0).color(0, 1, 1, 1).endVertex();
					builder.pos(matrix4f, (float) orientation.localZ.x, entity.getHeight() * 0.5f + (float) orientation.localZ.y, (float) orientation.localZ.z).color(0.0f, 0.0f, 1.0f, 1.0f).endVertex();

					WorldRenderer.drawBoundingBox(matrixStack, bufferIn.getBuffer(RenderType.LINES), new AxisAlignedBB(0, 0, 0, 0, 0, 0).offset((float) orientation.localZ.x, entity.getHeight() * 0.5f + (float) orientation.localZ.y, (float) orientation.localZ.z).grow(0.025f), 0.0f, 0.0f, 1.0f, 1.0f);

					matrixStack.pop();
				}

				matrixStack.translate(-x, -y, -z);
			}
		}
	}
}

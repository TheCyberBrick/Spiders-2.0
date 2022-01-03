package tcb.spiderstpo.client;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.BlockPos;
import com.mojang.math.Matrix4f;
import net.minecraft.world.phys.Vec3;
import com.mojang.math.Vector3f;
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

			float partialTicks = event.getPartialTick();
			PoseStack matrixStack = event.getPoseStack();

			Orientation orientation = climber.getOrientation();
			Orientation renderOrientation = climber.calculateOrientation(partialTicks);
			climber.setRenderOrientation(renderOrientation);

			float verticalOffset = climber.getVerticalOffset(partialTicks);

			float x = climber.getAttachmentOffset(Direction.Axis.X, partialTicks) - (float) renderOrientation.normal.x * verticalOffset;
			float y = climber.getAttachmentOffset(Direction.Axis.Y, partialTicks) - (float) renderOrientation.normal.y * verticalOffset;
			float z = climber.getAttachmentOffset(Direction.Axis.Z, partialTicks) - (float) renderOrientation.normal.z * verticalOffset;

			matrixStack.translate(x, y, z);

			matrixStack.mulPose(Vector3f.YP.rotationDegrees(renderOrientation.yaw));
			matrixStack.mulPose(Vector3f.XP.rotationDegrees(renderOrientation.pitch));
			matrixStack.mulPose(Vector3f.YP.rotationDegrees((float) Math.signum(0.5f - orientation.componentY - orientation.componentZ - orientation.componentX) * renderOrientation.yaw));
		}
	}

	@SubscribeEvent(priority = EventPriority.HIGHEST)
	public static void onPostRenderLiving(RenderLivingEvent.Post<?, ?> event) {
		LivingEntity entity = event.getEntity();

		if(entity instanceof IClimberEntity) {
			IClimberEntity climber = (IClimberEntity) entity;

			float partialTicks = event.getPartialTick();
			PoseStack matrixStack = event.getPoseStack();
			MultiBufferSource bufferIn = event.getMultiBufferSource();

			Orientation orientation = climber.getOrientation();
			Orientation renderOrientation = climber.getRenderOrientation();

			if(renderOrientation != null) {
				float verticalOffset = climber.getVerticalOffset(partialTicks);

				float x = climber.getAttachmentOffset(Direction.Axis.X, partialTicks) - (float) renderOrientation.normal.x * verticalOffset;
				float y = climber.getAttachmentOffset(Direction.Axis.Y, partialTicks) - (float) renderOrientation.normal.y * verticalOffset;
				float z = climber.getAttachmentOffset(Direction.Axis.Z, partialTicks) - (float) renderOrientation.normal.z * verticalOffset;

				matrixStack.mulPose(Vector3f.YP.rotationDegrees(-(float) Math.signum(0.5f - orientation.componentY - orientation.componentZ - orientation.componentX) * renderOrientation.yaw));
				matrixStack.mulPose(Vector3f.XP.rotationDegrees(-renderOrientation.pitch));
				matrixStack.mulPose(Vector3f.YP.rotationDegrees(-renderOrientation.yaw));

				if(Minecraft.getInstance().getEntityRenderDispatcher().shouldRenderHitBoxes()) {
					LevelRenderer.renderLineBox(matrixStack, bufferIn.getBuffer(RenderType.LINES), new AABB(0, 0, 0, 0, 0, 0).inflate(0.2f), 1.0f, 1.0f, 1.0f, 1.0f);

					double rx = entity.xo + (entity.getX() - entity.xo) * partialTicks;
					double ry = entity.yo + (entity.getY() - entity.yo) * partialTicks;
					double rz = entity.zo + (entity.getZ() - entity.zo) * partialTicks;

					Vec3 movementTarget = climber.getTrackedMovementTarget();

					if(movementTarget != null) {
						LevelRenderer.renderLineBox(matrixStack, bufferIn.getBuffer(RenderType.LINES), new AABB(movementTarget.x() - 0.25f, movementTarget.y() - 0.25f, movementTarget.z() - 0.25f, movementTarget.x() + 0.25f, movementTarget.y() + 0.25f, movementTarget.z() + 0.25f).move(-rx - x, -ry - y, -rz - z), 0.0f, 1.0f, 1.0f, 1.0f);
					}

					List<PathingTarget> pathingTargets = climber.getTrackedPathingTargets();

					if(pathingTargets != null) {
						int i = 0;

						for(PathingTarget pathingTarget : pathingTargets) {
							BlockPos pos = pathingTarget.pos;

                            LevelRenderer.renderLineBox(matrixStack, bufferIn.getBuffer(RenderType.LINES), new AABB(pos).move(-rx - x, -ry - y, -rz - z), 1.0f, i / (float) (pathingTargets.size() - 1), 0.0f, 0.15f);
							
							matrixStack.pushPose();
							matrixStack.translate(pos.getX() + 0.5D - rx - x, pos.getY() + 0.5D - ry - y, pos.getZ() + 0.5D - rz - z);

							matrixStack.mulPose(pathingTarget.side.getOpposite().getRotation());

							LevelRenderer.renderLineBox(matrixStack, bufferIn.getBuffer(RenderType.LINES), new AABB(-0.501D, -0.501D, -0.501D, 0.501D, -0.45D, 0.501D), 1.0f, i / (float) (pathingTargets.size() - 1), 0.0f, 1.0f);

							Matrix4f matrix4f = matrixStack.last().pose();
							VertexConsumer builder = bufferIn.getBuffer(RenderType.LINES);

							builder.vertex(matrix4f, -0.501f, -0.45f, -0.501f).color(1.0f, i / (float) (pathingTargets.size() - 1), 0.0f, 1.0f).endVertex();
							builder.vertex(matrix4f, 0.501f, -0.45f, 0.501f).color(1.0f, i / (float) (pathingTargets.size() - 1), 0.0f, 1.0f).endVertex();
							builder.vertex(matrix4f, -0.501f, -0.45f, 0.501f).color(1.0f, i / (float) (pathingTargets.size() - 1), 0.0f, 1.0f).endVertex();
							builder.vertex(matrix4f, 0.501f, -0.45f, -0.501f).color(1.0f, i / (float) (pathingTargets.size() - 1), 0.0f, 1.0f).endVertex();

							matrixStack.popPose();

							i++;
						}
					}

					Matrix4f matrix4f = matrixStack.last().pose();
					VertexConsumer builder = bufferIn.getBuffer(RenderType.LINES);

					builder.vertex(matrix4f, 0, 0, 0).color(0, 1, 1, 1).endVertex();
					builder.vertex(matrix4f, (float) orientation.normal.x * 2, (float) orientation.normal.y * 2, (float) orientation.normal.z * 2).color(1.0f, 0.0f, 1.0f, 1.0f).endVertex();

					LevelRenderer.renderLineBox(matrixStack, bufferIn.getBuffer(RenderType.LINES), new AABB(0, 0, 0, 0, 0, 0).move((float) orientation.normal.x * 2, (float) orientation.normal.y * 2, (float) orientation.normal.z * 2).inflate(0.025f), 1.0f, 0.0f, 1.0f, 1.0f);

					matrixStack.pushPose();

					matrixStack.translate(-x, -y, -z);

					matrix4f = matrixStack.last().pose();

					builder.vertex(matrix4f, 0, entity.getBbHeight() * 0.5f, 0).color(0, 1, 1, 1).endVertex();
					builder.vertex(matrix4f, (float) orientation.localX.x, entity.getBbHeight() * 0.5f + (float) orientation.localX.y, (float) orientation.localX.z).color(1.0f, 0.0f, 0.0f, 1.0f).endVertex();

					LevelRenderer.renderLineBox(matrixStack, bufferIn.getBuffer(RenderType.LINES), new AABB(0, 0, 0, 0, 0, 0).move((float) orientation.localX.x, entity.getBbHeight() * 0.5f + (float) orientation.localX.y, (float) orientation.localX.z).inflate(0.025f), 1.0f, 0.0f, 0.0f, 1.0f);

					builder.vertex(matrix4f, 0, entity.getBbHeight() * 0.5f, 0).color(0, 1, 1, 1).endVertex();
					builder.vertex(matrix4f, (float) orientation.localY.x, entity.getBbHeight() * 0.5f + (float) orientation.localY.y, (float) orientation.localY.z).color(0.0f, 1.0f, 0.0f, 1.0f).endVertex();

					LevelRenderer.renderLineBox(matrixStack, bufferIn.getBuffer(RenderType.LINES), new AABB(0, 0, 0, 0, 0, 0).move((float) orientation.localY.x, entity.getBbHeight() * 0.5f + (float) orientation.localY.y, (float) orientation.localY.z).inflate(0.025f), 0.0f, 1.0f, 0.0f, 1.0f);

					builder.vertex(matrix4f, 0, entity.getBbHeight() * 0.5f, 0).color(0, 1, 1, 1).endVertex();
					builder.vertex(matrix4f, (float) orientation.localZ.x, entity.getBbHeight() * 0.5f + (float) orientation.localZ.y, (float) orientation.localZ.z).color(0.0f, 0.0f, 1.0f, 1.0f).endVertex();

					LevelRenderer.renderLineBox(matrixStack, bufferIn.getBuffer(RenderType.LINES), new AABB(0, 0, 0, 0, 0, 0).move((float) orientation.localZ.x, entity.getBbHeight() * 0.5f + (float) orientation.localZ.y, (float) orientation.localZ.z).inflate(0.025f), 0.0f, 0.0f, 1.0f, 1.0f);

					matrixStack.popPose();
				}

				matrixStack.translate(-x, -y, -z);
			}
		}
	}
}

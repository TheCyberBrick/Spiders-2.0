package tcb.spiderstpo.common.entity.mob;

import org.apache.commons.lang3.tuple.Pair;

import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class Orientation {
	public final Vec3d normal, localZ, localY, localX;
	public final float componentZ, componentY, componentX, yaw, pitch;

	public Orientation(Vec3d normal, Vec3d localZ, Vec3d localY, Vec3d localX, float componentZ, float componentY, float componentX, float yaw, float pitch) {
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

	public Vec3d getGlobal(Vec3d local) {
		return this.localX.scale(local.x).add(this.localY.scale(local.y)).add(this.localZ.scale(local.z));
	}

	public Vec3d getGlobal(float yaw, float pitch) {
		float cy = MathHelper.cos(yaw * 0.017453292F);
		float sy = MathHelper.sin(yaw * 0.017453292F);
		float cp = -MathHelper.cos(-pitch * 0.017453292F);
		float sp = MathHelper.sin(-pitch * 0.017453292F);
		return this.localX.scale(sy * cp).add(this.localY.scale(sp)).add(this.localZ.scale(cy * cp));
	}

	public Vec3d getLocal(Vec3d global) {
		return new Vec3d(this.localX.dotProduct(global), this.localY.dotProduct(global), this.localZ.dotProduct(global));
	}

	public Pair<Float, Float> getLocalRotation(Vec3d global) {
		Vec3d local = this.getLocal(global);

		float yaw = (float) Math.toDegrees(MathHelper.atan2(local.x, local.z)) + 180.0f;
		float pitch = (float) -Math.toDegrees(MathHelper.atan2(local.y, MathHelper.sqrt(local.x * local.x + local.z * local.z)));

		return Pair.of(yaw, pitch);
	}
}
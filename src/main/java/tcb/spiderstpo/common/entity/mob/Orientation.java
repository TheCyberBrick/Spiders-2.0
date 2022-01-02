package tcb.spiderstpo.common.entity.mob;

import org.apache.commons.lang3.tuple.Pair;

import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3d;

public class Orientation {
	public final Vector3d normal, localZ, localY, localX;
	public final float componentZ, componentY, componentX, yaw, pitch;

	public Orientation(Vector3d normal, Vector3d localZ, Vector3d localY, Vector3d localX, float componentZ, float componentY, float componentX, float yaw, float pitch) {
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

	public Vector3d getGlobal(Vector3d local) {
		return this.localX.scale(local.x).add(this.localY.scale(local.y)).add(this.localZ.scale(local.z));
	}

	public Vector3d getGlobal(float yaw, float pitch) {
		float cy = MathHelper.cos(yaw * 0.017453292F);
		float sy = MathHelper.sin(yaw * 0.017453292F);
		float cp = -MathHelper.cos(-pitch * 0.017453292F);
		float sp = MathHelper.sin(-pitch * 0.017453292F);
		return this.localX.scale(sy * cp).add(this.localY.scale(sp)).add(this.localZ.scale(cy * cp));
	}

	public Vector3d getLocal(Vector3d global) {
		return new Vector3d(this.localX.dot(global), this.localY.dot(global), this.localZ.dot(global));
	}

	public Pair<Float, Float> getLocalRotation(Vector3d global) {
		Vector3d local = this.getLocal(global);

		float yaw = (float) Math.toDegrees(MathHelper.atan2(local.x, local.z)) + 180.0f;
		float pitch = (float) -Math.toDegrees(MathHelper.atan2(local.y, MathHelper.sqrt(local.x * local.x + local.z * local.z)));

		return Pair.of(yaw, pitch);
	}
}
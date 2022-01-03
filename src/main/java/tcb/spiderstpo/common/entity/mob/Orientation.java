package tcb.spiderstpo.common.entity.mob;

import org.apache.commons.lang3.tuple.Pair;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public class Orientation {
	public final Vec3 normal, localZ, localY, localX;
	public final float componentZ, componentY, componentX, yaw, pitch;

	public Orientation(Vec3 normal, Vec3 localZ, Vec3 localY, Vec3 localX, float componentZ, float componentY, float componentX, float yaw, float pitch) {
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

	public Vec3 getGlobal(Vec3 local) {
		return this.localX.scale(local.x).add(this.localY.scale(local.y)).add(this.localZ.scale(local.z));
	}

	public Vec3 getGlobal(float yaw, float pitch) {
		float cy = Mth.cos(yaw * 0.017453292F);
		float sy = Mth.sin(yaw * 0.017453292F);
		float cp = -Mth.cos(-pitch * 0.017453292F);
		float sp = Mth.sin(-pitch * 0.017453292F);
		return this.localX.scale(sy * cp).add(this.localY.scale(sp)).add(this.localZ.scale(cy * cp));
	}

	public Vec3 getLocal(Vec3 global) {
		return new Vec3(this.localX.dot(global), this.localY.dot(global), this.localZ.dot(global));
	}

	public Pair<Float, Float> getLocalRotation(Vec3 global) {
		Vec3 local = this.getLocal(global);

		float yaw = (float) Math.toDegrees(Mth.atan2(local.x, local.z)) + 180.0f;
		float pitch = (float) -Math.toDegrees(Mth.atan2(local.y, Mth.sqrt((float) (local.x * local.x + local.z * local.z))));

		return Pair.of(yaw, pitch);
	}
}
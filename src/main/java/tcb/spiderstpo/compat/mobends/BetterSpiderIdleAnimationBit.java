package tcb.spiderstpo.compat.mobends;

import goblinbob.mobends.core.animation.bit.AnimationBit;
import goblinbob.mobends.core.client.event.DataUpdateHandler;
import goblinbob.mobends.core.util.GUtil;
import goblinbob.mobends.standard.data.SpiderData;
import net.minecraft.entity.monster.EntitySpider;
import net.minecraft.util.math.MathHelper;
import tcb.spiderstpo.common.entity.mob.BetterSpiderEntity;

/**
 * Original file SpiderIdleAnimationBit.java - modified to support BetterSpiderEntity.
 * 
 * MIT License
 * 
 * Copyright (c) 2017 Iwo Plaza
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
public class BetterSpiderIdleAnimationBit extends AnimationBit<BetterSpiderData>
{
	protected static final float KNEEL_DURATION = 10F;
	
	@Override
	public String[] getActions(BetterSpiderData entityData)
	{
		return new String[] { "idle" };
	}

	@Override
	public void onPlay(BetterSpiderData data)
	{
		super.onPlay(data);
	}

	@Override
	public void perform(BetterSpiderData data)
	{
		final float ticks = DataUpdateHandler.getTicks();
		final float pt = DataUpdateHandler.partialTicks;
		BetterSpiderEntity spider = data.getEntity();
		
		final float headYaw = data.headYaw.get();
		final float headPitch = data.headPitch.get();
		
		double groundLevel = Math.sin(ticks * 0.1F) * 0.5 + (MathHelper.sqrt(spider.stickingOffsetX * spider.stickingOffsetX + spider.stickingOffsetY * spider.stickingOffsetY + spider.stickingOffsetZ * spider.stickingOffsetZ) - spider.getVerticalOffset(1)) * 16;
		final float touchdown = Math.min(data.getTicksAfterTouchdown() / KNEEL_DURATION, 1.0F);
		
		if (touchdown < 1.0F)
		{
			final float preBounce = 0.0F;
			float touchdownInv = 1.0F - touchdown;
			groundLevel += Math.sin((touchdown * (1+preBounce) - preBounce) * Math.PI * 2) * 4.0F * touchdownInv;
		}
		
		data.spiderHead.rotation.orientInstantX(headPitch);
		data.spiderHead.rotation.rotateY(headYaw).finish();

    	final double bodyX = Math.sin(ticks * 0.2F) * 0.4;
    	final double bodyZ = Math.cos(ticks * 0.2F) * 0.4;
    	
        for (BetterSpiderData.Limb limb : data.limbs)
        {
        	BetterSpiderData.IKResult ikResult = limb.solveIK(bodyX, bodyZ, pt);
        	double deviation = GUtil.getRadianDifference(limb.getNeutralYaw(), ikResult.xzAngle + Math.PI/2);
        	
        	if (deviation > 0.9 || ikResult.xzDistance * 0.0625 > 1.2)
        	{
        		limb.adjustToNeutralPosition();
        	}
        	
        	limb.applyIK(ikResult, groundLevel, 4, pt);
        }

		// Makes the spider move it's front limbs to resemble
		// 'feeling' the ground.
		if (spider.ticksExisted % 100 < 10)
		{
			data.limbs[6].adjustToLocalPosition(0, 1.5, 0.2F);
			data.limbs[7].adjustToLocalPosition(0, 1.5, 0.2F);
		}

		float climbingRotation = 0;
		float renderRotationY = MathHelper.wrapDegrees(spider.rotationYaw - data.headYaw.get() - climbingRotation);

		data.localOffset.slideToZero();
		data.globalOffset.set((float) bodyX, (float) -groundLevel, (float) -bodyZ);
		data.centerRotation.orientZero();
		data.renderRotation.orientZero();
	}
}
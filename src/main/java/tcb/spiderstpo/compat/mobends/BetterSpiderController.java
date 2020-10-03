package tcb.spiderstpo.compat.mobends;

import goblinbob.mobends.core.animation.bit.AnimationBit;
import goblinbob.mobends.core.animation.controller.IAnimationController;
import goblinbob.mobends.core.animation.layer.HardAnimationLayer;
import goblinbob.mobends.core.math.SmoothOrientation;
import goblinbob.mobends.standard.animation.bit.spider.*;
import goblinbob.mobends.standard.data.SpiderData;
import net.minecraft.entity.monster.EntitySpider;
import tcb.spiderstpo.common.entity.mob.BetterSpiderEntity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Original file SpiderController.java - modified to support BetterSpiderEntity.
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
/**
 * This is an animation controller for a spider instance.
 * It's a part of the EntityData structure.
 *
 * @author Iwo Plaza
 *
 */
public class BetterSpiderController implements IAnimationController<BetterSpiderData>
{

	protected HardAnimationLayer<BetterSpiderData> layerBase = new HardAnimationLayer<>();
	protected AnimationBit<BetterSpiderData> bitIdle = new BetterSpiderIdleAnimationBit();
	protected AnimationBit<BetterSpiderData> bitMove = new BetterSpiderMoveAnimationBit();
	protected AnimationBit<BetterSpiderData> bitJump = new BetterSpiderJumpAnimationBit();
	protected AnimationBit<BetterSpiderData> bitDeath = new BetterSpiderDeathAnimationBit();
	protected AnimationBit<BetterSpiderData> bitClimb = new BetterSpiderCrawlAnimationBit();

	protected boolean resetAfterJumped = false;

	@Override
	public Collection<String> perform(BetterSpiderData spiderData)
	{
		final BetterSpiderEntity spider = spiderData.getEntity();

		if (spider.getHealth() <= 0F)
		{
			this.layerBase.playOrContinueBit(this.bitDeath, spiderData);
		}
		else
		{
			if (Math.abs(spider.getOrientation(1).normal.y) < 0.5f)
			{
				this.layerBase.playOrContinueBit(bitClimb, spiderData);
			}
			else
			{
				if (!spiderData.isOnGround() || spiderData.getTicksAfterTouchdown() < 1)
				{
					this.layerBase.playOrContinueBit(bitJump, spiderData);

					if (resetAfterJumped)
						resetAfterJumped = false;
				}
				else
				{
					if (!resetAfterJumped)
					{
						for (BetterSpiderData.Limb limb : spiderData.limbs)
							limb.resetPosition();
						resetAfterJumped = true;
					}

					if (spiderData.isStillHorizontally())
					{
						this.layerBase.playOrContinueBit(bitIdle, spiderData);
					}
					else
					{
						this.layerBase.playOrContinueBit(bitMove, spiderData);
					}
				}
			}
		}

		final List<String> actions = new ArrayList<>();
		this.layerBase.perform(spiderData, actions);
		return actions;
	}

	public static void putLimbOnGround(SmoothOrientation upperLimb, SmoothOrientation lowerLimb, boolean odd, double stretchDistance, double groundLevel)
	{
		putLimbOnGround(upperLimb, lowerLimb, odd, stretchDistance, groundLevel, 1.0F);
		upperLimb.finish();
		lowerLimb.finish();
	}

	public static void putLimbOnGround(SmoothOrientation upperLimb, SmoothOrientation lowerLimb, boolean odd, double stretchDistance, double groundLevel, float smoothness)
	{
		final float limbSegmentLength = 12F;
		final float maxStretch = limbSegmentLength * 2;

		double c = groundLevel == 0F ? stretchDistance : Math.sqrt(stretchDistance * stretchDistance + groundLevel * groundLevel);
		if (c > maxStretch)
		{
			c = maxStretch;
		}

		final double alpha = c > maxStretch ? 0 : Math.acos((c/2)/limbSegmentLength);
		final double beta = Math.atan2(stretchDistance, -groundLevel);

		double lowerAngle = Math.max(-2.3, -2 * alpha);
		double upperAngle = Math.min(1, alpha + beta - Math.PI/2);
		upperLimb.setSmoothness(smoothness).localRotateZ((float) (upperAngle / Math.PI * 180) * (odd ? -1 : 1));
		lowerLimb.setSmoothness(smoothness).orientZ((float) (lowerAngle / Math.PI * 180) * (odd ? -1 : 1));
	}



}
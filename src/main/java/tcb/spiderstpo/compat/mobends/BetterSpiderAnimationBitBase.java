package tcb.spiderstpo.compat.mobends;

import goblinbob.mobends.core.animation.bit.AnimationBit;
import goblinbob.mobends.core.util.GUtil;
import goblinbob.mobends.standard.animation.controller.SpiderController;
import goblinbob.mobends.standard.data.SpiderData;
import net.minecraft.util.math.MathHelper;

/**
 * Original file SpiderAnimationBitBase.java - modified to support BetterSpiderEntity.
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
public abstract class BetterSpiderAnimationBitBase extends AnimationBit<BetterSpiderData>
{

    protected float startTransition = 0.0F;

    protected void animateMovingLimb(BetterSpiderData data, float groundLevel, float limbSwing, int index, float minDist, float maxDist, float minRot, float maxRot)
    {
        final boolean odd = index % 2 == 1;
        final float offset = (index + 1) / 2 % 2 == 0 ? GUtil.PI : 0;
        float smoothness = 1F;
        float sideRotation = minRot + (MathHelper.sin(limbSwing + offset) * .5F + .5F) * (maxRot - minRot);
        float dist = minDist + (MathHelper.sin(limbSwing + offset) * .5F + .5F) * (maxDist - minDist);
        groundLevel += -7 + Math.max(0, MathHelper.cos(limbSwing + offset)) * 4;

        BetterSpiderData.Limb limb = data.limbs[index];
        limb.upperPart.rotation.setSmoothness(smoothness).orientY(odd ? sideRotation : -sideRotation);

        if (startTransition >= 1.0F)
        {
            BetterSpiderController.putLimbOnGround(limb.upperPart.rotation, limb.lowerPart.rotation, odd, dist, groundLevel);
        }
        else
        {
        	BetterSpiderController.putLimbOnGround(limb.upperPart.rotation, limb.lowerPart.rotation, odd, dist, groundLevel, startTransition);
        }

        limb.setAngleAndDistance(odd ? sideRotation / 180F * GUtil.PI : GUtil.PI - sideRotation / 180F * GUtil.PI, dist * 0.0625F);
    }

}
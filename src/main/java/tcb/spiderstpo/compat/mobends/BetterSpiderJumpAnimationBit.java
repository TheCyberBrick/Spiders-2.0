package tcb.spiderstpo.compat.mobends;

import goblinbob.mobends.core.animation.bit.AnimationBit;
import goblinbob.mobends.core.util.GUtil;
import goblinbob.mobends.standard.data.SpiderData;
import net.minecraft.util.math.MathHelper;

/**
 * Original file SpiderJumpAnimationBit.java - modified to support BetterSpiderEntity.
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
public class BetterSpiderJumpAnimationBit extends AnimationBit<BetterSpiderData>
{

    @Override
    public String[] getActions(BetterSpiderData entityData)
    {
        return new String[] { "jump" };
    }

    @Override
    public void perform(BetterSpiderData data)
    {
        for (int i = 0; i < data.limbs.length; ++i)
        {
            boolean odd = i % 2 == 1;
            BetterSpiderData.Limb limb = data.limbs[i];
            float naturalYaw = -((float) i / (data.limbs.length - 1) * 2 - 1);
            naturalYaw = odd ? (-naturalYaw * 1.3F) : (naturalYaw * 1.3F);
            limb.upperPart.rotation.orientY(naturalYaw / GUtil.PI * 180F);
        }
        float motionY = (float) -data.getInterpolatedMotionY() * 10;
        motionY = MathHelper.clamp(motionY, -1, 1);

        float legAngle = -20.0F + motionY * 25.0F;
        float smoothness = 1F;
        data.limbs[0].upperPart.rotation.setSmoothness(smoothness).localRotateZ(legAngle);
        data.limbs[1].upperPart.rotation.setSmoothness(smoothness).localRotateZ(-legAngle);
        data.limbs[2].upperPart.rotation.setSmoothness(smoothness).localRotateZ(legAngle);
        data.limbs[3].upperPart.rotation.setSmoothness(smoothness).localRotateZ(-legAngle);
        data.limbs[4].upperPart.rotation.setSmoothness(smoothness).localRotateZ(legAngle);
        data.limbs[5].upperPart.rotation.setSmoothness(smoothness).localRotateZ(-legAngle);
        data.limbs[6].upperPart.rotation.setSmoothness(smoothness).localRotateZ(legAngle);
        data.limbs[7].upperPart.rotation.setSmoothness(smoothness).localRotateZ(-legAngle);

        float foreLegAngle = -70.0F - motionY * 40.0F;
        data.limbs[0].lowerPart.rotation.setSmoothness(smoothness).orientZ(foreLegAngle);
        data.limbs[1].lowerPart.rotation.setSmoothness(smoothness).orientZ(-foreLegAngle);
        data.limbs[2].lowerPart.rotation.setSmoothness(smoothness).orientZ(foreLegAngle);
        data.limbs[3].lowerPart.rotation.setSmoothness(smoothness).orientZ(-foreLegAngle);
        data.limbs[4].lowerPart.rotation.setSmoothness(smoothness).orientZ(foreLegAngle);
        data.limbs[5].lowerPart.rotation.setSmoothness(smoothness).orientZ(-foreLegAngle);
        data.limbs[6].lowerPart.rotation.setSmoothness(smoothness).orientZ(foreLegAngle);
        data.limbs[7].lowerPart.rotation.setSmoothness(smoothness).orientZ(-foreLegAngle);

        data.localOffset.slideToZero();
        data.globalOffset.set(0, 0, 0);
        data.centerRotation.orientZero();
        data.renderRotation.orientZero();
    }

}
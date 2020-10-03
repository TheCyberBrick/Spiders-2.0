package tcb.spiderstpo.compat.mobends;

import goblinbob.mobends.core.client.event.DataUpdateHandler;
import goblinbob.mobends.standard.data.SpiderData;
import net.minecraft.util.math.MathHelper;

/**
 * Original file SpiderMoveAnimationBit.java - modified to support BetterSpiderEntity.
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
public class BetterSpiderMoveAnimationBit extends BetterSpiderAnimationBitBase
{

    protected static final String[] ACTIONS = new String[] { "move" };
    protected static final float KNEEL_DURATION = 10F;

    @Override
    public String[] getActions(BetterSpiderData data)
    {
        return ACTIONS;
    }

    @Override
    public void perform(BetterSpiderData data)
    {
        final float ticks = DataUpdateHandler.getTicks();
        final float headYaw = data.headYaw.get();
        final float headPitch = data.headPitch.get();
        final float limbSwing = data.limbSwing.get() * 0.6662F;

        float groundLevel = MathHelper.sin(ticks * 0.6F) * 1.2F;
        final float touchdown = Math.min(data.getTicksAfterTouchdown() / KNEEL_DURATION, 1.0F);

        if (startTransition < 1.0F)
            startTransition += DataUpdateHandler.ticksPerFrame * 0.1F;

        if (touchdown < 1.0F)
        {
            float touchdownInv = 1.0F - touchdown;
            groundLevel += Math.sin((touchdown * 1.2F - 0.2F) * Math.PI * 2) * 3.0F * touchdownInv;
        }

        data.spiderHead.rotation.orientInstantX(headPitch);
        data.spiderHead.rotation.rotateY(headYaw).finish();

        final float bodyX = MathHelper.sin(ticks * 0.2F) * 0.4F;
        final float bodyZ = MathHelper.cos(ticks * 0.2F) * 0.4F;

        // Back limbs
        animateMovingLimb(data, groundLevel, limbSwing + .0F, 0, 20.0F, 10F, -80, -50);
        animateMovingLimb(data, groundLevel, limbSwing + .3F, 1, 20.0F, 10F, -80, -50);

        // Back-middle limbs
        animateMovingLimb(data, groundLevel, limbSwing + .3F, 2, 15F, 15.0F, -30F, 10.0F);
        animateMovingLimb(data, groundLevel, limbSwing + .0F, 3, 15F, 15.0F, -30F, 10.0F);

        // Front-middle limbs
        animateMovingLimb(data, groundLevel, limbSwing + .4F, 4, 7F, 15.0F, 20, 50.0F);
        animateMovingLimb(data, groundLevel, limbSwing + .7F, 5, 7F, 15.0F, 20, 50.0F);

        // Front limbs
        animateMovingLimb(data, groundLevel, limbSwing + .7F, 6, 10F, 20.0F, 60, 80.0F);
        animateMovingLimb(data, groundLevel, limbSwing + .4F, 7, 10F, 20.0F, 60, 80.0F);

        data.localOffset.slideToZero();
        data.globalOffset.set(bodyX, -groundLevel, -bodyZ);
        data.renderRotation.orientZero();
        data.centerRotation.orientZero();
    }

}
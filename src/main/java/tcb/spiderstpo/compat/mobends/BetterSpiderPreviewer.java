package tcb.spiderstpo.compat.mobends;

import goblinbob.mobends.core.bender.BoneMetadata;
import goblinbob.mobends.core.bender.IPreviewer;
import goblinbob.mobends.core.client.event.DataUpdateHandler;
import goblinbob.mobends.standard.data.SpiderData;
import net.minecraft.client.renderer.GlStateManager;

import java.util.Map;

/**
 * Original file SpiderPreviewer.java - modified to support BetterSpiderEntity.
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
public class BetterSpiderPreviewer implements IPreviewer<BetterSpiderData>
{

	/**
	 * The Entity is generated specifically just for preview, so
	 * it can be manipulated in any way.
	 */
	@Override
	public void prePreview(BetterSpiderData data, String animationToPreview)
	{
		data.limbSwingAmount.override(0F);
		
		switch (animationToPreview)
		{
			case "jump":
				{
					final float ticks = DataUpdateHandler.getTicks();
					
					final float JUMP_DURATION = 10;
					final float WAIT_DURATION = 10;
					final float TOTAL_DURATION = JUMP_DURATION + WAIT_DURATION;
					float t = ticks % TOTAL_DURATION;
					
					if (t <= JUMP_DURATION)
					{
						data.overrideOnGroundState(false);
						
						double yOffset = Math.sin(t/JUMP_DURATION * Math.PI) * 1.5;
						GlStateManager.translate(0, yOffset, 0);
					} else {
						data.overrideOnGroundState(true);
					}
					
					data.limbSwingAmount.override(0F);
					data.overrideStillness(true);
				}
				break;
			case "move":
				final float ticks = DataUpdateHandler.getTicks();
				
				data.getEntity().posZ += DataUpdateHandler.ticksPerFrame * 0.1F;
				data.getEntity().prevPosZ = data.getEntity().posZ;
				data.getEntity().noClip = true;
				//System.out.println(data.getEntity().posZ);
				data.limbSwing.override(ticks * 0.6F);
				data.overrideOnGroundState(true);
				data.limbSwingAmount.override(1F);
				data.overrideStillness(false);
				break;
			default:
				data.overrideOnGroundState(true);
				data.overrideStillness(true);
		}
	}

	@Override
	public void postPreview(BetterSpiderData data, String animationToPreview)
	{
		// No behaviour
	}

	@Override
	public Map<String, BoneMetadata> getBoneMetadata()
	{
		return null;
	}
	
}
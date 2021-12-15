package jrtr.gldeferredrenderer;

import static org.lwjgl.opengl.GL46.*;

/**
 * A simple GLBuffer, which contains only one texture.
 * This class can be used for ping pong buffers.
 * @author Heinrich Reich
 *
 */
public class FrameBuffer extends GLBuffer{
	
	
	public FrameBuffer(int width, int height, boolean useDepthBuffer) throws RuntimeException{
		super(width, height, 1, useDepthBuffer, GL_RGB8);
	}
	
	FrameBuffer(int width, int height, boolean useDepthBuffer, int format){
		super(width, height, 1, useDepthBuffer, format);
	}
	
	/**
	 * Basically the same as {@link GLBuffer#beginRead(int)}.
	 */
	public void beginRead(){
		super.beginRead(0);
	}

	@Override
	protected void handleCreationError(boolean failed) throws RuntimeException{
		if(failed)
	        throw new RuntimeException("Error occured while creating the framebuffer object!");
	}
	
	/**
	 * @return the internal OpenGL id of the texture.
	 */
	public int getRenderedTexture(){
		return this.textures.get(0);
	}
}
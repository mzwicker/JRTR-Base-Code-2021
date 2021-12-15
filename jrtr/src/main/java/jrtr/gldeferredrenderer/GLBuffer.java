package jrtr.gldeferredrenderer;

import java.nio.*;

import static org.lwjgl.opengl.GL46.*;
import static org.lwjgl.system.MemoryUtil.*;

/**
 * A simple class for creating, reading, and writing to an OpenGL framebuffer object (FBO).
 * FBOs are the key OpenGL data structures for writing (rendering) and reading from off-screen
 * images and textures.
 * 
 * @author Heinrich Reich
 * 
 */
public abstract class GLBuffer {
	
	// references to the different OpenGL objects
	public IntBuffer frameBuffer, drawBuffers, textures, depthBuffer;
	// format and render target index
	public final int format, renderTargets;
	// temporary indices of read and written fbo
	// width and height of the textures in this buffer
	private int prevWriteFBO, prevReadFBO, width, height;
	// whether to use depth buffer or not
	public final boolean useDepthBuffer;
	
	/**
	 * Creates a new OpenGL framebuffer object (FBO).
	 * @param gl the OpenGL context
	 * @param width the width of the textures
	 * @param height the height of the texture
	 * @param renderTargets number of textures/render targets
	 * @param useDepthBuffer whether to use a depth buffer
	 * @param format the format
	 */
	public GLBuffer(int width, int height, int renderTargets, boolean useDepthBuffer, int format){
		this.renderTargets = renderTargets;
		this.width = width;
		this.height = height;
		this.format = format;
		this.useDepthBuffer = useDepthBuffer;
		this.init();
	}
	
	/**
	 * Creates a new OpenGL framebuffer object (FBO) with a default GL3.GL_RGB8 format.
	 * @param gl
	 * @param width
	 * @param height
	 * @param renderTargets
	 * @param useDepthBuffer
	 */
	public GLBuffer(int width, int height, int renderTargets, boolean useDepthBuffer){
		this(width, height, renderTargets, useDepthBuffer, GL_RGB8);
	}
	
	/**
	 * Initialize the OpenGL framebuffer object (FBO). First we generate a framebuffer object 
	 * and bind it as the current buffer. Then we create as many textures as specified in 
	 * {@link GLBuffer#GLBuffer}. If needed, we also create a depth buffer for the render target.
	 * Later (after we rendered into the FBO), we can read from these textures via the
	 * usual OpenGL functionality. 
	 * The abstract method {@link GLBuffer#handleCreationError} gets called here.
	 */
	private void init(){
		this.frameBuffer = memAllocInt(1);
		this.textures = memAllocInt(renderTargets);
		this.drawBuffers = memAllocInt(renderTargets);
		
		// Generate a reference to a FBO and bind it ("activate it")
		glGenFramebuffers(frameBuffer);
	    glBindFramebuffer(GL_DRAW_FRAMEBUFFER, frameBuffer.get(0));
	    
	    // Create references for the FBO textures
	    glGenTextures(textures);
	    	    
	    // Attach textures to the FBO
	    for (int i = 0 ; i < textures.capacity() ; i++) {
	    	glBindTexture(GL_TEXTURE_2D, this.textures.get(i));
	    	glTexImage2D(GL_TEXTURE_2D, 0, format, width, height, 0, GL_RGBA, GL_FLOAT, (FloatBuffer) null);
	    	glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
	        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
	        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
	        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
	    	// Each texture will be linked to a buffer index i; the corresponding index is used in the shader
	    	// using the directive layout(location = i) to address this texture 
	    	glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0 + i, GL_TEXTURE_2D, this.textures.get(i), 0);
	    }
	    
	    // Depth texture
	    if(this.useDepthBuffer) this.createDepthBuffer();
	    
		for(int i = 0; i< drawBuffers.capacity(); i++)
	    	drawBuffers.put(i, GL_COLOR_ATTACHMENT0+i);
	    glDrawBuffers(drawBuffers);
	    
	    this.handleCreationError(glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE);
	    
	    // Bind default render and frame buffer ("deactivate" the FBO we just created)
	    glBindFramebuffer(GL_FRAMEBUFFER, 0);
	    glBindRenderbuffer(GL_RENDERBUFFER, 0);
	}
	
	/**
	 * Resizes the frame buffer, i.e. destroys the buffer and its textures,
	 * and re-initializes the buffer with the new dimensions.
	 * @param width
	 * @param height
	 */
	public void resize(int width, int height){
		this.width = width;
		this.height = height;
		this.dispose();
		this.init();
	}
	
	/**
	 * Gets called after the initialization.
	 * @param failed <code>true</code> if an error occurred during initialization step.
	 */
	protected abstract void handleCreationError(boolean failed);
	
	/**
	 * Binds ("activate") this FBO, so any rendering calls will draw into this FBO.
	 * Use {@link GLBuffer#endWrite()} when you are finished with writing.
	 */
	public void beginWrite(){
		// Store current FBO to restore when done with writing
		this.prevWriteFBO = glGetInteger(GL_DRAW_FRAMEBUFFER);
		
		// Bind this FBO
	    glBindFramebuffer(GL_DRAW_FRAMEBUFFER, frameBuffer.get(0));
	    glViewport(0, 0, width, height);
	}
	
	/**
	 * Unbinds this framebuffer, i.e. binds the previous buffer again.
	 * Only use this method if you called {@link GLBuffer#beginWrite()} before.
	 */
	public void endWrite(){
	    glBindFramebuffer(GL_DRAW_FRAMEBUFFER, this.prevWriteFBO);
	}
	
	/**
	 * Begins reading from a texture from this FBO by binding  ("activating") it.
	 * Use {@link GLBuffer#endRead()} if you are done with reading.
	 * @param i the id of the wished texture.
	 */
	public void beginRead(int i){
		if(this.prevReadFBO != this.frameBuffer.get(0)){
			this.prevReadFBO = glGetInteger(GL_READ_FRAMEBUFFER);
			glBindFramebuffer(GL_READ_FRAMEBUFFER, frameBuffer.get(0));
		}
	    glReadBuffer(GL_COLOR_ATTACHMENT0+i);
	}
	
	/**
	 * Ends reading from this buffer.
	 * Only use this method if you called {@link GLBuffer#beginRead(int)} before.
	 */
	public void endRead(){
	    glBindFramebuffer(GL_READ_FRAMEBUFFER, this.prevReadFBO);
	}
	
	/**
	 * Does all OpenGL calls for creating a depth buffer for this buffer.
	 */
	private void createDepthBuffer(){
		this.depthBuffer = memAllocInt(1);
		glGenTextures(this.depthBuffer);
		glBindTexture(GL_TEXTURE_2D, this.depthBuffer.get(0));
		glTexImage2D(GL_TEXTURE_2D, 0, GL_DEPTH_COMPONENT32F, width, height, 0, GL_DEPTH_COMPONENT, GL_FLOAT, (FloatBuffer) null);
		glFramebufferTexture2D(GL_DRAW_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, this.depthBuffer.get(0), 0);
	}
	
	/**
	 * Disposes this buffer and its textures, i.e. releases all memory. 
	 */
	public void dispose(){
	    glBindFramebuffer(GL_DRAW_FRAMEBUFFER, 0);
		if(this.useDepthBuffer) glDeleteTextures(depthBuffer);
		glDeleteTextures(textures);
		glDeleteFramebuffers(frameBuffer);
		memFree(frameBuffer);
		memFree(textures);
		if(this.useDepthBuffer) memFree(depthBuffer);
	}
	
	/**
	 * @return the current set width.
	 */
	public int getWidth(){
		return this.width;
	}
	
	/**
	 * @return the current set height.
	 */
	public int getHeight(){
		return this.height;
	}
	
	/**
	 * @param index
	 * @return the internal texture index in OpenGL, need for passing textures to a shader.
	 */
	public int getTexture(int index){
		return this.textures.get(index);
	}

}

package jrtr.glrenderer;

import static org.lwjgl.opengl.GL11.GL_RGBA;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL11.glTexImage2D;
import static org.lwjgl.opengl.GL45.*;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.stb.STBImage;

import jrtr.Texture;

import java.io.*;
import java.nio.*;


/**
 * Manages OpenGL textures. This class will be used in the
 * "Texturing and Shading" project.
 */
public class GLTexture implements Texture {
	
	private IntBuffer id;	// Stores the OpenGL texture identifier
	private int w, h;		// Width and height
	
	public GLTexture()
	{
		id = IntBuffer.allocate(1);	// Make the buffer that will store the texture identifier
		id.put(0, glGenTextures());
	}

	/**
	 * Load the texture from an image file.
	 */
	public void load(String fileName) throws IOException
	{
		// Memory management necessary to pass image data to lwjgl
		try (MemoryStack stack = MemoryStack.stackPush()) {        
		} catch(Exception e)
		{
			e.printStackTrace();    
		}
		
		ByteBuffer buffer;
		try (MemoryStack stack = MemoryStack.stackPush())
		{	
			IntBuffer w = stack.mallocInt(1);	
			IntBuffer h = stack.mallocInt(1);	
			IntBuffer channels = stack.mallocInt(1);		  	
			
			// Use STB library to load image from file into a ByteBuffer
			buffer = STBImage.stbi_load(fileName, w, h, channels, 4);	
			if(buffer ==null) 
			{		  
				throw new Exception("Can't load file " + fileName + " " + STBImage.stbi_failure_reason());	
			}	
			this.w = w.get();	
			this.h = h.get();		  		
			
			// Make an OpenGL texture and pass the buffer containing the texture
			id = IntBuffer.allocate(1);	// Make the buffer that will store the texture identifier
			id.put(0, glGenTextures());
			glBindTexture(GL_TEXTURE_2D, id.get(0));	
			glPixelStorei(GL_UNPACK_ALIGNMENT, 1);		  	
			glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, this.w, this.h, 0, GL_RGBA, GL_UNSIGNED_BYTE, buffer);	
			glGenerateMipmap(GL_TEXTURE_2D);	
			STBImage.stbi_image_free(buffer);	
			
		} catch(Exception e) 
		{	
			e.printStackTrace();
		}	    
	}

	public int getId()
	{
		return id.get(0);
	}
}

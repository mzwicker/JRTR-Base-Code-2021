package jrtr.glrenderer;

import java.nio.*;

import static org.lwjgl.opengl.GL46.*;
import static org.lwjgl.system.MemoryUtil.*;

/**
 * A utility class to encapsulate an OpenGL "vertex array object" (VAO).
 */
public class GLVertexArrayObject {

	private IntBuffer vao;
	private IntBuffer vbo;
	
	/**
	 * Make an OpenGL "vertex array object" (VAO) with a desired number of
	 * "vertex buffer objects" (VBOs). Each VBO refers to a buffer 
	 * with one vertex attribute, like vertex positions, normals, or 
	 * texture coordinates.
	 * 
	 * @param numberOfVBOs
	 * 		the number of VBOs to be stored in the VAO
	 */
	public GLVertexArrayObject(int numberOfVBOs) {
		// For all vertex attributes, make vertex buffer objects.
		// References to the VBOs are stored in the array vbo.
		vbo = memAllocInt(numberOfVBOs);
		for(int i=0; i<numberOfVBOs;i++)
			vbo.put(i, glGenBuffers());
		
		// Make a vertex array object. A reference to the VAO
		// is stored in the array vao.
		vao = memAllocInt(1);
		vao.put(0, glGenVertexArrays());
	}

	/**
	 * Rewind the {@link IntBuffer} storing the references to the VBOs.
	 */
	public void rewindVBO() {
		vbo.rewind();
	}

	/**
	 * Get reference to next VBO stored in the {@link IntBuffer}.
	 */
	public int getNextVBO() {
		return vbo.get();
	}

	/**
	 * Bind the VAO. This means all the information associated
	 * with the VAO becomes active in OpenGL.
	 */
	public void bind() {
		glBindVertexArray(vao.get(0));
	}

	/**
	 * Deletes all vbos and the vertex array;
	 */
	public void dispose(){
		glBindVertexArray(0);
		glBindBuffer(0, 0);
		glDeleteBuffers(vbo);
		glDeleteVertexArrays(vao);
		memFree(vao);
		memFree(vbo);
	}
}
package jrtr.glrenderer;

import java.util.*;

import javax.vecmath.*;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL46.*;
import static org.lwjgl.openvr.VR.*;
import static org.lwjgl.openvr.VRCompositor.*;

import jrtr.*;
import jrtr.gldeferredrenderer.*;

/**
 * This class implements a {@link RenderContext} (a renderer) using OpenGL
 * version 3 (or later).
 */
public class VRRenderContext implements RenderContext {

	private SceneManagerInterface sceneManager;
	/**
	 * The buffer containing the data that is passed to the HMD. 
	 */
	protected FrameBuffer vrBuffer;
	
	/**
	 * The default shader for this render context, will be used for items that
	 * do not have their own shader.
	 */
	private GLShader defaultShader;

	/**
	 * The id of the currently active shader (you should always
	 * useuseShader(GLShader) and useDefaultShader() to switch between the
	 * shaders!).
	 */
	private int activeShaderID;
	
	private org.lwjgl.openvr.Texture texType;
	private VRRenderPanel renderPanel;
		
	public void setOpenVR(VRRenderPanel renderPanel)
	{
		this.renderPanel = renderPanel;
		
		texType = org.lwjgl.openvr.Texture.create();
		texType.eColorSpace(EColorSpace_ColorSpace_Gamma);
		texType.eType(ETextureType_TextureType_OpenGL);
		texType.handle(-1);
		
		//we need to disable vertical synchronisation on the mirrored monitor on screen
		//The screen has 60 Hz and the HMD 90 Hz, thus vsync at 60Hz will block frames on the HMD periodically!
		glfwSwapInterval(0);
	}
	
	/**
	 * This constructor is called by {@link VRRenderPanel}.
	 */
	public VRRenderContext() {
		glEnable(GL_DEPTH_TEST);
		glClearColor(0.0f, 0.0f, 0.0f, 0.0f);

		// Load and use default shader, will be used for items that do not have
		// their own shader.
		defaultShader = (GLShader) makeShader();
		try {
			defaultShader.load("../jrtr/shaders/default.vert", "../jrtr/shaders/default.frag");
		} catch (Exception e) {
			System.out.print("Problem with shader:\n");
			System.out.print(e.getMessage());
		}
		useShader(defaultShader);
	}

	public void resize(int width, int height){
		this.vrBuffer.resize(width, height);
	}
	
	/**
	 * Set the scene manager. The scene manager contains the 3D scene that will
	 * be rendered. The scene includes geometry as well as the camera and
	 * viewing frustum.
	 */
	public void setSceneManager(SceneManagerInterface sceneManager) {
		this.sceneManager = sceneManager;
	}

	/**
	 * This method is called by the GLRenderPanel to redraw the 3D scene. The
	 * method traverses the scene using the scene manager and passes each object
	 * to the rendering method.
	 */
	public void display() {
		
		if(!renderPanel.posesReady)
			renderPanel.waitGetPoses();
        
        // Save scene camera and projection matrices
        Matrix4f sceneCamera = new Matrix4f(this.sceneManager.getCamera().getCameraMatrix());
        Matrix4f projectionMatrix = new Matrix4f(this.sceneManager.getFrustum().getProjectionMatrix());
                
        // Render two eyes and pass to OpenVR compositor
        for(int eye=0; eye<2; eye++)
        {
	        // Applying tracking in addition to scene camera
	        Matrix4f worldToHead = new Matrix4f(renderPanel.poseMatrices[0]);
	        // Need to invert to get world-to-head
	        if(worldToHead.determinant()!=0)
	        	worldToHead.invert();
	        else
	        	System.out.println("tracking lost!");
	        worldToHead.mul(sceneCamera);
	        if(eye == 0) {
	        	renderPanel.headToLeftEye.mul(worldToHead);
	        	sceneManager.getCamera().setCameraMatrix(renderPanel.headToLeftEye);
	        } else {
	        	renderPanel.headToRightEye.mul(worldToHead);
	        	sceneManager.getCamera().setCameraMatrix(renderPanel.headToRightEye);
	        }
	        	        
	        // Set projection matrix
	        if(eye == 0)
	        	sceneManager.getFrustum().setProjectionMatrix(renderPanel.leftProjectionMatrix);
	        else
	        	sceneManager.getFrustum().setProjectionMatrix(renderPanel.rightProjectionMatrix);
	        
			//draw scene into framebuffer
	        if (vrBuffer == null) {
	        	vrBuffer = new FrameBuffer(renderPanel.targetWidth, renderPanel.targetHeight, true);
	        }
			vrBuffer.beginWrite();
			beginFrame();
			SceneManagerIterator iterator = sceneManager.iterator();
			while (iterator.hasNext()) {
				RenderItem r = iterator.next();
				if (r.getShape() != null) {
					draw(r);
				}
			}
			endFrame();
			vrBuffer.endWrite();
			
			// Draw the result to the screen using a bit of OpenGL hacking
			beginFrame();
			vrBuffer.beginRead(0);
			glBlitFramebuffer(0, 0, vrBuffer.getWidth(), vrBuffer.getHeight(),  0, 0, 
				renderPanel.targetWidth, renderPanel.targetHeight, GL_COLOR_BUFFER_BIT, GL_LINEAR);
			vrBuffer.endRead();
			this.endFrame();
			
			// Pass rendered image to OpenVR compositor
			glBindFramebuffer(GL_FRAMEBUFFER, vrBuffer.frameBuffer.get(0));
			texType.handle(vrBuffer.textures.get(0));
			
			// Pass texture to the compositor
			if(eye == 0) {			
				int err	= VRCompositor_Submit(EVREye_Eye_Left, texType, null,
		                  EVRSubmitFlags_Submit_Default);
				if( err != 0 ) System.out.println("Submit compositor error (left): " + Integer.toString(err));
			} else {
				int err	= VRCompositor_Submit(EVREye_Eye_Right, texType, null,
		                EVRSubmitFlags_Submit_Default);
				if( err != 0 ) System.out.println("Submit compositor error (right): " + Integer.toString(err));
			}
			
			// Un-bind our frame buffer
			glBindFramebuffer(GL_DRAW_FRAMEBUFFER, GL_NONE);
        }
        		
        // Restore original scene camera and projection matrices
     	sceneManager.getCamera().setCameraMatrix(sceneCamera);
     	sceneManager.getFrustum().setProjectionMatrix(projectionMatrix);
     	
		// Not sure if this is useful...
     	VRCompositor_PostPresentHandoff();
		
		// We consumed the poses, get new ones for next frame
		renderPanel.posesReady = false;
	}

	/**
	 * This method is called at the beginning of each frame, i.e., before scene
	 * drawing starts.
	 */
	private void beginFrame() {
		glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
	}

	/**
	 * This method is called at the end of each frame, i.e., after scene drawing
	 * is complete.
	 */
	private void endFrame() {
		glFlush();
	}

	/**
	 * The main rendering method.
	 * 
	 * @param renderItem
	 *            the object that needs to be drawn
	 */
	private void draw(RenderItem renderItem) {
		setMaterial(renderItem.getShape().getMaterial());

		GLVertexData vertexData = (GLVertexData) renderItem.getShape().getVertexData();

		// In the first pass the object has to be given to the buffer (on the
		// GPU) and the renderItem has to store the handle, so we do not have to
		// send the object to the GPU in each pass.
		if (vertexData.getVAO() == null) {
			initArrayBuffer(vertexData);
		}

		// Set modelview and projection matrices in shader (has to be done in
		// every step, since they usually have changed)
		setTransformation(renderItem.getT());

		// Bind the VAO of this shape (all the vertex data are already on the
		// GPU, we do not have to send them again)
		vertexData.getVAO().bind();
							
		// Try to connect the vertex arrays to the corresponding variables 
		// in the current vertex shader.
		// Note: This is not part of the vertex array object, because the active
		// shader may have changed since the vertex array object was initialized. 
		// We need to make sure the vertex buffers are connected to the right
		// variables in the shader
		ListIterator<VertexData.VertexElement> itr = vertexData.getElements().listIterator(0);
		vertexData.getVAO().rewindVBO();
		while (itr.hasNext()) {
			VertexData.VertexElement e = itr.next();
			int dim = e.getNumberOfComponents();

			// Bind the next vertex buffer object
			glBindBuffer(GL_ARRAY_BUFFER, vertexData.getVAO().getNextVBO());

			// Tell OpenGL which "in" variable in the vertex shader corresponds
			// to the current vertex buffer object.
			// We use our own convention to name the variables, i.e.,
			// "position", "normal", "color", "texcoord", or others if
			// necessary.
			int attribIndex = -1;
			switch (e.getSemantic()) {
			case POSITION:
				attribIndex = glGetAttribLocation(activeShaderID, "position");
				break;
			case NORMAL:
				attribIndex = glGetAttribLocation(activeShaderID, "normal");
				break;
			case COLOR:
				attribIndex = glGetAttribLocation(activeShaderID, "color");
				break;
			case TEXCOORD:
				attribIndex = glGetAttribLocation(activeShaderID, "texcoord");
				break;
			}

			glVertexAttribPointer(attribIndex, dim, GL_FLOAT, false, 0, 0);
			glEnableVertexAttribArray(attribIndex);
		}

		// Render the vertex buffer objects
		glDrawElements(GL_TRIANGLES, renderItem.getShape().getVertexData().getIndices().length, GL_UNSIGNED_INT, 0);

		// we are done with this shape, bind the default vertex array
		glBindVertexArray(0);

		cleanMaterial(renderItem.getShape().getMaterial());
	}

	/**
	 * A utility method to load vertex data into an OpenGL "vertex array object"
	 * (VAO) for efficient rendering.
	 *  
	 * @param data
	 * 			reference to the vertex data to be loaded into a VAO
	 */
	private void initArrayBuffer(GLVertexData data) {
		
		// Make a vertex array object (VAO) for this vertex data
		GLVertexArrayObject vao = new GLVertexArrayObject(data.getElements().size() + 1);
	//	vertexArrayObjects.add(vao);
		data.setVAO(vao);
		
		// Bind (activate) the VAO for the vertex data
		vao.bind();
		
		// Store all vertex attributes in the buffers
		ListIterator<VertexData.VertexElement> itr = data.getElements()
				.listIterator(0);
		while (itr.hasNext()) {
			VertexData.VertexElement e = itr.next();

			// Bind the next vertex buffer object
			glBindBuffer(GL_ARRAY_BUFFER, data.getVAO().getNextVBO());
			// Upload vertex data
			glBufferData(GL_ARRAY_BUFFER, e.getData(), GL_DYNAMIC_DRAW);
		}

		// bind the default vertex buffer objects
		glBindBuffer(GL_ARRAY_BUFFER, 0);

		// store the indices into the last buffer
		glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, data.getVAO().getNextVBO());
		glBufferData(GL_ELEMENT_ARRAY_BUFFER, data.getIndices(), GL_DYNAMIC_DRAW);
		
		// bind the default vertex array object
		glBindVertexArray(0);
	}

	private void setTransformation(Matrix4f transformation) {
		// Compute the modelview matrix by multiplying the camera matrix and
		// the transformation matrix of the object
		Matrix4f modelview = new Matrix4f(sceneManager.getCamera()
				.getCameraMatrix());
		modelview.mul(transformation);

		// Set modelview and projection matrices in shader
		glUniformMatrix4fv(
				glGetUniformLocation(activeShaderID, "modelview"), false,
				transformationToFloat16(modelview));
		glUniformMatrix4fv(glGetUniformLocation(activeShaderID,
				"projection"), false, transformationToFloat16(sceneManager
				.getFrustum().getProjectionMatrix()));

	}

	/**
	 * Pass the material properties to OpenGL, including textures and shaders.
	 * 
	 * Implementation here is incomplete. It's just for demonstration purposes. 
	 */
	private void setMaterial(Material m) {
		
		// Set up the shader for the material, if it has one
		if(m != null && m.shader != null) {
			useShader(m.shader);
			
			// Pass shininess parameter to shader 
			int id = glGetUniformLocation(activeShaderID, "shininess");
			if(id!=-1)
				glUniform1f(id, m.shininess);
			else
				System.out.print("Could not get location of uniform variable shininess\n");
			
			// Activate the texture, if the material has one
			if(m.texture != null) {
				// OpenGL calls to activate the texture 
				glActiveTexture(GL_TEXTURE0);	// Work with texture unit 0
				glEnable(GL_TEXTURE_2D);
				glBindTexture(GL_TEXTURE_2D, ((GLTexture)m.texture).getId());
				glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
				glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
				id = glGetUniformLocation(activeShaderID, "myTexture");
				glUniform1i(id, 0);	// The variable in the shader needs to be set to the desired texture unit, i.e., 0
			}
			
			// Pass light source information to shader, iterate over all light sources
			Iterator<Light> iter = sceneManager.lightIterator();			
			int i=0;
			Light l;
			if(iter != null) {
				
				while(iter.hasNext() && i<8)
				{
					l = iter.next(); 
					
					// Pass light direction to shader
					String lightString = "lightDirection[" + i + "]";			
					id = glGetUniformLocation(activeShaderID, lightString);
					if(id!=-1)
						glUniform4f(id, l.direction.x, l.direction.y, l.direction.z, 0.f);		// Set light direction
					else
						System.out.print("Could not get location of uniform variable " + lightString + "\n");
					
					i++;
				}
				
				// Pass number of lights to shader
				id = glGetUniformLocation(activeShaderID, "nLights");
				if(id!=-1)
					glUniform1i(id, i);		// Set number of lights
				else
					System.out.print("Could not get location of uniform variable nLights\n");

			}

		}
	}	
	
	/**
	 * Disable a material.
	 * 
	 * To be implemented in the "Textures and Shading" project.
	 */
	private void cleanMaterial(Material m) {
	}

	public void useShader(Shader s) {
		if (s != null) {
			activeShaderID = ((GLShader)s).programId();
			glUseProgram(activeShaderID);
		}
	}

	public Shader makeShader() {
		return new GLShader();
	}

	public Texture makeTexture() {
		return new GLTexture();
	}

	public VertexData makeVertexData(int n) {
		return new GLVertexData(n);
	}

	/**
	 * Convert a Transformation to a float array in column major ordering, as
	 * used by OpenGL.
	 */
	private static float[] transformationToFloat16(Matrix4f m) {
		float[] f = new float[16];
		for (int i = 0; i < 4; i++)
			for (int j = 0; j < 4; j++)
				f[j * 4 + i] = m.getElement(i, j);
		return f;
	}
	
	/**
	 * Disposes all disposables
	 */
	public void dispose(){
		this.vrBuffer.dispose();
	}

	@Override
	public void useDefaultShader() {
		// TODO Auto-generated method stub
		
	}
	
}


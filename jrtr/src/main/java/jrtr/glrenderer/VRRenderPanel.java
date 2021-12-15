package jrtr.glrenderer;

import java.nio.*;

import javax.vecmath.*;

import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
import org.lwjgl.openvr.*;
import org.lwjgl.system.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.openvr.VR.*;
import static org.lwjgl.openvr.VRCompositor.*;
import static org.lwjgl.openvr.VRSystem.*;
import static org.lwjgl.system.MemoryStack.*;
import static org.lwjgl.system.MemoryUtil.*;

import jrtr.*;

/**
 * An implementation of the {@link RenderPanel} interface using
 * OpenGL. The interface to OpenGL and the native window system
 * are provided by the LWJGL and GLFW libraries. The class 
 * {@link VRRenderContext} performs the actual rendering. Note
 * that all OpenGL calls apply to the GLFW window that is created
 * by the current thread. 
 * 
 * The user needs to extend this class and provide an 
 * implementation for the <code>init</code> call-back function.
 */
public abstract class VRRenderPanel implements RenderPanel {
	
	// Access to OpenVR native data structures containing 3D tracked device poses
	protected static TrackedDevicePose.Buffer hmdTrackedDevicePoses;

	public static final VRControllerState.Buffer cStates = VRControllerState.create(k_unMaxTrackedDeviceCount);
	
	// Provides application access to data from OpenVR
	public boolean posesReady;
	public Matrix4f[] poseMatrices;
	public boolean[] poseValid;
	public Matrix4f headToLeftEye;
	public Matrix4f headToRightEye;
	public Matrix4f leftProjectionMatrix;
	public Matrix4f rightProjectionMatrix;
	public int controllerIndexHand, controllerIndexRacket;
	public int targetWidth, targetHeight;
	
	// The window handle
	protected long window;
	
	// Fixed time step to perform some periodic tasks 
	protected double timeStep;
	
	private VRRenderContext renderContext;
	
	public VRRenderPanel()
	{		
		// Initizalize OpenVR
		
        System.out.println("OpenVR library is present: " + VR_IsHmdPresent());
        
        try (MemoryStack stack = stackPush()) {
        	IntBuffer hmdErrorStore = stack.mallocInt(1);
        	int token = VR_InitInternal(hmdErrorStore, EVRApplicationType_VRApplication_Scene);
        	
        	// Get access to IVRSystem functions
            if( hmdErrorStore.get(0) == 0 ) {
                // Try and get the vrsystem pointer..
            	VR_GetGenericInterface(IVRSystem_Version, hmdErrorStore);
            }
            if( hmdErrorStore.get(0) != 0 ) {
                System.out.println("OpenVR Initialize Result: " + VR_GetVRInitErrorAsEnglishDescription(hmdErrorStore.get(0)));
                return;
            } 
            
            OpenVR.create(token);
            
            System.out.println("OpenVR System initialized & VR connected.");
            
            IntBuffer width = stack.mallocInt(1);
            IntBuffer height = stack.mallocInt(1);
            VRSystem_GetRecommendedRenderTargetSize(width, height);
            targetWidth = width.get(0);
            targetHeight = height.get(0);
            System.out.println("Target render size " + width.get(0) + " x " + height.get(0));
            
            // Get access to IVRCompositor functions
            VR_GetGenericInterface(IVRCompositor_Version, hmdErrorStore);
            if(hmdErrorStore.get(0) != 0 ){
                System.out.println("OpenVR Initialize Result: " + VR_GetVRInitErrorAsEnglishDescription(hmdErrorStore.get(0)));
                return;
            } 

            System.out.println("OpenVR Compositor initialized.\n");
            
            VRCompositor_SetTrackingSpace(ETrackingUniverseOrigin_TrackingUniverseSeated);

            // Prepare tracking matrices
            hmdTrackedDevicePoses = TrackedDevicePose.create(k_unMaxTrackedDeviceCount);
            poseMatrices = new Matrix4f[k_unMaxTrackedDeviceCount];
            poseValid = new boolean[k_unMaxTrackedDeviceCount];
            for(int i=0;i<poseMatrices.length;i++) { 
            	poseMatrices[i] = new Matrix4f();
            	poseValid[i] = false;
            }
            
            posesReady = false;
            
            // Find index for one of the VR hand controller(s)
            // Find index for one of the VR hand controller(s)
            controllerIndexHand = -1;
            controllerIndexRacket = -1;
            for(int i=0;i<k_unMaxTrackedDeviceCount;i++)
            {
            	int deviceClass = VRSystem_GetTrackedDeviceClass(i);
            	if(deviceClass == ETrackedDeviceClass_TrackedDeviceClass_Controller) 
            	{
            		System.out.println(i);
            		      
            		if (controllerIndexHand!=-1)
            			controllerIndexRacket = i;
            		else
            			controllerIndexHand = i; 
            	}
            }
            
            // Setup an error callback. The default implementation
    		// will print the error message in System.err.
    		GLFWErrorCallback.createPrint(System.err).set();

    		// Initialize GLFW. Most GLFW functions will not work before doing this.
    		if ( !glfwInit() )
    			throw new IllegalStateException("Unable to initialize GLFW");

    		// Configure GLFW
    		glfwDefaultWindowHints(); // optional, the current window hints are already the default
    		glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE); // the window will stay hidden after creation
    		glfwWindowHint(GLFW_RESIZABLE, GLFW_FALSE); // the window will not be resizable (since VR headset resolution is static)

    		// Create the window
    		window = glfwCreateWindow(width.get(0), height.get(0), "OpenVR Render Window", NULL, NULL);
    		if ( window == NULL )
    			throw new RuntimeException("Failed to create the GLFW window");		
    		
    		
			IntBuffer pWidth = stack.mallocInt(1); // int*
			IntBuffer pHeight = stack.mallocInt(1); // int*
			
			// Get the window size passed to glfwCreateWindow
			glfwGetWindowSize(window, pWidth, pHeight);
			
			// Get the resolution of the primary monitor
			GLFWVidMode vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor());
			
			// Center the window
			glfwSetWindowPos(
				window,
				(vidmode.width() - pWidth.get(0)) / 2,
				(vidmode.height() - pHeight.get(0)) / 2
			);
			
    		// Make the OpenGL context current
    		glfwMakeContextCurrent(window);
        }
	}
	
	public void showWindow()
	{
		// Make the window visible
		glfwShowWindow(window);
		
		// This line is critical for LWJGL's interoperation with GLFW's
		// OpenGL context, or any context that is managed externally.
		// LWJGL detects the context that is current in the current thread,
		// creates the GLCapabilities instance and makes the OpenGL
		// bindings available for use.
		GL.createCapabilities();
		
		for (int i=0;i<k_unMaxTrackedDeviceCount;i++) {
			cStates.get(i).set(VRControllerState.create());
		}

		// Call user defined initialization 
		renderContext = new VRRenderContext();
		renderContext.setOpenVR(this);
		init(renderContext);
		
		// Resize OpenGL viewport when window is resized
		glfwSetFramebufferSizeCallback(window, (window, width, height) -> {
			glViewport(0, 0, width, height);
			renderContext.resize(width, height);
		});
		
		// Run the rendering loop until the user has attempted to close
		// the window or has pressed the ESCAPE key.
		double t0 = glfwGetTime();
		timeStep = 1d/90; // 90 FPS
		while ( !glfwWindowShouldClose(window) ) {

			// Execute next time step
			double t1 = glfwGetTime();
			if(t1-t0 > timeStep)
			{
				executeStep();
				renderContext.display();
				t0=t1;
			}
			
			glfwSwapBuffers(window); // swap the color buffers

			// Poll for window events. The key callback above will only be
			// invoked during this call.
			glfwPollEvents();
		}
	}
	
	public boolean getSideTouched(int idx){
		return cStates.get(idx).ulButtonTouched() == 4 || cStates.get(idx).ulButtonTouched() == 2;
	}
	
	public boolean getTriggerTouched(int idx){
		//we have access to the raw data. If the trigger is pushed down, ulButtonTouched=8589934592		
		return cStates.get(idx).ulButtonTouched() == 8589934592l;
	}
	
	public void triggerHapticPulse(int idx, float t){
		VRSystem_TriggerHapticPulse(idx, 0, (short)Math.round(3f * t / 1e-3f));
	}
	
	/**
	 * This call-back function needs to be implemented by the user.
	 */
	abstract public void init(RenderContext renderContext);
	
	/**
	 * May be overwritten by the user to clean up.
	 */
	public void dispose()
	{
		renderContext.dispose(); //required if we don't use any FBOs?
		// This call makes sure we wait and give other threads enough time 
		// (like a timer that triggers rendering of animation frames) 
		// that may still call OpenVR functionality to finish first
		waitGetPoses();  
		VR_ShutdownInternal();
	}
	
	/**
	 * Wait and get 3D tracking poses (HMD and controllers) from OpenVR. Needs to be called before rendering
	 * and passing the rendered image to the OpenVR compositor.
	 */
	public void waitGetPoses()
	{
		VRCompositor_WaitGetPoses(hmdTrackedDevicePoses, null);
		posesReady = true;
		
		VRSystem_GetControllerState(controllerIndexHand, cStates.get(controllerIndexHand));
	    cStates.get(controllerIndexHand).ulButtonTouched();
	    
		// Get head-to-eye transformations
		HmdMatrix34 leftEyeToHead = HmdMatrix34.create();
		VRSystem_GetEyeToHeadTransform(0, leftEyeToHead);
		headToLeftEye = new Matrix4f(leftEyeToHead.m(0), leftEyeToHead.m(1), leftEyeToHead.m(2), leftEyeToHead.m(3), 
        		leftEyeToHead.m(4), leftEyeToHead.m(5), leftEyeToHead.m(6), leftEyeToHead.m(7), 
        		leftEyeToHead.m(8), leftEyeToHead.m(9), leftEyeToHead.m(10), leftEyeToHead.m(11), 
                0f, 0f, 0f, 1f);
        headToLeftEye.invert();
        HmdMatrix34 rightEyeToHead = HmdMatrix34.create();
        VRSystem_GetEyeToHeadTransform(1, rightEyeToHead);
        headToRightEye = new Matrix4f(rightEyeToHead.m(0), rightEyeToHead.m(1), rightEyeToHead.m(2), rightEyeToHead.m(3), 
        		rightEyeToHead.m(4), rightEyeToHead.m(5), rightEyeToHead.m(6), rightEyeToHead.m(7), 
        		rightEyeToHead.m(8), rightEyeToHead.m(9), rightEyeToHead.m(10), rightEyeToHead.m(11), 
                0f, 0f, 0f, 1f);
        headToRightEye.invert();

        // Get projection matrices
        HmdMatrix44 lPr = HmdMatrix44.create();
        VRSystem_GetProjectionMatrix(0, .1f, 40.f, lPr);
        leftProjectionMatrix = new Matrix4f(lPr.m(0), lPr.m(1), lPr.m(2), lPr.m(3), 
        		lPr.m(4), lPr.m(5), lPr.m(6), lPr.m(7), 
        		lPr.m(8), lPr.m(9), lPr.m(10), lPr.m(11), 
        		lPr.m(12), lPr.m(13), lPr.m(14), lPr.m(15));
        HmdMatrix44 rPr = HmdMatrix44.create();
        VRSystem_GetProjectionMatrix(1, .1f, 40.f, rPr);
        rightProjectionMatrix = new Matrix4f(rPr.m(0), rPr.m(1), rPr.m(2), rPr.m(3), 
        		rPr.m(4), rPr.m(5), rPr.m(6), rPr.m(7), 
        		rPr.m(8), rPr.m(9), rPr.m(10), rPr.m(11), 
        		rPr.m(12), rPr.m(13), rPr.m(14), rPr.m(15));
        
		// Read tracked poses data from native
        for (int nDevice = 0; nDevice < k_unMaxTrackedDeviceCount; ++nDevice ){
            ;
            if( hmdTrackedDevicePoses.get(nDevice).bPoseIsValid() ){
                HmdMatrix34 hmdMatrix = hmdTrackedDevicePoses.get(nDevice).mDeviceToAbsoluteTracking();
                poseMatrices[nDevice] = new Matrix4f(hmdMatrix.m(0), hmdMatrix.m(1), hmdMatrix.m(2), hmdMatrix.m(3), 
                		hmdMatrix.m(4), hmdMatrix.m(5), hmdMatrix.m(6), hmdMatrix.m(7), 
                        hmdMatrix.m(8), hmdMatrix.m(9), hmdMatrix.m(10), hmdMatrix.m(11), 
                        0f, 0f, 0f, 1f);
                poseValid[nDevice] = true;
            } else {
            	poseValid[nDevice] = false;
            }
        }
	}
}

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package volvis;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.util.texture.Texture;
import com.jogamp.opengl.util.texture.awt.AWTTextureIO;

import gui.RaycastRendererPanel;
import gui.TransferFunction2DEditor;
import gui.TransferFunctionEditor;

import java.awt.image.BufferedImage;

import util.TFChangeListener;
import util.VectorMath;
import volume.GradientVolume;
import volume.Volume;

/**
 *
 * @author michel
 */
public class RaycastRenderer extends Renderer implements TFChangeListener {
	
	public static final int SLICER_MODE = 0;
	public static final int MIP_MODE = 1;
	public static final int COMPOSITING_MODE = 2;
	public static final int TRANSFER_2D_MODE = 3;

    private Volume volume = null;
    private GradientVolume gradients = null;
    RaycastRendererPanel panel;
    TransferFunction tFunc;
    TransferFunctionEditor tfEditor;
    TransferFunction2DEditor tfEditor2D;
    private int mode = 0;
    private boolean shading = false;
    
    public RaycastRenderer() {
        panel = new RaycastRendererPanel(this);
        panel.setSpeedLabel("0");
    }
    
    public void setMode(int mode) {
    	this.mode = mode;
    }
    
    public void setShading(boolean shading) {
    	this.shading = shading;
    }

    public void setVolume(Volume vol) {
        System.out.println("Assigning volume");
        volume = vol;

        System.out.println("Computing gradients");
        gradients = new GradientVolume(vol);

        // set up image for storing the resulting rendering
        // the image width and height are equal to the length of the volume diagonal
        int imageSize = (int) Math.floor(Math.sqrt(vol.getDimX() * vol.getDimX() + vol.getDimY() * vol.getDimY()
                + vol.getDimZ() * vol.getDimZ()));
        if (imageSize % 2 != 0) {
            imageSize = imageSize + 1;
        }
        image = new BufferedImage(imageSize, imageSize, BufferedImage.TYPE_INT_ARGB);
        // create a standard TF where lowest intensity maps to black, the highest to white, and opacity increases
        // linearly from 0.0 to 1.0 over the intensity range
        tFunc = new TransferFunction(volume.getMinimum(), volume.getMaximum());
        
        // uncomment this to initialize the TF with good starting values for the orange dataset 
        tFunc.setTestFunc();
        
        
        tFunc.addTFChangeListener(this);
        tfEditor = new TransferFunctionEditor(tFunc, volume.getHistogram());
        
        tfEditor2D = new TransferFunction2DEditor(volume, gradients);
        tfEditor2D.addTFChangeListener(this);

        System.out.println("Finished initialization of RaycastRenderer");
    }

    public RaycastRendererPanel getPanel() {
        return panel;
    }

    public TransferFunction2DEditor getTF2DPanel() {
        return tfEditor2D;
    }
    
    public TransferFunctionEditor getTFPanel() {
        return tfEditor;
    }
     
    //Gets the voxel value at the specified coordinates through interpolation
    //See https://en.wikipedia.org/wiki/Trilinear_interpolation#Method
    short getVoxel(double[] coord) {

        if (coord[0] < 0 || coord[0] > volume.getDimX() || coord[1] < 0 || coord[1] > volume.getDimY()
                || coord[2] < 0 || coord[2] > volume.getDimZ()) {
            return 0;
        }

        int xMin = (int) Math.floor(coord[0]);
        int yMin = (int) Math.floor(coord[1]);
        int zMin = (int) Math.floor(coord[2]);
        int xMax = xMin+1;
        int yMax = yMin+1;
        int zMax = zMin+1;
        
        double x0 = coord[0] - Math.floor(coord[0]);
        double y0 = coord[1] - Math.floor(coord[1]);
        double z0 = coord[2] - Math.floor(coord[2]);
        
        //Get the 8 surrounding points
        double c000 =  volume.getVoxel(xMin, yMin, zMin);
        double c100 =  volume.getVoxel(xMax, yMin, zMin);
        double c110 =  volume.getVoxel(xMax, yMax, zMin);
        double c010 =  volume.getVoxel(xMin, yMax, zMin);
        double c001 =  volume.getVoxel(xMin, yMin, zMax);
        double c101 =  volume.getVoxel(xMax, yMin, zMax);
        double c111 =  volume.getVoxel(xMax, yMax, zMax);
        double c011 =  volume.getVoxel(xMin, yMax, zMax);
        
        //Interpolate 4 surrounding points on the final x
        double c00 = (c100-c000)*x0 + c000;
        double c01 = (c101-c001)*x0 + c001;
        double c10 = (c110-c010)*x0 + c010;
        double c11 = (c111-c011)*x0 + c011;
        
        //Interpolate 2 surrounding points on the final x and y
        double c0 = (c10-c00)*y0 + c00;
        double c1 = (c11-c01)*y0 + c01;
        
        //Interpolate final point
        double c = (c1-c0)*z0 + c0;
        
        return (short) Math.round(c);
    }

    void slicer(double[] viewMatrix) {

        // clear image (NOT NEEDED?)
//        for (int j = 0; j < image.getHeight(); j++) {
//            for (int i = 0; i < image.getWidth(); i++) {
//                image.setRGB(i, j, 0);
//            }
//        }

        // vector uVec and vVec define a plane through the origin, 
        // perpendicular to the view vector viewVec
        double[] viewVec = new double[3];
        double[] uVec = new double[3];
        double[] vVec = new double[3];
        VectorMath.setVector(viewVec, viewMatrix[2], viewMatrix[6], viewMatrix[10]);
        VectorMath.setVector(uVec, viewMatrix[0], viewMatrix[4], viewMatrix[8]);
        VectorMath.setVector(vVec, viewMatrix[1], viewMatrix[5], viewMatrix[9]);

        // image is square
        int imageCenter = image.getWidth() / 2;

        double[] pixelCoord = new double[3];
        double[] volumeCenter = new double[3];
        VectorMath.setVector(volumeCenter, volume.getDimX() / 2, volume.getDimY() / 2, volume.getDimZ() / 2);

        // sample on a plane through the origin of the volume data
        for (int j = 0; j < image.getHeight(); j++) {
            for (int i = 0; i < image.getWidth(); i++) {
                pixelCoord[0] = uVec[0] * (i - imageCenter) + vVec[0] * (j - imageCenter)
                        + volumeCenter[0];
                pixelCoord[1] = uVec[1] * (i - imageCenter) + vVec[1] * (j - imageCenter)
                        + volumeCenter[1];
                pixelCoord[2] = uVec[2] * (i - imageCenter) + vVec[2] * (j - imageCenter)
                        + volumeCenter[2];

                int val = getVoxel(pixelCoord);
                
             // Apply the transfer function to obtain a color
            	TFColor voxelColor = new TFColor();
                voxelColor = tFunc.getColor(val);
                // Alternatively, map the intensity to a grey value by linear scaling
//                voxelColor.r = maxVal/volume.getMaximum();
//                voxelColor.g = voxelColor.r;
//                voxelColor.b = voxelColor.r;
//                voxelColor.a = val > 0 ? 1.0 : 0.0;  // this makes intensity 0 completely transparent and the rest opaque
                
                
                // BufferedImage expects a pixel color packed as ARGB in an int
                int c_alpha = voxelColor.a <= 1.0 ? (int) Math.floor(voxelColor.a * 255) : 255;
                int c_red = voxelColor.r <= 1.0 ? (int) Math.floor(voxelColor.r * 255) : 255;
                int c_green = voxelColor.g <= 1.0 ? (int) Math.floor(voxelColor.g * 255) : 255;
                int c_blue = voxelColor.b <= 1.0 ? (int) Math.floor(voxelColor.b * 255) : 255;
                int pixelColor = (c_alpha << 24) | (c_red << 16) | (c_green << 8) | c_blue;
                image.setRGB(i, j, pixelColor);
            }
        }

    }

    void MIP(double[] viewMatrix) {
    	//Number of sample "slices" to take for the MIP
    	int MIPsamples = image.getHeight()/2;

    	// clear image (NOT NEEDED?)
//        for (int j = 0; j < image.getHeight(); j++) {
//            for (int i = 0; i < image.getWidth(); i++) {
//                image.setRGB(i, j, 0);
//            }
//        }

        // vector uVec and vVec define a plane through the origin, 
        // perpendicular to the view vector viewVec
        double[] viewVec = new double[3];
        double[] uVec = new double[3];
        double[] vVec = new double[3];
        VectorMath.setVector(viewVec, viewMatrix[2], viewMatrix[6], viewMatrix[10]);
        VectorMath.setVector(uVec, viewMatrix[0], viewMatrix[4], viewMatrix[8]);
        VectorMath.setVector(vVec, viewMatrix[1], viewMatrix[5], viewMatrix[9]);
        
        //Normalize the view vector
        double[] viewVecNorm = new double[3];
        double viewVecLength = VectorMath.length(viewVec);
        VectorMath.setVector(viewVecNorm, viewVec[0]/viewVecLength
        		, viewVec[1]/viewVecLength, viewVec[2]/viewVecLength);
        
        //The dimension is always equal to the length of the volume diagonal
//        double dim = Math.sqrt(volume.getDimX()*volume.getDimX()
//        		+volume.getDimY()*volume.getDimY()+volume.getDimZ()*volume.getDimZ());
        //Already calculated for image dimensions
        double dim = image.getHeight();
        //Distance between the sample "slices"
        double increment = dim / (double)MIPsamples;

        // image is square
        int imageCenter = image.getWidth() / 2;

        double[] pixelCoordCenter = new double[3];
        double[] pixelCoord = new double[3];
        double[] volumeCenter = new double[3];
        VectorMath.setVector(volumeCenter, volume.getDimX() / 2, volume.getDimY() / 2, volume.getDimZ() / 2);

        // sample on a plane through the origin of the volume data
        for (int j = 0; j < image.getHeight(); j++) {
            for (int i = 0; i < image.getWidth(); i++) {
                
            	int maxVal = 0;
            	//Slice in the center
            	pixelCoordCenter[0] = uVec[0] * (i - imageCenter) + vVec[0] * (j - imageCenter)
                        + volumeCenter[0];
            	pixelCoordCenter[1] = uVec[1] * (i - imageCenter) + vVec[1] * (j - imageCenter)
                        + volumeCenter[1];
            	pixelCoordCenter[2] = uVec[2] * (i - imageCenter) + vVec[2] * (j - imageCenter)
                        + volumeCenter[2];
            	                
            	//Calculate the different slices based on the center and the slice vectors
            	for(int k = 0 ; k < MIPsamples ; k++) {
            		//Vector from the center to the slice
            		double[] sliceVector = new double[3];
            		sliceVector[0] = viewVecNorm[0] * ((k*increment)-(dim/2));
            		sliceVector[1] = viewVecNorm[1] * ((k*increment)-(dim/2));
            		sliceVector[2] = viewVecNorm[2] * ((k*increment)-(dim/2));
                	
            		pixelCoord[0] = pixelCoordCenter[0] + sliceVector[0];
                    pixelCoord[1] = pixelCoordCenter[1] + sliceVector[1];
                    pixelCoord[2] = pixelCoordCenter[2] + sliceVector[2];

                    int val = getVoxel(pixelCoord);
                    if(val > maxVal) maxVal = val;
            	}
                
                // Apply the transfer function to obtain a color
            	TFColor voxelColor = new TFColor();
                voxelColor = tFunc.getColor(maxVal);
                // Alternatively, map the intensity to a grey value by linear scaling
//                voxelColor.r = maxVal/volume.getMaximum();
//                voxelColor.g = voxelColor.r;
//                voxelColor.b = voxelColor.r;
//                voxelColor.a = maxVal > 0 ? 1.0 : 0.0;  // this makes intensity 0 completely transparent and the rest opaque
                
                // BufferedImage expects a pixel color packed as ARGB in an int
                int c_alpha = voxelColor.a <= 1.0 ? (int) Math.floor(voxelColor.a * 255) : 255;
                int c_red = voxelColor.r <= 1.0 ? (int) Math.floor(voxelColor.r * 255) : 255;
                int c_green = voxelColor.g <= 1.0 ? (int) Math.floor(voxelColor.g * 255) : 255;
                int c_blue = voxelColor.b <= 1.0 ? (int) Math.floor(voxelColor.b * 255) : 255;
                int pixelColor = (c_alpha << 24) | (c_red << 16) | (c_green << 8) | c_blue;
                image.setRGB(i, j, pixelColor);
            }
        }
    }
    
    void compositing(double[] viewMatrix) {
    	//Number of sample "slices" to take for the MIP
    	int samples = image.getHeight()/4;

        // vector uVec and vVec define a plane through the origin, 
        // perpendicular to the view vector viewVec
        double[] viewVec = new double[3];
        double[] uVec = new double[3];
        double[] vVec = new double[3];
        VectorMath.setVector(viewVec, viewMatrix[2], viewMatrix[6], viewMatrix[10]);
        VectorMath.setVector(uVec, viewMatrix[0], viewMatrix[4], viewMatrix[8]);
        VectorMath.setVector(vVec, viewMatrix[1], viewMatrix[5], viewMatrix[9]);
        
        //Normalize the view vector
        double[] viewVecNorm = new double[3];
        double viewVecLength = VectorMath.length(viewVec);
        VectorMath.setVector(viewVecNorm, viewVec[0]/viewVecLength
        		, viewVec[1]/viewVecLength, viewVec[2]/viewVecLength);
        
        //Already calculated for image dimensions
        double dim = image.getHeight();
        //Distance between the sample "slices"
        double increment = dim / (double)samples;

        // image is square
        int imageCenter = image.getWidth() / 2;

        double[] pixelCoordCenter = new double[3];
        double[] pixelCoord = new double[3];
        double[] volumeCenter = new double[3];
        VectorMath.setVector(volumeCenter, volume.getDimX() / 2, volume.getDimY() / 2, volume.getDimZ() / 2);

        // sample on a plane through the origin of the volume data
        for (int j = 0; j < image.getHeight(); j++) {
            for (int i = 0; i < image.getWidth(); i++) {
                
            	TFColor voxelColor = tFunc.getColor(0);
            	//Slice in the center
            	pixelCoordCenter[0] = uVec[0] * (i - imageCenter) + vVec[0] * (j - imageCenter)
                        + volumeCenter[0];
            	pixelCoordCenter[1] = uVec[1] * (i - imageCenter) + vVec[1] * (j - imageCenter)
                        + volumeCenter[1];
            	pixelCoordCenter[2] = uVec[2] * (i - imageCenter) + vVec[2] * (j - imageCenter)
                        + volumeCenter[2];
            	//Calculate the different slices based on the center and the slice vectors
            	for(int k = 0 ; k < samples ; k++) {
            		//Vector from the center to the slice
            		double[] sliceVector = new double[3];
            		sliceVector[0] = viewVecNorm[0] * ((k*increment)-(dim/2));
            		sliceVector[1] = viewVecNorm[1] * ((k*increment)-(dim/2));
            		sliceVector[2] = viewVecNorm[2] * ((k*increment)-(dim/2));
                	
            		pixelCoord[0] = pixelCoordCenter[0] + sliceVector[0];
                    pixelCoord[1] = pixelCoordCenter[1] + sliceVector[1];
                    pixelCoord[2] = pixelCoordCenter[2] + sliceVector[2];

                    int val = getVoxel(pixelCoord);
                    // Apply the transfer function to obtain a color
                    voxelColor = nextColor(voxelColor, tFunc.getColor(val));
            	}
                
                // BufferedImage expects a pixel color packed as ARGB in an int
                int c_alpha = voxelColor.a <= 1.0 ? (int) Math.floor(voxelColor.a * 255) : 255;
                int c_red = voxelColor.r <= 1.0 ? (int) Math.floor(voxelColor.r * 255) : 255;
                int c_green = voxelColor.g <= 1.0 ? (int) Math.floor(voxelColor.g * 255) : 255;
                int c_blue = voxelColor.b <= 1.0 ? (int) Math.floor(voxelColor.b * 255) : 255;
                int pixelColor = (c_alpha << 24) | (c_red << 16) | (c_green << 8) | c_blue;
                image.setRGB(i, j, pixelColor);
            }
        }
    }
    
    void transfer2D(double[] viewMatrix) {
    	//Number of sample "slices" to take for the MIP
    	int samples = image.getHeight()/4;

        // vector uVec and vVec define a plane through the origin, 
        // perpendicular to the view vector viewVec
        double[] viewVec = new double[3];
        double[] uVec = new double[3];
        double[] vVec = new double[3];
        VectorMath.setVector(viewVec, viewMatrix[2], viewMatrix[6], viewMatrix[10]);
        VectorMath.setVector(uVec, viewMatrix[0], viewMatrix[4], viewMatrix[8]);
        VectorMath.setVector(vVec, viewMatrix[1], viewMatrix[5], viewMatrix[9]);
        
        //Normalize the view vector
        double[] viewVecNorm = new double[3];
        double viewVecLength = VectorMath.length(viewVec);
        VectorMath.setVector(viewVecNorm, viewVec[0]/viewVecLength
        		, viewVec[1]/viewVecLength, viewVec[2]/viewVecLength);
        
        //Already calculated for image dimensions
        double dim = image.getHeight();
        //Distance between the sample "slices"
        double increment = dim / (double)samples;

        // image is square
        int imageCenter = image.getWidth() / 2;

        double[] pixelCoordCenter = new double[3];
        double[] pixelCoord = new double[3];
        double[] volumeCenter = new double[3];
        VectorMath.setVector(volumeCenter, volume.getDimX() / 2, volume.getDimY() / 2, volume.getDimZ() / 2);

        // sample on a plane through the origin of the volume data
        for (int j = 0; j < image.getHeight(); j++) {
            for (int i = 0; i < image.getWidth(); i++) {
                
            	TFColor voxelColor = tFunc.getColor(0);
            	//Slice in the center
            	pixelCoordCenter[0] = uVec[0] * (i - imageCenter) + vVec[0] * (j - imageCenter)
                        + volumeCenter[0];
            	pixelCoordCenter[1] = uVec[1] * (i - imageCenter) + vVec[1] * (j - imageCenter)
                        + volumeCenter[1];
            	pixelCoordCenter[2] = uVec[2] * (i - imageCenter) + vVec[2] * (j - imageCenter)
                        + volumeCenter[2];
            	//Calculate the different slices based on the center and the slice vectors
            	for(int k = 0 ; k < samples ; k++) {
            		//Vector from the center to the slice
            		double[] sliceVector = new double[3];
            		sliceVector[0] = viewVecNorm[0] * ((k*increment)-(dim/2));
            		sliceVector[1] = viewVecNorm[1] * ((k*increment)-(dim/2));
            		sliceVector[2] = viewVecNorm[2] * ((k*increment)-(dim/2));
                	
            		pixelCoord[0] = pixelCoordCenter[0] + sliceVector[0];
                    pixelCoord[1] = pixelCoordCenter[1] + sliceVector[1];
                    pixelCoord[2] = pixelCoordCenter[2] + sliceVector[2];

                    int val = getVoxel(pixelCoord);
                    // Apply the transfer function to obtain a color
                    voxelColor = nextColor(voxelColor, tFunc.getColor(val));
            	}
                
                // BufferedImage expects a pixel color packed as ARGB in an int
                int c_alpha = voxelColor.a <= 1.0 ? (int) Math.floor(voxelColor.a * 255) : 255;
                int c_red = voxelColor.r <= 1.0 ? (int) Math.floor(voxelColor.r * 255) : 255;
                int c_green = voxelColor.g <= 1.0 ? (int) Math.floor(voxelColor.g * 255) : 255;
                int c_blue = voxelColor.b <= 1.0 ? (int) Math.floor(voxelColor.b * 255) : 255;
                int pixelColor = (c_alpha << 24) | (c_red << 16) | (c_green << 8) | c_blue;
                image.setRGB(i, j, pixelColor);
            }
        }
    }
    
    //Calculate next color for compositing from the current color and the next color in front
    public TFColor nextColor(TFColor current, TFColor next) {
    	TFColor newColor = new TFColor();
    	newColor.r = next.a*next.r + (1-next.a)*current.r;
    	newColor.g = next.a*next.g + (1-next.a)*current.g;
    	newColor.b = next.a*next.b + (1-next.a)*current.b;
    	return newColor;
    }

    private void drawBoundingBox(GL2 gl) {
        gl.glPushAttrib(GL2.GL_CURRENT_BIT);
        gl.glDisable(GL2.GL_LIGHTING);
        gl.glColor4d(1.0, 1.0, 1.0, 1.0);
        gl.glLineWidth(1.5f);
        gl.glEnable(GL.GL_LINE_SMOOTH);
        gl.glHint(GL.GL_LINE_SMOOTH_HINT, GL.GL_NICEST);
        gl.glEnable(GL.GL_BLEND);
        gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);

        gl.glBegin(GL.GL_LINE_LOOP);
        gl.glVertex3d(-volume.getDimX() / 2.0, -volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(-volume.getDimX() / 2.0, volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, -volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glEnd();

        gl.glBegin(GL.GL_LINE_LOOP);
        gl.glVertex3d(-volume.getDimX() / 2.0, -volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glVertex3d(-volume.getDimX() / 2.0, volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, -volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glEnd();

        gl.glBegin(GL.GL_LINE_LOOP);
        gl.glVertex3d(volume.getDimX() / 2.0, -volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, -volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glEnd();

        gl.glBegin(GL.GL_LINE_LOOP);
        gl.glVertex3d(-volume.getDimX() / 2.0, -volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glVertex3d(-volume.getDimX() / 2.0, -volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(-volume.getDimX() / 2.0, volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(-volume.getDimX() / 2.0, volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glEnd();

        gl.glBegin(GL.GL_LINE_LOOP);
        gl.glVertex3d(-volume.getDimX() / 2.0, volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glVertex3d(-volume.getDimX() / 2.0, volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glEnd();

        gl.glBegin(GL.GL_LINE_LOOP);
        gl.glVertex3d(-volume.getDimX() / 2.0, -volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glVertex3d(-volume.getDimX() / 2.0, -volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, -volume.getDimY() / 2.0, volume.getDimZ() / 2.0);
        gl.glVertex3d(volume.getDimX() / 2.0, -volume.getDimY() / 2.0, -volume.getDimZ() / 2.0);
        gl.glEnd();

        gl.glDisable(GL.GL_LINE_SMOOTH);
        gl.glDisable(GL.GL_BLEND);
        gl.glEnable(GL2.GL_LIGHTING);
        gl.glPopAttrib();

    }

    @Override
    public void visualize(GL2 gl) {


        if (volume == null) {
            return;
        }

        drawBoundingBox(gl);

        gl.glGetDoublev(GL2.GL_MODELVIEW_MATRIX, viewMatrix, 0);

        long startTime = System.currentTimeMillis();
        switch(mode) {
        case SLICER_MODE:
        	slicer(viewMatrix);   
        	break;
        case MIP_MODE:
        	MIP(viewMatrix);   
        	break;
        case COMPOSITING_MODE:
        	compositing(viewMatrix);   
        	break;
        case TRANSFER_2D_MODE:
        	transfer2D(viewMatrix);   
        	break;
        default:
        	slicer(viewMatrix);
        	break;
        }
        
        long endTime = System.currentTimeMillis();
        double runningTime = (endTime - startTime);
        panel.setSpeedLabel(Double.toString(runningTime));

        Texture texture = AWTTextureIO.newTexture(gl.getGLProfile(), image, false);

        gl.glPushAttrib(GL2.GL_LIGHTING_BIT);
        gl.glDisable(GL2.GL_LIGHTING);
        gl.glEnable(GL.GL_BLEND);
        gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);

        // draw rendered image as a billboard texture
        texture.enable(gl);
        texture.bind(gl);
        double halfWidth = image.getWidth() / 2.0;
        gl.glPushMatrix();
        gl.glLoadIdentity();
        gl.glBegin(GL2.GL_QUADS);
        gl.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        gl.glTexCoord2d(0.0, 0.0);
        gl.glVertex3d(-halfWidth, -halfWidth, 0.0);
        gl.glTexCoord2d(0.0, 1.0);
        gl.glVertex3d(-halfWidth, halfWidth, 0.0);
        gl.glTexCoord2d(1.0, 1.0);
        gl.glVertex3d(halfWidth, halfWidth, 0.0);
        gl.glTexCoord2d(1.0, 0.0);
        gl.glVertex3d(halfWidth, -halfWidth, 0.0);
        gl.glEnd();
        texture.disable(gl);
        texture.destroy(gl);
        gl.glPopMatrix();

        gl.glPopAttrib();


        if (gl.glGetError() > 0) {
            System.out.println("some OpenGL error: " + gl.glGetError());
        }

    }
    private BufferedImage image;
    private double[] viewMatrix = new double[4 * 4];

    @Override
    public void changed() {
        for (int i=0; i < listeners.size(); i++) {
            listeners.get(i).changed();
        }
    }
}

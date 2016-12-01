/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package volume;

/**
 *
 * @author michel
 */
public class GradientVolume {

    public GradientVolume(Volume vol) {
        volume = vol;
        dimX = vol.getDimX();
        dimY = vol.getDimY();
        dimZ = vol.getDimZ();
        data = new VoxelGradient[dimX * dimY * dimZ];
        compute();
    }
    
    //Interpolate two VoxelGradients
    public static  VoxelGradient interpolateGradients(VoxelGradient g0, VoxelGradient g1, double ratio) {
    	return new VoxelGradient((g1.x-g0.x)*ratio + g0.x, (g1.y-g0.y)*ratio + g0.y, (g1.z-g0.z)*ratio + g0.z);
    }
    
    //Gets the gradient value at the specified coordinates through interpolation
    //See https://en.wikipedia.org/wiki/Trilinear_interpolation#Method
    public VoxelGradient getGradient(double x, double y, double z) {
    	int xMin = (int) Math.floor(x);
        int yMin = (int) Math.floor(y);
        int zMin = (int) Math.floor(z);
        int xMax = xMin+1;
        int yMax = yMin+1;
        int zMax = zMin+1;
        
        double x0 = x - Math.floor(x);
        double y0 = y - Math.floor(y);
        double z0 = z - Math.floor(z);
        
        //Get the 8 surrounding gradients
        VoxelGradient c000 =  getGradient(xMin, yMin, zMin);
        VoxelGradient c100 =  getGradient(xMax, yMin, zMin);
        VoxelGradient c110 =  getGradient(xMax, yMax, zMin);
        VoxelGradient c010 =  getGradient(xMin, yMax, zMin);
        VoxelGradient c001 =  getGradient(xMin, yMin, zMax);
        VoxelGradient c101 =  getGradient(xMax, yMin, zMax);
        VoxelGradient c111 =  getGradient(xMax, yMax, zMax);
        VoxelGradient c011 =  getGradient(xMin, yMax, zMax);
        
        //Interpolate 4 surrounding gradients on the final x
        VoxelGradient c00 = interpolateGradients(c000,c100,x0);
        VoxelGradient c01 = interpolateGradients(c001,c101,x0);
        VoxelGradient c10 = interpolateGradients(c010,c110,x0);
        VoxelGradient c11 = interpolateGradients(c011,c111,x0);
        
        //Interpolate 2 surrounding gradients on the final x and y
        VoxelGradient c0 = interpolateGradients(c00,c10,y0);
        VoxelGradient c1 = interpolateGradients(c01,c11,y0);
        
        //Interpolate final gradient
        VoxelGradient c = interpolateGradients(c0,c1,z0);
        
        return c;
    }

    public VoxelGradient getGradient(int x, int y, int z) {
    	int index = x + dimX * (y + dimY * z);
        if(index > 0 && index < data.length) return data[index];
        else return new VoxelGradient();
    }
    
    public void setGradient(int x, int y, int z, VoxelGradient value) {
        data[x + dimX * (y + dimY * z)] = value;
    }

    public void setVoxel(int i, VoxelGradient value) {
        data[i] = value;
    }

    public VoxelGradient getVoxel(int i) {
        return data[i];
    }

    public int getDimX() {
        return dimX;
    }

    public int getDimY() {
        return dimY;
    }

    public int getDimZ() {
        return dimZ;
    }

    private void compute() {
        
    	maxmag = -1.0;
        //Calculate the gradients
        for(int x = 0 ; x < dimX ; x++) {
        	for(int y = 0 ; y < dimY ; y++) {
        		for(int z = 0 ; z < dimZ ; z++) {
        			float gradX = x==0 || x==dimX-1 ? 0 : (float)(volume.getVoxel(x+1, y, z) - volume.getVoxel(x-1, y, z))/2;
        			float gradY = y==0 || y==dimY-1 ? 0 : (float)(volume.getVoxel(x, y+1, z) - volume.getVoxel(x, y-1, z))/2;
        			float gradZ = z==0 || z==dimZ-1 ? 0 : (float)(volume.getVoxel(x, y, z+1) - volume.getVoxel(x, y, z-1))/2;
        			VoxelGradient grad = new VoxelGradient(gradX,gradY,gradZ);
        			setGradient(x, y, z, grad);
        			if(grad.mag > maxmag) maxmag = grad.mag;
                }
            }
        }
                
    }
    
    public double getMaxGradientMagnitude() {
        if (maxmag >= 0) {
            return maxmag;
        } else {
            double magnitude = data[0].mag;
            for (int i=0; i<data.length; i++) {
                magnitude = data[i].mag > magnitude ? data[i].mag : magnitude;
            }   
            maxmag = magnitude;
            return magnitude;
        }
    }
    
    private int dimX, dimY, dimZ;
    private VoxelGradient zero = new VoxelGradient();
    VoxelGradient[] data;
    Volume volume;
    double maxmag;
}

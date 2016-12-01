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

    public VoxelGradient getGradient(int x, int y, int z) {
        return data[x + dimX * (y + dimY * z)];
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

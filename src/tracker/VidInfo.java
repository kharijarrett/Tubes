package tracker;

import java.io.Serializable;
import java.util.ArrayList;

public class VidInfo implements Serializable {
	
	int width;
	int height;
	ArrayList<float[]> features;
	
	public VidInfo(int width, int height, ArrayList<float[]> features) {
		this.width = width;
		this.height = height;
		this.features = features;
	}

}

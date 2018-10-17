package test;

import java.io.Serializable;
import java.util.ArrayList;

public class ClusterFrame implements Serializable {

	int frameNum;
	ArrayList<ClusterFeature> features;
	
	public ClusterFrame(int frameNum, ArrayList<ClusterFeature> features) {
		this.frameNum = frameNum;
		this.features = features;
	}
	
	public String toString() {
		String output = "---frame " + frameNum + "---\n";
		for(int i = 0; i < features.size(); i++) {
			output += "id: " + features.get(i).id + ", cluster: " + features.get(i).clusterno + ", x: " + features.get(i).x + ", y: " + features.get(i).y + "\n";
		}
		return output;
	}
	
}

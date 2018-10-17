package test;

import java.io.Serializable;
import java.util.ArrayList;

/**
 * <p>
 * Holds basic frame information together, used in FeatureLabeler class
 * 
 * </p>
 *
 * @author Chris Kymn
 */
public class SimpleFrame implements Serializable {
	
	int frameNum;
	ArrayList<FeaturePosition> features;
	
	public SimpleFrame(int frameNum, ArrayList<FeaturePosition> features) {
		this.frameNum = frameNum;
		this.features = features;
	}
	
	public String toString() {
		String output = "---frame " + frameNum + "---\n";
		for(int i = 0; i < features.size(); i++) {
			output += "id: " + features.get(i).id + ", x: " + features.get(i).x + ", y: " + features.get(i).y + "\n";
		}
		return output;
	}
	
}
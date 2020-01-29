package test;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * <p>
 * Used to store information about features/descriptors in a single video, for easy replay later on.
 * </p>
 *
 * @author Chris Kymn
 */
public class ClipArchive implements Serializable {
	
	Map<Long, Integer> featapps;
	Map<Long, Double[]> featSURF;
	ArrayList<SimpleFrame> localFrames;
	int height;
	int width;
	
	public ClipArchive() {
		featapps = new HashMap<Long, Integer>();
		featSURF = new HashMap<Long, Double[]>();
		localFrames = new ArrayList<SimpleFrame>();
	}
	
	public String toString() {
		String output = "";
		for(Long p : featapps.keySet()) {
			output += "id: " + p + ", apps: " + featapps.get(p).toString() + "\n";
		}
		for(int i = 0; i < localFrames.size(); i++) {
			output += localFrames.get(i).toString();
		}
		return output;
	}
	
}

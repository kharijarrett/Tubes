package tracker;

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
	
	private static final long serialVersionUID = 1L;
	Map<Long, Integer> featapps; //number of times each feature appeared
	Map<Long, Double[]> featSURF; //SURF feature information
	ArrayList<SimpleFrame> localFrames; //information about features on each frame
	int height, width; //video dimensions
	
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

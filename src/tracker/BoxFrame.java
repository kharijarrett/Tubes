package tracker;

import java.awt.Polygon;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;

public class BoxFrame {
	
	Rectangle2D bound;
	Polygon polybound;
	int frame;
	ArrayList<FeaturePosition> feats;
	
	public BoxFrame(Rectangle2D bound, Polygon polybound, int frame) {
		this.polybound = polybound;
		this.bound = bound;
		this.frame = frame;
	}
	
	public void setIncluded(ArrayList<FeaturePosition> features) {
		feats = new ArrayList<FeaturePosition>();
		if(features == null) return;
		for(FeaturePosition f : features) {
			if(polybound.contains(f.x, f.y)) feats.add(f);
		}
		return;
	}
	
	public String toString() {
		String output = "";
		output += "BOXFRAME @ frame" + frame;
		output += "\n" + bound + "\nFeatures: ";
		for(int i = 0; i < feats.size(); i++) {
			output += feats.get(i) + " ";
		}
		return output;
	}
	
}

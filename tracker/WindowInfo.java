package tracker;

import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.Serializable;
import java.util.ArrayList;

public class WindowInfo implements Serializable{
	
//	BufferedImage image;
	Rectangle2D rect;
	ArrayList<FeaturePosition> feats;
	
	public WindowInfo(Rectangle2D rect, ArrayList<FeaturePosition> feats) {
//		this.image = image;
		this.rect = rect;
		this.feats = feats;
	}
	
}

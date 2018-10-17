package tracker;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Map;

import boofcv.struct.image.ImageGray;

/**
 * <p>
 * Abstract class to wrap Feature Detectors and allow easy substitution
 * </p>
 *
 * @author Chris Kymn
 */
@SuppressWarnings("rawtypes")
public abstract class FeatureDetector<T extends ImageGray> {
	
	Class<T> imageType;
	
	public FeatureDetector(Class<T> imageType) {
		this.imageType = imageType;
	}
	
	public abstract void process(T frame); //pass in frame for tracker to process
	public abstract void process(BufferedImage frame); //pass in frame for tracker to process
	public abstract ArrayList<FeaturePosition> getFrameFeatures(); // get features in a given frame
	public abstract Map<Long, Integer> updateFeatApps(Map<Long, Integer> featapps); //update appearances of feature
	public abstract Map<Long, Double[]> updateFeatSURF(Map<Long, Double[]> featSURF); //get SURF descriptor
	
}

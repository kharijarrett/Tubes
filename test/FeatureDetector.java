package test;

import java.util.ArrayList;
import java.util.Map;

import boofcv.struct.image.ImageGray;

@SuppressWarnings("rawtypes")
public abstract class FeatureDetector<T extends ImageGray> {
	
	Class<T> imageType;
	
	public FeatureDetector(Class<T> imageType) {
		this.imageType = imageType;
	}
	
	public abstract void process(T frame);
	public abstract ArrayList<FeaturePosition> getFrameFeatures();
	public abstract Map<Long, Integer> updateFeatApps(Map<Long, Integer> featapps);
	public abstract Map<Long, Double[]> updateFeatSURF(Map<Long, Double[]> featSURF);
	
}

package tracker;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Map;

import boofcv.abst.feature.detect.interest.ConfigGeneralDetector;
import boofcv.abst.feature.tracker.PointTrack;
import boofcv.abst.feature.tracker.PointTracker;
import boofcv.alg.filter.derivative.GImageDerivativeOps;
import boofcv.alg.tracker.klt.PkltConfig;
import boofcv.io.image.ConvertBufferedImage;
import boofcv.struct.image.GrayF32;
import boofcv.struct.image.ImageGray;

@SuppressWarnings("rawtypes")

/**
 * <p>
 * Frame processor that supports bounded boxes.
 * </p>
 *
 * @author Chris Kymn
 */
public class BoxKLTDetector<T extends ImageGray> extends FeatureDetector {

	PointTracker<T> tracker;
	int frames = 0;
	
	@SuppressWarnings("unchecked")
	public BoxKLTDetector(Class<T> imageType) {
		
		super(imageType);
		
		PkltConfig config = new PkltConfig();
		config.templateRadius = 3;
		config.pyramidScaling = new int[]{1,2,4,8};

		//KLT tracker that only spawns points in bounded boxes
		tracker = BoxFactoryPointTracker.klt(config, new ConfigGeneralDetector(-1, 6, 1),
				imageType, GImageDerivativeOps.getDerivativeType(imageType));
		
	}
	
	/**
	 * Returns number of currently tracked features that are in a box.
	 */
	@SuppressWarnings("unchecked")
	public void process(ImageGray frame) {
		
		frames++;
		// tell the tracker to process the frame
		tracker.process((T) frame);
		BoxPointTrackerKltPyramid wrap = (BoxPointTrackerKltPyramid) tracker;
		
		//are there enough features in boxes?
		//ignores first 10 frames as magno stream is not too reliable at this point
		if(wrap.boxedFeatures() < 400 && frames > 10)
			tracker.spawnTracks();
		
	}
	
	public void process(BufferedImage frame) {
		GrayF32 newFrame = ConvertBufferedImage.convertFromSingle(frame, null, GrayF32.class);
		process(newFrame);
	}
	
	/**
	 * Call this function after calling process().
	 * Returns the features that are active in the frame just processed.
	 */
	public ArrayList<FeaturePosition> getFrameFeatures() {
		
		ArrayList<FeaturePosition> framefeatures = new ArrayList<FeaturePosition>();
		for(PointTrack p : tracker.getActiveTracks(null)) {
			framefeatures.add(new FeaturePosition(p.featureId, p.x, p.y));
		}	
		
		return framefeatures;
	}
	
	/**
	 * Input: Map<Long, Integer> with features and count of their appearances
	 * Output: Updated Map based on information in the last frame
	 */
	@SuppressWarnings("unchecked")
	@Override
	public Map<Long, Integer> updateFeatApps(Map featapps) {

		for( PointTrack p : tracker.getActiveTracks(null)) {
			
			featapps = (Map<Long, Integer>) featapps;
			
			//number of appearances
			if(!featapps.containsKey(p.featureId)) {
				featapps.put(p.featureId, 1);
			}
			else {
				featapps.put(p.featureId, (Integer)featapps.get(p.featureId)+1);
			}
			
		}
		return featapps;
	}

	/**
	 * Input: Map<Long, Double[]> with SURF feature information
	 * Output: Updated Map based on information in the last frame
	 */
	@Override
	public Map<Long, Double[]> updateFeatSURF(Map featSURF) {
		
		//currently null, since KLT tracker does not yet have SURF descriptors
		return null;
	}
	
}

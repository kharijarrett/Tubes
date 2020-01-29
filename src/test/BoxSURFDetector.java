package test;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

import boofcv.abst.feature.detect.interest.ConfigFastHessian;
import boofcv.abst.feature.detect.interest.ConfigGeneralDetector;
import boofcv.abst.feature.tracker.PointTrack;
import boofcv.abst.feature.tracker.PointTracker;
import boofcv.alg.filter.derivative.GImageDerivativeOps;
import boofcv.alg.tracker.klt.PkltConfig;
import boofcv.factory.feature.tracker.FactoryPointTracker;
import boofcv.struct.feature.BrightFeature;
import boofcv.struct.image.ImageGray;

@SuppressWarnings("rawtypes")

/**
 * <p>
 * Frame processor that supports bounded boxes.
 * Currently, the code that is commented in determines what type of tracker is being used.
 * </p>
 *
 * @author Chris Kymn
 */
public class BoxSURFDetector<T extends ImageGray> extends SURFDetector {

	PointTracker<T> tracker;
	int frames = 0;
	
	@SuppressWarnings("unchecked")
	public BoxSURFDetector(Class<T> imageType) {
		
		super(imageType);
		
		PkltConfig config = new PkltConfig();
		config.templateRadius = 3;
		config.pyramidScaling = new int[]{1,2,4,8};
		
		//generic KLT tracker
//		tracker = FactoryPointTracker.klt(config, new ConfigGeneralDetector(600, 6, 1),
//				imageType, GImageDerivativeOps.getDerivativeType(imageType));

		//KLT tracker that only spawns points in bounded boxes
		tracker = BoxFactoryPointTracker.klt(config, new ConfigGeneralDetector(-1, 6, 1),
				imageType, GImageDerivativeOps.getDerivativeType(imageType));
		
		//KLT tracker with SURF descriptors- currently getting these to work on other programs
//		tracker = FactoryPointTracker.combined_ST_SURF_KLT(new ConfigGeneralDetector(600, 6, 1), 
//				config, 0, null, null, imageType, GImageDerivativeOps.getDerivativeType(imageType));
//		ConfigFastHessian configDetector = new ConfigFastHessian();
//		configDetector.maxFeaturesPerScale = 20;
//		configDetector.extractRadius = 1;
//		configDetector.initialSampleSize = 1;
//		configDetector.numberOfOctaves = 4;
//		configDetector.numberScalesPerOctave = 4;
		
		//Fast Hessian SURF tracker
//		tracker = BoxFactoryPointTracker.dda_FH_SURF_Fast(configDetector, null, null, imageType);
		
	}
	
	/**
	 * Returns number of currently tracked features that are in a box.
	 */
	@SuppressWarnings("unchecked")
	public void process(ImageGray frame) {
		
		frames++;
		// tell the tracker to process the frame
		tracker.process((T) frame);
		
//		BoxDetectDescribeAssociate boxtracker = (BoxDetectDescribeAssociate) tracker;
		
		// if there are too few tracks spawn more
//		BoxDetectDescribeAssociate wrap = (BoxDetectDescribeAssociate) tracker;
		BoxPointTrackerKltPyramid wrap = (BoxPointTrackerKltPyramid) tracker;
		if(wrap.boxedFeatures() < 400 && frames > 10)
//		if( tracker.getActiveTracks(null).size() < 800)
//		if(Math.random() > 0.5)
			tracker.spawnTracks();

//		System.out.println("New tracks: " + tracker.getNewTracks(null));
//		System.out.println("Dropped tracks: " + tracker.getDroppedTracks(null));

	}
	
	public ArrayList<FeaturePosition> getFrameFeatures() {
		
		ArrayList<FeaturePosition> framefeatures = new ArrayList<FeaturePosition>();
		for(PointTrack p : tracker.getActiveTracks(null)) {
			framefeatures.add(new FeaturePosition(p.featureId, p.x, p.y));
		}	
		
		return framefeatures;
	}

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
		// TODO Auto-generated method stub
		return featapps;
	}

	@SuppressWarnings("unchecked")
	@Override
	public Map<Long, Double[]> updateFeatSURF(Map featSURF) {
		
		
		return null;
		
		//currently commented out, since KLT tracker does not yet have SURF descriptors
		
//		featSURF = (Map<Long, Double[]>) featSURF;
//		
//		for( PointTrack p : tracker.getAllTracks(null)) {
//			
//			if(!featSURF.containsKey(p.featureId)) {
//				double storeDoubles[] = new double[64];
//				NewDetectDescribeAssociate<T, BrightFeature> wrap = (NewDetectDescribeAssociate<T, BrightFeature>) tracker;
//				BrightFeature b[] = wrap.getFeatSrc().data;
//				if(p.featureId >= b.length || b[(int) p.featureId] == null) continue;
//				storeDoubles = wrap.getFeatSrc().data[(int) p.featureId].value;
//				featSURF.put((Long) p.featureId, Arrays.stream(storeDoubles).boxed().toArray(Double[]::new));
//			}
//			
//		}
//		
//		return featSURF;
	}
	
}

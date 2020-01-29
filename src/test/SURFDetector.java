package test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

import boofcv.abst.feature.detect.interest.ConfigFastHessian;
import boofcv.abst.feature.tracker.PointTrack;
import boofcv.abst.feature.tracker.PointTracker;
import boofcv.struct.feature.BrightFeature;
import boofcv.struct.image.ImageGray;

@SuppressWarnings("rawtypes")
public class SURFDetector<T extends ImageGray> extends FeatureDetector {

	PointTracker<T> tracker;
	
	@SuppressWarnings("unchecked")
	public SURFDetector(Class<T> imageType) {
		
		super(imageType);
		
		ConfigFastHessian configDetector = new ConfigFastHessian();
		configDetector.maxFeaturesPerScale = 800;
		configDetector.extractRadius = 1;
		configDetector.initialSampleSize = 1;
		tracker = NewFactoryPointTracker.dda_FH_SURF_Fast(configDetector, null, null, imageType);
		
	}

	@SuppressWarnings("unchecked")
	public void process(ImageGray frame) {
		
		// tell the tracker to process the frame
		tracker.process((T) frame);

		// if there are too few tracks spawn more
		if( tracker.getActiveTracks(null).size() < 800 )
			tracker.spawnTracks();
		// TODO Auto-generated method stub
		
	}

	@Override
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
		
		featSURF = (Map<Long, Double[]>) featSURF;
		
		for( PointTrack p : tracker.getAllTracks(null)) {
			
			if(!featSURF.containsKey(p.featureId)) {
				double storeDoubles[] = new double[64];
				NewDetectDescribeAssociate<T, BrightFeature> wrap = (NewDetectDescribeAssociate<T, BrightFeature>) tracker;
				BrightFeature b[] = wrap.getFeatSrc().data;
				if(p.featureId >= b.length || b[(int) p.featureId] == null) continue;
				storeDoubles = wrap.getFeatSrc().data[(int) p.featureId].value;
				featSURF.put((Long) p.featureId, Arrays.stream(storeDoubles).boxed().toArray(Double[]::new));
			}
			
		}
		
		return featSURF;
	}

		
	

}

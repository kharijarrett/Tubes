package test;

import java.util.ArrayList;

import boofcv.abst.feature.tracker.PointTrack;
import boofcv.abst.feature.tracker.PointTrackerKltPyramid;
import boofcv.abst.filter.derivative.ImageGradient;
import boofcv.alg.feature.detect.interest.GeneralFeatureDetector;
import boofcv.alg.interpolate.InterpolateRectangle;
import boofcv.alg.tracker.klt.KltConfig;
import boofcv.struct.image.ImageGray;
import boofcv.struct.pyramid.PyramidDiscrete;
import georegression.struct.shapes.Rectangle2D_I32;

/**
 * <p>
 * Override of PointTrackerKltPyramid used to get bounding boxes for track spawning
 * </p>
 *
 * @author Chris Kymn
 */
@SuppressWarnings("rawtypes")
public class BoxPointTrackerKltPyramid<I extends ImageGray,D extends ImageGray> extends PointTrackerKltPyramid {

	private ArrayList<Rectangle2D_I32> boxes = new ArrayList<Rectangle2D_I32>();
	
	@SuppressWarnings("unchecked")
	public BoxPointTrackerKltPyramid(KltConfig config, int templateRadius, PyramidDiscrete pyramid,
			GeneralFeatureDetector detector, ImageGradient gradient, InterpolateRectangle interpInput,
			InterpolateRectangle interpDeriv, Class derivType) {
		super(config, templateRadius, pyramid, detector, gradient, interpInput, interpDeriv, derivType);
	}
	
	public void setBoxes(ArrayList<Rectangle2D_I32> boxes) {
		this.boxes = boxes;
	}
	
	public ArrayList<Rectangle2D_I32> getBoxes() {
		return boxes;
	}
	
	/**
	 * Does the spawn occur within a box?
	 */
	protected boolean checkValidSpawn( PointTrack p ) {
		for(Rectangle2D_I32 r : boxes) {
			if(p.x > r.x0 && p.x < r.x1 && p.y > r.y0 && p.y < r.y1) return true;
		}
		return false;
	}
	
	/**
	 * Returns number of currently tracked features that are in a box.
	 */
	@SuppressWarnings("unchecked")
	public int boxedFeatures() {
		int count = 0;
		for(Object o : getActiveTracks(null)) {
			PointTrack p = (PointTrack) o;
			for(Rectangle2D_I32 r : boxes) {
				if(p.x > r.x0 && p.x < r.x1 && p.y > r.y0 && p.y < r.y1) count++;
				break;
			}
		}
		return count;
	}
	
}

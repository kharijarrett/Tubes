package test;

import java.util.ArrayList;

import org.ddogleg.struct.FastQueue;

import boofcv.abst.feature.associate.AssociateDescription2D;
import boofcv.abst.feature.tracker.DdaFeatureManager;
import boofcv.abst.feature.tracker.DdaManagerDetectDescribePoint;
import boofcv.abst.feature.tracker.DetectDescribeAssociate;
import boofcv.abst.feature.tracker.PointTrack;
import boofcv.struct.feature.BrightFeature;
import boofcv.struct.feature.TupleDesc;
import boofcv.struct.image.ImageGray;
import georegression.struct.shapes.Rectangle2D_I32;

/**
 * <p>
 * Override of DetectDescribeAssociate used to get extra SURF feature information
 * </p>
 *
 * @author Chris Kymn
 */
@SuppressWarnings("rawtypes")
public class BoxDetectDescribeAssociate<I extends ImageGray, Desc extends TupleDesc> extends NewDetectDescribeAssociate<I, Desc> {
	
	private ArrayList<Rectangle2D_I32> boxes = new ArrayList<Rectangle2D_I32>();
	
	public BoxDetectDescribeAssociate(DdaManagerDetectDescribePoint<I, BrightFeature> manager2,
			   final AssociateDescription2D<BrightFeature> generalAssoc,
			   final boolean updateDescription) {
		
		super(manager2, generalAssoc, updateDescription);
		
	}

	public DdaFeatureManager<I,Desc> getManager() {
		return manager;
	}
	
	public FastQueue<Desc> getFeatSrc() {
		return featSrc;
	}
	
	public void setBoxes(ArrayList<Rectangle2D_I32> boxes) {
		this.boxes = boxes;
	}
	
	public ArrayList<Rectangle2D_I32> getBoxes() {
		return boxes;
	}
	
	protected boolean checkValidSpawn( PointTrack p ) {
		for(Rectangle2D_I32 r : boxes) {
			if(p.x > r.x0 && p.x < r.x1 && p.y > r.y0 && p.y < r.y1) return true;
		}
		return false;
	}
	
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

package test;

import org.ddogleg.struct.FastQueue;

import boofcv.abst.feature.associate.AssociateDescription2D;
import boofcv.abst.feature.tracker.DdaFeatureManager;
import boofcv.abst.feature.tracker.DdaManagerDetectDescribePoint;
import boofcv.abst.feature.tracker.DetectDescribeAssociate;
import boofcv.struct.feature.BrightFeature;
import boofcv.struct.feature.TupleDesc;
import boofcv.struct.image.ImageGray;

/**
 * <p>
 * Override of DetectDescribeAssociate used to get extra SURF feature information
 * </p>
 *
 * @author Chris Kymn
 */
@SuppressWarnings("rawtypes")
public class NewDetectDescribeAssociate<I extends ImageGray, Desc extends TupleDesc> extends DetectDescribeAssociate<I, Desc> {
	
	@SuppressWarnings("unchecked")
	public NewDetectDescribeAssociate(DdaManagerDetectDescribePoint<I, BrightFeature> manager2,
			   final AssociateDescription2D<BrightFeature> generalAssoc,
			   final boolean updateDescription) {
		
		this.manager = (DdaFeatureManager<I, Desc>) manager2;
		this.associate = (AssociateDescription2D<Desc>) generalAssoc;

		featSrc = new FastQueue<Desc>(10,(Class<Desc>) manager2.getDescriptionType(),false);
		featDst = new FastQueue<Desc>(10,(Class<Desc>) manager2.getDescriptionType(),false);
		
	}

	public DdaFeatureManager<I,Desc> getManager() {
		return manager;
	}
	
	public FastQueue<Desc> getFeatSrc() {
		return featSrc;
	}
}

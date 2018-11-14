package test;

import boofcv.abst.feature.associate.AssociateDescTo2D;
import boofcv.abst.feature.associate.AssociateDescription2D;
import boofcv.abst.feature.associate.ScoreAssociation;
import boofcv.abst.feature.associate.WrapAssociateSurfBasic;
import boofcv.abst.feature.describe.ConfigSurfDescribe;
import boofcv.abst.feature.detdesc.DetectDescribePoint;
import boofcv.abst.feature.detect.interest.ConfigFastHessian;
import boofcv.abst.feature.orientation.ConfigAverageIntegral;
import boofcv.abst.feature.tracker.DdaManagerDetectDescribePoint;
import boofcv.abst.feature.tracker.PointTracker;
import boofcv.alg.feature.associate.AssociateSurfBasic;
import boofcv.factory.feature.associate.FactoryAssociation;
import boofcv.factory.feature.tracker.FactoryPointTracker;
import boofcv.struct.feature.BrightFeature;
import boofcv.struct.feature.TupleDesc_F64;
import boofcv.struct.image.ImageGray;

/**
 * <p>
 * Override of FactoryPointTracker used to get extra SURF feature information
 * </p>
 *
 * @author Chris Kymn
 */
public class NewFactoryPointTracker extends FactoryPointTracker {

	@SuppressWarnings("rawtypes")
	public static <I extends ImageGray>
	PointTracker<I> dda_FH_SURF_Fast(
										  ConfigFastHessian configDetector ,
										  ConfigSurfDescribe.Speed configDescribe ,
										  ConfigAverageIntegral configOrientation ,
										  Class<I> imageType)
	{
		ScoreAssociation<TupleDesc_F64> score = FactoryAssociation.scoreEuclidean(TupleDesc_F64.class, true);
		AssociateSurfBasic assoc = new AssociateSurfBasic(FactoryAssociation.greedy(score, 5, true));

		AssociateDescription2D<BrightFeature> generalAssoc =
				new AssociateDescTo2D<BrightFeature>(new WrapAssociateSurfBasic(assoc));

		DetectDescribePoint<I,BrightFeature> fused =
				NewFactoryDetectDescribe.surfFast(configDetector, configDescribe, configOrientation,imageType);

		DdaManagerDetectDescribePoint<I,BrightFeature> manager = new NewDdaManagerDetectDescribePoint<I,BrightFeature>(fused);

		return new NewDetectDescribeAssociate<I,BrightFeature>(manager, generalAssoc,false);
	}
	
}

package test;

import boofcv.abst.feature.describe.ConfigSurfDescribe;
import boofcv.abst.feature.detdesc.DetectDescribePoint;
import boofcv.abst.feature.detect.interest.ConfigFastHessian;
import boofcv.abst.feature.orientation.ConfigAverageIntegral;
import boofcv.abst.feature.orientation.OrientationIntegral;
import boofcv.alg.feature.describe.DescribePointSurf;
import boofcv.alg.feature.detect.interest.FastHessianFeatureDetector;
import boofcv.alg.transform.ii.GIntegralImageOps;
import boofcv.factory.feature.describe.FactoryDescribePointAlgs;
import boofcv.factory.feature.detdesc.FactoryDetectDescribe;
import boofcv.factory.feature.detect.interest.FactoryInterestPointAlgs;
import boofcv.factory.feature.orientation.FactoryOrientationAlgs;
import boofcv.struct.feature.BrightFeature;
import boofcv.struct.image.ImageGray;

/**
 * <p>
 * Override of FactoryDetectDescribe used to get extra SURF feature information
 * </p>
 *
 * @author Chris Kymn
 */
public class NewFactoryDetectDescribe extends FactoryDetectDescribe {
	
	@SuppressWarnings("rawtypes")
	public static <T extends ImageGray, II extends ImageGray>
	DetectDescribePoint<T,BrightFeature> surfFast(ConfigFastHessian configDetector ,
												  ConfigSurfDescribe.Speed configDesc,
												  ConfigAverageIntegral configOrientation,
												  Class<T> imageType) {

		Class<II> integralType = GIntegralImageOps.getIntegralType(imageType);

		FastHessianFeatureDetector<II> detector = FactoryInterestPointAlgs.fastHessian(configDetector);
		DescribePointSurf<II> describe = FactoryDescribePointAlgs.surfSpeed(configDesc, integralType);
		OrientationIntegral<II> orientation = FactoryOrientationAlgs.average_ii(configOrientation, integralType);

		return new NewWrapDetectDescribeSurf<T,II>( detector, orientation, describe );
	}
	
	
}

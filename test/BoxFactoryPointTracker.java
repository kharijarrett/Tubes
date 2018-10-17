package test;

import boofcv.abst.feature.associate.AssociateDescTo2D;
import boofcv.abst.feature.associate.AssociateDescription2D;
import boofcv.abst.feature.associate.ScoreAssociation;
import boofcv.abst.feature.associate.WrapAssociateSurfBasic;
import boofcv.abst.feature.describe.ConfigSurfDescribe;
import boofcv.abst.feature.detdesc.DetectDescribePoint;
import boofcv.abst.feature.detect.interest.ConfigFastHessian;
import boofcv.abst.feature.detect.interest.ConfigGeneralDetector;
import boofcv.abst.feature.orientation.ConfigAverageIntegral;
import boofcv.abst.feature.tracker.DdaManagerDetectDescribePoint;
import boofcv.abst.feature.tracker.PointTracker;
import boofcv.abst.feature.tracker.PointTrackerKltPyramid;
import boofcv.abst.filter.derivative.ImageGradient;
import boofcv.alg.feature.associate.AssociateSurfBasic;
import boofcv.alg.feature.detect.interest.GeneralFeatureDetector;
import boofcv.alg.filter.derivative.GImageDerivativeOps;
import boofcv.alg.interpolate.InterpolateRectangle;
import boofcv.alg.tracker.klt.PkltConfig;
import boofcv.factory.feature.associate.FactoryAssociation;
import boofcv.factory.feature.tracker.FactoryPointTracker;
import boofcv.factory.filter.derivative.FactoryDerivative;
import boofcv.factory.interpolate.FactoryInterpolation;
import boofcv.factory.transform.pyramid.FactoryPyramid;
import boofcv.struct.feature.BrightFeature;
import boofcv.struct.feature.TupleDesc_F64;
import boofcv.struct.image.ImageGray;
import boofcv.struct.pyramid.PyramidDiscrete;

/**
 * <p>
 * Override of FactoryPointTracker used to get extra SURF feature information
 * </p>
 *
 * @author Chris Kymn
 */
public class BoxFactoryPointTracker extends FactoryPointTracker {

	@SuppressWarnings("rawtypes")
	public static <I extends ImageGray>
	PointTracker<I> dda_FH_SURF_Fast(
										  ConfigFastHessian configDetector ,
										  ConfigSurfDescribe.Speed configDescribe ,
										  ConfigAverageIntegral configOrientation ,
										  Class<I> imageType)
	{
		ScoreAssociation<TupleDesc_F64> score = FactoryAssociation.scoreEuclidean(TupleDesc_F64.class, true);
		AssociateSurfBasic assoc = new AssociateSurfBasic(FactoryAssociation.greedy(score, 100, true));

		AssociateDescription2D<BrightFeature> generalAssoc =
				new AssociateDescTo2D<BrightFeature>(new WrapAssociateSurfBasic(assoc));

		DetectDescribePoint<I,BrightFeature> fused =
				NewFactoryDetectDescribe.surfFast(configDetector, configDescribe, configOrientation,imageType);

		DdaManagerDetectDescribePoint<I,BrightFeature> manager = new NewDdaManagerDetectDescribePoint<I,BrightFeature>(fused);

		return new BoxDetectDescribeAssociate<I,BrightFeature>(manager, generalAssoc,false);
	}
	
	/**
	 * Pyramid KLT feature tracker.
	 *
	 * @see boofcv.alg.tracker.klt.PyramidKltTracker
	 *
	 * @param config Config for the tracker. Try PkltConfig.createDefault().
	 * @param configExtract Configuration for extracting features
	 * @return KLT based tracker.
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static <I extends ImageGray, D extends ImageGray>
	PointTracker<I> klt(PkltConfig config, ConfigGeneralDetector configExtract,
						Class<I> imageType, Class<D> derivType ) {

		if( derivType == null )
			derivType = GImageDerivativeOps.getDerivativeType(imageType);

		if( config == null ) {
			config = new PkltConfig();
		}

		if( configExtract == null ) {
			configExtract = new ConfigGeneralDetector();
		}

		GeneralFeatureDetector<I, D> detector = createShiTomasi(configExtract, derivType);

		InterpolateRectangle<I> interpInput = FactoryInterpolation.<I>bilinearRectangle(imageType);
		InterpolateRectangle<D> interpDeriv = FactoryInterpolation.<D>bilinearRectangle(derivType);

		ImageGradient<I,D> gradient = FactoryDerivative.sobel(imageType, derivType);

		PyramidDiscrete<I> pyramid = FactoryPyramid.discreteGaussian(config.pyramidScaling,-1,2,true,imageType);

		return new BoxPointTrackerKltPyramid<I, D>(config.config,config.templateRadius,pyramid,detector,
				gradient,interpInput,interpDeriv,derivType);
	}
	
}

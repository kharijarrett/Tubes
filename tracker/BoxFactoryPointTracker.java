package tracker;

import boofcv.abst.feature.detect.interest.ConfigGeneralDetector;
import boofcv.abst.feature.tracker.PointTracker;
import boofcv.abst.filter.derivative.ImageGradient;
import boofcv.alg.feature.detect.interest.GeneralFeatureDetector;
import boofcv.alg.filter.derivative.GImageDerivativeOps;
import boofcv.alg.interpolate.InterpolateRectangle;
import boofcv.alg.tracker.klt.PkltConfig;
import boofcv.factory.feature.tracker.FactoryPointTracker;
import boofcv.factory.filter.derivative.FactoryDerivative;
import boofcv.factory.interpolate.FactoryInterpolation;
import boofcv.factory.transform.pyramid.FactoryPyramid;
import boofcv.struct.image.ImageGray;
import boofcv.struct.pyramid.PyramidDiscrete;

/**
 * <p>
 * Override of FactoryPointTracker used to get extra SURF feature information
 * Sets up KLT tracker
 * </p>
 *
 * @author Chris Kymn
 */
public class BoxFactoryPointTracker extends FactoryPointTracker {

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

package test;

import boofcv.abst.feature.detdesc.DetectDescribePoint;
import boofcv.abst.feature.tracker.DdaManagerDetectDescribePoint;
import boofcv.struct.feature.TupleDesc;
import boofcv.struct.image.ImageGray;

/**
 * <p>
 * Override of DdaManagerDetectDescribePoint used to get extra SURF feature information
 * </p>
 *
 * @author Chris Kymn
 */
@SuppressWarnings("rawtypes")
public class NewDdaManagerDetectDescribePoint<I extends ImageGray, Desc extends TupleDesc> extends DdaManagerDetectDescribePoint<I, Desc> {
	
	public NewDdaManagerDetectDescribePoint(final DetectDescribePoint<I, Desc> detDesc) {
		super(detDesc);
	}

	public DetectDescribePoint<I, Desc> getdetDesc() {
		return detDesc;
	}
}

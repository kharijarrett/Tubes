package tracker;

import java.util.Comparator;

public class FrameComparator implements Comparator<double[]> {

	@Override
	public int compare(double[] arg0, double[] arg1) {
		// TODO Auto-generated method stub

		return  ((int) arg0[5] - (int) arg1[5]);
	}



}

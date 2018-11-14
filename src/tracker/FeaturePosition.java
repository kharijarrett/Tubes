package tracker;

import java.io.Serializable;
/**
 * <p>
 * Simple data structure that holds feature information
 * </p>
 *
 * @author Chris Kymn
 */
public class FeaturePosition implements Serializable {

	private static final long serialVersionUID = 1L;
	long id;
	double x;
	double y;
		
	public FeaturePosition(long id, double x, double y) {
		this.id = id;
		this.x = x;
		this.y = y;
	}
		
}

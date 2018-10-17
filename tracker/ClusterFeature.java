package tracker;

import java.io.Serializable;

/**
 * <p>
 * Simple structure to hold Feature information along with cluster number
 * </p>
 *
 * @author Chris Kymn
 */
public class ClusterFeature extends FeaturePosition implements Serializable {

	private static final long serialVersionUID = 1L;
	short clusterno;
	
	public ClusterFeature(long id, short clusterno, double x, double y) {
		super(id, x, y);
		this.clusterno = clusterno;
	}

}

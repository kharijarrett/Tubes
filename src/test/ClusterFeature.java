package test;

import java.io.Serializable;

public class ClusterFeature extends FeaturePosition implements Serializable {
	
	short clusterno;
	
	public ClusterFeature(long id, short clusterno, double x, double y) {
		super(id, x, y);
		this.clusterno = clusterno;
	}

}

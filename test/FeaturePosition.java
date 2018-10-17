package test;

import java.io.Serializable;

public class FeaturePosition implements Serializable {
	
	long id;
	double x;
	double y;
		
	public FeaturePosition(long id, double x, double y) {
		this.id = id;
		this.x = x;
		this.y = y;
	}
		
}

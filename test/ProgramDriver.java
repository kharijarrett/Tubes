package test;

import java.io.FileNotFoundException;

import boofcv.struct.image.GrayF32;

public class ProgramDriver {
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static void main( String args[] ) throws FileNotFoundException {
		//full set of 150 videos
		FLOPModular app = new FLOPModular(new BoxSURFDetector(GrayF32.class), GrayF32.class, "hnp76.mp4", 100);
		
		app.batch("/home/grangerlab/Desktop/hnp");
		app.saveClusterApps("hnp76.csv");

	}
}

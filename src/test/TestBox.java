package test;

import java.io.File;
import java.io.FileNotFoundException;

import boofcv.struct.image.GrayF32;
import common.FileIO;

public class TestBox {
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static void main( String args[] ) throws FileNotFoundException {
		//full set of 150 videos
//		FeatureLabelerOnePass app = new FeatureLabelerOnePass(GrayF32.class, "hnp.mp4", 50);
//
//		app.createSURF();
//		
//		app.batch("/home/grangerlab/Desktop/hnp");
//		app.saveClusterApps("hnp.csv");
		//"toy" set of 24 videos
		
//		ClipArchive vidInfo = (ClipArchive) FileIO.LoadObject(new File("/home/grangerlab/Desktop/sim/news17_arc.zip"));
//		System.out.println(vidInfo);
//		FeatureLabelerOnePass toy = new FeatureLabelerOnePass(GrayF32.class, "toy.mp4", 50);
//
//		toy.createSURF();
//		toy.changeFPS(4);
//		toy.batch("/home/grangerlab/Desktop/toykit");
//		toy.saveClusterApps("toykit.csv");

//		FLOPModular pob = new FLOPModular(new SURFDetector(GrayF32.class), GrayF32.class, "pob.mp4", 50);
//
//		pob.changeFPS(4);
//		pob.batch("/home/grangerlab/Desktop/pob");
//		pob.saveClusterApps("pob.csv");
//		
//		FLOPModular pacman = new FLOPModular(new SURFDetector(GrayF32.class), GrayF32.class, "pacman2.mp4", 50);
//		pacman.changeFPS(4);
//		pacman.batch("/home/grangerlab/Desktop/pacman");

//		RetinaChannelM m = new RetinaChannelM(new BoxSURFDetector(GrayF32.class), GrayF32.class, "pacmanMS.mp4", 10);
//		m.changeFPS(4);
//		m.batch("/home/grangerlab/Desktop/pacman");
		
//		ClipArchive vidInfo = (ClipArchive) FileIO.LoadObject(new File("/home/grangerlab/Desktop/pob/news51_arc.zip"));
//		System.out.println(vidInfo);

		RetinaChannelM m = new RetinaChannelM(new BoxSURFDetector(GrayF32.class), GrayF32.class, "bunchbenches.mp4", 10);
		m.changeFPS(240);
		m.batch("/home/grangerlab/Desktop/go");
		
	}
}

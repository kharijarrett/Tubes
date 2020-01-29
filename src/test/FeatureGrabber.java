package test;

import boofcv.abst.feature.detect.interest.ConfigFastHessian;
import boofcv.abst.feature.tracker.PointTrack;
import boofcv.abst.feature.tracker.PointTracker;
import boofcv.alg.filter.derivative.GImageDerivativeOps;
import boofcv.gui.feature.VisualizeFeatures;
import boofcv.gui.image.ImagePanel;
import boofcv.gui.image.ShowImages;
import boofcv.io.MediaManager;
import boofcv.io.UtilIO;
import boofcv.io.image.SimpleImageSequence;
import boofcv.io.wrapper.DefaultMediaManager;
import boofcv.struct.feature.BrightFeature;
import boofcv.struct.image.GrayF32;
import boofcv.struct.image.ImageGray;
import boofcv.struct.image.ImageType;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import common.FileIO;
import common.bagofwords.*;
import common.math.Distance;

/**
 * <p>
 * This code is capable of processing a series of videos and generating SURF features for each frame.
 * It is based on code from ExamplePointFeatureTracker in the BoofCV library.
 * It extends the functionality to store the 64-dimensional double for each SURF Feature and recording their times.
 * It also uses code from VocabKMeans to generate a "vocabulary of features." It stores these features to a CSV
 * </p>
 *
 * @author Chris Kymn
 */
@SuppressWarnings("rawtypes")
public class FeatureGrabber< T extends ImageGray, D extends ImageGray>
{
	// type of input image
	Class<T> imageType;
	Class<D> derivType;

	// tracks point features inside the image
	PointTracker<T> tracker;

	// displays the video sequence and tracked features
	ImagePanel gui = new ImagePanel();
	
	//keeps track of how many times each feature (denoted by Long ID) appears.
	//only features that appear a significant* amount of time are clustered in the vocabulary.
	
	//*significance metric may need to change
	Map<Long, Integer> featapps = new HashMap<Long, Integer>();
	
	//keeps track of the 64-dim descriptors of features (denoted by Long ID)
	Map<Long, Double[]> featSURF = new HashMap<Long, Double[]>();
	
	//for each feature, store the times that it happened to appear
	Map<Long, ArrayList<Integer>> featTimes = new HashMap<Long, ArrayList<Integer>>();
		
	//simple counter, helps with storing values in snapshots.
	int frames = 0;

	//not modified from example code
	public FeatureGrabber(Class<T> imageType) {
		this.imageType = imageType;
		this.derivType = GImageDerivativeOps.getDerivativeType(imageType);
	}
		
	/**
	 * Processes the sequence of images and displays the tracked features in a window
	 */
	@SuppressWarnings({ "unchecked" })
	public void process(String vidLoc) {
		
		SimpleImageSequence<T> sequence = processVideo(vidLoc);
		
		// Figure out how large the GUI window should be
		T frame = sequence.next();
		gui.setPreferredSize(new Dimension(frame.getWidth(),frame.getHeight()));
		ShowImages.showWindow(gui,"SURF Tracker", true);

		// process each frame in the image sequence
		while( sequence.hasNext() ) {
			frame = sequence.next();
			frames++;
			// tell the tracker to process the frame
			tracker.process(frame);

			// if there are too few tracks spawn more
			if( tracker.getActiveTracks(null).size() < 20 )
				tracker.spawnTracks();

			// visualize tracking results
			updateGUI(sequence);
			
			//update active feature information: number of appearances and times of those appearances
			for( PointTrack p : tracker.getActiveTracks(null)) {
				
				//number of appearances
				if(!featapps.containsKey(p.featureId)) {
					featapps.put(p.featureId, 1);
				}
				else {
					featapps.put(p.featureId, featapps.get(p.featureId)+1);
				}
				
				//timing of appearances
				if(!featTimes.containsKey(p.featureId)) {
					ArrayList<Integer> myApps = new ArrayList<Integer>();
					myApps.add(frames);
					featTimes.put(p.featureId, myApps);
				}
				else {
					ArrayList<Integer> myApps = featTimes.get(p.featureId);
					myApps.add(frames);
				}
				
			}
			
			//store 64 dim float information, if it had not been stored already
			//wrappers needed to access what were private variables in the original library classes
			for( PointTrack p : tracker.getAllTracks(null)) {
								
				if(!featSURF.containsKey(p.featureId)) {
					double storeDoubles[] = new double[64];
					NewDetectDescribeAssociate<T, BrightFeature> wrap = (NewDetectDescribeAssociate<T, BrightFeature>) tracker;
					BrightFeature b[] = wrap.getFeatSrc().data;
					if(p.featureId >= b.length || b[(int) p.featureId] == null) continue;
					storeDoubles = wrap.getFeatSrc().data[(int) p.featureId].value;
					featSURF.put((Long) p.featureId, Arrays.stream(storeDoubles).boxed().toArray(Double[]::new));
				}
				
			}
		}
	}

	/**
	 * Draw tracked features in blue, or red if they were just spawned.
	 */
	private void updateGUI(SimpleImageSequence<T> sequence) {
		BufferedImage orig = sequence.getGuiImage();
		Graphics2D g2 = orig.createGraphics();

		// draw tracks with semi-unique colors so you can track individual points with your eyes
		for( PointTrack p : tracker.getActiveTracks(null) ) {
			int red = (int)(2.5*(p.featureId%100));
			int green = (int)((255.0/150.0)*(p.featureId%150));
			int blue = (int)(p.featureId%255);
			if(featapps.containsKey(p.featureId) && featapps.get(p.featureId) > 50) VisualizeFeatures.drawPoint(g2, (int)p.x, (int)p.y, new Color(red,green,blue));
		}

		// draw tracks which have just been spawned green
		for( PointTrack p : tracker.getNewTracks(null) ) {
			if(featapps.containsKey(p.featureId) && featapps.get(p.featureId) > 50) VisualizeFeatures.drawPoint(g2, (int)p.x, (int)p.y, Color.green);
		}

		// tell the GUI to update
		gui.setBufferedImage(orig);
		gui.repaint();
	}

	/**
	 * Creates a SURF feature tracker.
	 */
	public void createSURF() {
		ConfigFastHessian configDetector = new ConfigFastHessian();
		configDetector.maxFeaturesPerScale = 500;
		configDetector.extractRadius = 5;
		configDetector.initialSampleSize = 1;
		tracker = NewFactoryPointTracker.dda_FH_SURF_Fast(configDetector, null, null, imageType);
	}
	
	/**
	 * Sets up video to process given a file path. Mainly for convenience.
	 */
	@SuppressWarnings({"unchecked" })
	public SimpleImageSequence processVideo(String vidPath) {

		Class imageType = GrayF32.class;
		MediaManager media = DefaultMediaManager.INSTANCE;
		SimpleImageSequence sequence = media.openVideo(UtilIO.pathExample(vidPath), ImageType.single(imageType)); 
		sequence.setLoop(false);
		
		return sequence;
	}
	
	/**
	 * Generates a feature cluster, using the information from videos processed up to this point.
	 */
	public VocabKMeans genFeatCluster(int minApps, int numClusters, String csvDest, String objDest) {

		ArrayList<Double[]> relevantFeatures = new ArrayList<Double[]>();
		
		for(Object l : featapps.keySet()) {
			long id = (long) l;
			if(featapps.containsKey(id) && (Integer) featapps.get(id) > minApps) {
				relevantFeatures.add((Double[]) featSURF.get(id));
				System.out.println("Added feature # " + id + " to list of important features.");
			}
		}
		
		System.out.println("Number of important features: " + relevantFeatures.size());
		
		Double[][] featArray = relevantFeatures.toArray(new Double[relevantFeatures.size()][64]);
		double arrayToCluster[][] = new double[relevantFeatures.size()][64];
		
		for(int i = 0; i<relevantFeatures.size(); i++)
		{
		    for(int j = 0; j < 64; j++)
		    {
		        arrayToCluster[i][j] = (double) featArray[i][j];
		        System.out.print(arrayToCluster[i][j] + " ");
		    }
		    System.out.println();
		}
		
		VocabKMeans clustering = new VocabKMeans(numClusters, 20, Distance.Kernel.EUCLIDIAN, 64);
		clustering.Generate(arrayToCluster);
				
		FileIO.SaveObject(new File(objDest), clustering);
		return clustering;
	}

	@SuppressWarnings({"unchecked", "unused" })
	public static void main( String args[] ) throws FileNotFoundException {

		FeatureGrabber app = new FeatureGrabber(GrayF32.class);

		app.createSURF();
		app.process("/home/grangerlab/Desktop/gopro/GOPR0125.MP4");
		app.process("/home/grangerlab/Desktop/gopro/GOPR0126.MP4");
		app.process("/home/grangerlab/Desktop/gopro/GOPR0127.MP4");
		app.process("/home/grangerlab/Desktop/gopro/GOPR0128.MP4");

		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss");
		VocabKMeans cluster = app.genFeatCluster(app.frames/20, 50, "bench_" + sdf.toString() + ".csv", "benchCluster");

	}

}
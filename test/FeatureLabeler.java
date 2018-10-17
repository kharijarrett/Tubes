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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import common.FileIO;
import common.bagofwords.*;
import common.math.Distance;

/**
 * <p>
 * Uses "vocabulary" generated in FeatureGrabber to label points
 * Also does some processing of features to fill in anomalous gaps and get rid of features that appear
 * for insignificant periods of time.
 * </p>
 *
 * @author Chris Kymn
 */
@SuppressWarnings("rawtypes")
public class FeatureLabeler< T extends ImageGray, D extends ImageGray>
{
	// type of input image
	Class<T> imageType;
	Class<D> derivType;

	// tracks point features inside the image
	PointTracker<T> tracker;

	// displays the video sequence and tracked features
	ImagePanel gui = new ImagePanel();
	
	//keeps track of how many times each feature appears
	Map<Long, Integer> featapps = new HashMap<Long, Integer>();
	Map<Long, Double[]> featSURF = new HashMap<Long, Double[]>();

	//keeps track of features in each position and the frames they appear in
	ArrayList<SimpleFrame> currentFrames = new ArrayList<SimpleFrame>();

	//Array of [frameNumber][featureID] that keeps track of appearances
	//also fills in feature if 3 of its 4 neighbors were there
	int featGrid[][];
	
	//was the feature rendered on frame x?
	int continuousFeatGrid[][];
	
	VocabKMeans cluster;
	
	int frames = 0;

	public FeatureLabeler(Class<T> imageType, VocabKMeans cluster) {
		this.imageType = imageType;
		this.derivType = GImageDerivativeOps.getDerivativeType(imageType);
		this.cluster = cluster;
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
	 * Runs through video, tracking features. Then calls updateRender to fill in any spots
	 */
	@SuppressWarnings("unchecked")
	public void relabel(String vidLoc) {

		SimpleImageSequence<T> sequence = processVideo(vidLoc);
		// Figure out how large the GUI window should be
		T frame = sequence.next();

		// process each frame in the image sequence
		while( sequence.hasNext() ) {
			frame = sequence.next();
			// tell the tracker to process the frame
			tracker.process(frame);

			// if there are too few tracks spawn more
			if( tracker.getActiveTracks(null).size() < 20 )
				tracker.spawnTracks();
			
			ArrayList<FeaturePosition> framefeatures = new ArrayList<FeaturePosition>();
			for(PointTrack p : tracker.getActiveTracks(null)) {
				framefeatures.add(new FeaturePosition(p.featureId, p.x, p.y));
			}			
			currentFrames.add(new SimpleFrame(frames, framefeatures));

			frames++;
			System.out.println("Just processed frame " + frames);

		}
		
		//turn the arraylists into a large array (frames, getAllTracks.size())
		//fill in the gaps, rerender
		System.out.println("Frame processing complete.");
		updateRender(vidLoc);
		
	}
	
	/**
	 * Processes supposed feature appearances.
	 * Says a feature was present even if not detected if it was present 3/4 frames in its vicinity
	 * i.e. 2 ahead / 2 back
	 * 
	 * Ignores a feature if it moves more than five pixels within a single frame.
	 */
	@SuppressWarnings("unchecked")
	public void updateRender(String vidLoc) {

		int numFeatures = tracker.getAllTracks(null).size();
		int numFrames = currentFrames.size();
		
		if(numFrames < 30) {
			System.out.println("Notice: Insufficient number of frames. Quitting render early.");
		}
		
		//set up feature grid. If feature was added at that frame, say it was there in array (value 1)
		featGrid = new int[numFrames][numFeatures];
		for(int i = 0; i < numFrames; i++) {
			for(FeaturePosition p : currentFrames.get(i).features) {
				featGrid[i][(int) p.id] = 1;
			}
		}
		
		//process jumping SURF features. If it moves too much in a frame, say it was not there (value -1)
		for(int i = 0; i < numFrames; i++) {
			for(int j = 0; j < numFeatures; j++) {
				if(featGrid[i][j] == 1) {
					if(i > 1 && featGrid[i-1][j] >= 1) {
						
						//initialized to values to suppress warning
						//in practice shouldn't need these values, courtesy of most recent if-check
						double prevX = (int) Integer.MIN_VALUE;
						double prevY = (int) Integer.MIN_VALUE;
						double currX = (int) Integer.MAX_VALUE;
						double currY = (int) Integer.MAX_VALUE;
						
						//look at current position
						for(FeaturePosition p : currentFrames.get(i).features) {
							if((int) p.id == j) {
								currX = p.x;
								currY = p.y;
							}
						}
						
						//look at previous position
						for(FeaturePosition p : currentFrames.get(i-1).features) {
							if((int) p.id == j) {
								prevX = p.x;
								prevY = p.y;
							}
						}
						
						//compare euclidean distance
						if((currX-prevX)*(currX-prevX) + (currY-prevY)*(currY-prevY) > 25) {
							for(FeaturePosition p : currentFrames.get(i).features) {
								if((int) p.id == j) {
									p.x = prevX;
									p.y = prevY;
								}
							}
							featGrid[i][j] = -1; //i.e. not detected 
						}
						
					}
				}
			}
		}
		
		//fill in missing features in the case of a short gap
		for(int i = 0; i < numFrames; i++) {
			for(int j = 0; j < numFeatures; j++) {
				if(featGrid[i][j] == 0) {
					int presentNeighbors = 0;
					if(i >= 1 && (featGrid[i-1][j] == 1 || featGrid[i-1][j] == 2)) presentNeighbors++;
					if(i >= 2 && (featGrid[i-2][j] == 1 || featGrid[i-2][j] == 2)) presentNeighbors++;
					if(i <= numFrames-2 && (featGrid[i+1][j] == 1 || featGrid[i+1][j] == 2)) presentNeighbors++;
					if(i <= numFrames-3 && (featGrid[i+2][j] == 1 || featGrid[i+2][j] == 2)) presentNeighbors++;
					if(presentNeighbors >= 3) featGrid[i][j] = 3;
				}
			}
		}
		
		//initialize based on number of frames and features. processed in update display.
		continuousFeatGrid = new int[numFrames][numFeatures];
		
		// set up window to show rendered result on screen.
		
		//run through video again to save memory
		SimpleImageSequence<T> sequence = processVideo(vidLoc);
		// Figure out how large the GUI window should be
		T frame = sequence.next();
		
		gui.setPreferredSize(new Dimension(frame.getWidth(),frame.getHeight()));
		
		ShowImages.showWindow(gui,"SURF Tracker", true);
		
		for(int i = 0; i < numFrames; i++) {
			if(!sequence.hasNext());
			frame = sequence.next();
			updateDisplay(i, sequence);
		}
		
	}
	
	/**
	 * Updates displayed image by a frame to visually check results.
	 */
	@SuppressWarnings("unchecked")
	public void updateDisplay(int i, SimpleImageSequence<T> sequence) {
		
		int numFrames = currentFrames.size();

		BufferedImage orig = sequence.getGuiImage();
		Graphics2D g2 = orig.createGraphics();		
		
		//check if the feature appeared in a window of 30 frames 
		//(number of consecutive past appearances + number of consecutive future appearances) > 30
		for(PointTrack p : tracker.getAllTracks(null)) {
			if(featGrid[i][(int) p.featureId] > 0) {
				
				int backframe = 0;
				int fwdframe = 0;
				
				while(i-backframe > 0 && featGrid[i-backframe][(int) p.featureId] > 0) backframe++;
				while(i+fwdframe < numFrames && featGrid[i+fwdframe][(int) p.featureId] > 0) fwdframe++;
				
				//don't draw if it failed criteria
				if(backframe + fwdframe < 20) continue;
				
				double storeDoubles[] = new double[64];
				NewDetectDescribeAssociate<T, BrightFeature> wrap = (NewDetectDescribeAssociate<T, BrightFeature>) tracker;
				
				storeDoubles = wrap.getFeatSrc().data[(int) p.featureId].value;
				
				//draw feature in unique color
				short clusterno = cluster.Lookup(storeDoubles);
				int red = (int)(255/50)*(clusterno);
				int green = (int)((255/50)*(50-clusterno));
				int blue = (int)((128+(255/50)*clusterno)%255);					

				Color featColor = new Color(red, green, blue);
				
				//get feature position
				int x = (int) p.x;
				int y = (int) p.y;
				
				for(FeaturePosition n : currentFrames.get(i).features) {
					if(n.id == p.featureId && (n.x != p.x || n.y != p.y)) {
						x = (int) n.x;
						y = (int) n.y;
					}
				}
				
				//in case it didn't appear in this frame, render position from 1 frame ago.
				if(i >= 1 && x == (int) p.x) {
					for(FeaturePosition n : currentFrames.get(i-1).features) {
						if(n.id == p.featureId) {
							x = (int) n.x;
							y = (int) n.y;
						}
					}					
				}
				
				//in case it wasn't in that frame either, render position from 2 frames ago.
				if(i >= 2 && x == (int) p.x) {
					for(FeaturePosition n : currentFrames.get(i-2).features) {
						if(n.id == p.featureId) {
							x = (int) n.x;
							y = (int) n.y;
						}
					}					
				}
				VisualizeFeatures.drawPoint(g2, x, y, featColor);
				
				//put feature cluster number
				g2.setColor(Color.WHITE);
				g2.drawString(((Short) clusterno).toString(), x-5, y+5);
				
				//save information to continuousFeatGrid
				//this information isn't used in the program, just stored so as not to have to run the whole program 
				//to see results again
				continuousFeatGrid[i][(int) p.featureId] = 1;
				
			}
		}
		
		//draw frame number for convenient reference
		g2.setColor(Color.BLACK);
		g2.setFont(new Font("TimesRoman", Font.BOLD, 20));
		g2.drawString("FRAME: " + i, orig.getWidth()- 200, orig.getHeight()-50);

		gui.setBufferedImage(orig);	
		gui.repaint();
				
	}
	
	/**
	 * Saves basic rendering information to a CSV file.
	 */
	public void saveApps(String csvDest) {
		FileIO.SaveCSV(continuousFeatGrid, new File(csvDest));
		return;
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
	
	@SuppressWarnings({"unchecked" })
	public static void main( String args[] ) throws FileNotFoundException {

		VocabKMeans cluster = (VocabKMeans) FileIO.LoadObject(new File("benchCluster"));
		
		FeatureLabeler app = new FeatureLabeler(GrayF32.class, cluster);
		app.createSURF();
		app.relabel("/home/grangerlab/Desktop/gopro/GOPR0125.MP4");
//		app.relabel("/home/grangerlab/Desktop/gopro/GOPR0126.MP4", cluster);
//		app.relabel("/home/grangerlab/Desktop/gopro/GOPR0127.MP4", cluster);
//		app.relabel("/home/grangerlab/Desktop/gopro/GOPR0128.MP4", cluster);
		
		app.saveApps("benchV1.csv");


	}

}
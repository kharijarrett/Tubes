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
//import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import common.FileIO;
import common.bagofwords.*;
import common.math.Distance;
import common.video.*;

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
public class FeatureLabelerOnePass< T extends ImageGray, D extends ImageGray>
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
	
	//for each feature, store the times that it happened to appear. Currently not used.
//	Map<Long, ArrayList<Integer>> featTimes = new HashMap<Long, ArrayList<Integer>>();

	//keeps track of features in each position and the frames they appear in
	ArrayList<SimpleFrame> currentFrames = new ArrayList<SimpleFrame>();

	//Array of [frameNumber][featureID] that keeps track of appearances
	//also fills in feature if 3 of its 4 neighbors were there
	int featGrid[][];
	
	//was the feature rendered on frame x?
	int continuousFeatGrid[][];
	
	int vocabsize;
	Map<String, int[]> vocabapps = new HashMap<String, int[]>();
	VocabKMeans cluster;
	
	int frames = 0;
	
	//for video processing
	
	VideoFrameWriter outVid;
	String outLoc;
	int height = 0;
	int width = 0;
	int fps = 30;	
	
	public FeatureLabelerOnePass(Class<T> imageType, String outLoc, int vocabsize) {
		this.imageType = imageType;
		this.derivType = GImageDerivativeOps.getDerivativeType(imageType);
		this.outLoc = outLoc;
		this.vocabsize = vocabsize;
	}
	
	/**
	 * Creates a SURF feature tracker.
	 */
	public void createSURF() {
		ConfigFastHessian configDetector = new ConfigFastHessian();
		configDetector.maxFeaturesPerScale = 500;
		configDetector.extractRadius = 1;
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
		
		if(height == 0 || width == 0) {
			this.height = sequence.getNextHeight();
			this.width = sequence.getNextWidth();
			outVid = new VideoFrameWriter(new File(outLoc), this.width, this.height, this.fps);			
		}
		
		return sequence;
	}
	
	public void unarchive(String arcLoc) {
		ClipArchive vidInfo = (ClipArchive) FileIO.LoadObject(new File(arcLoc));

		for(Long p : vidInfo.featapps.keySet()) {
			if(!featapps.containsKey(p)) {
				featapps.put(p, vidInfo.featapps.get(p));
			}
			else {
				featapps.put(p, featapps.get(p)+vidInfo.featapps.get(p));
			}			
		}
		
		for(Long p : vidInfo.featSURF.keySet()) {
			if(!featSURF.containsKey(p)) {
				featSURF.put(p, vidInfo.featSURF.get(p));
			}
		}
		
		for(int i = 0; i < vidInfo.localFrames.size(); i++) {
			frames++;
			currentFrames.add(new SimpleFrame(frames, vidInfo.localFrames.get(i).features));
		}
		
		if(height == 0 || width == 0) {
			this.height = vidInfo.height;
			this.width = vidInfo.width;
			outVid = new VideoFrameWriter(new File(outLoc), this.width, this.height, this.fps);			
		}
		
		return;

	}
	
	
	/**
	 * Processes the sequence of images and displays the tracked features in a window
	 */
	@SuppressWarnings({ "unchecked" })
	public void process(String vidLoc) {
		
		String arcLoc = CatGetter.constructArc(vidLoc);
		System.out.println("Looking for archive @ " + arcLoc + " ...");
		File f = new File(arcLoc);
		if(f.exists() && !f.isDirectory()) {
			System.out.println("Archive at " + arcLoc + " found, processing that instead...");
			unarchive(arcLoc);
			return;
		}
		
		int localframe = 0;
		ClipArchive local = new ClipArchive();
		
		SimpleImageSequence<T> sequence = processVideo(vidLoc);
		
		local.height = sequence.getNextHeight();
		local.width = sequence.getNextWidth();
		
		// Figure out how large the GUI window should be
		T frame = sequence.next();
//		BufferedImage newthing = new BufferedImage(width/2, height/2, frame.imageType);
//		Graphics g = newthing.getGraphics();
//		g.drawImage()
		
		// process each frame in the image sequence
		while( sequence.hasNext() ) {
			frame = sequence.next();
			frames++;
			localframe++;
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
			local.localFrames.add(new SimpleFrame(localframe, framefeatures));
			
			//update active feature information: number of appearances and times of those appearances
			for( PointTrack p : tracker.getActiveTracks(null)) {
				
				//number of appearances
				if(!featapps.containsKey(p.featureId)) {
					featapps.put(p.featureId, 1);
				}
				else {
					featapps.put(p.featureId, featapps.get(p.featureId)+1);
				}

				if(!local.featapps.containsKey(p.featureId)) {
					local.featapps.put(p.featureId, 1);
				}
				else {
					local.featapps.put(p.featureId, featapps.get(p.featureId)+1);
				}
				
				//timing of appearances
//				if(!featTimes.containsKey(p.featureId)) {
//					ArrayList<Integer> myApps = new ArrayList<Integer>();
//					myApps.add(frames);
//					featTimes.put(p.featureId, myApps);
//				}
//				else {
//					ArrayList<Integer> myApps = featTimes.get(p.featureId);
//					myApps.add(frames);
//				}
				
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
					local.featSURF.put((Long) p.featureId, Arrays.stream(storeDoubles).boxed().toArray(Double[]::new));
				}
				
			}
			System.out.println("Processed frame " + frames);
			
		}
		
		FileIO.SaveObject(new File(CatGetter.stem(vidLoc)), local);
		return;
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

		int numFeatures = featapps.keySet().size();
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
//						if((currX-prevX)*(currX-prevX) + (currY-prevY)*(currY-prevY) > 25) {
//							for(FeaturePosition p : currentFrames.get(i).features) {
//								if((int) p.id == j) {
//									p.x = prevX;
//									p.y = prevY;
//								}
//							}
//							featGrid[i][j] = -1; //i.e. not detected 
//						}
						
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
		
		String itemid = CatGetter.extract(vidLoc);
		
		ArrayList<ClusterFrame> renderInfo = new ArrayList<ClusterFrame>();
		
		for(int i = 0; i < numFrames; i++) {
			if(!sequence.hasNext()) break;
			frame = sequence.next();
			renderInfo.add(updateDisplay(i, frames, sequence, itemid, 15));
			frames++;
		}
		
		FileIO.SaveObject(new File(CatGetter.render(vidLoc)), renderInfo);
		return;
	}
	
	/**
	 * Updates displayed image by a frame to visually check results.
	 */
	public ClusterFrame updateDisplay(int localframe, int i, SimpleImageSequence<T> sequence, String itemid, int minConsecutive) {
		
		ArrayList<ClusterFeature> features = new ArrayList<ClusterFeature>();
		
		int numFrames = currentFrames.size();

		BufferedImage orig = sequence.getGuiImage();
		Graphics2D g2 = orig.createGraphics();		
		
		//check if the feature appeared in a window of 30 frames 
		//(number of consecutive past appearances + number of consecutive future appearances) > 30
		for(long p : featapps.keySet()) {
			if(featGrid[i][(int) p] > 0) {
				
				int backframe = 0;
				int fwdframe = 0;
				
				while(i-backframe > 0 && featGrid[i-backframe][(int) p] > 0) backframe++;
				while(i+fwdframe < numFrames && featGrid[i+fwdframe][(int) p] > 0) fwdframe++;
				
				//don't draw if it failed criteria
//				if(backframe + fwdframe < minConsecutive) continue;
				
				double storeDoubles[] = new double[64];
				Double[] surfd = featSURF.get((Long) p);
				for(int j = 0; j < 64; j++) {
					storeDoubles[j] = (double) surfd[j];
 				}
				
				//draw feature in unique color
				short clusterno = cluster.Lookup(storeDoubles);
				int red = (int)((255/50)*(clusterno))%255;
				int green = (int)(((255/50)*(100-clusterno)))%255;
				int blue = (int)((128+(255/50)*clusterno)%255);					
				
				Color featColor;
				if(backframe + fwdframe < minConsecutive) {
					continue;
//					featColor = Color.BLACK;
				}
				else {
					featColor = new Color(red, green, blue);
				}
//				Color featColor = new Color(red, green, blue);
				
				//store cluster ID information
				if(!vocabapps.containsKey(itemid)) {
					int clustapp[] = new int[vocabsize];
					clustapp[clusterno] = 1;
					vocabapps.put(itemid, clustapp);
				}
				else {
					int clustapp[] = vocabapps.get(itemid);
					clustapp[clusterno] += 1;
				}				
				
				//get feature position
				int x = 0;
				int y = 0;
				
				for(FeaturePosition n : currentFrames.get(i).features) {
					if(n.id == p) {
						x = (int) n.x;
						y = (int) n.y;
					}
				}
				
				//in case it didn't appear in this frame, render position from 1 frame ago.
				if(i >= 1 && x == 0) {
					for(FeaturePosition n : currentFrames.get(i-1).features) {
						if(n.id == p) {
							x = (int) n.x;
							y = (int) n.y;
						}
					}					
				}
				
				//in case it wasn't in that frame either, render position from 2 frames ago.
				if(i >= 2 && x == 0) {
					for(FeaturePosition n : currentFrames.get(i-2).features) {
						if(n.id == p) {
							x = (int) n.x;
							y = (int) n.y;
						}
					}					
				}
				VisualizeFeatures.drawPoint(g2, x, y, featColor);
				
				features.add(new ClusterFeature(p, clusterno, x, y));
				
				//put feature cluster number
				g2.setColor(Color.WHITE);
				g2.drawString(((Short) clusterno).toString(), x-5, y+5);
//				g2.drawString(((Long) p).toString(), x-5, y+5);
//				g2.drawString(((Integer)(backframe+fwdframe)).toString(),x-5, y+5);
				//save information to continuousFeatGrid
				//this information isn't used in the program, just stored so as not to have to run the whole program 
				//to see results again
				continuousFeatGrid[i][(int) p] = 1;
				
			}
		}
		
		//draw frame number for convenient reference
		g2.setColor(Color.BLACK);
		g2.setFont(new Font("TimesRoman", Font.BOLD, 20));
		g2.drawString("FRAME: " + i, orig.getWidth()- 200, orig.getHeight()-50);

		gui.setBufferedImage(orig);	
		gui.repaint();
		outVid.ProcessFrame(orig);
		
		return new ClusterFrame(localframe, features);
	}
	
	public void openVid() {
		outVid.OpenFile();
		return;
	}
	
	public void closeVid() {
		outVid.Close();
		return;
	}
	
	public void changeFPS(int rate) {
		this.fps = rate;
	}
	
	public void saveClusterApps(String csvDest) {
		
		String[][] clustergrid = new String[vocabsize+1][vocabapps.size()];
		
		int i = 0;
		for(String s : vocabapps.keySet()) {
			clustergrid[0][i] = s;
			i++;
		}
		
		for(int j = 1; j < vocabsize+1; j++) {
			for(int k = 0; k < vocabapps.size(); k++) {
				int[] piece = vocabapps.get(clustergrid[0][k]);
				clustergrid[j][k] = Integer.toString(piece[j-1]);
			}
		}
		FileIO.SaveCSV(clustergrid, new File(csvDest));
		return;
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
	public void genFeatCluster(int minApps, String objDest) {

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
		
		VocabKMeans clustering = new VocabKMeans(vocabsize, 20, Distance.Kernel.EUCLIDIAN, 64);
		clustering.Generate(arrayToCluster);
				
		FileIO.SaveObject(new File(objDest), clustering);
		this.cluster = clustering;
		return;
	}
	
	/**
	 * Simple setter method for frame number
	 * Primarily used to reset frames to 0, after processing all videos
	 */
	public void setFrames(int framenum) {
		this.frames = framenum;
	}
	
	/**
	 * Processes every video in a folder
	 */	
	public void batch(String folder) {
		
		File dir = new File(folder);
		File[] listOfFiles = dir.listFiles();
		
		ArrayList<String> fq = new ArrayList<String>();
		int i = 1;
		for (File file : listOfFiles) {
		    if (file.isFile() && !file.getName().endsWith(".zip")) {
		    	System.out.println("PROCESSING VIDEO " + i + " of " + listOfFiles.length + ", " + file.getAbsolutePath());
		    	process(file.getAbsolutePath());
		    	fq.add(file.getAbsolutePath());
		    	i++;
		    }
		}
		
		genFeatCluster(frames/20, "cluster_new");
		
		setFrames(0);
		openVid();
		
		System.out.println("Stream opened...");
		
		for(int j = 0; j < fq.size(); j++) {
			updateRender(fq.get(j));
		}
		
		closeVid();
		
		return;
	}
	
	@SuppressWarnings({ })
	public static void main( String args[] ) throws FileNotFoundException {

//		FeatureLabelerOnePass app = new FeatureLabelerOnePass(GrayF32.class, "hydrant.mp4");
//		app.createSURF();
//		
//		app.batch("/home/grangerlab/Desktop/hydrants");
		
////		VocabKMeans cluster = (VocabKMeans) FileIO.LoadObject(new File("benchCluster"));
//		
//		FeatureLabelerOnePass app = new FeatureLabelerOnePass(GrayF32.class, "benchgrab.mp4");
//		app.createSURF();
//		
////		app.process("/home/grangerlab/Desktop/GOPR0125.m4v");
//		
//		app.process("/home/grangerlab/Desktop/gopro/GOPR0125.MP4");
//		app.process("/home/grangerlab/Desktop/gopro/GOPR0126.MP4");
//		app.process("/home/grangerlab/Desktop/gopro/GOPR0127.MP4");
//		app.process("/home/grangerlab/Desktop/gopro/GOPR0128.MP4");
//
////		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss");
////		app.genFeatCluster(app.frames/20, 50, "bench_" + sdf.toString() + ".csv", "benchCluster");
//		app.genFeatCluster(app.frames/20, 50, "benchCluster");
//
//		app.setFrames(0);
//		app.openVid();
////		app.updateRender("/home/grangerlab/Desktop/GOPR0125.m4v");
//		
//		app.updateRender("/home/grangerlab/Desktop/gopro/GOPR0125.MP4");
//		app.updateRender("/home/grangerlab/Desktop/gopro/GOPR0126.MP4");
//		app.updateRender("/home/grangerlab/Desktop/gopro/GOPR0127.MP4");
//		app.updateRender("/home/grangerlab/Desktop/gopro/GOPR0128.MP4");
//		
//		app.closeVid();
//		
//		app.saveApps("benchV1.csv");


	}

}
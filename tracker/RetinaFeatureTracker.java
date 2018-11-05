package tracker;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.stream.*;

import org.apache.commons.lang3.ArrayUtils;
import org.math.plot.utils.Array;




import boofcv.alg.filter.binary.BinaryImageOps;
import boofcv.alg.filter.binary.Contour;
import boofcv.alg.filter.binary.ThresholdImageOps;
import boofcv.alg.misc.ImageStatistics;
import boofcv.alg.shapes.FitData;
import boofcv.alg.shapes.ShapeFittingOps;
import boofcv.core.image.ConvertImage;
import boofcv.gui.feature.VisualizeFeatures;
import boofcv.gui.feature.VisualizeShapes;
import boofcv.gui.image.ShowImages;
import boofcv.io.image.ConvertBufferedImage;
import boofcv.io.image.SimpleImageSequence;
import boofcv.struct.ConnectRule;
import boofcv.struct.PointIndex_I32;
import boofcv.struct.image.GrayF32;
import boofcv.struct.image.GrayS32;
import boofcv.struct.image.GrayU8;
import boofcv.struct.image.ImageGray;
import common.FileIO;
import common.math.Mat;
import common.math.Vec;
import common.utils.ImageUtils;
import common.video.VideoFrameReader;
import common.video.VideoFrameWriter;
import common.video.VideoRetina;
import georegression.struct.point.Point2D_F64;
import georegression.struct.point.Point2D_I32;
import georegression.struct.shapes.EllipseRotated_F64;
import georegression.struct.shapes.Rectangle2D_I32;
import ij.ImageStack;
import ij.process.*;

import inra.ijpb.morphology.*;
import inra.ijpb.morphology.strel.CubeStrel;
import inra.ijpb.morphology.strel.SquareStrel;

import java.awt.Shape;

@SuppressWarnings("rawtypes")
public class RetinaFeatureTracker< T extends ImageGray, D extends ImageGray> extends FeatureTracker {
	
	BoxStreak bs = new BoxStreak();
	
	@SuppressWarnings("unchecked")
	public RetinaFeatureTracker(FeatureDetector tracker, Class imageType, String outLoc, int vocabsize) {
		super(tracker, imageType, outLoc, vocabsize);
	}

	@SuppressWarnings({ "unchecked", "unused" })
	public void streamMagnoParvo(String stemLoc, String vidLoc, boolean color) {
		
//		SimpleImageSequence<T> sequence = processVideo(vidLoc);
		VideoFrameReader sequence = new VideoFrameReader(vidLoc); 

		
		sequence.OpenFile();
		int height = sequence.getFrameHeight();
		int width = sequence.getFrameWidth(); 
		
		VideoFrameWriter magno = new VideoFrameWriter(new File(stemLoc + "_magno.mp4"), width, height, 30);
		VideoFrameWriter parvo = new VideoFrameWriter(new File(stemLoc + "_parvo.mp4"), width, height, 30);
		
		magno.OpenFile();
		parvo.OpenFile();
		
		VideoRetina retina = new VideoRetina(width, height, color);
		
//		T frame;
		int frames = 0;
		BufferedImage currFrame = sequence.NextFrame();
		
		while((currFrame = sequence.NextFrame()) != null) {
			//frame = sequence.next();
//			BufferedImage currFrame = sequence.getGuiImage();
			

			retina.ProcessFrame(currFrame);
			magno.ProcessFrame(retina.getMagno());
			parvo.ProcessFrame(retina.getParvo());
			System.out.println("Finished processing frame " + frames);
			frames++;
		}
		sequence.Close();
		
		magno.Close();
		parvo.Close();
		
	}
	
	@SuppressWarnings({ "unchecked", "unused", "deprecation" })
	public void magnoFilter(String stemLoc, String vidLoc) {

		ArrayList<double[]> list;
		
		//file has the list already stored in it if the program was run before
		File file = new File("C:/Users/f002tj9/Documents/Research/kj/EllipseLists/" + CatGetter.stemOnly(vidLoc) + ".zip" );
		if (!(file.exists() && !file.isDirectory())) { //if the file doesn't exist...
		
		VideoFrameReader sequence = new VideoFrameReader(vidLoc);
		sequence.OpenFile();
		
		int height = sequence.getFrameHeight();
		int width = sequence.getFrameWidth(); 
		
		
		
		//VideoFrameWriter mask = new VideoFrameWriter(new File(stemLoc), width, height, 30); 
		VariableRetina retina = new VariableRetina(width, height, false); //create retina

		//mask.OpenFile(); //open videowriter
		
		//This part shows the video on screen
		//gui.setPreferredSize(new Dimension(width, height));
		//ShowImages.showWindow(gui,CatGetter.extract(vidLoc), true);
		
		
		//T frame;
		int frameNum = 0;
		

		//Initialize an ArrayList to keep track of centers/velocities/colors
		list = new ArrayList<double[]>();
		
		//Initialize an ArrayList to keep track of centroids
		ArrayList<double[]> centroids = new ArrayList<double[]>();
		
		//Initialize List to keep track of sums
		List<Integer> sumList = new ArrayList();
		
		//How many frames to skip
		int frameSkips = 5;
		int cutoff = 0;
		
		BufferedImage newFrame = null;
		while ((newFrame = sequence.NextFrame()) != null) {
		
		/*BufferedImage newFrame = sequence.NextFrame();
		while((newFrame = sequence.NextFrame())!=null) { //until we run out of frames */
		
			/*	//This loop is made to skip every frameSkip frames
			for(int i = 0; i < frameSkips; i++) {
				try{
				newFrame = sequence.NextFrame();
				} 
				
				catch(Exception e) {
					
				}
			}
			if (newFrame == null) break;
			*/
			
			//BufferedImage blank = new BufferedImage(newFrame.getWidth(),newFrame.getHeight(), newFrame.getType());
			
			retina.ProcessFrame(RGBModel.maxContrast(newFrame));
			
			BufferedImage currFrame = retina.getMagno(); //currFrame = magno version
						
			byte grayscale[][] = ImageUtils.BufferedImage2Grayscale(currFrame);
			int gray[][] = Mat.Unsigned2Int(grayscale); //The frame is now an 2D int matrix
			
			int unthresholded[][] = gray;
			
			
			//The following loop thresholds our 2D matrix of ints (<100 -> 0) (>100 -> 255) 
			for(int i = 0; i < gray.length; i++) {
				for(int j = 0; j < gray[i].length; j++) {

					if(gray[i][j] < 100) gray[i][j] = 0;
					else gray[i][j] = 255;

				}
			}
			
			
			grayscale = Mat.Int2Unsigned(gray); //grayscale is the unsigned version of gray 
			
			BufferedImage redoneFrame = ImageUtils.Grayscale2BufferedImage(grayscale, 255);
			GrayF32 input = ConvertBufferedImage.convertFromSingle(redoneFrame, null, GrayF32.class); //More conversions
	 
			GrayU8 binary = new GrayU8(input.width,input.height);
	 
			// the mean pixel value is often a reasonable threshold when creating a binary image
			double mean = ImageStatistics.mean(input);
	 
			// create a binary image by thresholding (binary is the output)
			// Values <= mean go to 0.  Values > mean go to 1.
			ThresholdImageOps.threshold(input, binary, (float) mean, false);
	 
			// reduce noise with some filtering (null = no output)
			GrayU8 filtered = BinaryImageOps.erode8(binary, 1, null);
			filtered = BinaryImageOps.dilate8(filtered, 1, null);
	 
			GrayS32 blobs = new GrayS32(filtered.width, filtered.height);
			GrayU8 blobs2 = new GrayU8(filtered.width, filtered.height);
			BufferedImage blobs3 = new BufferedImage(filtered.width,filtered.height,newFrame.getType());
			
			
			// Find the contour around the shapes using (null = no output)
			List<Contour> contours = BinaryImageOps.contour(filtered, ConnectRule.EIGHT,blobs);
	 		
			
			ConvertImage.convert(blobs, blobs2);
			blobs3 = ConvertBufferedImage.convertTo(blobs2, blobs3);
			
			// Fit a box to each external contour and draw the results
			//GRAPHICS STUFF COMING UP
			Graphics2D g2 = newFrame.createGraphics(); 
			Graphics2D g3 = blobs3.createGraphics();

		
			double R;
			double G;
			double B;

			
			outerloop:
			for( Contour c : contours ) { //For every contour

				FitData<EllipseRotated_F64> ellipse = ShapeFittingOps.fitEllipse_I32(c.external,0,false,null); //Fit an algebraic ellipse
				
				//Calculate the sum (intensity value) for this contour
				int sum = 0;
				for(Point2D_I32 p: c.external) {
					sum = sum + blobs.unsafe_get(p.getX(), p.getY());
				}
				sumList.add(sum); //Add this contour's sum to the list
				
				
				//Eliminate some of the unnecessary contours
				if (frameNum % 3365 == 0){ //every 5 frames (5 is arbitrary)
					double P = .85;
					Collections.sort(sumList);
					if (sumList.size() > 1){
						cutoff =  sumList.get((int) Math.ceil(sumList.size() * P));
					}
					else{
						cutoff = 0;
					}
						
				}
					
				if ((cutoff != 0) && (sum < cutoff)){
					continue outerloop;
				}
				
				int boxsize = Integer.max((int) ellipse.shape.a, (int) ellipse.shape.b); //distance from center to perpendicular edge
				
				if(boxsize < 6) continue; //If it's too small, skip to next contour
				
				 R = Math.random();
				 G = Math.random();
				 B = Math.random();
				 
				 //Storing the location, size, and frameNum of each ellipse (may include RGB later)
				 //List item: [xloc, yloc, a, b, phi, frame, Vx, Vy, R,G,B]
				 double[] info = {ellipse.shape.center.x, ellipse.shape.center.y, ellipse.shape.a, ellipse.shape.b, ellipse.shape.phi, (double) frameNum, 0, 0, 0, 0, 0};
				 
				 list.add(info);
				

              
			} //End of "for every contour" loop
			
			if (frameNum % 5 == 0){
				sumList.clear();
			}

			System.out.println("Finished processing frame " + frameNum + ", contours: " + contours.size());
			frameNum++;
			
			//Deciding what to process and show
			
		//	mask.ProcessFrame(newFrame);
		//	gui.setBufferedImage(newFrame);
		//	gui.repaint();

			
			//mask.ProcessFrame(blobs3);			
			//gui.setBufferedImage(blobs3);	
			//gui.repaint();
			
			
			
		} //End of "for every frame" loop
		
		
		
		FileIO.SaveObject(file, list);
		//mask.Close();
		sequence.Close();
		
		}
		
		
		
		//THIS IS WHERE WE START THE COMBINING AND TUBING STUFF
		//------------------------------------------------------------------------------------------------------------------------
		

		else{ //If already available... load ellipse list from file
			list = (ArrayList<double[]>) FileIO.LoadObject(file);
		}
		
		ArrayList<double[]> initials = new ArrayList<double[]>();
		ArrayList<double[]> finals = new ArrayList<double[]>();
		ArrayList<double[]> toBeAdded = new ArrayList<double[]>();
		double istart = 6;
		double iend = 10;
		double fstart = 11;
		double fend = 15;
		
		double r;
		double sizediff;
		double framediff;
		double sizeThreshold = 50;
		double locThreshold = 80;
		double Vx;
		double Vy;
		double R;
		double G;
		double B;
		ArrayList<ArrayList<double[]>> paths = new ArrayList<ArrayList<double[]>>();
		boolean familiarFlag;
		boolean matchFlag;
		boolean addFlag;
		double[] toAdd = new double[11];
		
		
		while(fend + 10 < list.size()-1){//While there's more video left
		
			//Fill up the initials and finals lists
			for(double[] e : list){
				if((e[5] >= istart) && (e[5]<=iend)){
					initials.add(e);
				}
				
				if((e[5] >= fstart) && (e[5]<=fend)){
					finals.add(e);
				}
			}
			
	
			
			iloop:
			for(double[] i: initials){
				for(double[] f: finals){
				//f is the "final" instance of the ellipse. i is the "initial" one
				matchFlag = false;
					
				r = Math.sqrt( Math.pow(f[0] -i[0], 2) + Math.pow(f[1] - i[1], 2));
				sizediff = Integer.max((int) f[2], (int) f[3]) - Integer.max((int) i[2], (int) i[3]);
				framediff = f[5] -i[5];
				
				//Make a match based on velocity info if it exists
				if (i[6] != 0){
					//Predict where it should be
					double pred_x = i[6]*framediff + i[0];
					double pred_y = i[7]*framediff + i[1];
					if ((pred_x -50 < f[0] && f[0]< pred_x+50) && (pred_y -50 < f[1] && f[1]< pred_y+50)){
						//If f is within 50 of our prediction
						//Show us the match
						matchFlag = true;
					}
				}
				else{ //no velocity info (new)
					if ((r<locThreshold) && (sizediff<sizeThreshold)){ //Within some size/loc threshold
						//Show us the match
						matchFlag = true;
					}
				}
				
				if (matchFlag){//if it's a match
					
	
					if (i[8]== 0){//If it hasn't been assigned a color
						//Give it a random color
						i[8] = Math.random();
						i[9] = Math.random();
						i[10] = Math.random();
					}
					//Keeps paths the same color
					f[8] = i[8];
					f[9] = i[9];
					f[10] = i[10];
					
					
					// Calculating velocity:
					// Final location - initial location / final frame - initial frame
					f[6] = (f[0] - i[0])/framediff;
					f[7] = (f[1] - i[1])/framediff;
					
					
					//Add it into the path and add the path into the list of paths
					ArrayList<double[]> path = new ArrayList<double[]>();
					
					familiarFlag = false;
					addFlag = false;
					
					//Checks the rest of the initials for very close ellipses
					for (int j= (int) i[5]+1; j<=iend; j++){
						double r0 = 100000;
						for (double[] init: initials){
							
							//Find the closest in the frame
							if (init[5] ==j){ //if in the jth frame
								
								r = Math.sqrt( Math.pow(init[0] -i[0], 2) + Math.pow(init[1] - i[1], 2));
								
								if(r<r0){
									toAdd = init;
									r0 = r;
								}	
							}
						}
						if (r0<50){
							//Keeps paths the same color
							toAdd[8] = i[8];
							toAdd[9] = i[9];
							toAdd[10] = i[10];
							addFlag = true;
						}
					}
					
					//path.addAll(toBeAdded);
					
					//Figure out if we've seen this before
					for(ArrayList<double[]> a : paths){
						for(double[] b : a){
							
							r = Math.sqrt( Math.pow(b[0] -i[0], 2) + Math.pow(b[1] - i[1], 2));
							sizediff = Integer.max((int) b[2], (int) b[3]) - Integer.max((int) i[2], (int) i[3]);
							
							if ((Math.abs(i[5] - b[5]) <= 2) && ((r<80) && (sizediff<80))){
								

							/*if (b.equals(i)){
								System.out.println("Familiar");
								System.out.println(Arrays.toString(b));
								System.out.println(Arrays.toString(i));*/
								
								//If we have then add it on the end of that path
								if (addFlag){
									a.add(toAdd);
								}
								a.add(f);
								Collections.sort(a,new FrameComparator());
								familiarFlag = true;
								break;
							}
							
						}
					}
					
					//If we haven't then make a new path
					if (!familiarFlag){
						if (addFlag){
							path.add(toAdd);
						}
						path.add(i);
						path.add(f);
						Collections.sort(path,new FrameComparator());
						paths.add(path);
					}

					
					
					//Maybe clear out the final one so it can't be used again?
					finals.remove(f);
					
					continue iloop;
				}//closes matchFlag if
				
				}
			}
		
			initials.clear();
			finals.clear();
			istart = istart + 5;
			iend = iend + 5;
			fstart = fstart + 5;
			fend = fend + 5;
		
		}
		
		// END OF FIRST PASS (CREATES THE PATHS)
		
		
		

		ArrayList<ArrayList<double[]>> newpaths = new ArrayList<ArrayList<double[]>>();
		boolean makePathFlag;
		
		
		//This loop fixes paths so that they correspond to colors 
		for (ArrayList<double[]> a: paths){
			for (double[] b: a){
				
				R = b[8];
				G = b[9];
				B= b[10];
				makePathFlag = true;
				//pathToAdd.clear();
				
				for (ArrayList<double[]> newa: newpaths){
					
					if (newa.get(0)[8] != R){ //If this path doesn't have your color
						continue;
					}
					else{
						newa.add(b);
						Collections.sort(newa, new FrameComparator());
						makePathFlag = false;
						break; 
					}
				}
				
				if(makePathFlag){
					ArrayList<double[]> pathToAdd = new ArrayList<double[]>();
					pathToAdd.add(b);
					newpaths.add(pathToAdd);
				}
			}
		}
		
		
		//NEED TO REMOVE REPEATS FROM EVERY PATH
		for (ArrayList<double[]> a: paths){

			Set<double[]> hs = new HashSet<double[]>();
			hs.addAll(a);
			a.clear();
			a.addAll(hs);
			Collections.sort(a, new FrameComparator());
		}
		//OKAY DONE
		
		
		
		//newpaths is the paths variable now. It has been through only one pass.
		//It doesn't contain repeat ellipses... Now, we gotta transform to tubes
		

		ArrayList<ArrayList<double[]>> tubes = new ArrayList<ArrayList<double[]>>();
		double X,Y;
		double Xsum,Ysum;
//		double Xavg,Yavg;
		double Amax,Bmax;
		double Asum,Bsum;
		double Aavg,Bavg;
		double Rotavg,Vxavg,Vyavg;
		double Rotsum,Vxsum,Vysum;
		boolean exactFrameFlag;
		double lastframe = 0;
		
		
		//This loop makes tubes out of the paths
		for (ArrayList<double[]> thispath : newpaths){
			
			//Make a new tube object
			ArrayList<double[]> tube = new ArrayList<double[]>();
			
			
			for(int f = (int) thispath.get(0)[5]; f < thispath.get(thispath.size()-1)[5]  ;f++) { //for every frame in the path
				
				
				if (f>= lastframe){
					lastframe = f;
				}
				
				//New chunk and new variables
				ArrayList<double[]> chunk = new ArrayList<double[]>();
				ArrayList<double[]> chunksmall = new ArrayList<double[]>();
				ArrayList<double[]> chunkmed = new ArrayList<double[]>();
				ArrayList<double[]> chunklarge = new ArrayList<double[]>();
				Amax = 0;
				Bmax = 0;
				Asum = 0;
				Bsum = 0;
				Rotsum = 0;
				Rotavg = 0;
				Vxsum = 0;
				Vysum = 0;
				X= 0;
				Y= 0;
				R = thispath.get(0)[8];
				
				G = thispath.get(0)[9];
				B = thispath.get(0)[10];
				
				//Fill up chunk with ellipses up to 15 frames away either way
				for (double[] e : thispath){
					if (Math.abs(e[5] - f) <= 15) {
						chunk.add(e);
					}
				}
				
				if (chunk.isEmpty()){
					//Then we in a big ass gap and we gotta finesse
					//ADD FINESSE
				}
				

				for(double[] e : chunk){
					
					//These ifs sort ellipses into sub chunks for X/Y stuff
					if(Math.abs(e[5] -f) <= 2){
						chunksmall.add(e);
					}
					if (Math.abs(e[5] -f) <= 5){
						chunkmed.add(e);
					}
					if (Math.abs(e[5] -f) <= 10){
						chunklarge.add(e);
					}
					
					//Keep track of the maximum A,B and keep sum of Rot, Vx, and Vy
					if (e[2] > Amax){
						Amax = e[2];
					}
					if (e[3] > Bmax){
						Bmax = e[3];
					}
					
					Asum = Asum + e[2];
					Bsum = Bsum + e[3];
					//Rotsum = Rotsum + e[4];
					Vxsum = Vxsum + e[6];
					Vysum = Vysum + e[7];
					
				} //Done going through ellipses in chunk
				
				Aavg = Asum/chunk.size();
				Bavg = Bsum/chunk.size();
				//Rotavg = Rotsum/chunk.size();
				Vxavg = Vxsum/chunk.size();
				Vyavg = Vysum/chunk.size();
				
				//Here, we check 2/5/10 frames away for ellipses and decide on X/Y values
				//If there's nothing in that exact frame we take an avg of the surrounding 2/5/10 frames
				Xsum = 0;
				Ysum = 0;
				exactFrameFlag = false;
				if (!chunksmall.isEmpty()){
					//Sum up Xs and Ys and take avg
					for (double[] e : chunksmall){
						Xsum = Xsum + e[0];
						Ysum = Ysum + e[1];
						Rotavg = e[4];
						
						if (e[5] == f){
							exactFrameFlag = true;
							X = e[0];
							Y = e[1];
							Rotavg = e[4];
							break;
						}
					}
					if (!exactFrameFlag){
						X = Xsum/chunksmall.size();
						Y = Ysum/chunksmall.size();	
						//Rotavg = Rotsum/chunksmall.size();
					}
				}
				
				else if (!chunkmed.isEmpty()){
					//Sum up Xs and Ys and take avg
					for (double[] e : chunkmed){
						Xsum = Xsum + e[0];
						Ysum = Ysum + e[1];
						Rotavg = e[4];
					}
					X = Xsum/chunkmed.size();
					Y = Ysum/chunkmed.size();
					//Rotavg = Rotsum/chunkmed.size();
				} 
				
				else if (!chunklarge.isEmpty()){
					//Sum up Xs and Ys and take avg
					for (double[] e : chunklarge){
						Xsum = Xsum + e[0];
						Ysum = Ysum + e[1];
						Rotavg = e[4];
					}
					X = Xsum/chunklarge.size();
					Y = Ysum/chunklarge.size();
					//Rotavg = Rotsum/chunklarge.size();
				}
				
				else{
					for (double[] e : chunk){
						Xsum = Xsum + e[0];
						Ysum = Ysum + e[1];
						//Rotsum = Rotsum + e[4];
					}
					X = Xsum/chunk.size();
					Y = Ysum/chunk.size();
					//Rotavg = Rotsum/chunk.size();
				}
				
				//Add a slice of the tube for frame f
				double[] tubeslice = {X,Y,Amax*1.2,Bmax*1.2,Rotavg,f,Vxavg,Vyavg,R,G,B};
				tube.add(tubeslice);

			}//End for every frame loop
			
			tubes.add(tube);
		}//Now tubes is the object of concern.  
		
		
		//This part is to get rid of faulty tubes (like the all black one)
		ArrayList<ArrayList<double[]>> toRemove = new ArrayList<ArrayList<double[]>>();
		for(ArrayList<double[]> tube : tubes){
			
			if((!tube.isEmpty()) && (tube.get(0)[8] == 0)){ //if not empty and the color is somehow empty
				toRemove.add(tube);
			}
		}
		tubes.removeAll(toRemove);
		
		
		//------------------------------- SECOND PASS ----------------------------------------
		
		ArrayList<ArrayList<double[]>> newtubes = new ArrayList<ArrayList<double[]>>();
		ArrayList<double[]> ipath;
		ArrayList<double[]> jpath;
		ArrayList<double[]> toConnect = new ArrayList<double[]>();
		ArrayList<double[]> oldConnect = new ArrayList<double[]>();
		ArrayList<double[]> oldj = new ArrayList<double[]>();		
		double[] toCheck;
		double superVx;
		double superVy;
		double backsuperVx;
		double backsuperVy;
		double predx;
		double predy;
		double predf;
		double backpredx;
		double backpredy;
		double backpredf;
		double lookahead = 5;
		
		
		//This big ass for loop takes things from tubes to newtubes
		//(takes small paths and connects them into bigger ones)
		for (int i = 0;i<= tubes.size()-1; i++){
			
			//The ith path in paths
			ipath = tubes.get(i);
			Collections.sort(ipath,new FrameComparator());
			
			if (ipath.size() < 10){
				continue;
			}
			

			
			//Look at velocities of last 5 ellipses in path and take avg
			superVx = (ipath.get(ipath.size()-1)[6] + ipath.get(ipath.size()-2)[6] + ipath.get(ipath.size()-3)[6] + ipath.get(ipath.size()-4)[6] + ipath.get(ipath.size()-5)[6])/5;
			superVy = (ipath.get(ipath.size()-1)[7] + ipath.get(ipath.size()-2)[7] + ipath.get(ipath.size()-3)[7] + ipath.get(ipath.size()-4)[7] + ipath.get(ipath.size()-5)[7])/5;
			
			//Look at velocities of first 5 ellipses in path and take avg
			backsuperVx = (ipath.get(0)[6] + ipath.get(1)[6] + ipath.get(2)[6] + ipath.get(3)[6] + ipath.get(4)[6])/5;
			backsuperVy = (ipath.get(0)[7] + ipath.get(1)[7] + ipath.get(2)[7] + ipath.get(3)[7] + ipath.get(4)[7])/5;
			
			//Make prediction about where it'll be in (lookahead) frames
			predx = (ipath.get(ipath.size()-1)[0] + ipath.get(ipath.size()-2)[0] + ipath.get(ipath.size()-3)[0])/3 + (superVx * lookahead);
			predy = (ipath.get(ipath.size()-1)[1] + ipath.get(ipath.size()-2)[1] + ipath.get(ipath.size()-3)[1])/3 + (superVy * lookahead);
			predf = ipath.get(ipath.size()-1)[5] + lookahead;
			
			//Make prediction about where it'll be in (lookahead) frames
			backpredx = (ipath.get(0)[0] + ipath.get(1)[0] + ipath.get(2)[0])/3 - (backsuperVx * lookahead);
			backpredy = (ipath.get(0)[1] + ipath.get(1)[1] + ipath.get(2)[1])/3 - (backsuperVy * lookahead);
			backpredf = ipath.get(0)[5] - lookahead;
			
			
			double r0 = 10000;
			
			for(int j = 1;j <= tubes.size()-1; j++){
				
				//The jth path in paths
				jpath = tubes.get(j);
				Collections.sort(jpath, new FrameComparator());
				
				if(jpath.equals(oldj)){
					continue;
				}
				
				if (jpath.size() < 10){
					continue;
				}
				
				if (j==i){
					continue;
				}
				
				for(double[] e: jpath){ //For every ellipse in jpath
					
					if(Math.abs(e[5]-predf) <= 5){// That is (at most) 5 away from prediction frame
						
						//See if the location is close...
						r = Math.sqrt( Math.pow(e[0] -predx, 2) + Math.pow(e[1] - predy, 2));
												
						if ((r<r0) && (Math.abs(e[6] - superVx) < 2) && (Math.abs(e[7] - superVy) < 2)) { 
							toCheck = e;
							r0 = r;
						}
						//The one with the closest is the right jpath (as long as it's X close)
					}
					
					if(Math.abs(e[5]-backpredf) <= 5){// That is (at most) 5 away from prediction frame
					
						//See if the location is close...
						r = Math.sqrt( Math.pow(e[0] -backpredx, 2) + Math.pow(e[1] - backpredy, 2));
						
						if ((r<r0) && (Math.abs(e[6] - backsuperVx) < 2) && (Math.abs(e[7] - backsuperVy) < 2)) { 
							toCheck = e;
							r0 = r;
						}
					}
				} //After going thru all ellipses in jpath
				
				oldj = jpath;
				
				if (r0<100){
					toConnect = jpath;
					break;
				}
				else{
					oldConnect = toConnect;
					continue;
				}

				
				
			} //end of going through js
			
			if(!toConnect.isEmpty() && (!oldConnect.equals(toConnect)) ) { 
				
				
				//Calculate velocity of each to make sure they're going in the same direction
				//Velocity of ipath
				double VxsumI = 0;
				double VysumI = 0;
				for(double[] E : ipath){
					VxsumI = VxsumI + E[6];
					VysumI = VysumI + E[7];
				}
				double VxavgI = VxsumI / ipath.size();
				double VyavgI = VysumI / ipath.size();
				
				//Velocity of toConnect
				double VxsumJ = 0;
				double VysumJ = 0;
				for(double[] F : toConnect){
					VxsumJ = VxsumJ + F[6];
					VysumJ = VysumJ + F[7];
				}
				double VxavgJ = VxsumJ / toConnect.size();
				double VyavgJ = VysumJ / toConnect.size();
				
				if ( (VxavgI/VxavgJ > 0) &&   ((VyavgI/VyavgJ > 0) ||  ((Math.abs(VyavgI)<1) && (Math.abs(VyavgJ)<1)) ) ){ //to check direction
					//MAKE A THIRD PATHS OBJECT AND COMBINE PATHS INTO THERE 
					
					
					ArrayList<double[]> superTube = new ArrayList<double[]>();
					
					//Maybe check thirdpaths for the first ellipse in ipath.  If there, nah
					double[] checkFor = ipath.get(0); 
						
					superTube.addAll(ipath);
					superTube.addAll(toConnect);
					Collections.sort(superTube, new FrameComparator());
					
					
					//HERE! Check for duplicates in a single frame of supertube and if there is, pick whichever is most like toConnect
					//-------------------------------------------------------------------------------------------------------
					ArrayList<double[]> toDelete = new ArrayList<double[]>();
					
					for (int f = 0; f<= lastframe; f++){
						
						
						
						ArrayList<double[]> similarSlices = new ArrayList<double[]>();
	
						for (double[] tubeslice: superTube){ //This collects same color ellipses in the same frame
							if (tubeslice[5] == f){
								similarSlices.add(tubeslice);
							}
						}
						
						if (similarSlices.size()>1){ 
							
							if (f==306){
								System.out.println("306!");
							}
							
							double[] compareSlice = new double[11];
							double[] worstslice = new double[11];
							
							for (double[] tubeslice : superTube){
								
								if (tubeslice[5] == f + 25){
									compareSlice = tubeslice;
									break;
								}
								
							}
							
							
							
							//Mark for deletion everything but the slice closest to where it will be
							r0 = 0;
							for (double[] slice: similarSlices){ 

								r = Math.sqrt( Math.pow(slice[0] -compareSlice[0], 2) + Math.pow(slice[1] - compareSlice[1], 2));
								if (r> r0){
									worstslice = slice;
								}
							}
							
							toDelete.add(worstslice);
						}
						superTube.removeAll(toDelete);
						
						for (ArrayList<double[]> tube : newtubes){
							if (tube.contains(toDelete)){
								tube.remove(toDelete);
							}
						}
					}
					
					
					
					Collections.sort(superTube, new FrameComparator());
					//Okay done-----------------------------------------------------------------------------------------------------
					
					
					R = superTube.get(0)[8];
					G = superTube.get(0)[9];
					B = superTube.get(0)[10];
					for (double[] E : superTube){
						E[8] = R;
						E[9] = G;
						E[10]= B;
					}
	
					newtubes.add(superTube);

				}
				else{
					newtubes.add(ipath);
				}
				
			}//end of if toConnect isn't empty...
			
			else{
				newtubes.add(ipath);
			}
		}
		
		//----------------------------- END OF SECOND PASS ----------------------------------------
		
		
		
		ArrayList<double[]> itube = new ArrayList<double[]>();
		ArrayList<double[]> jtube = new ArrayList<double[]>();
		ArrayList<ArrayList<double[]>> tubesToDelete = new ArrayList<ArrayList<double[]>>();
		ArrayList<double[]> slicesToDelete = new ArrayList<double[]>();
		
		ArrayList<ArrayList<double[]>> newtubes2 = newtubes;
		
		//Deleting empty tubes in newtubes
		for(ArrayList<double[]> tube : newtubes2){
			if (tube.isEmpty()){
				tubesToDelete.add(tube);
			}
		}
		newtubes2.removeAll(tubesToDelete);
		
		

		
		
		
		//----------------------------------- THIRD PASS ----------------------------------------
		
			ArrayList<ArrayList<double[]>> thirdtubes = new ArrayList<ArrayList<double[]>>();
			toConnect = new ArrayList<double[]>();
			oldConnect = new ArrayList<double[]>();
			oldj = new ArrayList<double[]>();		
			lookahead = 25;
			
			
			//This big ass for loop takes things from tubes to newtubes
			//(takes small paths and connects them into bigger ones)
			for (int i = 0;i<= newtubes2.size()-1; i++){
				
				//The ith path in paths
				ipath = newtubes2.get(i);
				Collections.sort(ipath,new FrameComparator());
				
				if (ipath.size() < 10){
					continue;
				}
				

				
				//Look at velocities of last 5 ellipses in path and take avg
				superVx = (ipath.get(ipath.size()-1)[6] + ipath.get(ipath.size()-2)[6] + ipath.get(ipath.size()-3)[6] + ipath.get(ipath.size()-4)[6] + ipath.get(ipath.size()-5)[6])/5;
				superVy = (ipath.get(ipath.size()-1)[7] + ipath.get(ipath.size()-2)[7] + ipath.get(ipath.size()-3)[7] + ipath.get(ipath.size()-4)[7] + ipath.get(ipath.size()-5)[7])/5;
				
				//Look at velocities of first 5 ellipses in path and take avg
				backsuperVx = (ipath.get(0)[6] + ipath.get(1)[6] + ipath.get(2)[6] + ipath.get(3)[6] + ipath.get(4)[6])/5;
				backsuperVy = (ipath.get(0)[7] + ipath.get(1)[7] + ipath.get(2)[7] + ipath.get(3)[7] + ipath.get(4)[7])/5;
				
				//Make prediction about where it'll be in (lookahead) frames
				predx = (ipath.get(ipath.size()-1)[0] + ipath.get(ipath.size()-2)[0] + ipath.get(ipath.size()-3)[0])/3 + (superVx * lookahead);
				predy = (ipath.get(ipath.size()-1)[1] + ipath.get(ipath.size()-2)[1] + ipath.get(ipath.size()-3)[1])/3 + (superVy * lookahead);
				predf = ipath.get(ipath.size()-1)[5] + lookahead;
				
				//Make prediction about where it'll be in (lookahead) frames
				backpredx = (ipath.get(0)[0] + ipath.get(1)[0] + ipath.get(2)[0])/3 - (backsuperVx * lookahead);
				backpredy = (ipath.get(0)[1] + ipath.get(1)[1] + ipath.get(2)[1])/3 - (backsuperVy * lookahead);
				backpredf = ipath.get(0)[5] - lookahead;
				
				
				double r0 = 10000;
				
				for(int j = 1;j <= newtubes2.size()-1; j++){
					
					//The jth path in paths
					jpath = newtubes2.get(j);
					Collections.sort(jpath, new FrameComparator());
					
					if(jpath.equals(oldj)){
						continue;
					}
					
					if (jpath.size() < 10){
						continue;
					}
					
					if (j==i){
						continue;
					}
					
					for(double[] e: jpath){ //For every ellipse in jpath
						
						if(Math.abs(e[5]-predf) <= 5){// That is (at most) 5 away from prediction frame
							
							//See if the location is close...
							r = Math.sqrt( Math.pow(e[0] -predx, 2) + Math.pow(e[1] - predy, 2));
													
							if ((r<r0) && (Math.abs(e[6] - superVx) < 2) && (Math.abs(e[7] - superVy) < 2)) { 
								toCheck = e;
								r0 = r;
							}
							//The one with the closest is the right jpath (as long as it's X close)
						}
						
						if(Math.abs(e[5]-backpredf) <= 5){// That is (at most) 5 away from prediction frame
						
							//See if the location is close...
							r = Math.sqrt( Math.pow(e[0] -backpredx, 2) + Math.pow(e[1] - backpredy, 2));
							
							if ((r<r0) && (Math.abs(e[6] - backsuperVx) < 2) && (Math.abs(e[7] - backsuperVy) < 2)) { 
								toCheck = e;
								r0 = r;
							}
						}
					} //After going thru all ellipses in jpath
					
					oldj = jpath;
					
					if (r0<100){
						toConnect = jpath;
						break;
					}
					else{
						oldConnect = toConnect;
						continue;
					}

					
					
				} //end of going through js
				
				if(!toConnect.isEmpty() && (!oldConnect.equals(toConnect)) ) { 
					
					
					//Calculate velocity of each to make sure they're going in the same direction
					//Velocity of ipath
					double VxsumI = 0;
					double VysumI = 0;
					for(double[] E : ipath){
						VxsumI = VxsumI + E[6];
						VysumI = VysumI + E[7];
					}
					double VxavgI = VxsumI / ipath.size();
					double VyavgI = VysumI / ipath.size();
					
					//Velocity of toConnect
					double VxsumJ = 0;
					double VysumJ = 0;
					for(double[] F : toConnect){
						VxsumJ = VxsumJ + F[6];
						VysumJ = VysumJ + F[7];
					}
					double VxavgJ = VxsumJ / toConnect.size();
					double VyavgJ = VysumJ / toConnect.size();
					
					if ( (VxavgI/VxavgJ > 0) &&   ((VyavgI/VyavgJ > 0) ||  ((Math.abs(VyavgI)<1) && (Math.abs(VyavgJ)<1)) ) ){ //to check direction
						//MAKE A THIRD PATHS OBJECT AND COMBINE PATHS INTO THERE 
						
						
						ArrayList<double[]> superTube = new ArrayList<double[]>();
						
						//Maybe check thirdpaths for the first ellipse in ipath.  If there, nah
						double[] checkFor = ipath.get(0); 
							
						superTube.addAll(ipath);
						superTube.addAll(toConnect);
						Collections.sort(superTube, new FrameComparator());
						
						R = superTube.get(0)[8];
						G = superTube.get(0)[9];
						B = superTube.get(0)[10];
						for (double[] E : superTube){
							E[8] = R;
							E[9] = G;
							E[10]= B;
						}
		
						thirdtubes.add(superTube);

					}
					else{
						thirdtubes.add(ipath);
					}
					
				}//end of if toConnect isn't empty...
				
				else{
					thirdtubes.add(ipath);
				}
			}
		
//--------------------------------- END OF THIRD PASS --------------------------------------------
			
			
			
//--------------------------------------- FOURTH PASS --------------------------------------------
			
			ArrayList<ArrayList<double[]>> fourthtubes = new ArrayList<ArrayList<double[]>>();
			toConnect = new ArrayList<double[]>();
			oldConnect = new ArrayList<double[]>();
			oldj = new ArrayList<double[]>();		
			lookahead = 60;
			
			
			//This big ass for loop takes things from tubes to newtubes
			//(takes small paths and connects them into bigger ones)
			for (int i = 0;i<= thirdtubes.size()-1; i++){
				
				//The ith path in paths
				ipath = thirdtubes.get(i);
				Collections.sort(ipath,new FrameComparator());
				
				if (ipath.size() < 10){
					continue;
				}
				
				//Look at velocities of last 5 ellipses in path and take avg
				superVx = (ipath.get(ipath.size()-1)[6] + ipath.get(ipath.size()-2)[6] + ipath.get(ipath.size()-3)[6] + ipath.get(ipath.size()-4)[6] + ipath.get(ipath.size()-5)[6])/5;
				superVy = (ipath.get(ipath.size()-1)[7] + ipath.get(ipath.size()-2)[7] + ipath.get(ipath.size()-3)[7] + ipath.get(ipath.size()-4)[7] + ipath.get(ipath.size()-5)[7])/5;
				
				//Look at velocities of first 5 ellipses in path and take avg
				backsuperVx = (ipath.get(0)[6] + ipath.get(1)[6] + ipath.get(2)[6] + ipath.get(3)[6] + ipath.get(4)[6])/5;
				backsuperVy = (ipath.get(0)[7] + ipath.get(1)[7] + ipath.get(2)[7] + ipath.get(3)[7] + ipath.get(4)[7])/5;
				
				//Make prediction about where it'll be in (lookahead) frames
				predx = (ipath.get(ipath.size()-1)[0] + ipath.get(ipath.size()-2)[0] + ipath.get(ipath.size()-3)[0])/3 + (superVx * lookahead);
				predy = (ipath.get(ipath.size()-1)[1] + ipath.get(ipath.size()-2)[1] + ipath.get(ipath.size()-3)[1])/3 + (superVy * lookahead);
				predf = ipath.get(ipath.size()-1)[5] + lookahead;
				
				//Make prediction about where it'll be in (lookahead) frames
				backpredx = (ipath.get(0)[0] + ipath.get(1)[0] + ipath.get(2)[0])/3 - (backsuperVx * lookahead);
				backpredy = (ipath.get(0)[1] + ipath.get(1)[1] + ipath.get(2)[1])/3 - (backsuperVy * lookahead);
				backpredf = ipath.get(0)[5] - lookahead;
				
				
				double r0 = 10000;
				
				for(int j = 1;j <= thirdtubes.size()-1; j++){
					
					//The jth path in paths
					jpath = thirdtubes.get(j);
					Collections.sort(jpath, new FrameComparator());
					
					if(jpath.equals(oldj)){
						continue;
					}
					
					if (jpath.size() < 10){
						continue;
					}
					
					if (j==i){
						continue;
					}
					
					for(double[] e: jpath){ //For every ellipse in jpath
						
						if(Math.abs(e[5]-predf) <= 5){// That is (at most) 5 away from prediction frame
							
							//See if the location is close...
							r = Math.sqrt( Math.pow(e[0] -predx, 2) + Math.pow(e[1] - predy, 2));
													
							if ((r<r0) && (Math.abs(e[6] - superVx) < 2) && (Math.abs(e[7] - superVy) < 2)) { 
								toCheck = e;
								r0 = r;
							}
							//The one with the closest is the right jpath (as long as it's X close)
						}
						
						if(Math.abs(e[5]-backpredf) <= 5){// That is (at most) 5 away from prediction frame
						
							//See if the location is close...
							r = Math.sqrt( Math.pow(e[0] -backpredx, 2) + Math.pow(e[1] - backpredy, 2));
							
							if ((r<r0) && (Math.abs(e[6] - backsuperVx) < 2) && (Math.abs(e[7] - backsuperVy) < 2)) { 
								toCheck = e;
								r0 = r;
							}
						}
					} //After going thru all ellipses in jpath
					
					oldj = jpath;
					
					if (r0<100){
						toConnect = jpath;
						break;
					}
					else{
						oldConnect = toConnect;
						continue;
					}

					
					
				} //end of going through js
				
				if(!toConnect.isEmpty() && (!oldConnect.equals(toConnect)) ) { 
					
					
					//Calculate velocity of each to make sure they're going in the same direction
					//Velocity of ipath
					double VxsumI = 0;
					double VysumI = 0;
					for(double[] E : ipath){
						VxsumI = VxsumI + E[6];
						VysumI = VysumI + E[7];
					}
					double VxavgI = VxsumI / ipath.size();
					double VyavgI = VysumI / ipath.size();
					
					//Velocity of toConnect
					double VxsumJ = 0;
					double VysumJ = 0;
					for(double[] F : toConnect){
						VxsumJ = VxsumJ + F[6];
						VysumJ = VysumJ + F[7];
					}
					double VxavgJ = VxsumJ / toConnect.size();
					double VyavgJ = VysumJ / toConnect.size();
					
					if ( (VxavgI/VxavgJ > 0) &&   ((VyavgI/VyavgJ > 0) ||  ((Math.abs(VyavgI)<1) && (Math.abs(VyavgJ)<1)) ) ){ //to check direction
						//MAKE A THIRD PATHS OBJECT AND COMBINE PATHS INTO THERE 
						
						
						ArrayList<double[]> superTube = new ArrayList<double[]>();
						
						//Maybe check thirdpaths for the first ellipse in ipath.  If there, nah
						double[] checkFor = ipath.get(0); 
							
						superTube.addAll(ipath);
						superTube.addAll(toConnect);
						Collections.sort(superTube, new FrameComparator());
						
						R = superTube.get(0)[8];
						G = superTube.get(0)[9];
						B = superTube.get(0)[10];
						for (double[] E : superTube){
							E[8] = R;
							E[9] = G;
							E[10]= B;
						}
		
						fourthtubes.add(superTube);

					}
					else{
						fourthtubes.add(ipath);
					}
					
				}//end of if toConnect isn't empty...
				
				else{
					fourthtubes.add(ipath);
				}
			}
		
//-------------------------------- END OF FOURTH PASS -------------------------------------------
			
			
			/*Iterates thru tubes and handles the situation where multiple tubes (same color)
			 *have a slice at the same time. 
			 *
			 *Note: Same color creates double representation
			 */
			for (int i = 0;i<= fourthtubes.size()-1; i++){
				for(int j = i;j <= fourthtubes.size()-1; j++){
					
					if (i==j){
						continue;
					}
					
					//Get the tubes
					itube = fourthtubes.get(i);
					jtube = fourthtubes.get(j);
					
					if (itube.isEmpty() || jtube.isEmpty()){
						continue;
					}
										
					double[] islice = new double[11];
					double[] jslice = new double[11];
					
					for(int f =0; f<=lastframe;f++){
						for(double[] e: itube){ //Find the slice in itube
							if (e[5] == f){
								islice = e;
								break;
							}
						}
						for(double[] e: jtube){ //Find the slice in jtube
							if (e[5] == f){
								jslice = e;
								break;
							}
						}
						
						if (islice[8] == jslice[8]){
							if (itube.size()>jtube.size()){
								jtube.remove(jslice);
							}
							else{
								itube.remove(islice);
							}
						}
					}//end of "for every frame" loop
				}
			}
					
			
		
			//This part is to get rid of empty tubes (and keeps color continuous throughout tube
			toRemove = new ArrayList<ArrayList<double[]>>();
			for(ArrayList<double[]> tube : fourthtubes){
				
				//Take out empty tube
				if(tube.isEmpty()){
					toRemove.add(tube);
					continue;
				}
				
				R = tube.get(0)[8];
				G = tube.get(0)[9];
				B = tube.get(0)[10];
				
				for(double[] slice: tube){
					slice[8] = R;
					slice[9] = G;
					slice[10]= B;
				}
			}
			fourthtubes.removeAll(toRemove);
			
			
			
			double maj;
			double min;
			double phi;
			
			//This part is to fill in gaps within tubes
			for(ArrayList<double[]> tube : fourthtubes){
				toBeAdded.clear();
				ArrayList<double[]> toBeRemoved = new ArrayList<double[]>();
				
				for(double[] slice : tube){
					double[] currSlice = slice;
					double[] nextSlice;
					
					try{ //if there's a next slice, get it. If not, jump outta this for loop
						nextSlice = tube.get(tube.indexOf(currSlice) + 1);
					}catch(IndexOutOfBoundsException e){
						break;
					}
					
					//Get the frame number for each
					double currF = currSlice[5];
					double nextF = nextSlice[5];
					
					if (currF == 620){
						System.out.println("BLAH");
					}
					
					if (nextF == currF){ //If there's a duplicate
						if (currSlice[2]*currSlice[3] > nextSlice[2]*nextSlice[3]){
							toBeRemoved.add(nextSlice);
						}
						else if(currSlice[2]*currSlice[3] < nextSlice[2]*nextSlice[3]) {
							toBeRemoved.add(currSlice);
						}
						else{
						}
						
						continue;
					}
					
					if (nextF - currF == 1){
						continue;
					}
					else{ //if there is a gap between slices
						//FILL IT IN
						for (int f = (int)currF+1; f<nextF;f++){
							framediff = f - currF;
							
							//Make the new slice
							X = (nextSlice[0] - currSlice[0]) * (framediff/(nextF - currF)) + currSlice[0];
							Y = (nextSlice[1] - currSlice[1]) * (framediff/(nextF - currF)) + currSlice[1];
							maj = (nextSlice[2] - currSlice[2]) * (framediff/(nextF - currF)) + currSlice[2];
							min = (nextSlice[3] - currSlice[3]) * (framediff/(nextF - currF)) + currSlice[3];
							phi =(nextSlice[4] - currSlice[4]) * (framediff/(nextF - currF)) + currSlice[4];
							Vx = (X - currSlice[0])/framediff;
							Vy = (Y - currSlice[1])/framediff;
							R = currSlice[8];
							G = currSlice[9];
							B = currSlice[10];
							
							//...and add it in this tube
							double[] newSlice = {X,Y,maj,min,phi,f,Vx,Vy,R,G,B};
							toBeAdded.add(newSlice);

						}
					}
					
				}
				
				//Sort slices to make sure they're in order again
				tube.addAll(toBeAdded);
				tube.removeAll(toBeRemoved);
				//Remove repeats from tube
				Set<double[]> set = new HashSet<double[]>();
				set.addAll(tube);
				tube.clear();
				tube.addAll(set);
				Collections.sort(tube,new FrameComparator());
			}


		
		
		File newfile = new File("C:/Users/f002tj9/Documents/Research/kj/TubeLists/" + CatGetter.stemOnly(vidLoc) + ".zip" );
		FileIO.SaveObject(newfile, fourthtubes);
		
		
		//----------------------------GRAPHICS STUFF----------------------------------------------
		VideoFrameReader sequence = new VideoFrameReader(vidLoc);
		sequence.OpenFile();
		
		int height = sequence.getFrameHeight();
		int width = sequence.getFrameWidth(); 

		
		VideoFrameWriter mask = new VideoFrameWriter(new File(stemLoc), width, height, 30); 
		VideoRetina retina = new VideoRetina(width, height, false); //create retina

		mask.OpenFile(); //open videowriter
		
		//This part shows the video on screen
		gui.setPreferredSize(new Dimension(width, height));
		ShowImages.showWindow(gui,CatGetter.extract(vidLoc), true);
		
		BufferedImage newFrame = sequence.NextFrame();
		int frameNum=2;
		EllipseRotated_F64 ellipse;
		while((newFrame = sequence.NextFrame())!=null) {
			
			
			Graphics2D g2 = newFrame.createGraphics(); 
			
			g2.setColor(Color.white);
			g2.drawString(Integer.toString(frameNum), 20, 20);
			g2.setStroke(new BasicStroke(3));
			
			//PUT OBJECT OF INTEREST BELOW
			for(ArrayList<double[]> a : fourthtubes){ 
				for(double[] b : a){
					
					if (b[5] ==frameNum){ //if this is your frame
						
						 if (frameNum == 14){
							 System.out.println("BLAH");
						 }
						
						//Make new ellipse with (x0, y0, a, b, rotation)
						ellipse = new EllipseRotated_F64(b[0], b[1], b[2], b[3], b[4]);
						g2.setColor(new Color((float) b[8],(float) b[9], (float) b[10]));
						g2.setStroke(new BasicStroke(3));  //thick tubes
						VisualizeShapes.drawEllipse(ellipse, g2);
						
						//Indicate where this ellipse came from
						//g2.setColor(Color.WHITE);
						//g2.drawString(Integer.toString(fourthtubes.indexOf(a)), (int) b[0],(int) b[1]);
						
					}
					
				}
			}
			
			mask.ProcessFrame(newFrame);
			gui.setBufferedImage(newFrame);
			gui.repaint();
			/*try {
				Thread.sleep(200);
			} catch (InterruptedException e) {

			}*/
			

			frameNum++;
		}
		mask.Close();
		sequence.Close();
		
		//-------------------END OF GRAPHICS STUFF-------------------------------------------------
		
		
	}

	/* This function writes out any ArrayList of ArrayLists of doubles out in a text file
	 * Each inner ArrayList is separated by "Tube: XX"
	 */
	public void tubeWriter(ArrayList<ArrayList<double[]>> tubes, String textToGo) {
		
		
		try {
			PrintWriter writer = new PrintWriter(textToGo, "UTF-8");

			
			for(ArrayList<double[]> tube: tubes){
				for (double[] tubeslice: tube){
					
					double[] newslice = new double[12];
					
					for(int i=0; i<=tubeslice.length-1;i++){ //Copy over tubeslice
						newslice[i] = tubeslice[i];
					}
					newslice[11] =  (double) tubes.indexOf(tube); //and add the tube # inside
					
					for(int i=0;i<=newslice.length-1;i++){
						newslice[i] = (double) Math.round(newslice[i] * 1000d) / 1000d ;
					}
				
						
					if (!((newslice[0] ==0) && (newslice[1]==0))){
						writer.println(Array.toString(newslice));
					}
					
				}
			}
			
			writer.close();

		} catch (IOException e) {
			//Blah
		}
		
	}
	
	
	@SuppressWarnings({ "unchecked", "unused", "deprecation" })
	public String makeMagno(String stemLoc, String vidLoc) {
		//TIMER STUFF
		long startTime;
		long endTime;
		double totalMagnoTime =0;
		
		startTime = System.nanoTime();

		VideoFrameReader sequence = new VideoFrameReader(vidLoc);
		sequence.OpenFile(); 
		int height = sequence.getFrameHeight();
		int width = sequence.getFrameWidth(); 
		

		
		ArrayList<BufferedImage> frames = new ArrayList<BufferedImage>();
		VariableRetina retina = new VariableRetina(width, height, true); //create retina
		
		BufferedImage inFrame = sequence.NextFrame();
		int frameNum=2;
		while((inFrame = sequence.NextFrame())!=null) { //Fill up 'frames' with magnos
		
			
			
			
			retina.ProcessFrame(RGBModel.maxContrast(inFrame));
			BufferedImage magFrame = retina.getMagno(); //currFrame = magno version
			
			
			frames.add(magFrame);
			//
			
			//SHOWS FRAME NUMBER
			//g2.setColor(Color.white);
			//g2.drawString(Integer.toString(frameNum), 20, 20);
			//g2.setStroke(new BasicStroke(3));
			
		}
		
		sequence.Close();
		
		
		VideoFrameWriter mask = new VideoFrameWriter(new File(stemLoc), width, height, 30); 
		mask.OpenFile(); //open videowriter

		for(BufferedImage currFrame: frames){
			
			
			
			mask.ProcessFrame(currFrame);
			Graphics2D g2 = currFrame.createGraphics();
			
			//SHOWS FRAME NUMBER
			g2.setColor(Color.white);
			g2.drawString(Integer.toString(frameNum), 20, 20);
			g2.setStroke(new BasicStroke(3));
			frameNum++;
		}
			
		mask.Close();
		endTime = System.nanoTime();
		totalMagnoTime += endTime - startTime;
		System.out.println("Time in seconds: " +  Integer.toString((int) Math.round(totalMagnoTime/1e9)));
		
		return stemLoc;
		

	}
	
	
	@SuppressWarnings({ "unchecked", "unused", "deprecation" })
	public String makeFakeMagno(String stemLoc, String vidLoc) {
		//TIMER STUFF
		long startTime;
		long endTime;
		double totalMagnoTime =0;

		startTime = System.nanoTime();

		VideoFrameReader sequence = new VideoFrameReader(vidLoc);
		sequence.OpenFile(); 
		int height = sequence.getFrameHeight();
		int width = sequence.getFrameWidth(); 
		
	
		
		ArrayList<int[][]> grayFrames = new ArrayList<int[][]>();
		ImageStack diffstack = new ImageStack(width,height);
		ImageStack dilatedstack= new ImageStack(width,height);
		ImageStack finalstack = new ImageStack(width,height);
		
		//VariableRetina retina = new VariableRetina(width, height, true); //create retina
		
		BufferedImage inFrame = sequence.NextFrame();
		int frameNum=2;
		while((inFrame = sequence.NextFrame())!=null) { //Fill up 'frames' with grayscales
		
			
			
			
			byte[][] gray = ImageUtils.BufferedImage2Grayscale(inFrame);
			int[][] G= Mat.Unsigned2Int(gray);
			grayFrames.add(G);
			

			
			
			
		
			
			//SHOWS FRAME NUMBER
			//g2.setColor(Color.white);
			//g2.drawString(Integer.toString(frameNum), 20, 20);
			//g2.setStroke(new BasicStroke(3));
			
		}
		
		for (int[][] gf : grayFrames){
			startTime = System.nanoTime();
			ByteProcessor B;
			int[][] diff = new int[width][height];
			if (grayFrames.indexOf(gf)==0){
				BufferedImage b = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
				//b = ImageUtils.Grayscale2SingleChannelBufferedImage(gf);
				B = new ByteProcessor(b);
			}
			else{
				int[][] old = grayFrames.get(grayFrames.indexOf(gf) -1);
				for(int i = 0; i < width  ; i++){
					for(int j = 0; j < height ; j++){
						int num = gf[i][j] - old[i][j];
						if(num <=10) diff[i][j] = 0;
						else diff[i][j] = 255;
					}
				}
				BufferedImage b = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
				//WritableRaster raster = (WritableRaster) b.getData();
				//raster.setPixels(0,0,width,height,diff);
				b = ImageUtils.Grayscale2BufferedImage(diff, 255);
				B = new ByteProcessor(b);
			}
			
			//System.out.println(B.isBinary());
			diffstack.addSlice(B);
			endTime = System.nanoTime();
			totalMagnoTime += endTime - startTime;
			
		}
		
		sequence.Close();
		
		startTime = System.nanoTime();
		
		Strel se = SquareStrel.fromDiameter(7);
		
		for(int i = 0; i < diffstack.size(); i++){
			ImageProcessor slice = diffstack.getProcessor(i+1);
			ImageProcessor dilated = Morphology.dilation(slice, se);
			dilatedstack.addSlice(dilated);
		} 
		
		Strel3D se3 = CubeStrel.fromDiameter(5);
		dilatedstack = Morphology.erosion(dilatedstack, se3);  //3DErode stack
		
		for(int i = 0; i < dilatedstack.size(); i++){
			ImageProcessor slice =  dilatedstack.getProcessor(i+1);
			ImageProcessor dilated = Morphology.dilation(slice, se);
			finalstack.addSlice(dilated);
		}
		
		endTime = System.nanoTime();
		totalMagnoTime += endTime - startTime;
		
		VideoFrameWriter mask = new VideoFrameWriter(new File(stemLoc), width, height, 30); 
		mask.OpenFile(); //open videowriter

		frameNum=2;
		for(int i = 0; i < finalstack.size() -1; i++){
			
			BufferedImage frameToShow = finalstack.getProcessor(i+1).getBufferedImage();
			Graphics2D g2 = frameToShow.createGraphics();
			
			//SHOWS FRAME NUMBER
			g2.setColor(Color.white);
			g2.drawString(Integer.toString(frameNum), 20, 20);
			g2.setStroke(new BasicStroke(3));
			frameNum++;
			
			mask.ProcessFrame(frameToShow);
			
		}
		
	/*	for(FloatProcessor currFrame: diffstack){
			
			
			
			mask.ProcessFrame(currFrame);
			Graphics2D g2 = currFrame.createGraphics();
			
			//SHOWS FRAME NUMBER
			g2.setColor(Color.white);
			g2.drawString(Integer.toString(frameNum), 20, 20);
			g2.setStroke(new BasicStroke(3));
			frameNum++;
		}
		*/
			
		mask.Close();
		endTime = System.nanoTime();
		totalMagnoTime += endTime - startTime;
		System.out.println("Time in seconds: " +  Integer.toString((int) Math.round(totalMagnoTime/1e9)));
		
		return stemLoc;
		

	}
	
	
	@SuppressWarnings({ "unchecked", "unused", "deprecation" })
	public String makeThreshMagno(String stemLoc, String vidLoc) {
		//TIMER STUFF
		long startTime;
		long endTime;

		VideoFrameReader sequence = new VideoFrameReader(vidLoc);
		sequence.OpenFile(); 
		int height = sequence.getFrameHeight();
		int width = sequence.getFrameWidth(); 

		
		
		ArrayList<BufferedImage> frames = new ArrayList<BufferedImage>();
		VariableRetina retina = new VariableRetina(width, height, true); //create retina
		
		/*
		BufferedImage currFrame = retina.getMagno(); //currFrame = magno version
		
		byte grayscale[][] = ImageUtils.BufferedImage2Grayscale(currFrame);
		int gray[][] = Mat.Unsigned2Int(grayscale); //The frame is now an 2D int matrix
		
		int unthresholded[][] = gray;
		
		
		//The following loop thresholds our 2D matrix of ints (<100 -> 0) (>100 -> 255) 
		for(int i = 0; i < gray.length; i++) {
			for(int j = 0; j < gray[i].length; j++) {

				if(gray[i][j] < 100) gray[i][j] = 0;
				else gray[i][j] = 255;

			}
		}
		
		
		grayscale = Mat.Int2Unsigned(gray); //grayscale is the unsigned version of gray 
		
		BufferedImage redoneFrame = ImageUtils.Grayscale2BufferedImage(grayscale, 255);
		GrayF32 input = ConvertBufferedImage.convertFromSingle(redoneFrame, null, GrayF32.class); //More conversions
 
		GrayU8 binary = new GrayU8(input.width,input.height);
 
		// the mean pixel value is often a reasonable threshold when creating a binary image
		double mean = ImageStatistics.mean(input);
 
		// create a binary image by thresholding (binary is the output)
		// Values <= mean go to 0.  Values > mean go to 1.
		ThresholdImageOps.threshold(input, binary, (float) mean, false);
 
		// reduce noise with some filtering (null = no output)
		GrayU8 filtered = BinaryImageOps.erode8(binary, 1, null);
		filtered = BinaryImageOps.dilate8(filtered, 1, null);
 
		GrayS32 blobs = new GrayS32(filtered.width, filtered.height);
		GrayU8 blobs2 = new GrayU8(filtered.width, filtered.height);
		BufferedImage blobs3 = new BufferedImage(filtered.width,filtered.height,newFrame.getType());
		
		*/
		
		BufferedImage inFrame = sequence.NextFrame();
		int frameNum=2;
		while((inFrame = sequence.NextFrame())!=null) { //Fill up 'frames' with magnos

			retina.ProcessFrame(RGBModel.maxContrast(inFrame));
			BufferedImage magFrame = retina.getMagno(); //currFrame = magno version
			
			byte grayscale[][] = ImageUtils.BufferedImage2Grayscale(magFrame);
			int gray[][] = Mat.Unsigned2Int(grayscale); //The frame is now an 2D int matrix
		
			//The following loop thresholds our 2D matrix of ints (<100 -> 0) (>100 -> 255) 
			for(int i = 0; i < gray.length; i++) {
				for(int j = 0; j < gray[i].length; j++) {

					if(gray[i][j] < 100) gray[i][j] = 0;
					else gray[i][j] = 255;

				}
			}
			grayscale = Mat.Int2Unsigned(gray); //grayscale is the unsigned version of gray 
			BufferedImage redoneFrame = ImageUtils.Grayscale2BufferedImage(grayscale, 255);
			
			
			
			
			frames.add(redoneFrame);
			//
			

			
		}
		
		sequence.Close();
		
		
		VideoFrameWriter mask = new VideoFrameWriter(new File(stemLoc), width, height, 30); 
		mask.OpenFile(); //open videowriter

		for(BufferedImage currFrame: frames){
			
			
			
			mask.ProcessFrame(currFrame);
			Graphics2D g2 = currFrame.createGraphics();
			
			//SHOWS FRAME NUMBER
			g2.setColor(Color.white);
			g2.drawString(Integer.toString(frameNum), 20, 20);
			g2.setStroke(new BasicStroke(3));
			frameNum++;
		}
			
		mask.Close();
		
		
		
		return stemLoc;
		

	}
	
	
	
	public void addFrameNum(String stemLoc, String vidLoc){
		

		VideoFrameReader sequence = new VideoFrameReader(vidLoc);
		sequence.OpenFile();
		
		int height = sequence.getFrameHeight();
		int width = sequence.getFrameWidth(); 

		
		VideoFrameWriter mask = new VideoFrameWriter(new File(stemLoc), width, height, 30); 
		
		mask.OpenFile(); //open videowriter
		
		//This part shows the video on screen (maybe not necessary)
		//gui.setPreferredSize(new Dimension(width, height));
		//ShowImages.showWindow(gui,CatGetter.extract(vidLoc), true);
		
		BufferedImage newFrame = sequence.NextFrame();
		int frameNum=2;
		while((newFrame = sequence.NextFrame())!=null) {
			
			Graphics2D g2 = newFrame.createGraphics(); 
			
			g2.setColor(Color.white);
			g2.drawString(Integer.toString(frameNum), 20, 20);
			g2.setStroke(new BasicStroke(3));
						
			mask.ProcessFrame(newFrame);
			//gui.setBufferedImage(newFrame);
			//gui.repaint();

			frameNum++;
		}
		mask.Close();
		sequence.Close();
		
	}
	
	@SuppressWarnings({ "unchecked", "unchecked" })
	public void topDownMagno(ArrayList<ArrayList<double[]>> tubes, String stemLoc, String vidLoc, String inLoc){ //vidLoc = magno video
		
		/*
		//Get rid of small BS tubes (under 30 frames)
		ArrayList<ArrayList<double[]>> toRemove = new ArrayList<ArrayList<double[]>>();
		for(ArrayList<double[]> T: tubes){
			if(T.size() <= 30){
				toRemove.add(T);
			}
		}
		tubes.removeAll(toRemove);
		*/
		

		
			
		VideoFrameReader magreader = new VideoFrameReader(vidLoc);
		magreader.OpenFile();
		int height = magreader.getFrameHeight();
		int width = magreader.getFrameWidth(); 
				
		BufferedImage newFrame = magreader.NextFrame();
		int frameNum=2;
		ArrayList<Integer> mehlist = new ArrayList<Integer>();
		while((newFrame = magreader.NextFrame())!=null) { //Scan magno for shitty frames
			
			GrayF32 input = ConvertBufferedImage.convertFromSingle(newFrame, null, GrayF32.class);
			
			
			//In lieu of a median... Check out the histogram... If there's less 0-4 than everything else... it's bad
			int[] hist = new int[width*height];
			ImageStatistics.histogram(input,(int) ImageStatistics.min(input), hist);
			if (IntStream.of(Arrays.copyOfRange(hist, 0, 5)).sum() < IntStream.of(Arrays.copyOfRange(hist, 6, hist.length)).sum()){
				mehlist.add(frameNum);
			}
			frameNum++;

		}
		magreader.Close();
		
		//PUT NUMBER OF TUBES PER FRAME IN A HASHMAP
		int tpf;
		HashMap<Integer, Integer> tubesPerFrame = new HashMap<Integer, Integer>();
		for(int f = 1; f < frameNum; f++){
			tpf = 0;
			
			//Calculate number of tubes in that frame (f)
			for (ArrayList<double[]> T: tubes){
				for (double[] slice: T){
					
					if ((int) slice[5] == f){
						tpf++;
						break;
					}
				}
			}
			tubesPerFrame.put(f, tpf);
		}
		//Okay done.
		
		
		ArrayList<Integer> badframelist = new ArrayList<Integer>(); 
		for(int frame: mehlist){
			if ( (mehlist.contains(frame-1) && mehlist.contains(frame+1)) && (frame > 25) ){
				badframelist.add(frame);
			}
		} 		
		//NOW BADFRAMELIST IS THE LIST OF MAGNO FRAMES WE CAN'T TRUST
		

		
		//SPLITS INTO CHUNKS OF BAD FRAMES
		Integer[] badframearray = new Integer[badframelist.size()];
		badframearray = badframelist.toArray(badframearray);
		ArrayList<Integer[]> badframechunks = new ArrayList<Integer[]>();
		int i = 0;
		
		while(i < badframearray.length){
			int start=i;
			if (i+1 <= badframearray.length-1) {
				while((badframearray[i+1] - badframearray[i] < 20) && (i <= badframearray.length-1)){
					//keep going
					i++;
					if (i+1 >= badframearray.length) break;
				}
			}
			badframechunks.add(Arrays.copyOfRange(badframearray, start, i+1));
			i++;
		}
		
		ArrayList<Integer[]> chunksToRemove = new ArrayList<Integer[]>();
		
		for (Integer[] chunk: badframechunks){ //Get rid of small ass chunks
			if(chunk.length < 5) chunksToRemove.add(chunk);
		}
		badframechunks.removeAll(chunksToRemove);
		//Okay done.
		
		//REMOVE TUBESLICES IN THOSE FRAMES
		ArrayList<double[]> toRemove;
		for(ArrayList<double[]> T : tubes){
			toRemove = new ArrayList<double[]>();
			
			for (double[] slice: T){
				
				for( Integer[] chunk: badframechunks){
					if (ArrayUtils.contains(chunk, (int) slice[5])){
						toRemove.add(slice);
					}
				}
			}
			
			T.removeAll(toRemove);
		}
		//Okay done.

		ArrayList<ArrayList<double[]>> tubesToRemove = new ArrayList<ArrayList<double[]>>();
		//Get rid of empty tubes now
		for(ArrayList<double[]> T: tubes){
			if (T.isEmpty()) tubesToRemove.add(T);
		}
		tubes.removeAll(tubesToRemove);
		
		
		
		
		//Go thru tubes and recalc velocity at every 15
		for(ArrayList<double[]> T: tubes){
			int jump = 15;
			double[] next;
			double[] start = new double[11];
			
			if(!T.isEmpty())  start = T.get(0);
			else continue;

			while(T.indexOf(start)+ jump < T.size()-1 ){
				
				next = T.get(T.indexOf(start) + jump);
				double Vx = (next[0] - start[0])/ jump;
				double Vy = (next[1] - start[1])/ jump;
				for(int k = T.indexOf(start); k < T.indexOf(next); k++){
					T.get(k)[6] = Vx;
					T.get(k)[7] = Vy;
				}
				start = next;
				
			}
		}
		
		
		
		ArrayList<ArrayList<double[]>> faketubes = new ArrayList<ArrayList<double[]>>(tubes); //make a copy

		//NOW IN BETWEEN ALL THE BAD FRAMES...
		for(Integer[] BFchunk: badframechunks){
		
			//Remove any in the window with too many tubes
			int framegap = 20;
			int startframe;
			int endframe;
			if(BFchunk[BFchunk.length-1] + framegap> frameNum) endframe = frameNum - 1;
			else endframe = BFchunk[BFchunk.length-1] + framegap;
			
			if(BFchunk[0]==26) startframe = 26;
			else startframe = BFchunk[0] - framegap;
			
			//Backtrack and get rid of frames w/ tons of BS tubes
			for(int f =startframe; f < endframe; f++){
				
				if (f<25) continue;
				if (badframelist.contains(f)) continue;
				
				if(tubesPerFrame.get(f) > 10){ //if >5/10 tubes in that frame...
					
					for(ArrayList<double[]> T : tubes){
						toRemove = new ArrayList<double[]>();
						
						for (double[] slice: T){
							if(f == (int) slice[5]){
								toRemove.add(slice);
							}
						}
						T.removeAll(toRemove);
					}
				}
			}
			
			ArrayList<ArrayList<double[]>> tubesatSF = new ArrayList<ArrayList<double[]>>();
			
			//Find what exists at startframe --> tubesatSF
			for (ArrayList<double[]> T: tubes){
				for(double[] slice: T){
					if(((int) slice[5] ==startframe) && (T.size() > 30)){ 
						tubesatSF.add(T);
						break;
					}
				}
			}
			
			tubes.removeAll(tubesatSF);
			
			double Vxavg=0;
			double Vyavg=0;
			
			for (ArrayList<double[]> T: tubesatSF){
				double[] startslice = new double[11];
				
				for(double[] slice: T){ //Get the info from startslice
					if((int) slice[5]==startframe){
						startslice = slice;
						//Vxavg += slice[6];
						//Vyavg += slice[7];
						//NEED TO CHECK IF THERE ARE 5 SLICES BEFORE 'SLICE' IN T
						Vxavg = (T.get(T.indexOf(slice)-1)[6] + T.get(T.indexOf(slice)-2)[6] + T.get(T.indexOf(slice)-3)[6] + T.get(T.indexOf(slice)-4)[6] + T.get(T.indexOf(slice)-5)[6]) /5;
						Vyavg = (T.get(T.indexOf(slice)-1)[7] + T.get(T.indexOf(slice)-2)[7] + T.get(T.indexOf(slice)-3)[7] + T.get(T.indexOf(slice)-4)[7] + T.get(T.indexOf(slice)-5)[7]) /5;		
					}
				}
				//Vxavg = Vxavg/ T.size();
				//Vyavg = Vyavg/ T.size();
				
				//Predict "final" location (at endframe)
				double Xf = startslice[6]* (endframe-startframe) + startslice[0]; //get loc
				double Yf = startslice[7]* (endframe-startframe) + startslice[1];
				
				double[] moneyslice = new double[11];
				ArrayList<double[]> moneytube = new ArrayList<double[]>();
				double r0 = 999999;
				double r;
				
				for (ArrayList<double[]> tube: tubes){ //Find slice closest to pred --> moneyslice
					for(double[] tubeslice: tube){
						if((int) tubeslice[5] == endframe){
							
							r = Math.sqrt( Math.pow(tubeslice[0] -Xf, 2) + Math.pow(tubeslice[1] - Yf, 2));
							if (r< r0){
								moneyslice = tubeslice;
								moneytube = tube;
								r0 = r;
							}
						}
					}
				}
				
				if (r0< 100){ //IF WE MATCH IT TO SOMETHING AT THE END
					for(int f =startframe+1; f < endframe; f++){ //for every inbetween frame... fill it in
						double framediff = moneyslice[5] - startslice[5];
						double a = ((moneyslice[2] - startslice[2])/framediff)*(f - startframe) + startslice[2];
						double b = ((moneyslice[3] - startslice[3])/framediff)*(f-startframe) + startslice[3];
						double Vx = (moneyslice[0] - startslice[0])/framediff;
						double Vy = (moneyslice[1] - startslice[1])/framediff;
						double X = Vx * (f-startframe) + startslice[0];
						double Y = Vy * (f-startframe) + startslice[1];
						
						
						/*for(double[] oldslice : T){
							if((int) oldslice[5] == f) sliceToRemove.add(oldslice);
						}
						T.removeAll(sliceToRemove);
						*/
						double[] info = {X, Y, a, b, startslice[4], f, Vx, Vy, startslice[8], startslice[9] ,startslice[10]}; 
						T.add(info);
					}
					for(double[] slice : moneytube){ //change the color to match
						slice[8] = startslice[8];
						slice[9] = startslice[9];
						slice[10] = startslice[10];
					}
					T.addAll(moneytube);
					tubes.remove(moneytube);
					Collections.sort(T,new FrameComparator());
					
					ArrayList<double[]> sliceToRemove = new ArrayList<double[]>();
					for (double[] slice: T){
						if(T.indexOf(slice)+1 < T.size()-1){
							double[] next = T.get(T.indexOf(slice)+1);
							if(next[5] == slice[5]){
								if (next[2]+next[3] > slice[2]+slice[3]) sliceToRemove.add(next);
								else sliceToRemove.add(slice);
							}
						}
					}
					T.removeAll(sliceToRemove);
				}
				else{
					for(int f =startframe+1; f < endframe; f++){ //for every inbetween frame
						double X = startslice[6]* (f-startframe) + startslice[0]; //get loc
						double Y = startslice[7]* (f-startframe) + startslice[1];
						
						double[] info = {X, Y, startslice[2], startslice[3], startslice[4], f, Vxavg, Vyavg, startslice[8], startslice[9], startslice[10]}; //red for now
						T.add(info);
						
					}
					
				}
			}

			tubes.addAll(tubesatSF);
		
		}
		
		
		
		//---------------------------GRAPHICS AND SUCH--------------------------------------
		VideoFrameReader sequence = new VideoFrameReader(inLoc);
		sequence.OpenFile();
		VideoFrameWriter mask = new VideoFrameWriter(new File(stemLoc), width, height, 30); 
		mask.OpenFile(); //open videowriter
		
		newFrame = sequence.NextFrame();
		frameNum=2;
		EllipseRotated_F64 ellipse;

		while((newFrame = sequence.NextFrame())!=null) { //Show tubes over magno
			
			Graphics2D g2 = newFrame.createGraphics(); 
			
			//Put frame number up top
			g2.setColor(Color.white);
			g2.drawString(Integer.toString(frameNum), 20, 20);
			g2.setStroke(new BasicStroke(3));
			
			//Throw some tubes down
			for(ArrayList<double[]> a : tubes){ 
				for(double[] b : a){
					
					if (b[5] ==frameNum){ //if this is your frame
						
						//Make new ellipse with (x0, y0, a, b, rotation)
						ellipse = new EllipseRotated_F64(b[0], b[1], b[2], b[3], b[4]);
						g2.setColor(new Color((float) b[8],(float) b[9], (float) b[10]));
						g2.setStroke(new BasicStroke(3));  //thick tubes
						VisualizeShapes.drawEllipse(ellipse, g2);
						
						//Indicate where this ellipse came from
						g2.setColor(Color.WHITE);
						g2.drawString(Integer.toString(tubes.indexOf(a)), (int) b[0],(int) b[1]);
					}
				}
			}
			mask.ProcessFrame(newFrame);
			frameNum++;
		}
		mask.Close();
		sequence.Close();
		

		
		
		
		
	/*	
		
		BufferedImage newFrame = magreader.NextFrame();
		int frameNum=2;
		EllipseRotated_F64 ellipse;
		while((newFrame = magreader.NextFrame())!=null) {
			
			
			
			GrayF32 input = ConvertBufferedImage.convertFromSingle(newFrame, null, GrayF32.class);
						
			//Put sum number in middle
			g2.setColor(Color.white);
			g2.drawString(Integer.toString((int) ImageStatistics.sum(input)), 20, 200);
			g2.setStroke(new BasicStroke(3));
			
			//Put mean number below
			g2.setColor(Color.white);
			g2.drawString(Integer.toString((int)ImageStatistics.mean(input)), 20, 300);
			g2.setStroke(new BasicStroke(3));
			
			
			
			//Throw some tubes down
			for(ArrayList<double[]> a : tubes){ 
				for(double[] b : a){
					
					if (b[5] ==frameNum){ //if this is your frame
						
						//Make new ellipse with (x0, y0, a, b, rotation)
						ellipse = new EllipseRotated_F64(b[0], b[1], b[2], b[3], b[4]);
						g2.setColor(new Color((float) b[8],(float) b[9], (float) b[10]));
						g2.setStroke(new BasicStroke(3));  //thick tubes
						VisualizeShapes.drawEllipse(ellipse, g2);
						
						//Indicate where this ellipse came from
						//g2.setColor(Color.WHITE);
						//g2.drawString(Integer.toString(fourthtubes.indexOf(a)), (int) b[0],(int) b[1]);
					}
				}
			}
			
			
			mask.ProcessFrame(newFrame);
			//gui.setBufferedImage(newFrame);
			//gui.repaint();

			frameNum++;
		}
		mask.Close();
		magreader.Close();
		*/
		
		
	}
	
	public void magnoTest(String stemLoc, String vidLoc){
		VideoFrameReader sequence = new VideoFrameReader(vidLoc);
		
		
		sequence.OpenFile();
		int height = sequence.getFrameHeight();
		int width = sequence.getFrameWidth(); 
		
		VideoFrameWriter mask = new VideoFrameWriter(new File(stemLoc), width, height, 30); 
		
		//VideoFrameWriter mask = new VideoFrameWriter(new File(stemLoc), width, height, 30); 
		VariableRetina retina = new VariableRetina(width, height, false); //create retina

		
		mask.OpenFile(); //open videowriter
		
		//This part shows the video on screen
		//gui.setPreferredSize(new Dimension(width, height));
		//ShowImages.showWindow(gui,CatGetter.extract(vidLoc), true);
		
		
		//T frame;
		int frameNum = 0;
		
		ArrayList<int[][]> magnoFrames = new ArrayList<int[][]>();
		
		BufferedImage newFrame = null;
		while ((newFrame = sequence.NextFrame()) != null) {
		

			//BufferedImage blank = new BufferedImage(newFrame.getWidth(),newFrame.getHeight(), newFrame.getType());
			
			retina.ProcessFrame(RGBModel.maxContrast(newFrame)); //Gotta do this first
			
			BufferedImage currFrame = retina.getMagno(); //currFrame = magno version
			
						
			byte grayscale[][] = ImageUtils.BufferedImage2Grayscale(currFrame);
			int gray[][] = Mat.Unsigned2Int(grayscale); //The frame is now an 2D int matrix
			
			magnoFrames.add(gray);
			
		}
		
		
		ArrayList<int[][]> newFrames= new ArrayList<int[][]>();
	/*	for (int[][] slice: magnoFrames){
			newFrames.add(slice.clone());
		}
	*/
		
		//GOTTA FIGURE OUT A WAY TO GET THESE SHITS REFERRING TO 2 DIFF THINGS!
		//Maybe try starting the new one from scratch and adding them in one at a time

		for(int[][] slice: magnoFrames){
			
			if(magnoFrames.indexOf(slice) <= 5) continue; //skip first 5
			if(magnoFrames.indexOf(slice) >= magnoFrames.size() -1) continue;
			
			for(int i = 0; i < slice.length; i++) {
				for(int j = 0; j < slice[i].length; j++) {
					boolean litFlag = false;
					
					if(magnoFrames.get(magnoFrames.indexOf(slice) -1)[i][j] < 10)  litFlag=true;
					if(magnoFrames.get(magnoFrames.indexOf(slice) +1)[i][j] < 10)  litFlag=true;
					
					if(!litFlag) slice[i][j] = 255;
					if(slice[i][j] < 150) slice[i][j] =0;
					

				}
			}
			newFrames.add(slice);
		}
		
		
		/*
		//This loop does some time filtering (so to speak)
		for (int f = 5; f < magnoFrames.size(); f++) {
			
			int[][] thisFrame = magnoFrames.get(f);
			int[][] thatFrame = newFrames.get(f);
			
			//The following loop thresholds our 2D matrix of ints (<100 -> 0) (>100 -> 255) 
			for(int i = 0; i < thisFrame.length; i++) {
				for(int j = 0; j < thisFrame[i].length; j++) {
					boolean litFlag = true;
					
					for(int frame = f-1; frame < f+1; frame++){
						if (frame >= magnoFrames.size()) continue;
						if (magnoFrames.get(frame)[i][j] < 150 ) litFlag=false;
					}
					
					if(litFlag) thatFrame[i][j] = 255;
					else thatFrame[i][j] = 0;

				}
			}
		}

*/
			
		//Convert and show	
		for(int[][] gray: newFrames){
		
			
			byte[][] grayscale = Mat.Int2Unsigned(gray); //grayscale is the unsigned version of gray 
			
			BufferedImage redoneFrame = ImageUtils.Grayscale2BufferedImage(grayscale, 255);
			GrayF32 input = ConvertBufferedImage.convertFromSingle(redoneFrame, null, GrayF32.class); //More conversions
	 
			GrayU8 binary = new GrayU8(input.width,input.height);


			
			BufferedImage outFrame = new BufferedImage(binary.width,binary.height,5);

			outFrame = ConvertBufferedImage.convertTo(binary, outFrame);
			mask.ProcessFrame(redoneFrame);
		}
		mask.Close();
		sequence.Close();


	}
	
	@SuppressWarnings("unchecked")
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
		
		VideoFrameWriter mask = new VideoFrameWriter(new File(CatGetter.process(vidLoc)), width, height, 10);
		VideoRetina retina = new VideoRetina(width, height, false);
		
		gui.setPreferredSize(new Dimension(width, height));
		ShowImages.showWindow(gui,CatGetter.extract(vidLoc), true);
		
		mask.OpenFile();

		
		// Figure out how large the GUI window should be
		T frame = sequence.next();
		
		// process each frame in the image sequence
		while( sequence.hasNext() ) {
			frame = sequence.next();
			BufferedImage newFrame = sequence.getGuiImage();
			
			retina.ProcessFrame(newFrame);
			
			BufferedImage currFrame = retina.getMagno();
					
			byte grayscale[][] = ImageUtils.BufferedImage2Grayscale(RGBModel.maxContrast(currFrame));
			int gray[][] = Mat.Unsigned2Int(grayscale);
			for(int i = 0; i < gray.length; i++) {
				for(int j = 0; j < gray[i].length; j++) {
					if(gray[i][j] < 100) gray[i][j] = 255; //usually 100
					else gray[i][j] = 0;
				}
			}
//			
			grayscale = Mat.Int2Unsigned(gray);
			
			BufferedImage redoneFrame = ImageUtils.Grayscale2BufferedImage(grayscale, 255);

			GrayF32 input = ConvertBufferedImage.convertFromSingle(redoneFrame, null, GrayF32.class);
	 
			GrayU8 binary = new GrayU8(input.width,input.height);
	 
			// the mean pixel value is often a reasonable threshold when creating a binary image
			double mean = ImageStatistics.mean(input);
	 
			// create a binary image by thresholding
			ThresholdImageOps.threshold(input, binary, (float) mean, true);
	 
			// reduce noise with some filtering
			GrayU8 filtered = BinaryImageOps.erode8(binary, 1, null);
			filtered = BinaryImageOps.dilate8(filtered, 1, null);
	 
			// Find the contour around the shapes
			List<Contour> contours = BinaryImageOps.contour(filtered, ConnectRule.FOUR,null);
	 			
			// Fit an ellipse to each external contour and draw the results
			
			
			
			Graphics2D g2 = newFrame.createGraphics();

			g2.setStroke(new BasicStroke(3));
//	 		
			ArrayList<Rectangle2D_I32> boxes = new ArrayList<Rectangle2D_I32>();
			
			for( Contour c : contours ) {
				FitData<EllipseRotated_F64> ellipse = ShapeFittingOps.fitEllipse_I32(c.external,0,false,null);
				int boxsize = Integer.max((int) ellipse.shape.a, (int) ellipse.shape.b); //distance from center to perpendicular edge
				Rectangle2D_I32 box = new Rectangle2D_I32((int) ellipse.shape.center.x - boxsize, (int) ellipse.shape.center.y - boxsize, (int) ellipse.shape.center.x + boxsize, (int) ellipse.shape.center.y + boxsize);
				boxes.add(box);
//				VisualizeShapes.drawRectangle(box, g2);
			}

			BoxKLTDetector det = (BoxKLTDetector) tracker;
			BoxPointTrackerKltPyramid innertrack = (BoxPointTrackerKltPyramid) det.tracker;
			innertrack.setBoxes(boxes);
			tracker.process(frame);
//			tracker.process(retina.getParvo());
			ArrayList<FeaturePosition> feats = tracker.getFrameFeatures();
			for(FeaturePosition ft: feats) {
				VisualizeFeatures.drawPoint(g2, (int) ft.x, (int) ft.y, Color.GREEN);
			}
			
			g2.setColor(Color.RED);
			for(Rectangle2D_I32 r : boxes) {
				VisualizeShapes.drawRectangle(r, g2);
			}

			g2.setColor(Color.WHITE);
			g2.setFont(new Font("TimesRoman", Font.BOLD, 20));
			g2.drawString("Frame: " + frames + ", Features: " + feats.size(), newFrame.getWidth()- 600, newFrame.getHeight()-50);
			
			mask.ProcessFrame(newFrame);
			gui.setBufferedImage(newFrame);	
			gui.repaint();
			
			currentFrames.add(new SimpleFrame(frames, feats));
			local.localFrames.add(new SimpleFrame(localframe, feats));
			
			//update active feature information: number of appearances and times of those appearances
			tracker.updateFeatApps(featapps);
			tracker.updateFeatApps(local.featapps);
			
			//store 64 dim float information, if it had not been stored already
			//wrappers needed to access what were private variables in the original library classes
			
			tracker.updateFeatSURF(featSURF);
			tracker.updateFeatSURF(local.featSURF);

			System.out.println("Processed frame " + frames);
			frames++;
			
			
		}
		
		framesInEachVideo.add(localframe);
		
		mask.Close();
		FileIO.SaveObject(new File(CatGetter.stem(vidLoc)), local);

		return;
	}
	
	@SuppressWarnings("unchecked")
	public void boundprocess(String vidLoc) {
		
		bs = new BoxStreak();
		
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
		
		VideoFrameWriter mask = new VideoFrameWriter(new File(CatGetter.process(vidLoc)), width, height, 10);
		VideoRetina retina = new VideoRetina(width, height, false);
		
		gui.setPreferredSize(new Dimension(width, height));
		ShowImages.showWindow(gui,CatGetter.extract(vidLoc), true);
		
		mask.OpenFile();

		
		// Figure out how large the GUI window should be
		T frame = sequence.next();
		
		// process each frame in the image sequence
		while( sequence.hasNext() ) {
			frame = sequence.next();
			BufferedImage newFrame = sequence.getGuiImage();
			
			retina.ProcessFrame(RGBModel.maxContrast(newFrame));
			
			BufferedImage currFrame = retina.getMagno();
					
			byte grayscale[][] = ImageUtils.BufferedImage2Grayscale(currFrame);
			int gray[][] = Mat.Unsigned2Int(grayscale);
			for(int i = 0; i < gray.length; i++) {
				for(int j = 0; j < gray[i].length; j++) {
					if(gray[i][j] < 130) gray[i][j] = 255; // was 130 pacman
					else gray[i][j] = 0;
				}
			}
//			
			grayscale = Mat.Int2Unsigned(gray);
			
			BufferedImage redoneFrame = ImageUtils.Grayscale2BufferedImage(grayscale, 255);

			GrayF32 input = ConvertBufferedImage.convertFromSingle(redoneFrame, null, GrayF32.class);
	 
			GrayU8 binary = new GrayU8(input.width,input.height);
	 
			// the mean pixel value is often a reasonable threshold when creating a binary image
			double mean = ImageStatistics.mean(input);
	 
			// create a binary image by thresholding
			ThresholdImageOps.threshold(input, binary, (float) mean, true);
	 
			// reduce noise with some filtering
			GrayU8 filtered = BinaryImageOps.erode8(binary, 1, null);
			filtered = BinaryImageOps.dilate8(filtered, 1, null);
	 
			// Find the contour around the shapes
			List<Contour> contours = BinaryImageOps.contour(filtered, ConnectRule.FOUR,null);
	 			
			// Fit an ellipse to each external contour and draw the results
			
			BufferedImage blank = new BufferedImage(newFrame.getWidth(), newFrame.getHeight(), newFrame.getType());
			
//			Graphics2D g2 = newFrame.createGraphics();
			Graphics2D g2 = blank.createGraphics();
			
			g2.setStroke(new BasicStroke(3));
			ArrayList<Rectangle2D_I32> boxes = new ArrayList<Rectangle2D_I32>();
			
			ArrayList<List<PointIndex_I32>> polygons = new ArrayList<List<PointIndex_I32>>();
			ArrayList<Polygon> polyset = new ArrayList<Polygon>();
			ArrayList<Rectangle2D> boxset = new ArrayList<Rectangle2D>();
			
			for( Contour c : contours ) {
				FitData<EllipseRotated_F64> ellipse = ShapeFittingOps.fitEllipse_I32(c.external,0,false,null);
				int boxsize = Integer.max((int) ellipse.shape.a, (int) ellipse.shape.b); //distance from center to perpendicular edge
				Rectangle2D_I32 box = new Rectangle2D_I32((int) ellipse.shape.center.x - boxsize, (int) ellipse.shape.center.y - boxsize, (int) ellipse.shape.center.x + boxsize, (int) ellipse.shape.center.y + boxsize);
				boxes.add(box);
				polygons.add(ShapeFittingOps.fitPolygon(c.external, false, 0.05, 0.01, 50));
//				VisualizeShapes.drawRectangle(box, g2);
			}
//			for(Rectangle2D_I32 r : boxes) {
//				g2.setClip(r.x0, r.y0, r.x1-r.x0, r.y1-r.y0);
//				g2.drawImage(newFrame, 0, 0, null);
//			}
			for(List<PointIndex_I32> p : polygons) {
				int x[] = new int[p.size()];
				int y[] = new int[p.size()];
				for(int i = 0; i < p.size(); i++) {
					x[i] = p.get(i).x;
					y[i] = p.get(i).y;
				}
				Polygon fit = new Polygon(x, y, p.size());
				polyset.add(fit);
				Rectangle2D b = fit.getBounds2D();
				boxset.add(b);
				g2.setClip(b);
				g2.drawImage(newFrame, 0, 0, null);
				g2.setClip(null);
//				g2.setColor(Color.RED);
//				g2.draw(fit);
			}
//			System.out.println(boxset.size());
//			boxset = BoxProcessor.mergeBoxes(boxset);
//			System.out.println(boxset.size());
			for(Rectangle2D r : boxset) {
//				if(r.getHeight() > 60 || r.getWidth() > 60) continue;
//				System.out.println(r);
				g2.setClip(r);
//				g2.drawImage(newFrame, 0, 0, null);
				g2.setClip(null);
				g2.setColor(Color.RED);
				g2.draw(r);
			}
			
			g2.setClip(null);
			
//			BoxKLTDetector det = (BoxKLTDetector) tracker;
//			BoxPointTrackerKltPyramid innertrack = (BoxPointTrackerKltPyramid) det.tracker;
//			innertrack.setBoxes(boxes);
//			tracker.process(frame);
//			tracker.process(retina.getParvo());
//			ArrayList<FeaturePosition> feats = tracker.getFrameFeatures();
//			for(FeaturePosition ft: feats) {
//				VisualizeFeatures.drawPoint(g2, (int) ft.x, (int) ft.y, Color.GREEN);
//			}
//			g2.setColor(Color.RED);
//			for(Rectangle2D_I32 r : boxes) {
//				VisualizeShapes.drawRectangle(r, g2);
//			}

			ArrayList<Integer> assoc = bs.associate(frames, boxset, polyset, null);
//			if(frames == 199) System.out.println(boxset.size() + " " + assoc.size());
			g2.setColor(Color.ORANGE);
			g2.setFont(new Font("TimesRoman", Font.BOLD, 20));
			for(int i = 0; i < boxset.size(); i++) {
				if(assoc.get(i) > 0) g2.drawString(assoc.get(i).toString(), (int) boxset.get(i).getCenterX(), (int) boxset.get(i).getCenterY());
			}
 			
			g2.setColor(Color.WHITE);
			g2.setFont(new Font("TimesRoman", Font.PLAIN, 10));
//			g2.drawString("Frame: " + frames + ", Features: " + feats.size(), newFrame.getWidth()-160, newFrame.getHeight()-20);
			
			mask.ProcessFrame(blank);
			gui.setBufferedImage(blank);	
			gui.repaint();
			
//			currentFrames.add(new SimpleFrame(frames, feats));
//			local.localFrames.add(new SimpleFrame(localframe, feats));
			
			//update active feature information: number of appearances and times of those appearances
//			tracker.updateFeatApps(featapps);
//			tracker.updateFeatApps(local.featapps);
			
			//store 64 dim float information, if it had not been stored already
			//wrappers needed to access what were private variables in the original library classes
			
//			tracker.updateFeatSURF(featSURF);
//			tracker.updateFeatSURF(local.featSURF);

//			System.out.println("Processed frame " + frames);
			frames++;
			localframe++;
			
			
		}
		
		framesInEachVideo.add(localframe);
		
		mask.Close();
//		FileIO.SaveObject(new File(CatGetter.stem(vidLoc)), local);

		return;
	}
	
	public void batch(String folder) {
		
		File dir = new File(folder);
		File[] listOfFiles = dir.listFiles();
		
		ArrayList<String> fq = new ArrayList<String>();
		for (File file : listOfFiles) {
		    if (file.isFile() && (file.getName().toLowerCase().endsWith(".mov") || file.getName().toLowerCase().endsWith(".m4v") || file.getName().toLowerCase().endsWith(".avi") || file.getName().toLowerCase().endsWith(".mp4"))) {
		    	fq.add(file.getAbsolutePath());
		    }
		}
		
		for(int i = 0; i < fq.size(); i++) {
			String currentFilePath = fq.get(i);
	    	System.out.println("PROCESSING VIDEO " + (i+1) + " of " + fq.size() + ", " + currentFilePath);
	    	setFrames(0);
			boundprocess(currentFilePath);
//			boxvideos3(currentFilePath, "interbig/" + CatGetter.stemOnly(currentFilePath), 20);
		}
		
//		System.out.println(bs.toString());
		
//		genFeatCluster(frames/20, "cluster_new");
		
//		setFrames(0);
//		openVid();
//				
//		for(int i = 0; i < fq.size(); i++) {
//			updateRender(fq.get(i), i);
//		}
//		
//		closeVid();
		
		return;
	}
	
	public void boxvideos(String vidLoc, String destbase, int minlength) {
				
		int numStreaks = bs.streaks.size();
		
		int minx[] = new int[numStreaks];
		int miny[] = new int[numStreaks];
		int maxx[] = new int[numStreaks];
		int maxy[] = new int[numStreaks];
		
		int width = 0;
		int height = 0;
		for(int i = 0; i < numStreaks; i++) {
			
			if(bs.streaks.get(i).size() < minlength) continue;
			
			minx[i] = Integer.MAX_VALUE;
			miny[i] = Integer.MAX_VALUE;

			ArrayList<BoxFrame> currStreak = bs.streaks.get(i);
			
			for(int j = 0; j < currStreak.size(); j++) {
				Rectangle2D rect = currStreak.get(j).bound;
				if(rect.getX() < minx[i]) minx[i] = (int) rect.getX();
				if(rect.getY() < miny[i]) miny[i] = (int) rect.getY();
				if(rect.getMaxX() > maxx[i]) maxx[i] = (int) rect.getMaxX();
				if(rect.getMaxY() > maxy[i]) maxy[i] = (int) rect.getMaxY();				
			}
			
			if(maxx[i]-minx[i] > width) width = maxx[i]-minx[i];
			if(maxy[i]-miny[i] > height) height = maxy[i]-miny[i];
			
		}
		
		width = (width + 15) / 16 * 16;
		
		
		
		VideoFrameWriter[] out = new VideoFrameWriter[numStreaks];
		for(int i = 0; i < numStreaks; i++) {
			if(bs.streaks.get(i).size() >= minlength) {
				String filename = destbase + "_f" + i + "_l" + bs.streaks.get(i).size() + ".mp4";
				out[i] = new VideoFrameWriter(new File(filename), width, height, 5);
			}
			else {
				out[i] = null;
			}
			
		}
		
		int ctr[] = new int[numStreaks];
		
		SimpleImageSequence<T> sequence = processVideo(vidLoc);

		T frame;
		int frames = 0;
		while(sequence.hasNext()) {
			frame = sequence.next();
			BufferedImage newFrame = sequence.getGuiImage();
			
			for(int i = 0; i < numStreaks; i++) {
				if(out[i] == null) continue;
				if(frames == bs.streaks.get(i).get(0).frame) {
					out[i].OpenFile();
				}
				if(ctr[i] < bs.streaks.get(i).size() && frames == bs.streaks.get(i).get(ctr[i]).frame) {
					Rectangle2D rect = bs.streaks.get(i).get(ctr[i]).bound;
					BufferedImage window = newFrame.getSubimage((int) rect.getX(), (int) rect.getY(), (int) rect.getWidth(), (int) rect.getHeight());
					BufferedImage output = new BufferedImage(width, height, newFrame.getType());
					Graphics2D g = output.createGraphics();
					Polygon p = bs.streaks.get(i).get(ctr[i]).polybound;
					p.translate(-minx[i], -miny[i]);
//					g.setClip(p);
					
					g.drawImage(window, (int)rect.getX() - minx[i] , (int)rect.getY() - miny[i], null);
					for(FeaturePosition f : bs.streaks.get(i).get(ctr[i]).feats) {
						VisualizeFeatures.drawPoint(g, (int) f.x-1 - minx[i], (int) f.y-1 - miny[i], Color.CYAN);
					}
//					System.out.print(bs.streaks.get(i).get(ctr[i]).feats.size() + " ");
					
					out[i].ProcessFrame(output);
					
					ctr[i]++;
					p.translate(minx[i], miny[i]);
				}
				if(ctr[i] >= bs.streaks.get(i).size()) {
					out[i].Close();
				}
			}
//			System.out.println("just processed frame " + frames);
			frames++;
		}
		
	}
	
	public void boxvideos2(String vidLoc, String destbase, int minlength) {
		
		int numStreaks = bs.streaks.size();
		
		int minx[] = new int[numStreaks];
		int miny[] = new int[numStreaks];
		int maxx[] = new int[numStreaks];
		int maxy[] = new int[numStreaks];
		
		int width = 0;
		int height = 0;
		for(int i = 0; i < numStreaks; i++) {
			
			if(bs.streaks.get(i).size() < minlength) continue;
			
			minx[i] = Integer.MAX_VALUE;
			miny[i] = Integer.MAX_VALUE;

			ArrayList<BoxFrame> currStreak = bs.streaks.get(i);
			
			for(int j = 0; j < currStreak.size(); j++) {
				Rectangle2D rect = currStreak.get(j).bound;
				if(rect.getWidth() > width) width = (int) rect.getWidth();
				if(rect.getHeight() > height) height = (int) rect.getHeight();
				
				if(rect.getX() < minx[i]) minx[i] = (int) rect.getX();
				if(rect.getY() < miny[i]) miny[i] = (int) rect.getY();
				if(rect.getMaxX() > maxx[i]) maxx[i] = (int) rect.getMaxX();
				if(rect.getMaxY() > maxy[i]) maxy[i] = (int) rect.getMaxY();				
			}
			
//			if(maxx[i]-minx[i] > width) width = maxx[i]-minx[i];
//			if(maxy[i]-miny[i] > height) height = maxy[i]-miny[i];
			
		}
		
		width = (width + 15) / 16 * 16;
		
		
		
		VideoFrameWriter[] out = new VideoFrameWriter[numStreaks];
		VideoFrameWriter[] featsOnly = new VideoFrameWriter[numStreaks];
		WindowWorker[] windows = new WindowWorker[numStreaks];
		
		for(int i = 0; i < numStreaks; i++) {
			if(bs.streaks.get(i).size() >= minlength) {
				String filename = destbase + "_f" + i + "_l" + bs.streaks.get(i).size();
				out[i] = new VideoFrameWriter(new File(filename + "_render.mp4"), width, height, 5);
				featsOnly[i] = new VideoFrameWriter(new File(filename + "_feature.mp4"), width, height, 5);
				windows[i] = new WindowWorker();
			}
			else {
				out[i] = null;
				featsOnly[i] = null;
			}
			
		}
		
		int ctr[] = new int[numStreaks];
		
		SimpleImageSequence<T> sequence = processVideo(vidLoc);

		T frame;
		int frames = 0;
		while(sequence.hasNext()) {
			frame = sequence.next();
			BufferedImage newFrame = sequence.getGuiImage();
			
			for(int i = 0; i < numStreaks; i++) {
				if(out[i] == null) continue;
				if(frames == bs.streaks.get(i).get(0).frame) {
					out[i].OpenFile();
					featsOnly[i].OpenFile();
				}
				if(ctr[i] < bs.streaks.get(i).size() && frames == bs.streaks.get(i).get(ctr[i]).frame) {
					Rectangle2D rect = bs.streaks.get(i).get(ctr[i]).bound;
					BufferedImage window = newFrame.getSubimage((int) rect.getX(), (int) rect.getY(), (int) rect.getWidth(), (int) rect.getHeight());
					BufferedImage output = new BufferedImage(width, height, newFrame.getType());
					Graphics2D g = output.createGraphics();
					Polygon p = bs.streaks.get(i).get(ctr[i]).polybound;
					p.translate((int) ((width/2 - rect.getWidth()/2) - rect.getX()) , (int) ((height/2 - rect.getHeight()/2) - rect.getY()));
//					g.setClip(p);
					
					ArrayList<FeaturePosition> feats = new ArrayList<FeaturePosition>();
					
					BufferedImage featOutput = new BufferedImage(width, height, newFrame.getType());
					Graphics2D g2 = featOutput.createGraphics();
					
					g.setClip(p);
					g.drawImage(window, (int) (width/2 - rect.getWidth()/2) , (int) (height/2 - rect.getHeight()/2), null);
					g.setClip(null);
					g2.drawImage(window, (int) (width/2 - rect.getWidth()/2) , (int) (height/2 - rect.getHeight()/2), null);
					
					for(FeaturePosition f : bs.streaks.get(i).get(ctr[i]).feats) {
						
						int consecframes = 1;
						int backframes = 0;
						ArrayList<Double> xpos = new ArrayList<Double>();
						ArrayList<Double> ypos = new ArrayList<Double>();
						int j = ctr[i]-1;
						while(j >= 0) {
							ArrayList<FeaturePosition> check = bs.streaks.get(i).get(j).feats;
							int flag = 0;
							for(int k = 0; k < check.size(); k++) {
								if(check.get(k).id == f.id) {
									backframes++;
									xpos.add(check.get(k).x);
									ypos.add(check.get(k).y);
									flag = 1;
									break;
								}
							}
							if(flag == 0) break;
							j--;
						}
						
						xpos.add(f.x);
						ypos.add(f.y);
						
						j = ctr[i]+1;
						int fwdframes = 0;					
						while(j < bs.streaks.get(i).size()) {
							ArrayList<FeaturePosition> check = bs.streaks.get(i).get(j).feats;
							int flag = 0;
							for(int k = 0; k < check.size(); k++) {
								if(check.get(k).id == f.id) {
									fwdframes++;
									xpos.add(check.get(k).x);
									ypos.add(check.get(k).y);
									flag = 1;
									break;
								}
							}
							if(flag == 0) break;
							j++;
						}
						
						consecframes += backframes + fwdframes;
						
						if(consecframes < minlength) continue;
						double xd[] = new double[xpos.size()];
						double yd[] = new double[ypos.size()];

						for(int k = 0; k < xpos.size(); k++) {
							xd[k] = (double) xpos.get(k);
							yd[k] = (double) ypos.get(k);
						}
						
						double xdev = Vec.StdDev(xd);
						double ydev = Vec.StdDev(yd);
						
						//add if standard deviation >= 5 for x or y
						if(xdev < 5 && ydev < 5) continue;
						
						g2.setColor(Color.MAGENTA);
						Ellipse2D.Double circle = new Ellipse2D.Double(f.x - rect.getX() + width/2 - rect.getWidth()/2, f.y - rect.getY() + height/2 - rect.getHeight()/2, 5, 5);
						g2.fill(circle);
						
//						VisualizeFeatures.drawPoint(g2, (int) (f.x - rect.getX() + width/2 - rect.getWidth()/2), (int) (f.y - rect.getY() + height/2 - rect.getHeight()/2), Color.CYAN);
						feats.add(f);
					}
//					System.out.print(bs.streaks.get(i).get(ctr[i]).feats.size() + " ");
					
					out[i].ProcessFrame(output);
					featsOnly[i].ProcessFrame(featOutput);
					
					ctr[i]++;
					p.translate(minx[i], miny[i]);
					
					windows[i].windows.add(new WindowInfo(rect, feats));
				}
				if(ctr[i] >= bs.streaks.get(i).size()) {
					out[i].Close();
					featsOnly[i].Close();
					String filename = destbase + "_f" + i + "_l" + bs.streaks.get(i).size();
					FileIO.SaveObject(new File(filename), windows[i]);
				}
			}
//			System.out.println("just processed frame " + frames);
			frames++;
		}
		
	}
	
	public static ArrayList<BoxFrame> boxresize(ArrayList<BoxFrame> streak) {
		
		ArrayList<BoxFrame> newstreak = new ArrayList<BoxFrame>();
		
		double maxheight = 0;
		double maxwidth = 0;
		
		//find max dimensions
		for(int i = 0; i < streak.size(); i++) {
			if(streak.get(i).bound.getHeight() > maxheight) maxheight = streak.get(i).bound.getHeight();
			if(streak.get(i).bound.getWidth() > maxwidth) maxwidth = streak.get(i).bound.getWidth();
		}
		
		//resize dimensions
		for(int i = 0; i < streak.size(); i++) {
			
			
			double centerx = streak.get(i).bound.getCenterX();
			double centery = streak.get(i).bound.getCenterY();
			Rectangle2D rect = new Rectangle2D.Double(centerx - maxwidth/2, centery - maxheight/2, centerx + maxwidth/2, centery + maxheight/2);
			
			BoxFrame box = new BoxFrame(rect, streak.get(i).polybound, streak.get(i).frame);
		}		
		
		return newstreak;
	}
	
	
	public void boxvideos3(String vidLoc, String destbase, int minlength) {
		
		System.out.println("(" + bs.streaks.size() + ") Boxing video @ " + vidLoc);
		
		int numStreaks = bs.streaks.size();
		
		int minx[] = new int[numStreaks];
		int miny[] = new int[numStreaks];
		int maxx[] = new int[numStreaks];
		int maxy[] = new int[numStreaks];
		
		int minw[] = new int[numStreaks];
		int minh[] = new int[numStreaks];
		
		int width = 0;
		int height = 0;
		for(int i = 0; i < numStreaks; i++) {
//			System.out.println(bs.streaks.get(i).size());
			if(bs.streaks.get(i).size() < minlength) continue;
			
			minx[i] = Integer.MAX_VALUE;
			miny[i] = Integer.MAX_VALUE;

			ArrayList<BoxFrame> currStreak = bs.streaks.get(i);
			
			for(int j = 0; j < currStreak.size(); j++) {
				Rectangle2D rect = currStreak.get(j).bound;
				if(rect.getWidth() > width) width = (int) rect.getWidth();
				if(rect.getHeight() > height) height = (int) rect.getHeight();
				
				if(rect.getWidth() > minw[i]) minw[i] = (int) rect.getWidth();
				if(rect.getHeight() > minh[i]) minh[i] = (int) rect.getHeight();
				
				if(rect.getX() < minx[i]) minx[i] = (int) rect.getX();
				if(rect.getY() < miny[i]) miny[i] = (int) rect.getY();
				if(rect.getMaxX() > maxx[i]) maxx[i] = (int) rect.getMaxX();
				if(rect.getMaxY() > maxy[i]) maxy[i] = (int) rect.getMaxY();				
			}
			
//			if(maxx[i]-minx[i] > width) width = maxx[i]-minx[i];
//			if(maxy[i]-miny[i] > height) height = maxy[i]-miny[i];
			
		}
		
		width = (width + 15) / 16 * 16;
		
		VideoFrameWriter[] out = new VideoFrameWriter[numStreaks];
		
		for(int i = 0; i < numStreaks; i++) {
			if(bs.streaks.get(i).size() >= minlength) {
//				System.out.println("PASS " + bs.streaks.get(i).size());
				String filename = destbase + "_f" + i + "_l" + bs.streaks.get(i).size();
				out[i] = new VideoFrameWriter(new File(filename + "_render.mp4"), minw[i], minh[i], 5);
			}
			else {
				out[i] = null;
			}
			
		}
		
		int ctr[] = new int[numStreaks];
		
		SimpleImageSequence<T> sequence = processVideo(vidLoc);

		T frame;
		int frames = 0;
		while(sequence.hasNext()) {
			frame = sequence.next();
			BufferedImage newFrame = sequence.getGuiImage();
			
			for(int i = 0; i < numStreaks; i++) {
				if(out[i] == null) continue;
				if(frames == bs.streaks.get(i).get(0).frame) {
					out[i].OpenFile();
				}
				if(ctr[i] < bs.streaks.get(i).size() && frames == bs.streaks.get(i).get(ctr[i]).frame) {
					Rectangle2D rect = bs.streaks.get(i).get(ctr[i]).bound;
					
					int lowx = (int) rect.getCenterX() - (minw[i]/2) - 1;
					int highx = minw[i];
					int lowy = (int) rect.getCenterY() - (minh[i]/2) - 1;
					int highy = minh[i];
					
					if(lowx < 0) {
						lowx = 0;
					}
					if(lowx + highx >= newFrame.getWidth()) {
						lowx = newFrame.getWidth()-highx-1;
					}
					if(lowy < 0) {
						lowy = 0;
					}
					if(lowy + highy >= newFrame.getHeight()) {
						lowy = newFrame.getHeight()-highy-1;
					}
					BufferedImage window = newFrame.getSubimage(lowx, lowy, highx, highy);

					
//					BufferedImage window = newFrame.getSubimage((int) rect.getX(), (int) rect.getY(), (int) rect.getWidth(), (int) rect.getHeight());
					BufferedImage output = new BufferedImage(minw[i], minh[i], newFrame.getType());
					Graphics2D g = output.createGraphics();
					
					ArrayList<FeaturePosition> feats = new ArrayList<FeaturePosition>();
					
					g.drawImage(window, 0, 0, null);
//					g.drawImage(window, (int) (minw[i]/2 - rect.getWidth()/2) , (int) (minh[i]/2 - rect.getHeight()/2), null);
					
//					System.out.print(bs.streaks.get(i).get(ctr[i]).feats.size() + " ");
					
					out[i].ProcessFrame(output);
					
					ctr[i]++;
					
				}
				if(ctr[i] >= bs.streaks.get(i).size()) {
					out[i].Close();
				}
			}
//			System.out.println("just processed frame " + frames);
			frames++;
		}
		
	}
	
	@SuppressWarnings("unchecked")
	public static void main(String[] args) {
	
//		DOOGDriver2.doogfilter("/home/grangerlab/workspace/chris/output138628_BIGGER.mp4", "output138638_doog.mp4");
		
		RetinaFeatureTracker p = new RetinaFeatureTracker(new BoxKLTDetector(GrayF32.class), GrayF32.class, "nameoffile.mp4", 25); //you can leave these parameters filled in like this; they aren't used for what follows
		//String filePathOfVideo = "C:/Users/Khari Jarrett/Documents/VideoProcessing-master/chris/GTA5_2.1.17.mp4"; //replace as desired
		//p.boundprocess(filePathOfVideo);  //Giving me a CRAZY fatal error
		//p.boxvideos3(filePathOfVideo, "C:/Users/Khari Jarrett/Documents/VideoProcessing-master/chris/gtasplit/gta ", 10); //split up videos are saved in folder "pacmansplit" with prefix "pac"
		//String magnoStemLoc = "C:/Users/Khari Jarrett/Documents/VideoProcessing-master/chris/Magnos/int9";
		
		String inputLoc = "C:/Users/f002tj9/Documents/Research/kj/Data/gta5.mp4";						//what vid to handle? (vidLoc)
		String magnoLoc= "C:/Users/f002tj9/Documents/Research/kj/Magnos/gta5_threshmagno.mp4";			//where to put result? (stemLoc)
		String outvidLoc= "C:/Users/f002tj9/Documents/Research/kj/Output/gta5_out.mp4";			//where to put result? (stemLoc)
		String magnoLoc2= "C:/Users/f002tj9/Documents/Research/kj/Output/gta5_TDout.mp4";			//where to put result?
		String txtLoc = "C:/Users/f002tj9/Documents/Research/kj/Text/virat6_tubelist.txt";
		

		
		//p.magnoFilter(outvidLoc, inputLoc);
		//File tubefile = new File("C:/Users/f002tj9/Documents/Research/kj/TubeLists/" + CatGetter.stemOnly(inputLoc) + ".zip" );
		//ArrayList<ArrayList<double[]>> tubes = (ArrayList<ArrayList<double[]>>) FileIO.LoadObject(tubefile);
		
		
		p.makeThreshMagno(magnoLoc, inputLoc);
		//p.topDownMagno(tubes, magnoLoc2,magnoLoc, inputLoc);
		//p.tubeWriter(tubes, txtLoc);
		
		
		 //p.addFrameNum(outvidLoc, inputLoc);
		
		//p.magnoTest(stemLoc, vidLoc);
		
		
		

	}
	
}

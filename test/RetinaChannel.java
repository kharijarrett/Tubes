package test;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import boofcv.alg.filter.binary.BinaryImageOps;
import boofcv.alg.filter.binary.Contour;
import boofcv.alg.filter.binary.ThresholdImageOps;
import boofcv.alg.misc.ImageStatistics;
import boofcv.alg.shapes.FitData;
import boofcv.alg.shapes.ShapeFittingOps;
import boofcv.gui.feature.VisualizeFeatures;
import boofcv.gui.feature.VisualizeShapes;
import boofcv.gui.image.ImagePanel;
import boofcv.gui.image.ShowImages;
import boofcv.io.MediaManager;
import boofcv.io.UtilIO;
import boofcv.io.image.ConvertBufferedImage;
import boofcv.io.image.SimpleImageSequence;
import boofcv.io.image.UtilImageIO;
import boofcv.io.wrapper.DefaultMediaManager;
import boofcv.struct.ConnectRule;
import boofcv.struct.image.GrayF32;
import boofcv.struct.image.GrayU8;
import boofcv.struct.image.ImageGray;
import boofcv.struct.image.ImageType;
import common.FileIO;
import common.math.Mat;
import common.utils.ImageUtils;
import common.video.VideoFrameWriter;
import common.video.VideoRetina;
import georegression.struct.shapes.EllipseRotated_F64;
import georegression.struct.shapes.Rectangle2D_I32;

@SuppressWarnings("rawtypes")
public class RetinaChannel<T extends ImageGray> {
	
	Class<T> imageType;
	BoxSURFDetector tracker;
	
	String stemLoc;
	String vidLoc;
	
	// displays the video sequence and tracked features
	ImagePanel gui = new ImagePanel();
	
	public RetinaChannel(BoxSURFDetector tracker, Class<T> imageType) {
		this.tracker = tracker;
		this.imageType = imageType;
	}
	
	/**
	 * Sets up video to process given a file path. Mainly for convenience.
	 */
	@SuppressWarnings({"unchecked", "rawtypes" })
	public SimpleImageSequence processVideo(String vidPath) {

		Class imageType = GrayF32.class;
		MediaManager media = DefaultMediaManager.INSTANCE;
		SimpleImageSequence sequence = media.openVideo(UtilIO.pathExample(vidPath), ImageType.single(imageType)); 
		sequence.setLoop(false);

		return sequence;
	}
	
	@SuppressWarnings({ "unchecked", "unused" })
	public void process(String stemLoc, String vidLoc, boolean color) {
		
		SimpleImageSequence<T> sequence = processVideo(vidLoc);

		int height = sequence.getNextHeight();
		int width = sequence.getNextWidth(); 
		
		VideoFrameWriter magno = new VideoFrameWriter(new File(stemLoc + "_magno.mp4"), width, height, 30);
		VideoFrameWriter parvo = new VideoFrameWriter(new File(stemLoc + "_parvo.mp4"), width, height, 30);
		
		magno.OpenFile();
		parvo.OpenFile();
		
		VideoRetina retina = new VideoRetina(width, height, color);
		
		T frame;
		int frames = 0;
		while(sequence.hasNext()) {
			frame = sequence.next();
			BufferedImage currFrame = sequence.getGuiImage();
//			
//			BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);  
//			Graphics g = image.getGraphics();  
//			g.drawImage(currFrame, 0, 0, null);  
//			g.dispose();
//			retina.ProcessFrame(image);
			
			retina.ProcessFrame(currFrame);
			magno.ProcessFrame(retina.getMagno());
			parvo.ProcessFrame(retina.getParvo());
			System.out.println("Finished processing frame " + frames);
			frames++;
		}
		
		magno.Close();
		parvo.Close();
		
	}
	
	public void magnoFilter(String stemLoc, String vidLoc) {

		SimpleImageSequence<T> sequence = processVideo(vidLoc);

		int height = sequence.getNextHeight();
		int width = sequence.getNextWidth(); 
		
		VideoFrameWriter mask = new VideoFrameWriter(new File(stemLoc), width, height, 30);
		
		mask.OpenFile();
				
		T frame;
		int frames = 0;
		while(sequence.hasNext()) {
			frame = sequence.next();
			BufferedImage currFrame = sequence.getGuiImage();
			
			byte grayscale[][] = ImageUtils.BufferedImage2Grayscale(currFrame);
			int gray[][] = Mat.Unsigned2Int(grayscale);
			for(int i = 0; i < gray.length; i++) {
				for(int j = 0; j < gray[i].length; j++) {

					if(gray[i][j] < 40) gray[i][j] = 255;
					else gray[i][j] = 0;
				}
			}
//			
			grayscale = Mat.Int2Unsigned(gray);
			
			BufferedImage redoneFrame = ImageUtils.Grayscale2BufferedImage(grayscale, 255);
//			GrayU8 filtered = new GrayU8(redoneFrame.getWidth(),redoneFrame.getHeight());
//			ConvertBufferedImage.convertFrom(redoneFrame, filtered);
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
			
			
			
			Graphics2D g2 = redoneFrame.createGraphics();
//
//			BufferedImage neo = ConvertBufferedImage.extractBuffered(filtered);
//			if(neo == null) System.out.println("gsjghskjhgsg");
//			Graphics2D g2 = neo.createGraphics();
//			
			g2.setStroke(new BasicStroke(3));
			g2.setColor(Color.RED);
//	 		
			if(contours.size() < 10) {
				for( Contour c : contours ) {
					FitData<EllipseRotated_F64> ellipse = ShapeFittingOps.fitEllipse_I32(c.external,0,false,null);
					if(ellipse.shape.a < 10 || ellipse.shape.b < 10 || ellipse.shape.a > 80 || ellipse.shape.b > 80 || ellipse.shape.a/ellipse.shape.b > 4 || ellipse.shape.b/ellipse.shape.a > 4) continue;
//					VisualizeShapes.drawEllipse(ellipse.shape, g2);
					
					int boxsize = Integer.max((int) ellipse.shape.a, (int) ellipse.shape.b); //distance from center to perpendicular edge
					Rectangle2D_I32 box = new Rectangle2D_I32((int) ellipse.shape.center.x - boxsize, (int) ellipse.shape.center.y - boxsize, (int) ellipse.shape.center.x + boxsize, (int) ellipse.shape.center.y + boxsize);
					
					VisualizeShapes.drawRectangle(box, g2);
				}
			}
			

//			
			
			mask.ProcessFrame(redoneFrame);

			System.out.println("Finished processing frame " + frames + ", contours: " + contours.size());
			frames++;
		}
		
		mask.Close();
		
	}
	
	@SuppressWarnings("unchecked")
	public void origMagnoFilter(String stemLoc, String vidLoc) {

		ClipArchive local = new ClipArchive();
		
		SimpleImageSequence<T> sequence = processVideo(vidLoc);

		int height = sequence.getNextHeight();
		int width = sequence.getNextWidth(); 
		
		local.height = height;
		local.width = width;
		
		VideoFrameWriter mask = new VideoFrameWriter(new File(stemLoc), width, height, 30);
		VideoRetina retina = new VideoRetina(width, height, false);

		gui.setPreferredSize(new Dimension(width, height));
		ShowImages.showWindow(gui,"Pacman", true);
		
		mask.OpenFile();
				
		T frame;
		int frames = 0;
		while(sequence.hasNext()) {
			frame = sequence.next();
			BufferedImage newFrame = sequence.getGuiImage();
			retina.ProcessFrame(newFrame);
			
			BufferedImage currFrame = retina.getMagno();
					
			byte grayscale[][] = ImageUtils.BufferedImage2Grayscale(currFrame);
			int gray[][] = Mat.Unsigned2Int(grayscale);
			for(int i = 0; i < gray.length; i++) {
				for(int j = 0; j < gray[i].length; j++) {

					if(gray[i][j] < 80) gray[i][j] = 255;
					else gray[i][j] = 0;
				}
			}
//			
			grayscale = Mat.Int2Unsigned(gray);
			
			BufferedImage redoneFrame = ImageUtils.Grayscale2BufferedImage(grayscale, 255);
//			GrayU8 filtered = new GrayU8(redoneFrame.getWidth(),redoneFrame.getHeight());
//			ConvertBufferedImage.convertFrom(redoneFrame, filtered);
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
//
//			BufferedImage neo = ConvertBufferedImage.extractBuffered(filtered);
//			if(neo == null) System.out.println("gsjghskjhgsg");
//			Graphics2D g2 = neo.createGraphics();
//			
			g2.setStroke(new BasicStroke(3));
			g2.setColor(Color.RED);
//	 		
			ArrayList<Rectangle2D_I32> boxes = new ArrayList<Rectangle2D_I32>();
			
			if(contours.size() < 10) {
				for( Contour c : contours ) {
					FitData<EllipseRotated_F64> ellipse = ShapeFittingOps.fitEllipse_I32(c.external,0,false,null);
					if(ellipse.shape.a < 10 || ellipse.shape.b < 10 || ellipse.shape.a > 80 || ellipse.shape.b > 80 || ellipse.shape.a/ellipse.shape.b > 4 || ellipse.shape.b/ellipse.shape.a > 4) continue;
//					VisualizeShapes.drawEllipse(ellipse.shape, g2);
					
					int boxsize = Integer.max((int) ellipse.shape.a, (int) ellipse.shape.b); //distance from center to perpendicular edge
					Rectangle2D_I32 box = new Rectangle2D_I32((int) ellipse.shape.center.x - boxsize, (int) ellipse.shape.center.y - boxsize, (int) ellipse.shape.center.x + boxsize, (int) ellipse.shape.center.y + boxsize);
					boxes.add(box);
					VisualizeShapes.drawRectangle(box, g2);
				}
			}	
			
			BoxDetectDescribeAssociate innertrack = (BoxDetectDescribeAssociate) tracker.tracker;
			innertrack.setBoxes(boxes);
			
			tracker.process(frame);
			ArrayList<FeaturePosition> feats = tracker.getFrameFeatures();
			for(FeaturePosition f: feats) {
				VisualizeFeatures.drawPoint(g2, (int) f.x, (int) f.y, Color.GREEN);
			}
			
			tracker.updateFeatApps(local.featapps);
			tracker.updateFeatSURF(local.featSURF);
			local.localFrames.add(new SimpleFrame(frames, feats));

			g2.setColor(Color.WHITE);
			g2.setFont(new Font("TimesRoman", Font.BOLD, 20));
			g2.drawString("Frame: " + frames + ", Features: " + feats.size(), newFrame.getWidth()- 600, newFrame.getHeight()-50);
			
			mask.ProcessFrame(newFrame);
			gui.setBufferedImage(newFrame);	
			gui.repaint();

			System.out.println("Finished processing frame " + frames + ", boxes: " + boxes.size());
			frames++;
		}
		
		mask.Close();
		FileIO.SaveObject(new File(CatGetter.stem(vidLoc)), local);
		
	}
	
	/**
	 * Processes supposed feature appearances.
	 * Says a feature was present even if not detected if it was present 3/4 frames in its vicinity
	 * i.e. 2 ahead / 2 back
	 * 
	 * Ignores a feature if it moves more than five pixels within a single frame.
	 */
	@SuppressWarnings({ "unchecked", "unused" })
	public void updateRender(String arcLoc, String vidLoc) {

		ClipArchive arc = (ClipArchive) FileIO.LoadObject(new File(arcLoc));
		Map<Long, Integer> featapps = arc.featapps;
		ArrayList<SimpleFrame> currentFrames = arc.localFrames;
		
		int numFeatures = featapps.keySet().size();
		int numFrames = currentFrames.size();
		
		if(numFrames < 30) {
			System.out.println("Notice: Insufficient number of frames. Quitting render early.");
		}
		
		//set up feature grid. If feature was added at that frame, say it was there in array (value 1)
		int featGrid[][] = new int[numFrames][numFeatures];
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
		int continuousFeatGrid[][] = new int[numFrames][numFeatures];
		
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
			renderInfo.add(updateDisplay(arc, featGrid, i, i, sequence, itemid, 15));
		}
		
		FileIO.SaveObject(new File(CatGetter.render(vidLoc)), renderInfo);
		return;
	}
	
	/**
	 * Updates displayed image by a frame to visually check results.
	 */
	public ClusterFrame updateDisplay(ClipArchive arc, int[][] featGrid, int localframe, int i, SimpleImageSequence<T> sequence, String itemid, int minConsecutive) {
		
		ArrayList<ClusterFeature> features = new ArrayList<ClusterFeature>();
		Map<Long, Integer> featapps = arc.featapps;
		Map<Long, Double[]> featSURF = arc.featSURF;
		ArrayList<SimpleFrame> currentFrames = arc.localFrames;
		
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
//				short clusterno = cluster.Lookup(storeDoubles);
				
				long clusterno = p;
				
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
//				if(!vocabapps.containsKey(itemid)) {
//					int clustapp[] = new int[vocabsize];
//					clustapp[clusterno] = 1;
//					vocabapps.put(itemid, clustapp);
//				}
//				else {
//					int clustapp[] = vocabapps.get(itemid);
//					clustapp[clusterno] += 1;
//				}				
				
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
				
				features.add(new ClusterFeature(p, (short) clusterno, x, y));
				
				//put feature cluster number
				g2.setColor(Color.WHITE);
//				g2.drawString(((Short) clusterno).toString(), x-5, y+5);
//				g2.drawString(((Long) p).toString(), x-5, y+5);
				g2.drawString(((Integer)(backframe+fwdframe)).toString(),x-5, y+5);

				
			}
		}
		
		//draw frame number for convenient reference
		g2.setColor(Color.BLACK);
		g2.setFont(new Font("TimesRoman", Font.BOLD, 20));
		g2.drawString("FRAME: " + i, orig.getWidth()- 200, orig.getHeight()-50);

		gui.setBufferedImage(orig);	
		gui.repaint();
//		outVid.ProcessFrame(orig);
		
		return new ClusterFrame(localframe, features);
	}
	
	@SuppressWarnings("unchecked")
	public static void main(String[] args) {
		RetinaChannel stream = new RetinaChannel(new BoxSURFDetector(GrayF32.class),GrayF32.class);
//		stream.process("pacman", "/home/grangerlab/Desktop/pacman/Pacman_Java_cropped.mp4", true);
//		stream.process("pob_color", "/home/grangerlab/Desktop/pob/news51.avi", true);
//		stream.magnoFilter("pacman_magno_mask2.mp4", "/home/grangerlab/workspace/chris/pacman_color_magno.mp4");
		stream.origMagnoFilter("pacman_overlay2.mp4", "/home/grangerlab/Desktop/pacman/Pacman_Java_cropped.mp4");
		stream.updateRender("/home/grangerlab/Desktop/pacman/Pacman_Java_cropped_arc.zip", "/home/grangerlab/Desktop/pacman/Pacman_Java_cropped.mp4");
	}
	
	
}

package test;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import boofcv.alg.filter.binary.BinaryImageOps;
import boofcv.alg.filter.binary.Contour;
import boofcv.alg.filter.binary.ThresholdImageOps;
import boofcv.alg.misc.ImageStatistics;
import boofcv.alg.shapes.FitData;
import boofcv.alg.shapes.ShapeFittingOps;
import boofcv.gui.feature.VisualizeFeatures;
import boofcv.gui.feature.VisualizeShapes;
import boofcv.gui.image.ShowImages;
import boofcv.io.image.ConvertBufferedImage;
import boofcv.io.image.SimpleImageSequence;
import boofcv.struct.ConnectRule;
import boofcv.struct.image.GrayF32;
import boofcv.struct.image.GrayU8;
import boofcv.struct.image.ImageGray;
import common.FileIO;
import common.math.Mat;
import common.utils.ImageUtils;
import common.video.VideoFrameWriter;
import common.video.VideoRetina;
import georegression.struct.shapes.EllipseRotated_F64;
import georegression.struct.shapes.Rectangle2D_I32;

@SuppressWarnings("rawtypes")
public class RetinaChannelM< T extends ImageGray, D extends ImageGray> extends FLOPModular {

//	BoxSURFDetector tracker;
	
	@SuppressWarnings("unchecked")
	public RetinaChannelM(FeatureDetector tracker, Class imageType, String outLoc, int vocabsize) {
		super(tracker, imageType, outLoc, vocabsize);
	}

	@SuppressWarnings({ "unchecked", "unused" })
	public void streamMagnoParvo(String stemLoc, String vidLoc, boolean color) {
		
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
	
	@SuppressWarnings({ "unchecked", "unused" })
	public void magnoFilter(String stemLoc, String vidLoc) {

		SimpleImageSequence<T> sequence = processVideo(vidLoc);

		int height = sequence.getNextHeight();
		int width = sequence.getNextWidth(); 
		
		VideoFrameWriter mask = new VideoFrameWriter(new File(stemLoc), width, height, 30);
		VideoRetina retina = new VideoRetina(width, height, false);

		mask.OpenFile();
		
		gui.setPreferredSize(new Dimension(width, height));
		ShowImages.showWindow(gui,CatGetter.extract(vidLoc), true);
		
		T frame;
		int frames = 0;
		while(sequence.hasNext()) {
			frame = sequence.next();
			BufferedImage newFrame = sequence.getGuiImage();
//			BufferedImage contrastFrame = RGBModel.maxContrast(newFrame);
			retina.ProcessFrame(newFrame);
			
			BufferedImage currFrame = retina.getMagno();
			
//			BufferedImage contrastFrame = RGBModel.maxContrast(currFrame);
			
			byte grayscale[][] = ImageUtils.BufferedImage2Grayscale(currFrame);
			int gray[][] = Mat.Unsigned2Int(grayscale);
			for(int i = 0; i < gray.length; i++) {
				for(int j = 0; j < gray[i].length; j++) {
//
					if(gray[i][j] < 100) gray[i][j] = 255;
					else gray[i][j] = 0;
//					
//					gray[i][j] = 255 - gray[i][j];
					
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
	 			
			// Fit a box to each external contour and draw the results
			
			Graphics2D g2 = newFrame.createGraphics();
			
			g2.setStroke(new BasicStroke(3));
			g2.setColor(Color.RED);
//	 		
//			if(contours.size() < 10) {
				for( Contour c : contours ) {
					FitData<EllipseRotated_F64> ellipse = ShapeFittingOps.fitEllipse_I32(c.external,0,false,null);
//					if(ellipse.shape.a < 10 || ellipse.shape.b < 10 || ellipse.shape.a > 80 || ellipse.shape.b > 80 || ellipse.shape.a/ellipse.shape.b > 4 || ellipse.shape.b/ellipse.shape.a > 4) continue;
//					VisualizeShapes.drawEllipse(ellipse.shape, g2);
					
					int boxsize = Integer.max((int) ellipse.shape.a, (int) ellipse.shape.b); //distance from center to perpendicular edge
					if(boxsize < 6) continue;
					Rectangle2D_I32 box = new Rectangle2D_I32((int) ellipse.shape.center.x - boxsize, (int) ellipse.shape.center.y - boxsize, (int) ellipse.shape.center.x + boxsize, (int) ellipse.shape.center.y + boxsize);
					
					VisualizeShapes.drawRectangle(box, g2);
				}
//			}
//			
			mask.ProcessFrame(newFrame);

			System.out.println("Finished processing frame " + frames + ", contours: " + contours.size());
			frames++;
			
			gui.setBufferedImage(newFrame);	
			gui.repaint();
		}
		
		mask.Close();
		
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
		
		VideoFrameWriter mask = new VideoFrameWriter(new File(CatGetter.process(vidLoc)), width, height, 30);
		VideoRetina retina = new VideoRetina(width, height, false);
		
		gui.setPreferredSize(new Dimension(width, height));
		ShowImages.showWindow(gui,CatGetter.extract(vidLoc), true);
		
		mask.OpenFile();

		
		// Figure out how large the GUI window should be
		T frame = sequence.next();
//		BufferedImage newthing = new BufferedImage(width/2, height/2, frame.imageType);
//		Graphics g = newthing.getGraphics();
//		g.drawImage()
		
		// process each frame in the image sequence
		while( sequence.hasNext() ) {
			frame = sequence.next();
			BufferedImage newFrame = sequence.getGuiImage();
			
//			BufferedImage contrastFrame = RGBModel.maxContrast(newFrame);
			retina.ProcessFrame(newFrame);
			
			BufferedImage currFrame = retina.getMagno();
					
			byte grayscale[][] = ImageUtils.BufferedImage2Grayscale(currFrame);
			int gray[][] = Mat.Unsigned2Int(grayscale);
			for(int i = 0; i < gray.length; i++) {
				for(int j = 0; j < gray[i].length; j++) {
					if(gray[i][j] < 100) gray[i][j] = 255;
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
//	 		
			ArrayList<Rectangle2D_I32> boxes = new ArrayList<Rectangle2D_I32>();
			
//			if(contours.size() < 10) {
				for( Contour c : contours ) {
					FitData<EllipseRotated_F64> ellipse = ShapeFittingOps.fitEllipse_I32(c.external,0,false,null);
//					if(ellipse.shape.a < 10 || ellipse.shape.b < 10 || ellipse.shape.a > 80 || ellipse.shape.b > 80 || ellipse.shape.a/ellipse.shape.b > 4 || ellipse.shape.b/ellipse.shape.a > 4) continue;
//					VisualizeShapes.drawEllipse(ellipse.shape, g2);
					
					int boxsize = Integer.max((int) ellipse.shape.a, (int) ellipse.shape.b); //distance from center to perpendicular edge
					Rectangle2D_I32 box = new Rectangle2D_I32((int) ellipse.shape.center.x - boxsize, (int) ellipse.shape.center.y - boxsize, (int) ellipse.shape.center.x + boxsize, (int) ellipse.shape.center.y + boxsize);
					boxes.add(box);
//					VisualizeShapes.drawRectangle(box, g2);
				}
//			}	
			BoxSURFDetector det = (BoxSURFDetector) tracker;
//			BoxDetectDescribeAssociate innertrack = (BoxDetectDescribeAssociate) det.tracker;			
			BoxPointTrackerKltPyramid innertrack = (BoxPointTrackerKltPyramid) det.tracker;
			innertrack.setBoxes(boxes);
			System.out.println(boxes);
			tracker.process(frame);
			
			ArrayList<FeaturePosition> feats = tracker.getFrameFeatures();
			for(FeaturePosition ft: feats) {
				VisualizeFeatures.drawPoint(g2, (int) ft.x, (int) ft.y, Color.GREEN);
			}
			
//			VisualizeFeatures.drawPoint(g2, 1422, 175, Color.ORANGE);

			
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
		
		mask.Close();
		FileIO.SaveObject(new File(CatGetter.stem(vidLoc)), local);

		return;
	}
	
	@SuppressWarnings("unchecked")
	public static void main(String[] args) {
//		RetinaChannelM m = new RetinaChannelM(new BoxSURFDetector(GrayF32.class), GrayF32.class, "pacmanM.mp4", 10);
////		m.changeFPS(4);
//		m.batch("/home/grangerlab/Desktop/pacman2");
//		m.streamMagnoParvo("pac_clust", "/home/grangerlab/workspace/chris/pacmanM.mp4", true);
//		m.streamMagnoParvo("car", "/home/grangerlab/Desktop/watchdog1.avi", true);
		
		RetinaChannelM g = new RetinaChannelM(new BoxSURFDetector(GrayF32.class), GrayF32.class, "intersect3.mp4", 20);
//		g.streamMagnoParvo("highway", "/home/grangerlab/Desktop/highway/carspass.avi", true);
//		g.magnoFilter("boxbig.mp4", "/home/grangerlab/Desktop/highway/carspass.avi");
//		g.magnoFilter("inter.mp4", "/home/grangerlab/Desktop/inter/intersection.m4v");
		g.batch("/home/grangerlab/Desktop/inter");

		
//		g.streamMagnoParvo("inter", "/home/grangerlab/Desktop/inter/intersection.m4v", true);
	}
	
}

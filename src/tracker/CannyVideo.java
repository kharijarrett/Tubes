package tracker;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JFrame;

import org.math.plot.Plot3DPanel;

import boofcv.alg.feature.detect.edge.CannyEdge;
import boofcv.alg.feature.detect.edge.EdgeContour;
import boofcv.alg.filter.binary.BinaryImageOps;
import boofcv.alg.filter.binary.Contour;
import boofcv.alg.filter.binary.ThresholdImageOps;
import boofcv.alg.misc.ImageStatistics;
import boofcv.alg.shapes.ShapeFittingOps;
import boofcv.factory.feature.detect.edge.FactoryEdgeDetectors;
import boofcv.gui.binary.VisualizeBinaryData;
import boofcv.gui.image.ImagePanel;
import boofcv.gui.image.ShowImages;
import boofcv.io.MediaManager;
import boofcv.io.UtilIO;
import boofcv.io.image.ConvertBufferedImage;
import boofcv.io.image.SimpleImageSequence;
import boofcv.io.wrapper.DefaultMediaManager;
import boofcv.struct.ConnectRule;
import boofcv.struct.PointIndex_I32;
import boofcv.struct.image.GrayF32;
import boofcv.struct.image.GrayS16;
import boofcv.struct.image.GrayU8;
import boofcv.struct.image.ImageType;
import common.FileIO;
import common.UI;
import common.math.Mat;
import common.utils.ColorUtils;
import common.utils.ImageUtils;
import common.utils.ColorUtils.ColorMap;
import common.video.VideoFrameReader;
import common.video.VideoFrameWriter;
import common.video.VideoRetina;
import georegression.struct.point.Point2D_I32;
import javafx.geometry.Point3D;

public class CannyVideo {
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static VideoFrameReader processVideo(String vidPath) {

		//Class imageType = GrayF32.class;
		//MediaManager media = DefaultMediaManager.INSTANCE;
		VideoFrameReader sequence = new VideoFrameReader(vidPath);
		

		return sequence;
	}
	/*
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static SimpleImageSequence processVideo(String vidPath) {

		Class imageType = GrayF32.class;
		MediaManager media = DefaultMediaManager.INSTANCE;
		SimpleImageSequence sequence = media.openVideo(UtilIO.pathExample(vidPath), ImageType.single(imageType)); 
		sequence.setLoop(false);

		return sequence;
	}
	*/
	
	@SuppressWarnings({ "unchecked", "unused" })
	public static void cannyStream(String stemLoc, String vidLoc) {

		// displays the video sequence and tracked features
		ImagePanel gui = new ImagePanel();
		
		SimpleImageSequence<GrayF32> sequence = (SimpleImageSequence<GrayF32>) processVideo(vidLoc);

		int height = sequence.getNextHeight();
		int width = sequence.getNextWidth(); 
		
		VideoFrameWriter mask = new VideoFrameWriter(new File(stemLoc), width, height, 5);
		VideoRetina retina = new VideoRetina(width, height, true);
		
		mask.OpenFile();
		
		gui.setPreferredSize(new Dimension(width, height));
		ShowImages.showWindow(gui,CatGetter.extract(vidLoc), true);
		
		GrayF32 frame;
		int frames = 0;
		while(sequence.hasNext()) {
			frame = sequence.next();
			BufferedImage newFrame = sequence.getGuiImage();
			retina.ProcessFrame(newFrame);
			
			BufferedImage parvo = retina.getParvo();

			byte grayscale[][] = ImageUtils.BufferedImage2Grayscale(parvo);
			int gray2[][] = Mat.Unsigned2Int(grayscale);
			for(int i = 0; i < gray2.length; i++) {
				for(int j = 0; j < gray2[i].length; j++) {
//					gray2[i][j] = 255 - gray2[i][j];
					if(gray2[i][j] < 160) gray2[i][j] = 255; // was 180 pacman
					else gray2[i][j] = 0;
				}
			}
//			
			grayscale = Mat.Int2Unsigned(gray2);
			
			parvo = ImageUtils.Grayscale2BufferedImage(grayscale, 255);
			
			GrayU8 gray = ConvertBufferedImage.convertFrom(parvo,(GrayU8)null);
			GrayU8 edgeImage = gray.createSameShape();
	 
			
			
			// Create a canny edge detector which will dynamically compute the threshold based on maximum edge intensity
			// It has also been configured to save the trace as a graph.  This is the graph created while performing
			// hysteresis thresholding.
			CannyEdge<GrayU8,GrayS16> canny = FactoryEdgeDetectors.canny(2,true, true, GrayU8.class, GrayS16.class);
	 
			// The edge image is actually an optional parameter.  If you don't need it just pass in null
			canny.process(gray,0.1f,0.3f,edgeImage);
	 
			// First get the contour created by canny
			List<EdgeContour> edgeContours = canny.getContours();
			// The 'edgeContours' is a tree graph that can be difficult to process.  An alternative is to extract
			// the contours from the binary image, which will produce a single loop for each connected cluster of pixels.
			// Note that you are only interested in external contours.
			List<Contour> contours = BinaryImageOps.contour(edgeImage, ConnectRule.EIGHT, null);
	 
			// display the results
			BufferedImage visualBinary = VisualizeBinaryData.renderBinary(edgeImage, false, null);
			BufferedImage visualCannyContour = VisualizeBinaryData.renderContours(edgeContours,null,
					gray.width,gray.height,null);
			BufferedImage visualEdgeContour = new BufferedImage(gray.width, gray.height,BufferedImage.TYPE_INT_RGB);
			VisualizeBinaryData.renderExternal(contours, (int[]) null, visualEdgeContour);
			
			
			System.out.println("Finished processing frame " + frames);
			frames++;
			
			mask.ProcessFrame(visualEdgeContour);
			gui.setBufferedImage(parvo);	
			gui.repaint();
			
//			BoofMiscOps.pause(1000);
		}
		
		mask.Close();
		
	}
	
	@SuppressWarnings("unchecked")
	public static ArrayList<ArrayList<Point2D_I32>> canny2(String stemLoc, String vidLoc, int startframe, int endframe) {

		ArrayList<ArrayList<Point2D_I32>> pointframes = new ArrayList<ArrayList<Point2D_I32>>();
		
		// displays the video sequence and tracked features
		ImagePanel gui = new ImagePanel();
				
		SimpleImageSequence<GrayF32> sequence = (SimpleImageSequence<GrayF32>) processVideo(vidLoc);

		int height = sequence.getNextHeight();
		int width = sequence.getNextWidth(); 
		
		double splitFraction = 0.05;
		double minimumSideFraction = 0.1;
				
		VideoFrameWriter mask = new VideoFrameWriter(new File(stemLoc), width, height, 5);
		VideoRetina retina = new VideoRetina(width, height, true);
				
		mask.OpenFile();
				
		gui.setPreferredSize(new Dimension(width, height));
		ShowImages.showWindow(gui,CatGetter.extract(vidLoc), true);
				
		GrayF32 frame;
		int frames = 0;
		while(sequence.hasNext()) {
			
			if(frames < startframe) {
				frames++;
				continue;
			}
			if(frames > endframe) break;
			
			ArrayList<Point2D_I32> points = new ArrayList<Point2D_I32>();
			
			frame = sequence.next();
			
			GrayF32 input = frame;
			BufferedImage newFrame = sequence.getGuiImage();
			retina.ProcessFrame(newFrame);
					
			BufferedImage parvo = retina.getParvo();
			
			byte grayscale[][] = ImageUtils.BufferedImage2Grayscale(parvo);
			int gray[][] = Mat.Unsigned2Int(grayscale);
			for(int i = 0; i < gray.length; i++) {
				for(int j = 0; j < gray[i].length; j++) {
//					gray[i][j] = 255 - gray[i][j];
					if(gray[i][j] < 180) gray[i][j] = 255; // was 180 pacman
					else gray[i][j] = 0;
				}
			}
//			
			grayscale = Mat.Int2Unsigned(gray);
			
			GrayU8 binary = new GrayU8(input.width,input.height);
			BufferedImage polygon = new BufferedImage(input.width,input.height,BufferedImage.TYPE_INT_RGB);
			
			BufferedImage dst = ImageUtils.Grayscale2BufferedImage(grayscale, 255);

			input = ConvertBufferedImage.convertFromSingle(parvo, null, GrayF32.class);
			
			// the mean pixel value is often a reasonable threshold when creating a binary image
			double mean = ImageStatistics.mean(input);
	 
			// create a binary image by thresholding
			ThresholdImageOps.threshold(input, binary, (float) mean, true);
	 
			// reduce noise with some filtering
			GrayU8 filtered = BinaryImageOps.erode8(binary, 1, null);
			filtered = BinaryImageOps.dilate8(filtered, 1, null);
			
			// Find the contour around the shapes
			List<Contour> contours = BinaryImageOps.contour(filtered, ConnectRule.EIGHT,null);
	 
			// Fit a polygon to each shape and draw the results
			Graphics2D g2 = polygon.createGraphics();
			g2.setStroke(new BasicStroke(2));
			
//			System.out.println(contours.size());
//			for( Contour c : contours ) {
//				// Fit the polygon to the found external contour.  Note loop = true
//				List<PointIndex_I32> vertexes = ShapeFittingOps.fitPolygon(c.external,true,
//						splitFraction, minimumSideFraction,100);
	// 
//				g2.setColor(Color.RED);
//				VisualizeShapes.drawPolygon(vertexes,true,g2);
	// 
//				// handle internal contours now
//				g2.setColor(Color.BLUE);
//				for( List<Point2D_I32> internal : c.internal ) {
//					vertexes = ShapeFittingOps.fitPolygon(internal,true, splitFraction, minimumSideFraction,100);
//					VisualizeShapes.drawPolygon(vertexes,true,g2);
//				}
//			}		
			int size = 0;
			for( Contour c : contours ) {
				
				List<PointIndex_I32> poly = ShapeFittingOps.fitPolygon(c.external,true,
				splitFraction, minimumSideFraction,100);
				int x[] = new int[poly.size()];
				int y[] = new int[poly.size()];
				for(int i = 0; i < poly.size(); i++) {
					x[i] = poly.get(i).x;
					y[i] = poly.get(i).y;
				}
				Polygon fit = new Polygon(x, y, poly.size());
				
				if(!fit.contains(input.width/2, input.height/2)) continue;
				
//				size += c.external.size();
				for( List<Point2D_I32> q : c.internal) {
					size += q.size();
				}
			}
			
			int target = size/50;
			if(target < 1) target = 1;
			
			int marker = 0;
			int ctr = 0;
			
			
			for( Contour c : contours ) {

				List<PointIndex_I32> poly = ShapeFittingOps.fitPolygon(c.external,true,
				splitFraction, minimumSideFraction,100);
				int x[] = new int[poly.size()];
				int y[] = new int[poly.size()];
				for(int i = 0; i < poly.size(); i++) {
					x[i] = poly.get(i).x;
					y[i] = poly.get(i).y;
				}
				Polygon fit = new Polygon(x, y, poly.size());
				
				if(!fit.contains(input.width/2, input.height/2)) continue;			
				for( Point2D_I32 p : c.external) {
//					if(ctr%target == 0) polygon.setRGB(p.x,p.y,10000000);
//					ctr++;
				}
				for( List<Point2D_I32> q : c.internal) {
					for(Point2D_I32 p : q) {
//						if(ctr%target == 0) polygon.setRGB(p.x,p.y,100000);
//						if(ctr%target == 0) polygon.setRGB(p.x,p.y,(int) (100000*Math.random()));
//						if(ctr % target == 0 && marker < 50) {
						if(ctr == (int) Math.ceil(marker * size / 70)) {
							points.add(p);
							marker++;
							polygon.setRGB(p.x,p.y,100000);
						}
						ctr++;		
					}
				}
			}
			
			mask.ProcessFrame(polygon);
			gui.setBufferedImage(polygon);	
			gui.repaint();
			
//			System.out.println("Finished processing frame " + frames);
//			System.out.println("markers: " + marker);
			frames++;
			
			pointframes.add(points);
		}
				
		mask.Close();
		return pointframes;
		
	}
	
	public static ArrayList<ArrayList<Point2D_I32>> cannyNoVideo(String vidLoc) {

		ArrayList<ArrayList<Point2D_I32>> pointframes = new ArrayList<ArrayList<Point2D_I32>>();
				
		SimpleImageSequence<GrayF32> sequence = (SimpleImageSequence<GrayF32>) processVideo(vidLoc);

		int height = sequence.getNextHeight();
		int width = sequence.getNextWidth(); 
		
		double splitFraction = 0.05;
		double minimumSideFraction = 0.1;
				
		VideoRetina retina = new VideoRetina(width, height, true);
				
		GrayF32 frame;
		int frames = 0;
		while(sequence.hasNext()) {
			
			ArrayList<Point2D_I32> points = new ArrayList<Point2D_I32>();
			
			frame = sequence.next();
			
			GrayF32 input = frame;
			BufferedImage newFrame = sequence.getGuiImage();
			retina.ProcessFrame(newFrame);
					
			BufferedImage parvo = retina.getParvo();
			
			byte grayscale[][] = ImageUtils.BufferedImage2Grayscale(parvo);
			int gray[][] = Mat.Unsigned2Int(grayscale);
			for(int i = 0; i < gray.length; i++) {
				for(int j = 0; j < gray[i].length; j++) {
//					gray[i][j] = 255 - gray[i][j];
					if(gray[i][j] < 180) gray[i][j] = 255; // was 180 pacman
					else gray[i][j] = 0;
				}
			}
//			
			grayscale = Mat.Int2Unsigned(gray);
			
			GrayU8 binary = new GrayU8(input.width,input.height);
			BufferedImage polygon = new BufferedImage(input.width,input.height,BufferedImage.TYPE_INT_RGB);
			
			BufferedImage dst = ImageUtils.Grayscale2BufferedImage(grayscale, 255);

			input = ConvertBufferedImage.convertFromSingle(parvo, null, GrayF32.class);
			
			// the mean pixel value is often a reasonable threshold when creating a binary image
			double mean = ImageStatistics.mean(input);
	 
			// create a binary image by thresholding
			ThresholdImageOps.threshold(input, binary, (float) mean, true);
	 
			// reduce noise with some filtering
			GrayU8 filtered = BinaryImageOps.erode8(binary, 1, null);
			filtered = BinaryImageOps.dilate8(filtered, 1, null);
			
			// Find the contour around the shapes
			List<Contour> contours = BinaryImageOps.contour(filtered, ConnectRule.EIGHT,null);
	 
			// Fit a polygon to each shape and draw the results
			Graphics2D g2 = polygon.createGraphics();
			g2.setStroke(new BasicStroke(2));
	
			int size = 0;
			for( Contour c : contours ) {
				
				List<PointIndex_I32> poly = ShapeFittingOps.fitPolygon(c.external,true,
				splitFraction, minimumSideFraction,100);
				int x[] = new int[poly.size()];
				int y[] = new int[poly.size()];
				for(int i = 0; i < poly.size(); i++) {
					x[i] = poly.get(i).x;
					y[i] = poly.get(i).y;
				}
				Polygon fit = new Polygon(x, y, poly.size());
				
				if(!fit.contains(input.width/2, input.height/2)) continue;
				
//				size += c.external.size();
				for( List<Point2D_I32> q : c.internal) {
					size += q.size();
				}
			}
			
			int target = size/50;
			if(target < 1) target = 1;
			
			int marker = 0;
			int ctr = 0;
			
			
			for( Contour c : contours ) {

				List<PointIndex_I32> poly = ShapeFittingOps.fitPolygon(c.external,true,
				splitFraction, minimumSideFraction,100);
				int x[] = new int[poly.size()];
				int y[] = new int[poly.size()];
				for(int i = 0; i < poly.size(); i++) {
					x[i] = poly.get(i).x;
					y[i] = poly.get(i).y;
				}
				Polygon fit = new Polygon(x, y, poly.size());
				
				if(!fit.contains(input.width/2, input.height/2)) continue;			
				for( Point2D_I32 p : c.external) {
//					if(ctr%target == 0) polygon.setRGB(p.x,p.y,10000000);
//					ctr++;
				}
				for( List<Point2D_I32> q : c.internal) {
					for(Point2D_I32 p : q) {
//						if(ctr%target == 0) polygon.setRGB(p.x,p.y,100000);
//						if(ctr%target == 0) polygon.setRGB(p.x,p.y,(int) (100000*Math.random()));
//						if(ctr % target == 0 && marker < 50) {
//						if(ctr == (int) Math.ceil(marker * size / 500)) {
							points.add(p);
							marker++;
							polygon.setRGB(p.x,p.y,100000);
//						}
//						ctr++;		
					}
				}
			}
			
//			System.out.println("Finished processing frame " + frames);
//			System.out.println("markers: " + marker);
			frames++;
			
			pointframes.add(points);
		}
				
		return pointframes;
		
	}	
	
	@SuppressWarnings("unchecked")
	public static void batch(String folder) {
		
		File dir = new File(folder);
		File[] listOfFiles = dir.listFiles();
		
		ArrayList<String> fq = new ArrayList<String>();
		for (File file : listOfFiles) {
		    if (file.isFile() && (file.getName().toLowerCase().endsWith("_render.mp4") )) {
		    	fq.add(file.getAbsolutePath());
		    }
		}
		
		ArrayList<ShapeContext3D> contextAll = new ArrayList<ShapeContext3D>();
		
		for(int i = 0; i < fq.size(); i++) {
			String currentFilePath = fq.get(i);
	    	System.out.println("PROCESSING VIDEO " + (i+1) + " of " + fq.size() + ", " + currentFilePath);
	    	
	    	ArrayList<ShapeContext3D> contexts;
	    	
	    	String arcLoc = CatGetter.constructArc(currentFilePath);
			File f = new File(arcLoc);
			if(f.exists() && !f.isDirectory()) {
				System.out.println("Archive at " + arcLoc + " found, processing that instead...");
				
				contexts = (ArrayList<ShapeContext3D>) FileIO.LoadObject(new File(arcLoc));
				
			}
			else {
		    	ArrayList<ArrayList<Point2D_I32>> pacshots = cannyNoVideo(currentFilePath);
				ArrayList<Point3D> timepoints = ShapeContext3D.computeTimePoints(pacshots, 1);		
				contexts = ShapeContext3D.computeContextSlices(timepoints);	
				FileIO.SaveObject(new File(CatGetter.stem(currentFilePath)), contexts);
			}

//			contextAll.addAll(contexts);

		}

//		int bagoffeatures[][] = new int[contextAll.size()][360];
//		for(int i = 0; i < contextAll.size(); i++) {
//			bagoffeatures[i] = contextAll.get(i).vector();
//		}
//		
//		FileIO.SaveCSV(bagoffeatures, new File("fullbagoffeatures.csv"));
		
		return;
		
	}
	
	public static void main(String[] args) {
//		ArrayList<ArrayList<Point2D_I32>> pacshots = canny2("pac_f269_l312_canny.mp4", "/home/grangerlab/workspace/chris/boxdos/pac_f269_l312_render.mp4");
//
//		ArrayList<ShapeContext> s1 = ShapeContext.computeContexts(pacshots.get(0));
//		ArrayList<ShapeContext> s2 = ShapeContext.computeContexts(pacshots.get(20));
//		
//		double[][] costmatrix = ShapeContext.costmatrix(s1, s2);
//		HungarianAlgorithm ha = new HungarianAlgorithm(costmatrix);
//		int[] output = ha.execute();
//		for(int i = 0; i < output.length; i++) {
//			System.out.print(output[i] + " ");
//		}
//		System.out.println("done");
//		
//		ImagePanel gui = new ImagePanel();
//		gui.setPreferredSize(new Dimension(800, 800));
//		BufferedImage polygon = new BufferedImage(1000, 1000,BufferedImage.TYPE_INT_RGB);
//		Graphics2D g2 = polygon.createGraphics();
//		g2.setColor(Color.RED);
//		for(ShapeContext sc : s1) {
//			g2.drawString("x", sc.x*5, sc.y*5);
//		}
//		g2.setColor(Color.BLUE);
//		for(ShapeContext sc : s2) {
//			g2.drawString("o", sc.x*5, sc.y*5);
//		}
//		g2.setColor(Color.GREEN);
//		for(int i = 0; i < output.length; i++) {
//			g2.drawLine(s1.get(i).x*5, s1.get(i).y*5, s2.get(output[i]).x*5, s2.get(output[i]).y*5);
//		}
//		
//		ShowImages.showWindow(gui, "match", true);
//		gui.setBufferedImage(polygon);	

		
//		ArrayList<ArrayList<Point2D_I32>> pacshots = canny2("pac_f269_l312_canny.mp4", "/home/grangerlab/workspace/chris/boxdos/pac_f269_l312_render.mp4", 50, 99);
//		ArrayList<Point3D> timepoints = ShapeContext3D.computeTimePoints(pacshots, 1);
//		Plot3DPanel plot = new Plot3DPanel();
		
//		double X[] = new double[timepoints.size()];
//		double Y[] = new double[timepoints.size()];
//		double Z[] = new double[timepoints.size()];
	
//		for(int i = 0; i < timepoints.size(); i++) {
//			X[i] = timepoints.get(i).getX();
//			Y[i] = timepoints.get(i).getY();
//			Z[i] = timepoints.get(i).getZ();
//		}
//		
//		plot.addScatterPlot("ShapeContext3D", X, Y, Z);

//		for(int i = 0; i < timepoints.size(); i++) {
//			
//			if(timepoints.get(i).getZ() < 40 || timepoints.get(i).getZ() > 90) continue;
//			
//			double X[] = new double[1];
//			double Y[] = new double[1];
//			double T[] = new double[1];
//			
//			X[0] = timepoints.get(i).getX();
//			Y[0] = timepoints.get(i).getY();
//			T[0] = timepoints.get(i).getZ();
//			
//			int rgb[] = ColorUtils.ColorMapRGB(ColorMap.JET, (double) i/timepoints.size());
//			Color color = new Color(rgb[0], rgb[1], rgb[2]);
//			
//			plot.addScatterPlot("ShapeContext3D", color, X, Y, T);		
//
//		}
//		
//		
//		JFrame frame = new JFrame("a plot panel");
//		frame.setSize(600, 600);
//		frame.setContentPane(plot);
//		frame.setVisible(true);

		batch("/home/grangerlab/workspace/chris/pac_all");
		
//		ArrayList<ArrayList<Point2D_I32>> pacshots = canny2("pac_f269_l312_canny.mp4", "/home/grangerlab/workspace/chris/boxdos/pac_f269_l312_render.mp4", -1, 10000);
//		UI.tic();
//
//		ArrayList<Point3D> timepoints = ShapeContext3D.computeTimePoints(pacshots, 1);		
////		ShapeContext3D.computeOneContext(timepoints, timepoints.size()/2 + 10);
//		
//		ArrayList<ShapeContext3D> contexts = ShapeContext3D.computeContextSlices(timepoints);
//		System.out.println(UI.toc());
//
//		int bagoffeatures[][] = new int[contexts.size()][360];
//		for(int i = 0; i < contexts.size(); i++) {
//			bagoffeatures[i] = contexts.get(i).vector();
//		}
//		
//		FileIO.SaveCSV(bagoffeatures, new File("bagoffeatures.csv"));
		
//		ArrayList<ArrayList<Point2D_I32>> pacshots2 = canny2("pac_f269_l312_canny.mp4", "/home/grangerlab/workspace/chris/boxdos/pac_f269_l312_render.mp4", 100, 109);
//		UI.tic();
//
//		ArrayList<Point3D> timepoints2 = ShapeContext3D.computeTimePoints(pacshots2, 1);		
//		ArrayList<ShapeContext3D> contexts2 = ShapeContext3D.computeContexts(timepoints2);
//		
//		System.out.println(UI.toc());
//
//		
//		UI.tic();
//		double[][] costmatrix = ShapeContext3D.costmatrix(contexts, contexts2);
//		HungarianAlgorithm ha = new HungarianAlgorithm(costmatrix);		
//		int[] output = ha.execute();
//		System.out.println(UI.toc());
//		for(int i = 0; i < output.length; i++) {
//			System.out.print(output[i] + " ");
//		}
//		
//		System.out.println("done");
		
		
//		System.out.println("sanity check");
//		System.out.println(contexts.get(timepoints.size()/2 + 20));
//		
//		canny2("pac_f57_l290_canny.mp4","/home/grangerlab/workspace/chris/boxdos/pac_f57_l290_render.mp4");
//		cannyStream("inter_edge.mp4", "/home/grangerlab/Desktop/inter/intersection.m4v");
//		cannyStream("car.mp4", "/home/grangerlab/Desktop/watchdog1.avi");
	}
	
}

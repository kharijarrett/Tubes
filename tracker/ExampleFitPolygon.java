package tracker;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Random;

import boofcv.alg.feature.detect.edge.CannyEdge;
import boofcv.alg.feature.detect.edge.EdgeContour;
import boofcv.alg.feature.detect.edge.EdgeSegment;
import boofcv.alg.filter.binary.BinaryImageOps;
import boofcv.alg.filter.binary.Contour;
import boofcv.alg.filter.binary.ThresholdImageOps;
import boofcv.alg.misc.ImageStatistics;
import boofcv.alg.shapes.ShapeFittingOps;
import boofcv.core.image.ConvertImage;
import boofcv.factory.feature.detect.edge.FactoryEdgeDetectors;
import boofcv.gui.ListDisplayPanel;
import boofcv.gui.feature.VisualizeShapes;
import boofcv.gui.image.ShowImages;
import boofcv.io.UtilIO;
import boofcv.io.image.ConvertBufferedImage;
import boofcv.io.image.UtilImageIO;
import boofcv.struct.ConnectRule;
import boofcv.struct.PointIndex_I32;
import boofcv.struct.image.GrayF32;
import boofcv.struct.image.GrayU8;
import common.math.Mat;
import common.utils.ImageUtils;
import common.video.VideoRetina;
import georegression.struct.point.Point2D_I32;

/**
 * Demonstration of how to convert a point sequence describing an objects outline/contour into a sequence of line
 * segments.  Useful when analysing shapes such as squares and triangles or when trying to simply the low level
 * pixel output.
 *
 * @author Peter Abeles
 */
public class ExampleFitPolygon {
 
	// Polynomial fitting tolerances
	static double splitFraction = 0.05;
	static double minimumSideFraction = 0.1;
 
	static ListDisplayPanel gui = new ListDisplayPanel();
 
	/**
	 * Fits polygons to found contours around binary blobs.
	 */
	public static void fitBinaryImage(GrayF32 input) {
		
		BufferedImage dst = new BufferedImage(input.width,input.height,BufferedImage.TYPE_INT_RGB);
		ConvertBufferedImage.convertTo(input, dst);
		byte grayscale[][] = ImageUtils.BufferedImage2Grayscale(dst);
		int gray[][] = Mat.Unsigned2Int(grayscale);
		for(int i = 0; i < gray.length; i++) {
			for(int j = 0; j < gray[i].length; j++) {
				gray[i][j] = 255 - gray[i][j];
//				if(gray[i][j] < 180) gray[i][j] = 255; // was 180 pacman
//				else gray[i][j] = 0;
			}
		}
//		
		grayscale = Mat.Int2Unsigned(gray);
		
		GrayU8 binary = new GrayU8(input.width,input.height);
		BufferedImage polygon = new BufferedImage(input.width,input.height,BufferedImage.TYPE_INT_RGB);
		
		dst = ImageUtils.Grayscale2BufferedImage(grayscale, 255);
		input = ConvertBufferedImage.convertFromSingle(dst, null, GrayF32.class);
		
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
		
		System.out.println(contours.size());
//		for( Contour c : contours ) {
//			// Fit the polygon to the found external contour.  Note loop = true
//			List<PointIndex_I32> vertexes = ShapeFittingOps.fitPolygon(c.external,true,
//					splitFraction, minimumSideFraction,100);
// 
//			g2.setColor(Color.RED);
//			VisualizeShapes.drawPolygon(vertexes,true,g2);
// 
//			// handle internal contours now
//			g2.setColor(Color.BLUE);
//			for( List<Point2D_I32> internal : c.internal ) {
//				vertexes = ShapeFittingOps.fitPolygon(internal,true, splitFraction, minimumSideFraction,100);
//				VisualizeShapes.drawPolygon(vertexes,true,g2);
//			}
//		}		
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
			
			size += c.external.size();
			for( List<Point2D_I32> q : c.internal) {
				size += q.size();
			}
		}
		
		int target = 3;
		if(target < 1) target = 1;
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
				if(ctr%target == 0) polygon.setRGB(p.x,p.y,10000000);
				ctr++;
			}
			for( List<Point2D_I32> q : c.internal) {
				for(Point2D_I32 p : q) {
					if(ctr%target == 0) polygon.setRGB(p.x,p.y,100000);
					ctr++;					
				}
			}
		}
		
		
		gui.addImage(polygon, "Binary Blob Contours");
	}
 
	/**
	 * Fits a sequence of line-segments into a sequence of points found using the Canny edge detector.  In this case
	 * the points are not connected in a loop. The canny detector produces a more complex tree and the fitted
	 * points can be a bit noisy compared to the others.
	 */
	public static void fitCannyEdges( GrayF32 input ) {
 
		BufferedImage displayImage = new BufferedImage(input.width,input.height,BufferedImage.TYPE_INT_RGB);
 
		// Finds edges inside the image
		CannyEdge<GrayF32,GrayF32> canny =
				FactoryEdgeDetectors.canny(2, true, true, GrayF32.class, GrayF32.class);
 
		canny.process(input,0.1f,0.3f,null);
		List<EdgeContour> contours = canny.getContours();
 
		Graphics2D g2 = displayImage.createGraphics();
		g2.setStroke(new BasicStroke(2));
 
		// used to select colors for each line
		Random rand = new Random(234);
 
		for( EdgeContour e : contours ) {
			g2.setColor(new Color(rand.nextInt()));
 
			for(EdgeSegment s : e.segments ) {
				// fit line segments to the point sequence.  Note that loop is false
				List<PointIndex_I32> vertexes = ShapeFittingOps.fitPolygon(s.points,true,
						splitFraction, minimumSideFraction,100);
 
				VisualizeShapes.drawPolygon(vertexes, false, g2);
			}
		}
 
		gui.addImage(displayImage, "Canny Trace");
	}
 
	/**
	 * Detects contours inside the binary image generated by canny.  Only the external contour is relevant. Often
	 * easier to deal with than working with Canny edges directly.
	 */
	public static void fitCannyBinary( GrayF32 input ) {
 
		BufferedImage displayImage = new BufferedImage(input.width,input.height,BufferedImage.TYPE_INT_RGB);
		GrayU8 binary = new GrayU8(input.width,input.height);
 
		// Finds edges inside the image
		CannyEdge<GrayF32,GrayF32> canny =
				FactoryEdgeDetectors.canny(2, false, true, GrayF32.class, GrayF32.class);
 
		canny.process(input,0.1f,0.3f,binary);
 
		List<Contour> contours = BinaryImageOps.contour(binary, ConnectRule.EIGHT, null);
 
		Graphics2D g2 = displayImage.createGraphics();
		g2.setStroke(new BasicStroke(2));
 
		// used to select colors for each line
		Random rand = new Random(234);
 
		for( Contour c : contours ) {
			// Only the external contours are relevant.
			List<PointIndex_I32> vertexes = ShapeFittingOps.fitPolygon(c.external,true,
					splitFraction, minimumSideFraction,100);
 
			g2.setColor(new Color(rand.nextInt()));
			VisualizeShapes.drawPolygon(vertexes,true,g2);
		}
 
		gui.addImage(displayImage, "Canny Contour");
	}
 
	public static void main( String args[] ) {
		// load and convert the image into a usable format
		BufferedImage image = UtilImageIO.loadImage(UtilIO.pathExample("/home/grangerlab/Pictures/pac.png"));
		VideoRetina retina = new VideoRetina(image.getWidth(), image.getHeight(), true);
		retina.ProcessFrame(image);
		image = retina.getParvo();
		GrayF32 input = ConvertBufferedImage.convertFromSingle(RGBModel.maxContrast(image), null, GrayF32.class);
 
		gui.addImage(image,"Original");
 
		fitCannyEdges(input);
		fitCannyBinary(input);
		fitBinaryImage(input);
 
		ShowImages.showWindow(gui, "Polygon from Contour", true);
	}
}
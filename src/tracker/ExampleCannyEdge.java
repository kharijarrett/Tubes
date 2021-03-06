package tracker;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;

import boofcv.alg.feature.detect.edge.CannyEdge;
import boofcv.alg.feature.detect.edge.EdgeContour;
import boofcv.alg.feature.detect.edge.EdgeSegment;
import boofcv.alg.filter.binary.BinaryImageOps;
import boofcv.alg.filter.binary.Contour;
import boofcv.factory.feature.detect.edge.FactoryEdgeDetectors;
import boofcv.gui.ListDisplayPanel;
import boofcv.gui.binary.VisualizeBinaryData;
import boofcv.gui.image.ShowImages;
import boofcv.io.UtilIO;
import boofcv.io.image.ConvertBufferedImage;
import boofcv.io.image.UtilImageIO;
import boofcv.struct.ConnectRule;
import boofcv.struct.image.GrayS16;
import boofcv.struct.image.GrayU8;
import georegression.struct.point.Point2D_I32;

/**
 * Demonstration of the Canny edge detection algorithm.  In this implementation the output can be a binary image and/or
 * a graph describing each contour.
 *
 * @author Peter Abeles
 */
public class ExampleCannyEdge {
	
	/**
	 * Draws contours. Internal and external contours are different user specified colors.
	 *
	 * @param contours List of contours
	 * @param colorExternal RGB color
	 * @param colorInternal RGB color
	 * @param width Image width
	 * @param height Image height
	 * @param out (Optional) storage for output image
	 * @return Rendered contours
	 */
	public static BufferedImage renderContours( List<Contour> contours , int colorExternal, int colorInternal ,
												int width , int height , BufferedImage out) {

		if( out == null ) {
			out = new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);
		} else {
			Graphics2D g2 = out.createGraphics();
			g2.setColor(Color.BLACK);
			g2.fillRect(0,0,width,height);
		}
		int ct = 0;
		for( Contour c : contours ) {
			for(Point2D_I32 p : c.external ) {
				if(Math.random() < 50./649) {
					out.setRGB(p.x,p.y,colorExternal);
					ct++;
				}
			}
			for( List<Point2D_I32> l : c.internal ) {
				for( Point2D_I32 p : l ) {
					if(Math.random() < 50./649) {
						out.setRGB(p.x,p.y,colorInternal);
						ct++;
					}
				}
			}
		}
		System.out.println(ct);
		return out;
	}
 
	public static void main( String args[] ) {
		BufferedImage image = UtilImageIO.loadImage(UtilIO.pathExample("/home/grangerlab/Pictures/watch.png"));
		// "/home/grangerlab/Pictures/ghost.png"
		// "/home/grangerlab/Pictures/pac.png"
		// "/home/grangerlab/Desktop/carshot.png"
		GrayU8 gray = ConvertBufferedImage.convertFrom(RGBModel.maxContrast(image),(GrayU8)null);
		GrayU8 edgeImage = gray.createSameShape();
 
		// Create a canny edge detector which will dynamically compute the threshold based on maximum edge intensity
		// It has also been configured to save the trace as a graph.  This is the graph created while performing
		// hysteresis thresholding.
		CannyEdge<GrayU8,GrayS16> canny = FactoryEdgeDetectors.canny(2,true, true, GrayU8.class, GrayS16.class);
 
		// The edge image is actually an optional parameter.  If you don't need it just pass in null
		canny.process(gray,0.01f,0.3f,edgeImage);
 
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
//		VisualizeBinaryData.renderExternal(contours, (int[]) null, visualEdgeContour);
		renderContours(contours, 100000000, 59990, gray.width,gray.height, visualEdgeContour);
 
		ListDisplayPanel panel = new ListDisplayPanel();
		panel.addImage(RGBModel.maxContrast(image), "enhanced original");
		panel.addImage(visualBinary,"Binary Edges from Canny");
		panel.addImage(visualCannyContour, "Canny Trace Graph");
		panel.addImage(visualEdgeContour,"Contour from Canny Binary");
		ShowImages.showWindow(panel,"Canny Edge", true);
	}
	
	
	
	
}
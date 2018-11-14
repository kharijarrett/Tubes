package tracker;

import java.awt.image.BufferedImage;
import java.io.File;

import boofcv.alg.filter.binary.BinaryImageOps;
import boofcv.alg.filter.binary.GThresholdImageOps;
import boofcv.gui.ListDisplayPanel;
import boofcv.gui.binary.VisualizeBinaryData;
import boofcv.gui.image.ShowImages;
import boofcv.io.UtilIO;
import boofcv.io.image.ConvertBufferedImage;
import boofcv.io.image.UtilImageIO;
import boofcv.struct.image.GrayF32;
import boofcv.struct.image.GrayU8;

/**
 * Simple example showing you how to thin a binary image.  This is also known as skeletonalization.  Thinning
 * discards most of objects foreground (value one) pixels are leaves behind a "skinny" object which still
 * mostly describes the original objects shape.
 *
 * @author Peter Abeles
 */
public class ExampleMorphologicalThinning {
	public static void main(String[] args) {
 
//		String[] images = new String[]{"drawings/drawing_text.png","standard/fingerprint.jpg","drawings/drawing_face.png"};
		String[] images = new String[]{"/home/grangerlab/Desktop/carshot.png", "/home/grangerlab/Pictures/ghost.png", "/home/grangerlab/Pictures/pac.png"};
		
		ListDisplayPanel uberPanel = new ListDisplayPanel();
		for( String path : images ) {
			// load and convert the image into a usable format
			BufferedImage image = UtilImageIO.loadImage(UtilIO.pathExample(path));
 
			// convert into a usable format
			GrayF32 input = ConvertBufferedImage.convertFromSingle(image, null, GrayF32.class);
			GrayU8 binary = new GrayU8(input.width, input.height);
 
			// Fixed threshold is best for B&W images, but the adaptive would improve results for the finger print
			GThresholdImageOps.threshold(input, binary, 120, true);
//			GThresholdImageOps.adaptiveSquare(input, binary, 20,0,true,null,null);
 
			// Tell it to thin the image until there are no more changes
			GrayU8 thinned = BinaryImageOps.thin(binary, -1, null);
 
			// display the results
			BufferedImage visualBinary = VisualizeBinaryData.renderBinary(binary, false, null);
			BufferedImage visualThinned = VisualizeBinaryData.renderBinary(thinned, false, null);
 
			ListDisplayPanel panel = new ListDisplayPanel();
			panel.addImage(visualThinned, "Thinned");
			panel.addImage(visualBinary, "Binary");
			panel.addImage(image, "Original");
 
			uberPanel.addItem(panel,new File(path).getName());
		}
		ShowImages.showWindow(uberPanel, "Thinned/Skeletonalized Images", true);
	}
}
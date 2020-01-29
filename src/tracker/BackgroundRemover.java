package tracker;

import java.awt.image.BufferedImage;

import boofcv.alg.background.BackgroundModelStationary;
import boofcv.factory.background.ConfigBackgroundBasic;
import boofcv.factory.background.ConfigBackgroundGaussian;
import boofcv.factory.background.FactoryBackgroundModel;
import boofcv.gui.binary.VisualizeBinaryData;
import boofcv.gui.image.ImageGridPanel;
import boofcv.gui.image.ShowImages;
import boofcv.io.MediaManager;
import boofcv.io.UtilIO;
import boofcv.io.image.SimpleImageSequence;
import boofcv.io.wrapper.DefaultMediaManager;
import boofcv.struct.image.GrayF32;
import boofcv.struct.image.GrayU8;
import boofcv.struct.image.ImageBase;
import boofcv.struct.image.ImageType;
import common.video.VideoRetina;

/**
 * Example showing how to perform background modeling when the camera is assumed to be stationary.  This scenario
 * can be computed much faster than the moving camera case and depending on the background model can some times produce
 * reasonable results when the camera has a little bit of jitter.
 *
 * @author Peter Abeles
 */
public class BackgroundRemover {
	public static void main(String[] args) {
 
		String fileName = UtilIO.pathExample("/home/grangerlab/Desktop/arvind/output885406.mp4");
//		String fileName = UtilIO.pathExample("background/horse_jitter.mp4"); // degraded performance because of jitter
//		String fileName = UtilIO.pathExample("tracking/chipmunk.mjpeg"); // Camera moves.  Stationary will fail here
 
		// Comment/Uncomment to switch input image type
		ImageType imageType = ImageType.single(GrayF32.class);
//		ImageType imageType = ImageType.il(3, InterleavedF32.class);
//		ImageType imageType = ImageType.il(3, InterleavedU8.class);
 
		// Configuration for Gaussian model.  Note that the threshold changes depending on the number of image bands
		// 12 = gray scale and 40 = color
		ConfigBackgroundGaussian configGaussian = new ConfigBackgroundGaussian(12,0.00005f);
		configGaussian.initialVariance = 100;
		configGaussian.minimumDifference = 10;
 
		// Comment/Uncomment to switch algorithms
		BackgroundModelStationary background =
//				FactoryBackgroundModel.stationaryBasic(new ConfigBackgroundBasic(35, 0.005f), imageType);
				FactoryBackgroundModel.stationaryGaussian(configGaussian, imageType);
 
		MediaManager media = DefaultMediaManager.INSTANCE;
		SimpleImageSequence video =
				media.openVideo(fileName, background.getImageType());
//				media.openCamera(null,640,480,background.getImageType());
 
		// Declare storage for segmented image.  1 = moving foreground and 0 = background
		GrayU8 segmented = new GrayU8(video.getNextWidth(),video.getNextHeight());
 
		BufferedImage visualized = new BufferedImage(segmented.width,segmented.height,BufferedImage.TYPE_INT_RGB);
		ImageGridPanel gui = new ImageGridPanel(1,2);
		gui.setImages(visualized, visualized);
 
		ShowImages.showWindow(gui, "Static Scene: Background Segmentation", true);
 
		double fps = 0;
		double alpha = 0.01; // smoothing factor for FPS
 
		VideoRetina retina = new VideoRetina(video.getNextWidth(),video.getNextHeight(), true);
		
		while( video.hasNext() ) {
			ImageBase input = video.next();
 
			long before = System.nanoTime();
			background.segment(input,segmented);
			background.updateBackground(input);
			long after = System.nanoTime();
 
			fps = (1.0-alpha)*fps + alpha*(1.0/((after-before)/1e9));
 
			VisualizeBinaryData.renderBinary(segmented, false, visualized);
			
			retina.ProcessFrame(visualized);
//			gui.setImage(0, 1, retina.getParvo());
			
			gui.setImage(0, 0, (BufferedImage)video.getGuiImage());
			gui.setImage(0, 1, visualized);
			gui.repaint();
			System.out.println("FPS = "+fps);
 
			try {Thread.sleep(0);} catch (InterruptedException e) {}
		}
		System.out.println("done!");
	}
}

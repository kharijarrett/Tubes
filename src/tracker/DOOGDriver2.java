package tracker;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;

import boofcv.gui.image.ImagePanel;
import boofcv.gui.image.ShowImages;
import boofcv.io.MediaManager;
import boofcv.io.UtilIO;
import boofcv.io.image.SimpleImageSequence;
import boofcv.io.wrapper.DefaultMediaManager;
import boofcv.struct.image.GrayF32;
import boofcv.struct.image.ImageType;
import common.FileIO;
import common.UI;
import common.utils.ColorUtils;
import common.utils.ColorUtils.ColorMap;
import common.video.VideoFrameReader;
import common.video.VideoFrameWriter;
import common.video.VideoRetina;
import common.video.VideoTransformDOOG;

public class DOOGDriver2 {
	
	float imageval[][];
	int width;
	int height;
	String inputVideo;
	ArrayList<float[]> features = new ArrayList<float[]>();

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static SimpleImageSequence processVideo(String vidPath) {

		Class imageType = GrayF32.class;
		MediaManager media = DefaultMediaManager.INSTANCE;
		SimpleImageSequence sequence = media.openVideo(UtilIO.pathExample(vidPath), ImageType.single(imageType)); 
		sequence.setLoop(false);

		return sequence;
		
	}
	
	public void doogfilter(String inputVideo, String outputVideo) {
		
		this.inputVideo = inputVideo;
		
		ImagePanel gui = new ImagePanel();
		SimpleImageSequence<GrayF32> sequence = processVideo(inputVideo);
		width = sequence.getNextWidth();
		height = sequence.getNextHeight();

		BufferedImage currFrame = null;

		VideoTransformDOOGOverride featJavaCVPar = new VideoTransformDOOGOverride(width, height, new int[]{5}, VideoTransformDOOGOverride.ColorMode.GRAYSCALE, VideoTransformDOOGOverride.LibraryChoice.JavaCVParallel, false);
//		int indexlookup[][][][] = new int[width][height][4][3];
		
		VideoFrameWriter mask = new VideoFrameWriter(new File(outputVideo), width, height, 30);				
		mask.OpenFile();
		
		VideoRetina retina = new VideoRetina(width, height, true);
		
		gui.setPreferredSize(new Dimension(width, height));
		ShowImages.showWindow(gui,"updates", true);
		
		
		int i = 0;
		while (sequence.hasNext()) {
			currFrame = sequence.getGuiImage();
			BufferedImage imgCopy = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
			java.awt.Graphics g = imgCopy.getGraphics();
			g.drawImage(currFrame, 0, 0, width, height, null);
			
//			for (int i = 0; i <= frames.size(); i++) {
//				BufferedImage bufferedImageTemp = imgCopy;
				retina.ProcessFrame(imgCopy);
				BufferedImage bufferedImageTemp = retina.getParvo();

				//extract periphery (grayscaled)
				BufferedImage peripheryBI = new BufferedImage(width/2, height/2, BufferedImage.TYPE_BYTE_GRAY);
				java.awt.Graphics g2 = peripheryBI.getGraphics();
				g2.drawImage(bufferedImageTemp, 0, 0, width/2, height/2, null);
				g2.dispose();
				
				BufferedImage skeleton = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
				
				UI.tic();
				featJavaCVPar.ProcessFrame(bufferedImageTemp);
				UI.toc("JavaCV Parallel DOOG");
				float[] scores = featJavaCVPar.getAllFeatures();
				int[] xpos = featJavaCVPar.getPixelXIndex();
				int[] ypos = featJavaCVPar.getPixelYIndex();
				int[] scale = featJavaCVPar.getScaleIndex();
				int[] orientation = featJavaCVPar.getOrientationIndex();
				
				System.out.println("processing frame" + i);
//				System.out.println(parDOOG.length);
//				System.out.println(parDOOG[3]);
//				System.out.println(parDOOG[17]);
				
				imageval = new float[width][height];
//				
//				if(i == 0) {
//					for(int x = 0; x < width; x++) {
//						for(int y = 0; y < height; y++) {
//							imageval[x][y] = -5000;
//						}
//					}
//				}
				float maxval = 0f;
				float minval = Float.MAX_VALUE;
				for(int j = 0; j < scores.length; j++) {
					
					float score = scores[j];
//					if(j >= xpos.length || j >= ypos.length) continue;
					int x = xpos[j];
					int y = ypos[j];
//					if(Math.abs(score) > imageval[x][y]) {
//						imageval[x][y] = Math.abs(score);
//					}
//					if(orientation[j] != 3) continue;
					if(Math.abs(score) > Math.abs(imageval[x][y])) {
						imageval[x][y] = score;
					}
					int s = scale[j];
//					for(int k = -3*(s+1)/2; k <= 3*(s+1)/2; k++) {
//						for(int l = -3*(s+1)/2; l <= 3*(s+1)/2; l++) {
					for(int k = -2; k <= 2; k++) {
						for(int l = -2; l <= 2; l++) {
//					for(int k = -(s+1)/2; k < (s+1)/2; k++) {
//						for(int l = -(s+1)/2; l < (s+1)/2; l++) {
//							if(Math.abs(score) > imageval[x+k][y+l]) imageval[x+k][y+l] = Math.abs(score);
							if(Math.abs(score) > Math.abs(imageval[x+k][y+l])) imageval[x+k][y+l] = score;

						}
					}
//					if(Math.abs(score) > maxval) {
//						maxval = Math.abs(score);
//					}
//					if(Math.abs(score) > 0 && Math.abs(score) < minval) minval = Math.abs(score);
					if(Math.abs(score) > maxval) {
						maxval = Math.abs(score);
					}
					if(score < minval) minval = score;
				}
//				
				for(int x = 0; x < width; x++) {
					for(int y = 0; y < height; y++) {
						float size = (imageval[x][y]/maxval);
//						size -= 1;
//						if(Math.abs(size) < 0.01 || size > 1) continue;
						
//						int c[] = ColorUtils.ColorMapRGB(ColorMap.REDBLUE, (size+1)/2);
						double val = (1 / ( 1 + (Math.pow(Math.E,(-1*(15*size+1)/2)))));
//						int c[] = ColorUtils.ColorMapRGB(ColorMap.REDBLUE, val);
//						
//						Color h = new Color(c[0], c[1], c[2]);
						Color h = new Color((int) (val*255), (int) (val*255), (int) (val*255));
						skeleton.setRGB(x, y, h.getRGB());						
//						System.out.print(imageval[x][y] + " ");
					}
//					System.out.println();
				}
				
				System.out.println("Maxval: " + maxval);
				System.out.println("Minval: " + minval);
				Graphics2D g3 = skeleton.createGraphics();
				g3.drawString(Integer.toString(i), 20, 20);
				gui.setBufferedImage(skeleton);
				mask.ProcessFrame(skeleton);
			i++;
			
			for(int x = 5; x + 10 < width-5; x++) {
				for(int y = 5; y + 10 < height-5; y++) {
					float[] vec = new float[25];
					for(int j = 0; j < 5; j++) {
						for(int k = 0; k < 5; k++) {
						
							vec[5*j+k] = imageval[x+2*j][y+2*k];
							
						}
					}
					features.add(vec);
				}
			}
		}

	mask.Close();
		
	}
	
	public void featuresOut() {
		
//		ArrayList<float[]> features = new ArrayList<float[]>();
//		for(int x = 5; x + 10 < width-5; x++) {
//			for(int y = 5; y + 10 < height-5; y++) {
//				float[] vec = new float[25];
//				for(int i = 0; i < 5; i++) {
//					for(int j = 0; j < 5; j++) {
//					
//						vec[5*i+j] = imageval[x+2*i][y+2*j];
//						
//					}
//				}
//				features.add(vec);
//			}
//		}
//		
		float bagoffeatures[][] = new float[features.size()][25];
		for(int i = 0; i < features.size(); i++) {
			bagoffeatures[i] = features.get(i);
		}
		
		FileIO.SaveCSV(bagoffeatures, new File(CatGetter.stemOnly(inputVideo) + "_feats.csv"));
		
	}
	
	@SuppressWarnings("deprecation")
	public void createArchive() {
		FileIO.SaveObject(new File(CatGetter.constructArc(inputVideo)), new VidInfo(width, height, features));
	}
	
	public void openArchive(String file) {
		VidInfo info = (VidInfo) FileIO.LoadObject(new File(file));
		this.width = info.width;
		this.height = info.height;
		this.features = features;
	}
	
	public static void main (String[] args) {
		
//		DOOGDriver2.doogfilter("/home/grangerlab/workspace/chris/output138628_BIGGEREST.mp4", "output138638_doog2-redo.mp4");
//		DOOGDriver2.doogfilter("/home/grangerlab/workspace/chris/output138628_BIGGER.mp4", "output138638_doog8.mp4");
		
		DOOGDriver2 d2 = new DOOGDriver2();
		
//		d2.doogfilter("/home/grangerlab/workspace/chris/output138628_BIGGER.mp4", "output138638_doog8.mp4");
		d2.doogfilter("/home/grangerlab/workspace/chris/intersection_BIG.mp4", "intersection_doogBIG.mp4");
		d2.createArchive();
		d2.featuresOut();
//		ImagePanel gui = new ImagePanel();
//		
//		int width = 1920;
//		int height = 1080;
////		
////		File datasetFolder = new File("F:/melissatest/");
////		File[] allVideos = datasetFolder.listFiles();
//    	VideoFrameReader frameReader = new VideoFrameReader("/home/grangerlab/Desktop/intersection.mp4", 1920, 1080);
//		frameReader.OpenFile();
//		ArrayList<BufferedImage> frames = new ArrayList<BufferedImage>();
//		BufferedImage currFrame = null;
//
////		VideoTransformDOOG featJavaCVPar = new VideoTransformDOOG(width, height, new int[]{33}, VideoTransformDOOG.ColorMode.GRAYSCALE, VideoTransformDOOG.LibraryChoice.JavaCVParallel, false);
//
//		VideoTransformDOOGOverride featJavaCVPar = new VideoTransformDOOGOverride(width, height, new int[]{5, 9, 17}, VideoTransformDOOGOverride.ColorMode.GRAYSCALE, VideoTransformDOOGOverride.LibraryChoice.JavaCVParallel, false);
//		int indexlookup[][][][] = new int[width][height][4][3];
//		
//		VideoFrameWriter mask = new VideoFrameWriter(new File("doog_map.mp4"), width, height, 30);				
//		mask.OpenFile();
//		
//		VideoRetina retina = new VideoRetina(width, height, true);
//		
//		gui.setPreferredSize(new Dimension(width, height));
//		ShowImages.showWindow(gui,"updates", true);
//		
//		
//		int i = 0;
//		while ((currFrame = frameReader.NextFrame()) != null) {
//			BufferedImage imgCopy = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
//			java.awt.Graphics g = imgCopy.getGraphics();
//			g.drawImage(currFrame, 0, 0, width, height, null);
//			
////			for (int i = 0; i <= frames.size(); i++) {
////				BufferedImage bufferedImageTemp = imgCopy;
//				retina.ProcessFrame(imgCopy);
//				BufferedImage bufferedImageTemp = retina.getParvo();
//
//				//extract periphery (grayscaled)
//				BufferedImage peripheryBI = new BufferedImage(width/2, height/2, BufferedImage.TYPE_BYTE_GRAY);
//				java.awt.Graphics g2 = peripheryBI.getGraphics();
//				g2.drawImage(bufferedImageTemp, 0, 0, width/2, height/2, null);
//				g2.dispose();
//				
//				BufferedImage skeleton = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
//				
//				UI.tic();
//				featJavaCVPar.ProcessFrame(bufferedImageTemp);
//				UI.toc("JavaCV Parallel DOOG");
//				float[] scores = featJavaCVPar.getAllFeatures();
//				int[] xpos = featJavaCVPar.getPixelXIndex();
//				int[] ypos = featJavaCVPar.getPixelYIndex();
//				int[] scale = featJavaCVPar.getScaleIndex();
//				
//				System.out.println("processing frame" + i);
////				System.out.println(parDOOG.length);
////				System.out.println(parDOOG[3]);
////				System.out.println(parDOOG[17]);
//				
//				float imageval[][] = new float[width][height];
////				
////				if(i == 0) {
////					for(int x = 0; x < width; x++) {
////						for(int y = 0; y < height; y++) {
////							imageval[x][y] = -5000;
////						}
////					}
////				}
//				float maxval = 0f;
//				float minval = Float.MAX_VALUE;
//				for(int j = 0; j < scores.length; j++) {
//					
//					float score = scores[j];
////					if(j >= xpos.length || j >= ypos.length) continue;
//					int x = xpos[j];
//					int y = ypos[j];
//					if(Math.abs(score) > imageval[x][y]) {
//						imageval[x][y] = Math.abs(score);
//					}
//					int s = scale[j];
//					for(int k = -3*(s+1)/2; k <= 3*(s+1)/2; k++) {
//						for(int l = -3*(s+1)/2; l <= 3*(s+1)/2; l++) {
////					for(int k = -(s+1)/2; k < (s+1)/2; k++) {
////						for(int l = -(s+1)/2; l < (s+1)/2; l++) {
//							if(Math.abs(score) > imageval[x+k][y+l]) imageval[x+k][y+l] = Math.abs(score);
//						}
//					}
//					if(Math.abs(score) > maxval) {
//						maxval = Math.abs(score);
//					}
//					if(Math.abs(score) > 0 && Math.abs(score) < minval) minval = Math.abs(score);
//				}
////				
//				for(int x = 0; x < width; x++) {
//					for(int y = 0; y < height; y++) {
//						float size = (imageval[x][y]/maxval);
////						size -= 1;
//						if(size < 0.1 || size > 1) continue;
//						
//						int c[] = ColorUtils.ColorMapRGB(ColorMap.REDBLUE, size);
//						Color h = new Color(c[0], c[1], c[2]);
//						skeleton.setRGB(x, y, h.getRGB());						
////						System.out.print(imageval[x][y] + " ");
//					}
////					System.out.println();
//				}
//				
//				System.out.println("Maxval: " + maxval);
//				System.out.println("Minval: " + minval);
//				Graphics2D g3 = skeleton.createGraphics();
//				g3.drawString(Integer.toString(i), 20, 20);
//				gui.setBufferedImage(skeleton);
//				mask.ProcessFrame(skeleton);
//			i++;
//		}
//
//	mask.Close();
//		
	}
	
}


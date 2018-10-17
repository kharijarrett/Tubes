package tracker;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;

import common.FileIO;
import common.UI;
import common.math.Vec;
import common.video.VideoFrameReader;
import common.video.VideoTransformDOOG;

public class DOOGDriver {

	public static void main (String[] args) {
		int width = 1920;
		int height = 1080;
//		
//		File datasetFolder = new File("F:/melissatest/");
//		File[] allVideos = datasetFolder.listFiles();
    	VideoFrameReader frameReader = new VideoFrameReader("/home/grangerlab/Desktop/intersection.mp4", 1920, 1080);
		frameReader.OpenFile();
		ArrayList<BufferedImage> frames = new ArrayList<BufferedImage>();
		BufferedImage currFrame = null;

		VideoTransformDOOG featJavaCVPar = new VideoTransformDOOG(width, height, new int[]{5,9,17}, VideoTransformDOOG.ColorMode.GRAYSCALE, VideoTransformDOOG.LibraryChoice.JavaCVParallel, false);
		int indexlookup[][][][] = new int[width][height][4][3];
		
		
		int i = 0;
		while ((currFrame = frameReader.NextFrame()) != null) {
			BufferedImage imgCopy = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
			java.awt.Graphics g = imgCopy.getGraphics();
			g.drawImage(currFrame, 0, 0, width, height, null);
			
//			for (int i = 0; i <= frames.size(); i++) {
				BufferedImage bufferedImageTemp = imgCopy;
				//extract periphery (grayscaled)
				BufferedImage peripheryBI = new BufferedImage(width/2, height/2, BufferedImage.TYPE_BYTE_GRAY);
				java.awt.Graphics g2 = peripheryBI.getGraphics();
				g2.drawImage(bufferedImageTemp, 0, 0, width/2, height/2, null);
				g2.dispose();
				
				UI.tic();
				featJavaCVPar.ProcessFrame(bufferedImageTemp);
				UI.toc("JavaCV Parallel DOOG");
				float[] parDOOG = featJavaCVPar.getAllFeatures();
				
				System.out.println("processing frame" + i);
//				System.out.println(parDOOG.length);
//				System.out.println(parDOOG[3]);
//				System.out.println(parDOOG[17]);
				
				float imageval[][] = new float[width][height];
				
				if(i == 0) {
					for(int x = 0; x < width; x++) {
						for(int y = 0; y < height; y++) {
							imageval[x][y] = -5000;
							for (int orientNum = 0; orientNum < 4; orientNum++) {
								for(int scaleNum = 0; scaleNum < 3; scaleNum++) {
									indexlookup[x][y][orientNum][scaleNum] = featJavaCVPar.getFeaturePosFromXY(x,  y,  orientNum,  scaleNum,  0);
									System.out.println(x + " " + y + " " + orientNum + " " + scaleNum + " " + indexlookup[x][y][orientNum][scaleNum]);
								}
							}
						}
					}
					FileIO.SaveObject(indexlookup, new File("indexlookup"));
				}
				
				for(int x = 0; x < width; x++) {
					for(int y = 0; y < height; y++) {
//						imageval[x][y] = -5000;
						for (int orientNum = 0; orientNum < 4; orientNum++) {
							for(int scaleNum = 0; scaleNum < 3; scaleNum++) {
								int index = indexlookup[x][y][orientNum][scaleNum];
								if(index < 0 || index > parDOOG.length){
//									System.out.println("yucky");
									continue;
								}
//								System.out.println("woooooooooooooooooooooooooooooo" + x + " " + y);
								float score = Math.abs(parDOOG[index]);
//								System.out.println("woooooooooooooooooooooooooooooo " + index + " " + score);
								if(score > imageval[x][y]) imageval[x][y] = score;
							}
						}					
					}
//				}
			i++;
//			frames.add(imgCopy);
		}

//		VideoTransformDOOG featJavaCVPar = new VideoTransformDOOG(width, height, new int[]{5,9,17}, VideoTransformDOOG.ColorMode.GRAYSCALE, VideoTransformDOOG.LibraryChoice.JavaCVParallel, false);
//		int indexlookup[][][][] = new int[width][height][4][3];
		
		System.out.println("hi");
		
//		for (int i = 0; i <= frames.size(); i++) {
//			BufferedImage bufferedImageTemp = frames.get(i);
//			//extract periphery (grayscaled)
//			BufferedImage peripheryBI = new BufferedImage(width/2, height/2, BufferedImage.TYPE_BYTE_GRAY);
//			java.awt.Graphics g2 = peripheryBI.getGraphics();
//			g2.drawImage(bufferedImageTemp, 0, 0, width/2, height/2, null);
//			g2.dispose();
//			
//			UI.tic();
//			featJavaCVPar.ProcessFrame(bufferedImageTemp);
//			UI.toc("JavaCV Parallel DOOG");
//			float[] parDOOG = featJavaCVPar.getAllFeatures();
//			
//			System.out.println("processing frame" + i);
////			System.out.println(parDOOG.length);
////			System.out.println(parDOOG[3]);
////			System.out.println(parDOOG[17]);
//			
//			float imageval[][] = new float[width][height];
//			
//			if(i == 0) {
//				for(int x = 0; x < width; x++) {
//					for(int y = 0; y < height; y++) {
//						imageval[x][y] = -5000;
//						for (int orientNum = 0; orientNum < 4; orientNum++) {
//							for(int scaleNum = 0; scaleNum < 3; scaleNum++) {
//								indexlookup[x][y][orientNum][scaleNum] = featJavaCVPar.getFeaturePosFromXY(x,  y,  orientNum,  scaleNum,  0);
////								System.out.println(x + " " + y + " " + orientNum + " " + scaleNum + " " + indexlookup[x][y][orientNum][scaleNum]);
//							}
//						}
//					}
//				}
//				FileIO.SaveObject(indexlookup, new File("indexlookup"));
//			}
//			
//			for(int x = 0; x < width; x++) {
//				for(int y = 0; y < height; y++) {
////					imageval[x][y] = -5000;
//					for (int orientNum = 0; orientNum < 4; orientNum++) {
//						for(int scaleNum = 0; scaleNum < 3; scaleNum++) {
//							int index = indexlookup[x][y][orientNum][scaleNum];
//							if(index < 0 || index > parDOOG.length){
////								System.out.println("yucky");
//								continue;
//							}
////							System.out.println("woooooooooooooooooooooooooooooo" + x + " " + y);
//							float score = Math.abs(parDOOG[index]);
////							System.out.println("woooooooooooooooooooooooooooooo " + index + " " + score);
//							if(score > imageval[x][y]) imageval[x][y] = score;
//						}
//					}					
//				}
//			}
			
			
			
//			for(int i = 0; i < 200; i++) {
//				for(int j = 0; j < 200; j++) {
//					System.out.print(imageval[i][j] + " ");
//				}
//				System.out.println();
//			}
			
		}
	}
	
}

package tracker;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.collections4.queue.CircularFifoQueue;

import boofcv.alg.background.BackgroundModelStationary;
import boofcv.alg.filter.binary.BinaryImageOps;
import boofcv.alg.filter.binary.Contour;
import boofcv.alg.filter.binary.ThresholdImageOps;
import boofcv.alg.misc.ImageStatistics;
import boofcv.alg.shapes.ShapeFittingOps;
import boofcv.factory.background.ConfigBackgroundGaussian;
import boofcv.factory.background.FactoryBackgroundModel;
import boofcv.gui.binary.VisualizeBinaryData;
import boofcv.gui.image.ImageGridPanel;
import boofcv.gui.image.ShowImages;
import boofcv.io.MediaManager;
import boofcv.io.UtilIO;
import boofcv.io.image.ConvertBufferedImage;
import boofcv.io.image.SimpleImageSequence;
import boofcv.io.wrapper.DefaultMediaManager;
import boofcv.struct.ConnectRule;
import boofcv.struct.PointIndex_I32;
import boofcv.struct.image.GrayF32;
import boofcv.struct.image.GrayU8;
import boofcv.struct.image.ImageBase;
import boofcv.struct.image.ImageType;
import common.FileIO;
import common.UI;
import common.math.Distance;
import common.math.Mat;
import common.utils.ColorUtils;
import common.utils.ColorUtils.ColorMap;
import common.utils.ImageUtils;
import common.video.VideoFrameReader;
import common.video.VideoFrameWriter;
import common.video.VideoRetina;
import georegression.struct.point.Point2D_I32;

public class CannySampler extends CannyVideo {
	
	public static void bgremover(String vidLoc, String stemLoc) {
 
		// Comment/Uncomment to switch input image type
		ImageType imageType = ImageType.single(GrayF32.class);
 
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
				media.openVideo(vidLoc, background.getImageType());
//				media.openCamera(null,640,480,background.getImageType());
 
		// Declare storage for segmented image.  1 = moving foreground and 0 = background
		GrayU8 segmented = new GrayU8(video.getNextWidth(),video.getNextHeight());
 
		BufferedImage visualized = new BufferedImage(segmented.width,segmented.height,BufferedImage.TYPE_INT_RGB);
 
		double fps = 0;
		double alpha = 0.01; // smoothing factor for FPS
 
		VideoFrameWriter mask = new VideoFrameWriter(new File(stemLoc), video.getNextWidth(), video.getNextHeight(), 50);
		mask.OpenFile();
		
		while( video.hasNext() ) {
			ImageBase input = video.next();
 
			long before = System.nanoTime();
			background.segment(input,segmented);
			background.updateBackground(input);
			long after = System.nanoTime();
 
			fps = (1.0-alpha)*fps + alpha*(1.0/((after-before)/1e9));
 
			VisualizeBinaryData.renderBinary(segmented, false, visualized);
			
			mask.ProcessFrame(visualized);
			
		}
		
		mask.Close();
	}
	
	
	public static void bgsubtract(String vidLoc, String stemLoc) {
		
		SimpleImageSequence<GrayF32> sequence = (SimpleImageSequence<GrayF32>) processVideo(vidLoc);

		int height = sequence.getNextHeight();
		int width = sequence.getNextWidth(); 
				
		VideoFrameWriter mask = new VideoFrameWriter(new File(stemLoc), width, height, 50);
		mask.OpenFile();
				
		GrayF32 frame;
		int frames = 0;
		CircularFifoQueue<BufferedImage> q = new CircularFifoQueue<BufferedImage>(3);
		
		while(sequence.hasNext()) {
						
			frame = sequence.next();
			
			GrayF32 input = frame;
			BufferedImage image = sequence.getGuiImage();
			
			BufferedImage clone = new BufferedImage(image.getWidth(),
		            image.getHeight(), image.getType());
		    Graphics2D g2d = clone.createGraphics();
		    g2d.drawImage(image, 0, 0, null);
		    g2d.dispose();
			
			for(int i = 0; i < image.getWidth(); i++) {
				for(int j = 0; j < image.getHeight(); j++) {
//					System.out.println(i + " " + j);
					for(BufferedImage b : q) {
						boolean didChange = false;
						if(b.getRGB(i, j) != image.getRGB(i, j)) {
							didChange = true;
							continue;
						}
						if(didChange == false) {
							clone.setRGB(i, j, 0);
							continue;
						}
					}
				}
			}		
			
			frames++;
			q.add(image);

			mask.ProcessFrame(clone);
			System.out.println(frames);
		}
		
		mask.Close();
		
	}
	
	public static void batch(String folder, boolean useArchives) {
		
		File dir = new File(folder);
		File[] listOfFiles = dir.listFiles();
		
		ArrayList<String> fq = new ArrayList<String>();
		for (File file : listOfFiles) {
		    if (file.isFile() && (file.getName().toLowerCase().endsWith(".mp4") )) {
		    	fq.add(file.getAbsolutePath());
		    }
		}
		
		ArrayList<ShapeContext3D> contextAll = new ArrayList<ShapeContext3D>();
		
		for(int i = 0; i < fq.size(); i++) {
			String currentFilePath = fq.get(i);
	    	System.out.println("PROCESSING VIDEO " + (i+1) + " of " + fq.size() + ", " + currentFilePath);
	    	
	    	ArrayList<LocalGrid> contexts;
	    	
	    	String arcLoc = CatGetter.constructArc(currentFilePath);
			File f = new File(arcLoc);
			if(f.exists() && !f.isDirectory() && useArchives == true) {
				System.out.println("Archive at " + arcLoc + " found, processing that instead...");
				
				contexts = (ArrayList<LocalGrid>) FileIO.LoadObject(new File(arcLoc));
				
			}
			else {
				
//				bgremover(currentFilePath, CatGetter.process(currentFilePath));
				
//		    	ArrayList<LocalGrid> pacshots = uncanny(currentFilePath, CatGetter.process(currentFilePath), "/home/grangerlab/workspace/chris/grid3-4.csv");
//				FileIO.SaveObject(new File(CatGetter.samp(currentFilePath)), pacshots);
				
		    	ArrayList<ArrayList<Point2D_I32>> pacshots = canny(currentFilePath, CatGetter.process(currentFilePath), "/home/grangerlab/workspace/chris/grid3-4.csv");

				SimpleImageSequence<GrayF32> sequence = (SimpleImageSequence<GrayF32>) processVideo(currentFilePath);
		    	
		    	contexts = LocalGrid.computeAllContexts(pacshots, 3, 4, sequence.getNextWidth(), sequence.getNextHeight(), true);	
				FileIO.SaveObject(new File(CatGetter.samp(currentFilePath)), contexts);
				
			}

		}
	
		return;
		
	}
	
	/*
	public static void batchDOOG(String folder, boolean useArchives) {
		
		File dir = new File(folder);
		File[] listOfFiles = dir.listFiles();
		
		ArrayList<String> fq = new ArrayList<String>();
		for (File file : listOfFiles) {
		    if (file.isFile() && (file.getName().toLowerCase().endsWith(".mp4") )) {
		    	fq.add(file.getAbsolutePath());
		    }
		}	// fq is an arraylist of the mp4 file paths in the folder
		
		ArrayList<ShapeContext3D> contextAll = new ArrayList<ShapeContext3D>();
		
		
		for(int i = 0; i < fq.size(); i++) {
			String currentFilePath = fq.get(i);
	    	System.out.println("PROCESSING VIDEO " + (i+1) + " of " + fq.size() + ", " + currentFilePath);
	    	
	    	ArrayList<LocalGrid> contexts;
	    		    	
	    	String arcLoc = CatGetter.constructArc(currentFilePath);
			File f = new File(arcLoc);
			if(f.exists() && !f.isDirectory() && useArchives == true) {
				System.out.println("Archive at " + arcLoc + " found, processing that instead...");
				
				contexts = (ArrayList<LocalGrid>) FileIO.LoadObject(new File(arcLoc));
				
			}
			else {
				
//				bgremover(currentFilePath, CatGetter.process(currentFilePath));
				
//		    	ArrayList<LocalGrid> pacshots = uncanny(currentFilePath, CatGetter.process(currentFilePath), "/home/grangerlab/workspace/chris/grid3-4.csv");
//				FileIO.SaveObject(new File(CatGetter.samp(currentFilePath)), pacshots);
				
		    	ArrayList<float[][][]> pacshots = doogWindowsSaveStructure(currentFilePath, CatGetter.process(currentFilePath));
				FileIO.SaveObject(new File(CatGetter.samp(currentFilePath)), pacshots);
				
			}

		}
	
		return;
		
	}
	*/
	public static ArrayList<float[]> doogWindows(String vidLoc, String stemLoc) {
		
		ArrayList<float[]> pointframes = new ArrayList<float[]>();
		
		SimpleImageSequence<GrayF32> sequence = (SimpleImageSequence<GrayF32>) processVideo(vidLoc);

		int height = sequence.getNextHeight();
		int width = sequence.getNextWidth(); 
		
		VideoFrameWriter mask = new VideoFrameWriter(new File(stemLoc), width, height, 5);

		mask.OpenFile();
		VideoRetina retina = new VideoRetina(width, height, true);
				
		GrayF32 frame;
		int frames = 0;
				
		VideoTransformDOOGOverride featJavaCVPar = new VideoTransformDOOGOverride(width, height, new int[]{5}, VideoTransformDOOGOverride.ColorMode.GRAYSCALE, VideoTransformDOOGOverride.LibraryChoice.JavaCVParallel, false);

		
		while(sequence.hasNext()) {
			
			ArrayList<Point2D_I32> points = new ArrayList<Point2D_I32>();
			
			frame = sequence.next();
			
			GrayF32 input = frame;
			BufferedImage newFrame = sequence.getGuiImage();
			retina.ProcessFrame(newFrame);
					
			BufferedImage parvo = retina.getParvo();
			
			byte grayscale[][] = ImageUtils.BufferedImage2Grayscale(newFrame);
			int gray[][] = Mat.Unsigned2Int(grayscale);
			for(int i = 0; i < gray.length; i++) {
				for(int j = 0; j < gray[i].length; j++) {
					if(gray[i][j] < 180) gray[i][j] = 255; // was 180 pacman
					else gray[i][j] = 0;
				}
			}
			
			UI.tic();
			featJavaCVPar.ProcessFrame(parvo);
			UI.toc("JavaCV Parallel DOOG");
			float[] scores = featJavaCVPar.getAllFeatures();
			int[] xpos = featJavaCVPar.getPixelXIndex();
			int[] ypos = featJavaCVPar.getPixelYIndex();
			int[] scale = featJavaCVPar.getScaleIndex();
			int[] orientation = featJavaCVPar.getOrientationIndex();
			
			System.out.println("processing frame " + frames);

			grayscale = Mat.Int2Unsigned(gray);
			
			GrayU8 binary = new GrayU8(input.width,input.height);
			BufferedImage polygon = new BufferedImage(input.width,input.height,BufferedImage.TYPE_INT_RGB);
			polygon = newFrame;

			float imageval[][] = new float[width][height];

			float maxval = 0f;
			float minval = Float.MAX_VALUE;
			for(int j = 0; j < scores.length; j++) {
				
				float score = scores[j];
				int x = xpos[j];
				int y = ypos[j];

				if(Math.abs(score) > Math.abs(imageval[x][y])) {
					imageval[x][y] = score;
				}
				int s = scale[j];
				for(int k = -2; k <= 2; k++) {
					for(int l = -2; l <= 2; l++) {
						if(Math.abs(score) > Math.abs(imageval[x+k][y+l])) imageval[x+k][y+l] = score;
					}
				}
				if(Math.abs(score) > maxval) {
					maxval = Math.abs(score);
				}
				if(score < minval) minval = score;
			}
//			
			for(int x = 0; x < width; x++) {
				for(int y = 0; y < height; y++) {
					float size = (imageval[x][y]/maxval);
					double val = (1 / ( 1 + (Math.pow(Math.E,(-1*(15*size+1)/2)))));
//					int c[] = ColorUtils.ColorMapRGB(ColorMap.REDBLUE, val);
//					
//					Color h = new Color(c[0], c[1], c[2]);
					Color h = new Color((int) (val*255), (int) (val*255), (int) (val*255));
					polygon.setRGB(x, y, h.getRGB());						
//					System.out.print(imageval[x][y] + " ");
				}
//				System.out.println();
			}
			
			System.out.println("Maxval: " + maxval);
			System.out.println("Minval: " + minval);
			Graphics2D g3 = polygon.createGraphics();
			g3.drawString(Integer.toString(frames), 20, 20);
		
			for(int x = 5; x + 10 < width-5; x++) {
				for(int y = 5; y + 10 < height-5; y++) {
					float[] vec = new float[25];
					for(int j = 0; j < 5; j++) {
						for(int k = 0; k < 5; k++) {
						
							vec[5*j+k] = imageval[x+2*j][y+2*k];
							
						}
					}
					pointframes.add(vec);
				}
			}

			frames++;
			mask.ProcessFrame(polygon);

		}
		
		mask.Close();
			
		return pointframes;
	}
	/*
	public static ArrayList<float[][][]> doogWindowsSaveStructure(String vidLoc, String stemLoc) {
		
		ArrayList<float[][][]> pointframes = new ArrayList<float[][][]>();
		
		VideoFrameReader sequence = processVideo(vidLoc);

		sequence.OpenFile();
		
		int height = sequence.getFrameHeight();
		int width = sequence.getFrameWidth(); 
		
		if(width < 20 || height < 20) return null;		//Only counts if the vid is at least 20x20
		VideoFrameWriter mask = new VideoFrameWriter(new File(stemLoc), width, height, 2); //Where the video is being written (2 frames/sec)

		mask.OpenFile();
		VideoRetina retina = new VideoRetina(width, height, true);
		
		
		
		GrayF32 frame;
		int frames = 0;
				
		VideoTransformDOOGOverride featJavaCVPar = new VideoTransformDOOGOverride(width, height, new int[]{13}, VideoTransformDOOGOverride.ColorMode.GRAYSCALE, VideoTransformDOOGOverride.LibraryChoice.JavaCVParallel, false);

		
		while(sequence.hasNext()) {
			
			ArrayList<Point2D_I32> points = new ArrayList<Point2D_I32>();
			float layerone[][][] = new float[width-20][height-20][25];

			frame = sequence.next();
			
			GrayF32 input = frame;
			BufferedImage newFrame = sequence.getGuiImage();
			retina.ProcessFrame(newFrame);
					
			BufferedImage parvo = retina.getParvo();
			
			byte grayscale[][] = ImageUtils.BufferedImage2Grayscale(newFrame);
			
/*			int gray[][] = Mat.Unsigned2Int(grayscale);
			for(int i = 0; i < gray.length; i++) {
				for(int j = 0; j < gray[i].length; j++) {
					if(gray[i][j] < 180) gray[i][j] = 255; // was 180 pacman
					else gray[i][j] = 0;
				}
			}
*/
			/*
			UI.tic();
			featJavaCVPar.ProcessFrame(parvo);
			UI.toc("JavaCV Parallel DOOG");
			float[] scores = featJavaCVPar.getAllFeatures();
			int[] xpos = featJavaCVPar.getPixelXIndex();
			int[] ypos = featJavaCVPar.getPixelYIndex();
			int[] scale = featJavaCVPar.getScaleIndex();
			int[] orientation = featJavaCVPar.getOrientationIndex();
			
			System.out.println("processing frame " + frames);

//			grayscale = Mat.Int2Unsigned(gray);
			
		//	GrayU8 binary = new GrayU8(input.width,input.height);
		//	BufferedImage polygon = new BufferedImage(input.width,input.height,BufferedImage.TYPE_INT_RGB);
			BufferedImage polygon = newFrame;

			float imageval[][] = new float[width][height];

			float maxval = 0f;
			float minval = Float.MAX_VALUE;	//start max at 0 and min at a lot
			for(int j = 0; j < scores.length; j++) {
				
				float score = scores[j];
				int x = xpos[j];
				int y = ypos[j];

				if(Math.abs(score) > Math.abs(imageval[x][y])) { //compare abs(score) to imageval and replace if bigger
					imageval[x][y] = score;
				}
				int s = scale[j];
				int d = 3;
				for(int k = -d; k <= d; k++) { 					//Same as above for a wider area (dxd)
					for(int l = -d; l <= d; l++) {
						if(Math.abs(score) > Math.abs(imageval[x+k][y+l])) imageval[x+k][y+l] = score;
					}
				}
				if(Math.abs(score) > maxval) {
					maxval = Math.abs(score);
				}
				if(score < minval) minval = score;
			}
//			
			for(int x = 0; x < width; x++) {		//Scaling the image based on maxval/minval
				for(int y = 0; y < height; y++) {
					float size = (imageval[x][y]/maxval);
					double val = (1 / ( 1 + (Math.pow(Math.E,(-1*(15*size+1)/2)))));
//					int c[] = ColorUtils.ColorMapRGB(ColorMap.REDBLUE, val);
//					
//					Color h = new Color(c[0], c[1], c[2]);
					Color h = new Color((int) (val*255), (int) (val*255), (int) (val*255));
					polygon.setRGB(x, y, h.getRGB());						
//					System.out.print(imageval[x][y] + " ");
				}
//				System.out.println();
			}
			
			System.out.println("Maxval: " + maxval);
			System.out.println("Minval: " + minval);
			Graphics2D g3 = polygon.createGraphics();
			g3.drawString(Integer.toString(frames), 20, 20);
		
			for(int x = 5; x + 10 < width-5; x++) {
				for(int y = 5; y + 10 < height-5; y++) {
					float[] vec = new float[25];
					for(int j = 0; j < 5; j++) {
						for(int k = 0; k < 5; k++) {
						
							vec[5*j+k] = imageval[x+2*j][y+2*k];
							
						}
					}
					layerone[x-5][y-5] = vec;
				}
			}
			pointframes.add(layerone);

			frames++;
			mask.ProcessFrame(polygon);

		}
		
		mask.Close();
			
		return pointframes;
	}
	
	*/
	public static void writeCSV(String folder, File saveFile) {
		
		if (!saveFile.toString().toUpperCase().endsWith(".CSV")) {
			throw new IllegalArgumentException("FileIO.SaveCSV(): savePath does not end with .csv!");
		}
		try {
			saveFile.createNewFile();
		    PrintWriter out = new PrintWriter(saveFile);
		    
		    File dir = new File(folder);
			File[] listOfFiles = dir.listFiles();
			
			ArrayList<String> fq = new ArrayList<String>();
			for (File file : listOfFiles) {
			    if (file.isFile() && (file.getName().toLowerCase().endsWith("_samp.zip"))) {
			    	fq.add(file.getAbsolutePath());
			    }
			}
			
			for(int i = 0; i < fq.size(); i++) {
				String currentFilePath = fq.get(i);
		    	System.out.println("processing video-archive " + (i+1) + " of " + fq.size() + ", " + currentFilePath);
		    	
		    	ArrayList<LocalGrid> contexts = (ArrayList<LocalGrid>) FileIO.LoadObject(new File(currentFilePath));
		    	for(int j = 0; j < contexts.size(); j++) {
		    		double[] currRow = contexts.get(j).histogram;
		    		
		    		boolean firstPrint = true;
					for (double currElement : currRow) {
						if (firstPrint == false) {
							out.print(",");
						}
						out.print(currElement);
						firstPrint = false;
					}
					out.println();
		    	}
		    	
			}
		    
			out.close();
			
		} catch (Exception ex) {
			UI.Error("FileIO.SaveCSV(): Can not create file:");
			UI.Error(ex);
		}
	}

	public static void writeCSVDOOG(String folder, File saveFile) {
		
		if (!saveFile.toString().toUpperCase().endsWith(".CSV")) {
			throw new IllegalArgumentException("FileIO.SaveCSV(): savePath does not end with .csv!");
		}
		try {
			saveFile.createNewFile();
		    PrintWriter out = new PrintWriter(saveFile);
		    
		    File dir = new File(folder);
			File[] listOfFiles = dir.listFiles();
			
			ArrayList<String> fq = new ArrayList<String>();
			for (File file : listOfFiles) {
			    if (file.isFile() && (file.getName().toLowerCase().endsWith("_samp.zip"))) {
			    	fq.add(file.getAbsolutePath());
			    }
			}
			
			for(int i = 0; i < fq.size(); i++) {
				String currentFilePath = fq.get(i);
		    	System.out.println("processing video-archive " + (i+1) + " of " + fq.size() + ", " + currentFilePath);
		    	
		    	ArrayList<float[]> contexts = (ArrayList<float[]>) FileIO.LoadObject(new File(currentFilePath));
		    	
		    	boolean skipHalf = true;
		    	for(int j = 0; j < contexts.size(); j++) {
		    		float[] currRow = contexts.get(j);
		    		
		    		skipHalf = !skipHalf;
		    		if(skipHalf) continue;
		    		
		    		boolean hasInfo = false;
		    		for(int k = 0; k < currRow.length; k++) {
		    			if(currRow[k] >= 0.3) {
		    				hasInfo = true;
		    				break;
		    			}
		    		}
		    		
		    		if(hasInfo == false) continue;
		    				
		    		boolean firstPrint = true;
					for (float currElement : currRow) {
						if (firstPrint == false) {
							out.print(",");
						}
						out.print(currElement);
						firstPrint = false;
					}
					out.println();
		    	}
		    	
			}
		    
			out.close();
			
		} catch (Exception ex) {
			UI.Error("FileIO.SaveCSV(): Can not create file:");
			UI.Error(ex);
		}
	}
	
	public static void writeCSVDOOGLayerOne(String folder, File saveFile) {
		
		if (!saveFile.toString().toUpperCase().endsWith(".CSV")) {
			throw new IllegalArgumentException("FileIO.SaveCSV(): savePath does not end with .csv!");
		}
		try {
			saveFile.createNewFile();
		    PrintWriter out = new PrintWriter(saveFile);
		    
		    File dir = new File(folder);
			File[] listOfFiles = dir.listFiles();
			
			ArrayList<String> fq = new ArrayList<String>();
			for (File file : listOfFiles) {
			    if (file.isFile() && (file.getName().toLowerCase().endsWith("samp.zip"))) {
			    	fq.add(file.getAbsolutePath());
			    }
			}
			
			for(int i = 0; i < fq.size(); i++) {
				String currentFilePath = fq.get(i);
		    	System.out.println("processing video-archive " + (i+1) + " of " + fq.size() + ", " + currentFilePath);
		    	
		    	ArrayList<float[][][]> contexts = (ArrayList<float[][][]>) FileIO.LoadObject(new File(currentFilePath));
		    	if(contexts == null) continue;
		    	for(int j = 0; j < contexts.size(); j++) {
		    		for(int k = 0; k < contexts.get(j).length; k++) {
		    			for(int l = 0; l < contexts.get(j)[k].length; l++) {
		    				float[] currRow = contexts.get(j)[k][l];
		    		
				    		boolean hasInfo = false;
				    		for(int m = 0; m < currRow.length; m++) {
				    			if(currRow[m] >= 0.3) {
				    				hasInfo = true;
				    				break;
				    			}
				    		}
				    		
				    		if(hasInfo == false) continue;
				    				
				    		boolean firstPrint = true;
							for (float currElement : currRow) {
								if (firstPrint == false) {
									out.print(",");
								}
								out.print(currElement);
								firstPrint = false;
							}
							out.println();
				    	}
		    			
		    		}
		    	}
		    	
			}
		    
			out.close();
			
		} catch (Exception ex) {
			UI.Error("FileIO.SaveCSV(): Can not create file:");
			UI.Error(ex);
		}
	}	
	
	public static void writeCSVDOOGLayerTwo(String folder, File saveFile, File clusterLocations) {
		
		double centroids[][] = FileIO.LoadCSV(clusterLocations);
		float floatCentroids[][] = new float[centroids.length][centroids[0].length];
		for(int i = 0; i < centroids.length; i++) {
			for(int j = 0; j < centroids[0].length; j++) {
				floatCentroids[i][j] = (float) centroids[i][j];
			}
		}
		
		
		if (!saveFile.toString().toUpperCase().endsWith(".CSV")) {
			throw new IllegalArgumentException("FileIO.SaveCSV(): savePath does not end with .csv!");
		}
		try {
			saveFile.createNewFile();
		    PrintWriter out = new PrintWriter(saveFile);
		    
		    File dir = new File(folder);
			File[] listOfFiles = dir.listFiles();
			
			ArrayList<String> fq = new ArrayList<String>();
			for (File file : listOfFiles) {
			    if (file.isFile() && (file.getName().toLowerCase().endsWith("samp.zip"))) {
			    	fq.add(file.getAbsolutePath());
			    }
			}
			
			for(int i = 0; i < fq.size(); i++) {
				String currentFilePath = fq.get(i);
		    	System.out.println("processing video-archive " + (i+1) + " of " + fq.size() + ", " + currentFilePath);
		    	
		    	ArrayList<float[][][]> contexts = (ArrayList<float[][][]>) FileIO.LoadObject(new File(currentFilePath));
		    	if(contexts == null) continue;
		    	for(int j = 0; j < contexts.size(); j++) {
		    		for(int k = 0; k < contexts.get(j).length-5; k+= 5) {
		    			for(int l = 0; l < contexts.get(j)[k].length-5; l+= 5) {
		    				float[] currRow = new float[1000];
		    				for(int m = 0; m < 5; m++) {
		    					for(int n = 0; n < 5; n++) {
		    						int clustpos = 40*(5*m + n);
		    						for(int o = 0; o < 40; o++) {
//		    							currRow[clustpos + o] = (float) (1 / Math.pow(Math.E, (-1 * (Math.sqrt(Distance.SquaredEuclidian(contexts.get(j)[k+m][l+n], floatCentroids[o]) - 3.63)))));
//		    						}
		    						currRow[clustpos + o] = 1 / (1 + (float) Math.sqrt(Distance.SquaredEuclidian(contexts.get(j)[k+m][l+n], floatCentroids[o])));
		    						}
		    					}
		    				}
		    		
				    		boolean hasInfo = false;
				    		for(int m = 0; m < currRow.length; m++) {
				    			if(currRow[m] >= 0.5) {
				    				hasInfo = true;
				    				break;
				    			}
				    		}
				    		
				    		if(hasInfo == false) continue;
				    				
				    		boolean firstPrint = true;
							for (float currElement : currRow) {
								if (firstPrint == false) {
									out.print(",");
								}
								out.print(currElement);
								firstPrint = false;
							}
							out.println();
							
				    	}
		    			
		    		}
		    	}
		    	
			}
		    
			out.close();
			
		} catch (Exception ex) {
			UI.Error("FileIO.SaveCSV(): Can not create file:");
			UI.Error(ex);
		}
	}
	
	public static ArrayList<LocalGrid> uncanny(String vidLoc, String stemLoc, String csvLoc) {

		ArrayList<LocalGrid> locals = new ArrayList<LocalGrid>();
		
		double centroids[][] = FileIO.LoadCSV(new File(csvLoc));
		
		SimpleImageSequence<GrayF32> sequence = (SimpleImageSequence<GrayF32>) processVideo(vidLoc);

		int height = sequence.getNextHeight();
		int width = sequence.getNextWidth(); 
		
		double splitFraction = 0.05;
		double minimumSideFraction = 0.1;
				
		VideoFrameWriter mask = new VideoFrameWriter(new File(stemLoc), width, height, 5);
		mask.OpenFile();
		VideoRetina retina = new VideoRetina(width, height, true);
				
		GrayF32 frame;
		int frames = 0;
		while(sequence.hasNext()) {
						
			frame = sequence.next();
			
			GrayF32 input = frame;
			BufferedImage newFrame = sequence.getGuiImage();
			retina.ProcessFrame(newFrame);
					
			BufferedImage parvo = retina.getParvo();
			
			byte grayscale[][] = ImageUtils.BufferedImage2Grayscale(newFrame);
			int gray[][] = Mat.Unsigned2Int(grayscale);
			for(int i = 0; i < gray.length; i++) {
				for(int j = 0; j < gray[i].length; j++) {
					if(gray[i][j] < 180) gray[i][j] = 255; // was 180 pacman
					else gray[i][j] = 0;
				}
			}

			grayscale = Mat.Int2Unsigned(gray);
			
			GrayU8 binary = new GrayU8(input.width,input.height);
			BufferedImage polygon = new BufferedImage(input.width,input.height,BufferedImage.TYPE_INT_RGB);
			polygon = newFrame;

//			for(int i = 0; i < polygon.getWidth(); i++) {
//				for(int j = 0; j < polygon.getHeight(); j++) {
//					
//					int rgb = polygon.getRGB(i,j); //always returns TYPE_INT_ARGB
//					int alpha = (rgb >> 24) & 0xFF;
//					int red =   (rgb >> 16) & 0xFF;
//					int green = (rgb >>  8) & 0xFF;
//					int blue =  (rgb      ) & 0xFF;
//					
//					System.out.println(alpha + " (" + red + "," + green + "," + blue + ")");
//				}
//			}	
	 
			// Fit a polygon to each shape and draw the results
			Graphics2D g2 = polygon.createGraphics();
			g2.setStroke(new BasicStroke(2));

			frames++;
			
			// also add in reading in of k-means vectors
			
			ArrayList<LocalGrid> local = LocalGrid.computeContexts(polygon, 3, 4, width, height, true);
			for(LocalGrid l : local ) {
				double cos = -2;
				//for each context
				for(int i = 0; i < centroids.length; i++) {
					double sim = Distance.Cosine(l.histogram, centroids[i]);
					System.out.print(sim + " ");
					if(sim > cos) cos = sim;
					
				}
				System.out.println();

//				if(cos <= 0.4) continue;
				int c[] = ColorUtils.ColorMapRGB(ColorMap.REDBLUE, cos);
				Color h = new Color(c[0], c[1], c[2]);
				polygon.setRGB(l.x, l.y, h.getRGB());
				//save minimum distance
					
				//print score in location to visualize
			}
			
			mask.ProcessFrame(polygon);
			locals.addAll(local);
		}
		
		mask.Close();
		return locals;
		
	}	
	
	
	public static ArrayList<ArrayList<Point2D_I32>> canny(String vidLoc, String stemLoc, String csvLoc) {

		ArrayList<ArrayList<Point2D_I32>> pointframes = new ArrayList<ArrayList<Point2D_I32>>();
		
		double centroids[][] = FileIO.LoadCSV(new File(csvLoc));
		
		SimpleImageSequence<GrayF32> sequence = (SimpleImageSequence<GrayF32>) processVideo(vidLoc);

		int height = sequence.getNextHeight();
		int width = sequence.getNextWidth(); 
		
		double splitFraction = 0.05;
		double minimumSideFraction = 0.1;
				
		VideoFrameWriter mask = new VideoFrameWriter(new File(stemLoc), width, height, 5);
		VideoFrameWriter mask2 = new VideoFrameWriter(new File("skel_" + stemLoc), width*6, height*6, 5);

		mask.OpenFile();
		mask2.OpenFile();
		VideoRetina retina = new VideoRetina(width, height, true);
				
		GrayF32 frame;
		int frames = 0;
		
		ArrayList<Integer[]> clustersinframes = new ArrayList<Integer[]>();
		
		while(sequence.hasNext()) {
			
			ArrayList<Point2D_I32> points = new ArrayList<Point2D_I32>();
			
			frame = sequence.next();
			
			GrayF32 input = frame;
			BufferedImage newFrame = sequence.getGuiImage();
			retina.ProcessFrame(newFrame);
					
			BufferedImage parvo = retina.getParvo();
			
			byte grayscale[][] = ImageUtils.BufferedImage2Grayscale(newFrame);
			int gray[][] = Mat.Unsigned2Int(grayscale);
			for(int i = 0; i < gray.length; i++) {
				for(int j = 0; j < gray[i].length; j++) {
					if(gray[i][j] < 180) gray[i][j] = 255; // was 180 pacman
					else gray[i][j] = 0;
				}
			}

			grayscale = Mat.Int2Unsigned(gray);
			
			GrayU8 binary = new GrayU8(input.width,input.height);
			BufferedImage polygon = new BufferedImage(input.width,input.height,BufferedImage.TYPE_INT_RGB);
			polygon = newFrame;

//			for(int i = 0; i < polygon.getWidth(); i++) {
//				for(int j = 0; j < polygon.getHeight(); j++) {
//					
//					int rgb = polygon.getRGB(i,j); //always returns TYPE_INT_ARGB
//					int alpha = (rgb >> 24) & 0xFF;
//					int red =   (rgb >> 16) & 0xFF;
//					int green = (rgb >>  8) & 0xFF;
//					int blue =  (rgb      ) & 0xFF;
//					
//					System.out.println(alpha + " (" + red + "," + green + "," + blue + ")");
//				}
//			}	
			
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
				for( List<Point2D_I32> q : c.internal) {
					for(Point2D_I32 p : q) {
						points.add(p);
						marker++;
						Color g = Color.GREEN;
						polygon.setRGB(p.x,p.y,g.getRGB());
					}
				}
			}

			frames++;
			
			// also add in reading in of k-means vectors
			
			BufferedImage skeleton = new BufferedImage(width*6, height*6, BufferedImage.TYPE_INT_RGB);
			Graphics2D skelg = skeleton.createGraphics();
//	        skelg.scale(6,6);
//
//	        // everything drawn with grph from now on will get scaled.
	        skelg.drawImage(sequence.getGuiImage(), 0, 0, null);
	        skelg.drawImage(sequence.getGuiImage(), 0, 0, width*6, height*6, 0, 0, width, height, null);
//	        skelg.scale(1, 1);
			
			ArrayList<LocalGrid> local = LocalGrid.computeContexts(points, 3, 4, width, height, false);
			ArrayList<Integer> clusterlist = new ArrayList<Integer>();			
			for(LocalGrid l : local ) {
				double cos = 0;
				int clust = -1;
				//for each context
				int sum = 0;
				for(int i = 0; i < l.histogram.length; i++) {
					sum += l.histogram[i];
				}
				
				for(int i = 0; i < centroids.length; i++) {
					if(sum == 0) break;
					double sim = Distance.Cosine(l.histogram, centroids[i]);
					if(sim > cos) {
						cos = sim;
						clust = i;
					}
//					System.out.print(sim + " ");
				}

//				if(cos <= 0.4) continue;
				int c[] = ColorUtils.ColorMapRGB(ColorMap.REDBLUE, cos);
				Color h = new Color(c[0], c[1], c[2]);
				polygon.setRGB(l.x, l.y, h.getRGB());
				skelg.setColor(Color.RED);
//				skelg.setFont(new Font("TimesRoman", Font.PLAIN, 8));
				skelg.drawString(Integer.toString(clust), l.x*6, l.y*6);
				//save minimum distance
			
				//print score in location to visualize
				clusterlist.add(clust);
			}
			
			Integer clust[] = clusterlist.toArray(new Integer[clusterlist.size()]);
			clustersinframes.add(clust);
			
			mask.ProcessFrame(polygon);
			mask2.ProcessFrame(skeleton);
			pointframes.add(points);
		}
		
		mask.Close();
		mask2.Close();
		
		int finvectors[][] = new int[clustersinframes.size()][40*clustersinframes.get(0).length];
		for(int i = 0; i < clustersinframes.size(); i++) {
			for(int j = 0; j < clustersinframes.get(i).length; j++) {
				if(clustersinframes.get(i)[j] != -1) {
					finvectors[i][40*j + clustersinframes.get(i)[j]] = 1;
				}
			}
		}
		FileIO.SaveCSV(finvectors, new File(stemLoc + ".csv"));
			
		return pointframes;

	}	
	
	
	public static void main(String[] args) {
//		bgremover("/home/grangerlab/Desktop/arvind/output885406.mp4", CatGetter.process("/home/grangerlab/Desktop/output885406.mp4"));
//		batch("/home/grangerlab/Desktop/pac_nobg", false);
//		batchDOOG("/home/grangerlab/workspace/chris/newpacbig", false);
//		batchDOOG("/home/grangerlab/workspace/chris/inter2process/", false);
//		writeCSVDOOGLayerOne("/home/grangerlab/workspace/chris/inter2process/", new File("intersection_doog_samplel1.csv"));

//		writeCSVDOOGLayerOne("/home/grangerlab/workspace/chris/pac138628bigger", new File("doog_samplel1.csv"));
//		writeCSVDOOGLayerTwo("C:/Users/Khari Jarrett/Documents/VideoProcessing-master/chris", new File("doog_sampleLAYERTWOredone.csv"), new File("doog_fullbb.csv"));
		
//		writeCSV("/home/grangerlab/Desktop/samplepacman", new File("newsample.csv"));
		
		//batchDOOG("C:/Users/Khari Jarrett/Documents/VideoProcessing-master/chris/int6_TubesOnly", false);
		//writeCSVDOOGLayerOne("C:/Users/Khari Jarrett/Documents/VideoProcessing-master/chris/intersection3split", new File("kjDOOG13.csv"));
		//bgremover("C:/Users/Khari Jarrett/Documents/VideoProcessing-master/chris/intersection3split/inter _f121_l15_render.mp4", "C:/Users/Khari Jarrett/Documents/VideoProcessing-master/chris/intersection1_bg/intersection3_bg3.mp4");
		System.out.print("Hi.");
	}
	
}

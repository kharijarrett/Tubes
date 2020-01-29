package tracker;

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.image.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;

import org.bytedeco.javacpp.*;
import org.bytedeco.javacpp.opencv_core.IplImage;
import org.bytedeco.javacpp.opencv_core.Mat;

import static org.bytedeco.javacpp.opencv_core.*;
import static org.bytedeco.javacpp.opencv_imgproc.*;
import static org.bytedeco.javacpp.opencv_video.*;
import static org.bytedeco.javacpp.opencv_highgui.*;

import common.*;
import common.imagekernel.*;
import common.math.*;
import common.utils.*;
import common.video.VideoFrameReader;
import common.video.VideoFrameWriter;
import common.video.VideoProcessorBase;
import boofcv.struct.convolve.*;
import boofcv.struct.image.*;
import boofcv.alg.filter.convolve.*;
import boofcv.core.image.border.*;
import boofcv.gui.image.ShowImages;

/**
 * DOOG = difference of (two) offset gaussians
 *
 * @author Eli Bowen
 * @since May 22, 2015
 */
public class VideoTransformDOOGOverride extends VideoProcessorBase {
	public enum ColorMode { GRAYSCALE, COLOR_OPPONENT }
	public enum LibraryChoice { Java, JavaCV, JavaCVParallel, BoofCV, BoofCVParallel }
	
	private final LibraryChoice m_library;
	private final boolean m_debugMode;
	private final ColorMode m_colorMode;
	private final int[] m_orientationIndex; //same length as output vector - for each output element, what orientation was it generated for?
	private final int[] m_distApartIndex; 	//same length as output vector - for each output element, what distNum was it generated for?
	private final int[] m_channelIndex;		//same length as output vector
	private final int[] m_pixelXIndex; 		//same length as output vector - for each output element, what was it's original pixel x coordinate?
	private final int[] m_pixelYIndex; 		//same length as output vector - for each output element, what was it's original pixel y coordinate?
	private float[] m_currFeatures = null;
	private int[] m_distsApart = null;
	private ArrayList<Integer> m_orientationList = new ArrayList<Integer>();
	private ArrayList<Integer> m_distsApartList = new ArrayList<Integer>();
	private ArrayList<Integer> m_channelList = new ArrayList<Integer>();
	
	private byte[][] m_luminance = null;
	private byte[][] m_redvsgreen = null;
	private byte[][] m_yellowvsblue = null;

	//memory reuse:
	private ConcurrentHashMap<Integer,ConcurrentHashMap<Integer,Gaussian>> m_gaussSet = null;
	private ConcurrentHashMap<Integer,ConcurrentHashMap<Integer,Kernel2D_F32>> m_gaussSetBoofCV = null;
	private ConcurrentHashMap<Integer,ConcurrentHashMap<Integer,CvMat>> m_gaussSetJavaCV = null;
	
	private final double[] ORIENTATIONS = new double[]{0,Math.PI/4, Math.PI/2, 3.0/4.0*Math.PI};
	
	public static void main (String[] args) {
	
		
		File datasetFolder = new File("C:/Users/Khari Jarrett/Documents/VideoProcessing-master/chris/IndividualTubesOnly");
		File[] allVideos = datasetFolder.listFiles();
    	VideoFrameReader frameReader = new VideoFrameReader(allVideos[4].getAbsolutePath().toString());
		frameReader.OpenFile();
		
		int height = frameReader.getFrameHeight();
		int width = frameReader.getFrameWidth(); 
		
		ArrayList<BufferedImage> frames = new ArrayList<BufferedImage>();
		ArrayList<BufferedImage> featureMaps = new ArrayList<BufferedImage>();
		ArrayList<int[][]> maps = new ArrayList<int[][]>();
		VideoFrameWriter vfw = new VideoFrameWriter(new File("C:/Users/Khari Jarrett/Documents/VideoProcessing-master/chris/DOOGFeatures/int8_t55_feats_000.mp4"), width, height, 30);
		vfw.OpenFile();
		
		BufferedImage currFrame = null;
		while ((currFrame = frameReader.NextFrame()) != null) {
			BufferedImage imgCopy = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
			java.awt.Graphics g = imgCopy.getGraphics();
			g.drawImage(currFrame, 0, 0, width, height, null);
			frames.add(imgCopy);
		}
		
		VideoTransformDOOGOverride  featJava = new VideoTransformDOOGOverride (width/2, height/2, new int[]{5,9,17}, VideoTransformDOOGOverride .ColorMode.GRAYSCALE, VideoTransformDOOGOverride .LibraryChoice.Java, true);
		VideoTransformDOOGOverride  featJavaCV = new VideoTransformDOOGOverride (width/2, height/2, new int[]{5,9,17}, VideoTransformDOOGOverride .ColorMode.GRAYSCALE, VideoTransformDOOGOverride .LibraryChoice.JavaCV, false);
		VideoTransformDOOGOverride  featJavaCVPar = new VideoTransformDOOGOverride (width/2, height/2, new int[]{5,9,17}, VideoTransformDOOGOverride .ColorMode.GRAYSCALE, VideoTransformDOOGOverride .LibraryChoice.JavaCVParallel, false);
		VideoTransformDOOGOverride  featBoofCV = new VideoTransformDOOGOverride (width/2, height/2, new int[]{5,9,17}, VideoTransformDOOGOverride .ColorMode.GRAYSCALE, VideoTransformDOOGOverride .LibraryChoice.BoofCV, false);
		VideoTransformDOOGOverride  featBoofCVPar = new VideoTransformDOOGOverride (width/2, height/2, new int[]{5,9,17}, VideoTransformDOOGOverride .ColorMode.GRAYSCALE, VideoTransformDOOGOverride .LibraryChoice.BoofCVParallel, false);

		for (BufferedImage bufferedImageTemp : frames) {
			//extract periphery (grayscaled)
			BufferedImage peripheryBI = new BufferedImage(width/2, height/2, BufferedImage.TYPE_BYTE_GRAY);
			java.awt.Graphics g2 = peripheryBI.getGraphics();
			g2.drawImage(bufferedImageTemp, 0, 0, width/2, height/2, null);
			g2.dispose();
			
/*			UI.tic();
			featJava.ProcessFrame(peripheryBI);
			UI.toc("Java DOOG");     */
			UI.tic();	
			featJavaCV.ProcessFrame(peripheryBI);
			UI.toc("JavaCV DOOG");
/*			UI.tic();
			featJavaCVPar.ProcessFrame(peripheryBI);
			UI.toc("JavaCV Parallel DOOG");	  
			UI.tic();
			featBoofCV.ProcessFrame(peripheryBI);
			UI.toc("BoofCV DOOG");
			UI.tic();
			featBoofCVPar.ProcessFrame(peripheryBI);
			UI.toc("BoofCV Parallel DOOG");    
			float[] DOOG = featJavaCV.getAllFeatures();
			float[] parDOOG = featJavaCVPar.getAllFeatures();
			UI.PrintLn(Vec.Sum(Vec.Sub(DOOG, parDOOG))); */
			
			//Orientation number: 0-3; Scale number: 0-2; 
			float[][] themap = featJavaCV.ExtractFeatureMap(0, 0, 0);
			System.out.println("Size: "+ themap.length+ "Size:" + themap[0].length);
			
			
			int[][] intmap = new int[themap.length][themap[0].length];
			//Convert feature map to integer[][]
			for(int i = 0; i < themap.length; i++) {
				for(int j = 0; j < themap[i].length; j++) {
					intmap[i][j] = (int) Math.ceil((themap[i][j] + 1) * 127.5);
				}
			}
			
			
			
			maps.add(intmap);
			BufferedImage feat = ImageUtils.Grayscale2BufferedImage(intmap, 255);
			featureMaps.add(feat);
			
		}
		
		int[][] finalMap = new int[maps.get(0).length][maps.get(0)[0].length];
		for (int[][] m: maps){
			finalMap = featJavaCV.AddMaps(finalMap,m);
		}
		
		for(int i = 0; i < finalMap.length; i++) {
			for(int j = 0; j < finalMap[i].length; j++) {
				finalMap[i][j] = finalMap[i][j]/maps.size();
			}
		}
		
		BufferedImage featBI = ImageUtils.Grayscale2BufferedImage(finalMap, 255);
		
		/*JFrame frame = new JFrame();
		frame.getContentPane().setLayout(new FlowLayout());
		frame.getContentPane().add(new JLabel(new ImageIcon(featBI)));
		frame.pack();
		frame.setVisible(true);    */
		
		for(int i = 0; i < 50; i++){
			vfw.ProcessFrame(featBI);
		}
		vfw.Close();
	}
	
	
	/**
	 * @param peripheryWidth
	 * @param peripheryHeight
	 * @param distsApart
	 * @param debugMode
	 */
	public VideoTransformDOOGOverride (int inWidth, int inHeight, int[] distsApart, ColorMode colorMode, LibraryChoice libraryChoice, boolean debugMode) {
		super(inWidth, inHeight);
		m_colorMode = colorMode;
		m_library = libraryChoice;
		for (int dist : distsApart) {
			if ((dist - 1) % 4 != 0) {
				throw new IllegalArgumentException("VideoTransformDOOGOverride  constructor: distsApart must only contain numbers = 4*x+1 where x is whatever!");
			}
		}
		m_debugMode = debugMode;
		
		m_orientationList.add(0);
		m_orientationList.add(1);
		m_orientationList.add(2);
		m_orientationList.add(3);
		
		m_distsApart = distsApart;
		for (int distApart : m_distsApart) {
			m_distsApartList.add(distApart);
		}
		
		m_channelList.add(0);
		m_channelList.add(1);
		m_channelList.add(2);
		
		int numStdDevs = 2;
		int aspectRatio = 2;
		m_gaussSet = new ConcurrentHashMap<Integer,ConcurrentHashMap<Integer,Gaussian>>(4); //indexed by orientation, then by sigma
		m_gaussSetBoofCV = new ConcurrentHashMap<Integer,ConcurrentHashMap<Integer,Kernel2D_F32>>(4); //indexed by orientation, then by sigma
		m_gaussSetJavaCV = new ConcurrentHashMap<Integer,ConcurrentHashMap<Integer,CvMat>>(4); //indexed by orientation, then by sigma
		for (int orientNum = 0; orientNum < 4; orientNum++) {
			m_gaussSet.put(orientNum, new ConcurrentHashMap<Integer,Gaussian>());
			m_gaussSetBoofCV.put(orientNum, new ConcurrentHashMap<Integer,Kernel2D_F32>());
			m_gaussSetJavaCV.put(orientNum, new ConcurrentHashMap<Integer,CvMat>());
			for (int distApart : m_distsApart) {
				int sigma = (distApart-1) / 4;
				double orientation = ORIENTATIONS[orientNum];
				double multiplier = 2.5 * numStdDevs * (double)sigma;
				if (orientNum == 1 || orientNum == 3) {
					multiplier = multiplier * Math.sqrt(2.0);
				}
				int width = (int)(multiplier * Math.max(Math.abs(Math.cos(orientation)), Math.abs(Math.sin(orientation)))) + 1;
				int height = (int)(multiplier * Math.max(Math.abs(aspectRatio*Math.sin(orientation)), Math.abs(aspectRatio*Math.cos(orientation)))) + 1;
				if (width > height) {
					height = width;
				} else {
					width = height;
				}
				if (width % 2 == 0) {
					width++;
				}
				if (height % 2 == 0) {
					height++;
				}
				
				Gaussian gauss = new Gaussian(width, height, (double)sigma, 2, orientation);
				if (m_debugMode) {
					gauss.Render();
				}
				m_gaussSet.get(orientNum).put(sigma, gauss);
				
				if (m_library == LibraryChoice.JavaCV || m_library == LibraryChoice.JavaCVParallel) {
					CvMat gaussJavaCV = CvMat.create(gauss.getWidth(), gauss.getWidth(), CV_32F);
				    for (int i = 0; i < gauss.getWidth(); i++) {
				    	for (int j = 0; j < gauss.getWidth(); j++) {
				    		gaussJavaCV.put(i, j, gauss.getKernelData()[i*gauss.getWidth() + j]);
				    	}
				    }
					m_gaussSetJavaCV.get(orientNum).put(sigma, gaussJavaCV);
				} else if (m_library == LibraryChoice.BoofCV || m_library == LibraryChoice.BoofCVParallel) {
					Kernel2D_F32 gaussBoofCV = new Kernel2D_F32(gauss.getWidth());
					gaussBoofCV.data = gauss.getKernelData();
					m_gaussSetBoofCV.get(orientNum).put(sigma, gaussBoofCV);
				}
			}
		}
		
		ArrayList<Integer> orientationIndex = new ArrayList<Integer>();
		ArrayList<Integer> distIndex = new ArrayList<Integer>();
		ArrayList<Integer> channelIndex = new ArrayList<Integer>();
		ArrayList<Integer> pixelXIndex = new ArrayList<Integer>();
		ArrayList<Integer> pixelYIndex = new ArrayList<Integer>();
		
		for (int orientNum = 0; orientNum < 4; orientNum++) {
			for (int distNum = 0; distNum < m_distsApart.length; distNum++) {
				int distApart = m_distsApartList.get(distNum);
				Gaussian gauss = m_gaussSet.get(orientNum).get((distApart-1) / 4);
				int border = (int)Math.ceil((double)gauss.getWidth()/2.0);
			    int step = distApart / 2;
			    int distSqrt = (int)(distApart*Math.sqrt(2));
			    int iStart = border;
			    int jStart = border+distSqrt; //add distSqrt for 135 degree case
			    int iStop = width-border-distSqrt;
			    int jStop = height-border-distSqrt;
			    for (int ii = iStart; ii < iStop; ii+=step) {
			    	for (int jj = jStart; jj < jStop; jj+=step) {
			    		orientationIndex.add(orientNum);
			    		distIndex.add(distNum);
			    		channelIndex.add(0);
			    		pixelXIndex.add(ii);
			    		pixelYIndex.add(jj);
			    	}
			    }
			    if (m_colorMode == ColorMode.COLOR_OPPONENT) {
				    for (int ii = iStart; ii < iStop; ii+=step) {
				    	for (int jj = jStart; jj < jStop; jj+=step) {
				    		orientationIndex.add(orientNum);
				    		distIndex.add(distNum);
				    		channelIndex.add(1);
				    		pixelXIndex.add(ii);
				    		pixelYIndex.add(jj);
				    	}
				    }
				    for (int ii = iStart; ii < iStop; ii+=step) {
				    	for (int jj = jStart; jj < jStop; jj+=step) {
				    		orientationIndex.add(orientNum);
				    		distIndex.add(distNum);
				    		channelIndex.add(2);
				    		pixelXIndex.add(ii);
				    		pixelYIndex.add(jj);
				    	}
				    }
			    }
			}
		}
		m_orientationIndex = Vec.ToPrimitiveInt(orientationIndex);
		m_distApartIndex = Vec.ToPrimitiveInt(distIndex);
		m_channelIndex = Vec.ToPrimitiveInt(channelIndex);
		m_pixelXIndex = Vec.ToPrimitiveInt(pixelXIndex);
		m_pixelYIndex = Vec.ToPrimitiveInt(pixelYIndex);
		
		m_luminance = new byte[width][height];
		m_redvsgreen = new byte[width][height];
		m_yellowvsblue = new byte[width][height];
	}
	
	
	public int[][] AddMaps (int[][] a, int[][] b){
		if ((a.length != b.length) || (a[0].length != b[0].length)){
			throw new IllegalArgumentException("Maps must be the same size");
		}
		
		int[][] toReturn = new int[a.length][a[0].length];
		
		for(int i = 0; i < a.length; i++) {
			for(int j = 0; j < a[i].length; j++) {
				toReturn[i][j] = a[i][j] + b[i][j];
			}
		}
		return toReturn;
	}
	
	/**
	 * @param 	currFrame	frame to process
	 * @return	set of DOOG responses at various scales and orientations - the sign matters!
	 */
	public void ProcessFrame (BufferedImage currFrame) {
		if (m_colorMode == ColorMode.COLOR_OPPONENT) {
			if (currFrame.getColorModel().getNumComponents() < 3) {
				throw new IllegalArgumentException("VideoTransformDOOGOverride .ProcessFrame(): currFrame must be at least a 3-byte image (can't be grayscale)!");
			}
		}
		
		if (m_colorMode == ColorMode.COLOR_OPPONENT) {
			int[][][] opponentImg = ImageUtils.Color2Opponent(currFrame);
			for (int i = 0; i < width; i++) {
				for (int j = 0; j < height; j++) {
					m_luminance[i][j] = common.utils.DataUtils.Int2Unsigned(opponentImg[i][j][0]);
					m_redvsgreen[i][j] = common.utils.DataUtils.Int2Unsigned(opponentImg[i][j][0]);
					m_yellowvsblue[i][j] = common.utils.DataUtils.Int2Unsigned(opponentImg[i][j][2]);
				}
			}
		} else {
			m_luminance = ImageUtils.BufferedImage2Grayscale(currFrame);
		}
		final BufferedImage luminanceBI = ImageUtils.Grayscale2SingleChannelBufferedImage(m_luminance);
		final IplImage luminanceJavaCVImg;
		final GrayF32 luminanceBoofCVImg;
		final BufferedImage redvsgreenBI;
		final IplImage redvsgreenJavaCVImg;
		final GrayF32 redvsgreenBoofCVImg;
		final BufferedImage yellowvsblueBI;
		final IplImage yellowvsblueJavaCVImg;
		final GrayF32 yellowvsblueBoofCVImg;
		if (m_colorMode == ColorMode.COLOR_OPPONENT) {
			redvsgreenBI = ImageUtils.Grayscale2SingleChannelBufferedImage(m_redvsgreen);
			yellowvsblueBI = ImageUtils.Grayscale2SingleChannelBufferedImage(m_yellowvsblue);
		} else { //this is some bullshit
			redvsgreenBI = null;
			yellowvsblueBI = null;
		}
		
		ArrayList<Float> retVal = new ArrayList<Float>();
	/*	
	 * This is to show what's being processed
		JFrame frame = new JFrame();
		frame.getContentPane().setLayout(new FlowLayout());
		frame.getContentPane().add(new JLabel(new ImageIcon(luminanceBI)));
		frame.pack();
		frame.setVisible(true);    //so there actually is something here
		*/
		
		//for JavaCV, sigma = radius / 2.575
		//for BoofCV, sigma = radius / 2.9 + .1724
		
		Map<Integer,LinkedList<Float>> doogs = null;
		
		switch (m_library) {
			case Java:
				//**********
				//Java DOOG
				//**********
				doogs = new TreeMap<Integer,LinkedList<Float>>();
				for (int orientationNum = 0; orientationNum < 4; orientationNum++) {
					LinkedList<Float> currOrientation = new LinkedList<Float>();
					for (int distNum = 0; distNum < m_distsApart.length; distNum++) {
						//distApart = 1 + 4*sigma
						currOrientation.addAll(JavaDOOG(luminanceBI, orientationNum, 0, m_distsApart[distNum]));
						if (m_colorMode == ColorMode.COLOR_OPPONENT) {
							currOrientation.addAll(JavaDOOG(redvsgreenBI, orientationNum, 1, m_distsApart[distNum]));
							currOrientation.addAll(JavaDOOG(yellowvsblueBI, orientationNum, 2, m_distsApart[distNum]));
						}
					}
					doogs.put(orientationNum, currOrientation);
				}
				break;
			case JavaCV:
				//**********
				//JavaCV DOOG
				//**********
				luminanceJavaCVImg = IplImage.createFrom(luminanceBI);			
				if (m_colorMode == ColorMode.COLOR_OPPONENT) {
					redvsgreenJavaCVImg = IplImage.createFrom(redvsgreenBI);
					yellowvsblueJavaCVImg = IplImage.createFrom(yellowvsblueBI);
				} else { //this is some bullshit
					redvsgreenJavaCVImg = null;
					yellowvsblueJavaCVImg = null;
				}
				doogs = new TreeMap<Integer,LinkedList<Float>>();
				for (int orientationNum = 0; orientationNum < 4; orientationNum++) {
					LinkedList<Float> currOrientation = new LinkedList<Float>();
					for (int distNum = 0; distNum < m_distsApart.length; distNum++) {
						//distApart = 1 + 4*sigma
						currOrientation.addAll(JavaCVDOOG(luminanceJavaCVImg, orientationNum, 0, m_distsApart[distNum]));
						if (m_colorMode == ColorMode.COLOR_OPPONENT) {
							currOrientation.addAll(JavaCVDOOG(redvsgreenJavaCVImg, orientationNum, 1, m_distsApart[distNum]));
							currOrientation.addAll(JavaCVDOOG(yellowvsblueJavaCVImg, orientationNum, 2, m_distsApart[distNum]));
						}
					}
					doogs.put(orientationNum, currOrientation);
				}
				break;
			case JavaCVParallel:
				//**********
				//JavaCV DOOG Parallel
				//**********
				luminanceJavaCVImg = IplImage.createFrom(luminanceBI);
				if (m_colorMode == ColorMode.COLOR_OPPONENT) {
					redvsgreenJavaCVImg = IplImage.createFrom(redvsgreenBI);
					yellowvsblueJavaCVImg = IplImage.createFrom(yellowvsblueBI);
				} else { //this is some bullshit
					redvsgreenJavaCVImg = null;
					yellowvsblueJavaCVImg = null;
				}
				doogs = new ConcurrentSkipListMap<Integer,LinkedList<Float>>();
				final Map<Integer,LinkedList<Float>> doogsFinalJavaCV = doogs;
				m_orientationList.stream().parallel().forEach(orientationNum -> doogsFinalJavaCV.put(orientationNum, JavaCVDOOGPar(luminanceJavaCVImg, redvsgreenJavaCVImg, redvsgreenJavaCVImg, orientationNum)));
				break;
			case BoofCV:
				//**********
				//BoofCV DOOG 2
				//**********
				luminanceBoofCVImg = boofcv.io.image.ConvertBufferedImage.convertFromSingle(luminanceBI, null, GrayF32.class);
				if (m_colorMode == ColorMode.COLOR_OPPONENT) {
					redvsgreenBoofCVImg = boofcv.io.image.ConvertBufferedImage.convertFromSingle(redvsgreenBI, null, GrayF32.class);
					yellowvsblueBoofCVImg = boofcv.io.image.ConvertBufferedImage.convertFromSingle(yellowvsblueBI, null, GrayF32.class);
				} else { //this is some bullshit
					redvsgreenBoofCVImg = null;
					yellowvsblueBoofCVImg = null;
				}
				doogs = new TreeMap<Integer,LinkedList<Float>>();
				for (int orientationNum = 0; orientationNum < 4; orientationNum++) {
					LinkedList<Float> currOrientation = new LinkedList<Float>();
					for (int distNum = 0; distNum < m_distsApart.length; distNum++) {
						//distApart = 1 + 4*sigma
						currOrientation.addAll(BoofCVDOOG2(luminanceBoofCVImg, orientationNum, 0, m_distsApart[distNum], frameNum));
						if (m_colorMode == ColorMode.COLOR_OPPONENT) {
							currOrientation.addAll(BoofCVDOOG2(redvsgreenBoofCVImg, orientationNum, 1, m_distsApart[distNum], frameNum));
							currOrientation.addAll(BoofCVDOOG2(yellowvsblueBoofCVImg, orientationNum, 2, m_distsApart[distNum], frameNum));
						}
					}
					doogs.put(orientationNum, currOrientation);
				}
				break;
			case BoofCVParallel:
				//**********
				//BoofCV DOOG 2 Parallel
				//**********
				luminanceBoofCVImg = boofcv.io.image.ConvertBufferedImage.convertFromSingle(luminanceBI, null, GrayF32.class);
				if (m_colorMode == ColorMode.COLOR_OPPONENT) {
					redvsgreenBoofCVImg = boofcv.io.image.ConvertBufferedImage.convertFromSingle(redvsgreenBI, null, GrayF32.class);
					yellowvsblueBoofCVImg = boofcv.io.image.ConvertBufferedImage.convertFromSingle(yellowvsblueBI, null, GrayF32.class);
				} else { //this is some bullshit
					redvsgreenBoofCVImg = null;
					yellowvsblueBoofCVImg = null;
				}
				doogs = new ConcurrentSkipListMap<Integer,LinkedList<Float>>();
				final Map<Integer,LinkedList<Float>> doogsFinalBoof = doogs;
				m_orientationList.stream().parallel().forEach(orientationNum -> doogsFinalBoof.put(orientationNum, BoofCVDOOG2Par(luminanceBoofCVImg, redvsgreenBoofCVImg, yellowvsblueBoofCVImg, orientationNum)));
				break;
		}

		for (Integer key : doogs.keySet()) {
			retVal.addAll(doogs.get(key));
		}
		
		super.FrameComplete();
		m_currFeatures = Vec.ToPrimitiveFloat(retVal);
	}
	
	
	public int getBorderLeft (int orientNum, int distNum) {
		int distApart = m_distsApart[distNum];
		Gaussian gauss = m_gaussSet.get(orientNum).get((distApart-1) / 4);
		int border = (int)Math.ceil((double)gauss.getWidth()/2.0);
	    return border;
	}
	
	
	public int getBorderTop (int orientNum, int distNum) {
		int distApart = m_distsApart[distNum];
		Gaussian gauss = m_gaussSet.get(orientNum).get((distApart-1) / 4);
		int border = (int)Math.ceil((double)gauss.getWidth()/2.0);
	    int distSqrt = (int)(distApart*Math.sqrt(2));
	    return border+distSqrt;
	}
	
	
	public int getBorderRight (int orientNum, int distNum) {
		int distApart = m_distsApart[distNum];
		Gaussian gauss = m_gaussSet.get(orientNum).get((distApart-1) / 4);
		int border = (int)Math.ceil((double)gauss.getWidth()/2.0);
	    int distSqrt = (int)(distApart*Math.sqrt(2));
	    return border+distSqrt;
	}
	
	
	public int getBorderBottom (int orientNum, int distNum) {
		int distApart = m_distsApart[distNum];
		Gaussian gauss = m_gaussSet.get(orientNum).get((distApart-1) / 4);
		int border = (int)Math.ceil((double)gauss.getWidth()/2.0);
	    int distSqrt = (int)(distApart*Math.sqrt(2));
	    return border+distSqrt;
	}


	public float[] getAllFeatures () {
		return java.util.Arrays.copyOf(m_currFeatures, m_currFeatures.length);
	}
	
	
	public int getFeatureMapOrientation (int featMapLoc) {
		return m_orientationIndex[featMapLoc];
	}
	
	
	public int getFeatureMapScaleNum (int featMapLoc) {
		return m_distApartIndex[featMapLoc];
	}
	
	
	public int getFeatureMapPixelX (int featMapLoc) {
		return m_pixelXIndex[featMapLoc];
	}
	
	
	public int getFeatureMapPixelY (int featMapLoc) {
		return m_pixelYIndex[featMapLoc];
	}
	
	
	/**
	 * same length as output vector - for each output element, what orientation was it generated for?
	 * @return
	 */
	public int[] getOrientationIndex () {
		return m_orientationIndex;
	}
	/**
	 * same length as output vector - for each output element, what distNum was it generated for?
	 * @return
	 */
	public int[] getScaleIndex () {
		return m_distApartIndex;
	}
	/**
	 * same length as output vector
	 * @return
	 */
	public int[] getChannelIndex () {
		return m_channelIndex;
	}
	/**
	 * same length as output vector - for each output element, what was it's original pixel x coordinate?
	 * @return
	 */
	public int[] getPixelXIndex () {
		return m_pixelXIndex;
	}
	/**
	 * same length as output vector - for each output element, what was it's original pixel y coordinate?
	 * @return
	 */
	public int[] getPixelYIndex () {
		return m_pixelYIndex;
	}
	
	public int getNumScales () {
		return m_distsApartList.size();
	}
	public int getNumOrientations () {
		return m_orientationList.size();
	}
	public int getNumChannels () {
		return m_channelList.size();
	}
	
	
	public int getFeaturePosFromXY(int pixelX, int pixelY, int orientNum, int scaleNum, int chanNum) {
		for (int i = 0; i < m_orientationIndex.length; i++) {
			if (m_orientationIndex[i] == orientNum && m_distApartIndex[i] == scaleNum && m_channelIndex[i] == chanNum && m_pixelXIndex[i] == pixelX && m_pixelYIndex[i] == pixelY) {
			    return i;
			}
		}
		return -1;
	}
	
	
	public float[][] ExtractFeatureMap (int orientNum, int scaleNum, int chanNum) {
		return ExtractFeatureMap(orientNum, scaleNum, chanNum, m_currFeatures);
	}
	
	
	/**
	 * Given a response, extracts the elements generated for a specific color channel, orientation, and distance, then reconstructs those elements into a matrix using their pixel coordinates
	 * 
	 * @param orientNum
	 * @param scaleNum
	 * @param chanNum
	 * @param feats	ASSUMES this was generated by this particular video transform
	 * @return
	 */
	public float[][] ExtractFeatureMap (int orientNum, int scaleNum, int chanNum, float[] feats) {
		if (feats.length != m_distApartIndex.length) {
			throw new IllegalArgumentException("VideoTransformDOOGOverride.ExtractFeatureMap(): response [length = " + feats.length + "] is not of correct length [" + m_distApartIndex.length + "]!");
		}
		int distApart = m_distsApart[scaleNum];
		Gaussian gauss = m_gaussSet.get(orientNum).get((distApart-1) / 4);
		int border = (int)Math.ceil((double)gauss.getWidth()/2.0);
	    int step = distApart / 2;
	    int distSqrt = (int)(distApart*Math.sqrt(2));
	    int iStart = border;
	    int jStart = border+distSqrt; //add distSqrt for 135 degree case
	    int iStop = width-border-distSqrt;
	    int jStop = height-border-distSqrt;
	    float[][] retVal = new float[(iStop-iStart)/step+1][(jStop-jStart)/step+1];
		
		for (int i = 0; i < feats.length; i++) {
			if (m_orientationIndex[i] == orientNum && m_distApartIndex[i] == scaleNum && m_channelIndex[i] == chanNum) {
			    retVal[(m_pixelXIndex[i]-iStart)/step][(m_pixelYIndex[i]-jStart)/step] = feats[i];
			}
		}
		return retVal;
	}
	
	
	/**
	 * @param input
	 * @param orientNum
	 * @param chanNum		
	 * @param distApart
	 * @return
	 */
	private ArrayList<Float> JavaDOOG (BufferedImage input, int orientNum, int chanNum, int distApart) {
		Gaussian gauss = m_gaussSet.get(orientNum).get((distApart-1) / 4);
		ArrayList<Float> retVal = new ArrayList<Float>();
		
		BufferedImage doogBI = gauss.Filter(input, null);
		
		JFrame frame = new JFrame();
		frame.getContentPane().setLayout(new FlowLayout());
		frame.getContentPane().add(new JLabel(new ImageIcon(input)));
		frame.pack();
		frame.setVisible(true);    //so there actually is something here
		
		int border = (int)Math.ceil((double)gauss.getWidth()/2.0);
		//remove border
//	    int subWidth = input.getWidth()-2*border;
//	    int subHeight = input.getHeight()-2*border;
//	    BufferedImage croppedImg = new BufferedImage(subWidth, subHeight, BufferedImage.TYPE_BYTE_GRAY);
//	    Graphics2D g2d = croppedImg.createGraphics();
//	    g2d.drawImage(doogBI, -border, -border, null);
//	    g2d.dispose();
//	    doogBI = croppedImg;
	    
		float[][] doog = common.math.Mat.Unsigned2Float(common.utils.ImageUtils.BufferedImage2Grayscale(doogBI));
		Mat2.Div(doog, 255.0f); //the end result should be between -1 and +1 (not -255 and +255)
		
	    int step = 1;
	    int distSqrt = (int)(distApart*Math.sqrt(2));
	    int iStart = border;
	    int jStart = border+distSqrt; //add distSqrt for 135 degree case
	    int iStop = doog.length-border-distSqrt;
	    int jStop = doog[0].length-border-distSqrt;
		float[][] csv = null;
		if (m_debugMode) {
			csv = new float[doog.length-2*border][doog[0].length-2*border];
		}
		for (int i = iStart; i < iStop; i+=step) {
			for (int j = jStart; j < jStop; j+=step) {
				switch (orientNum) {
					case 0:
						//**********VERTICAL**********
						retVal.add(doog[i][j] - doog[i+distApart][j]); //difference of OFFSET
						if (m_debugMode) {
							csv[i-border][j-border] = doog[i][j] - doog[i+distApart][j];
						}
						break;
					case 1:
						//**********45 DEGREES**********
						retVal.add(doog[i][j] - doog[i+distSqrt][j+distSqrt]); //difference of OFFSET
						if (m_debugMode) {
							csv[i-border][j-border] = doog[i][j] - doog[i+distSqrt][j+distSqrt];
						}
						break;
					case 2:
						//**********HORIZONTAL**********
						retVal.add(doog[i][j] - doog[i][j+distApart]); //difference of OFFSET
						if (m_debugMode) {
							csv[i-border][j-border] = doog[i][j] - doog[i][j+distApart];
						}
						break;
					case 3:
						//**********135 DEGREES**********
						retVal.add(doog[i][j] - doog[i+distSqrt][j-distSqrt]); //difference of OFFSET
						if (m_debugMode) {
							csv[i-border][j-border] = doog[i][j] - doog[i+distSqrt][j-distSqrt];
						}
						break;
				}
			}
		}
//		if (m_debugMode) {
//			Persistence.SaveCSV(csv, Paths.get("E:", "Desktop", "stashme_doog", "orient" + orientNum + "_dist" + distApart + "_java_diff_" + frameNum + ".csv"));
//			common.utils.Mat2.Sum(csv, 128);
//			FileIO.SaveImage(ImageUtils.Grayscale2BufferedImage(common.utils.Mat.Double2Unsigned(csv), 255), Paths.get("E:", "Desktop", "stashme_doog", "chan" + chanNum + "_orient" + orientNum + "_dist" + distApart + "_java_diff_" + frameNum + ".png"));
//			csv = common.utils.Mat.Normalize0To255(csv);
//			UI.RenderImage(ImageUtils.Grayscale2BufferedImage(csv, 255));
//			UI.RenderImage(ImageUtils.Grayscale2BufferedImage(common.utils.Mat.Double2Int(common.utils.Mat.Normalize0To255(doogDouble)), 255));
//			UI.RenderImage(ImageUtils.Grayscale2BufferedImage(doogByte, 255));
//		}
		return retVal;
	}
	
	
//	private BufferedImage BoofCVDOOG1 (GrayF32 input, double sigma) {
//		Kernel1D_F32 kernel0 = FactoryKernelGaussian.gaussian(Kernel1D_F32.class, sigma, -1);
//		Kernel1D_F32 kernel1 = FactoryKernelGaussian.derivative(1, true, sigma, -1);
////		Kernel1D_I32 kernel = new Kernel1D_I32(2);
////		kernel.offset = 1; // specify the kernel's origin
////		kernel.data[0] = 1;
////		kernel.data[1] = -1;
//		ImageBorder<GrayF32> border = FactoryImageBorder.general(input, BorderType.EXTENDED);
//		GrayF32 output1 = (GrayF32)input._createNew(input.width, input.height);
//		GrayF32 output2 = (GrayF32)input._createNew(input.width, input.height);
//		GConvolveImageOps.horizontal(kernel0, input, output1, border);
//		ShowImages.showWindow(VisualizeImageData.standard(output1, null), "1D Horizontal");
//		GConvolveImageOps.vertical(kernel1, input, output2, border);
//		ShowImages.showWindow(VisualizeImageData.standard(output2, null), "1D Vertical");
//		BufferedImage outputImage = null;
//		
//		double[][] finalKernel = new double[kernel0.width][kernel0.width];
//		for (int i = 0; i < kernel0.width; i++) {
//			for (int j = 0; j < kernel0.width; j++) {
//				finalKernel[i][j] = kernel0.getDouble(i) * kernel1.getDouble(j);
//			}
//		}
//		return outputImage;
//	}
//	private BufferedImage BoofCVDOOG2 (GrayF32 input, double sigma) {
//		Kernel1D_F32 kernel = FactoryKernelGaussian.gaussian2D(Kernel1D_F32.class, sigma, -1);
//		GrayF32 output = new GrayF32(gray.width,gray.height); //Output needs to handle the increased domain after convolution. Can't be 8bit
//		ImageBorder<GrayF32> border = FactoryImageBorder.general(input, BorderType.EXTENDED);
//		GConvolveImageOps.convolve(kernel, input, output, border);
//		ShowImages.showWindow(VisualizeImageData.standard(output, null), "2D Kernel");
//	}
	
	private LinkedList<Float> BoofCVDOOG2Par (GrayF32 luminanceBoofCVImg, GrayF32 redvsgreenBoofCVImg, GrayF32 yellowvsblueBoofCVImgint, int orientNum) {
		LinkedList<Float> currOrientation = new LinkedList<Float>();
		for (int distApart : m_distsApart) {
			//distApart = 1 + 4*sigma
			currOrientation.addAll(BoofCVDOOG2(luminanceBoofCVImg, orientNum, 0, distApart, frameNum));
			if (m_colorMode == ColorMode.COLOR_OPPONENT) {
				currOrientation.addAll(BoofCVDOOG2(redvsgreenBoofCVImg, orientNum, 1, distApart, frameNum));
				currOrientation.addAll(BoofCVDOOG2(yellowvsblueBoofCVImgint, orientNum, 2, distApart, frameNum));
			}
		}
		return currOrientation;
	}
	private LinkedList<Float> BoofCVDOOG2 (GrayF32 input, int orientNum, int chanNum, int distApart, int frameNum) {
		Kernel2D_F32 kernel = m_gaussSetBoofCV.get(orientNum).get((distApart-1) / 4);
		
		LinkedList<Float> retVal = new LinkedList<Float>();

		GrayF32 output = new GrayF32(input.width, input.height);
		ImageBorder<GrayF32> borderHandler = FactoryImageBorder.single(input, BorderType.EXTENDED);
		GConvolveImageOps.convolve(kernel, input, output, borderHandler);

		BufferedImage doogBI = new BufferedImage(output.width, output.height, BufferedImage.TYPE_BYTE_GRAY);
		doogBI = boofcv.io.image.ConvertBufferedImage.convertTo(output, doogBI);
		
		int border = (int)Math.ceil((double)kernel.getWidth()/2.0);
		//remove border
//	    int subWidth = input.getWidth()-2*border;
//	    int subHeight = input.getHeight()-2*border;
//	    BufferedImage croppedImg = new BufferedImage(subWidth, subHeight, BufferedImage.TYPE_BYTE_GRAY);
//	    Graphics2D g2d = croppedImg.createGraphics();
//	    g2d.drawImage(doogBI, -border, -border, null);
//	    g2d.dispose();
//	    doogBI = croppedImg;
		
		float[][] doog = common.math.Mat.Unsigned2Float(ImageUtils.BufferedImage2Grayscale(doogBI));
		Mat2.Div(doog, 255.0f); //the end result should be between -1 and +1 (not -255 and +255)
		
	    int step = distApart / 2;
	    int distSqrt = (int)(distApart*Math.sqrt(2));
	    int iStart = border;
	    int jStart = border+distSqrt; //add distSqrt for 135 degree case
	    int iStop = doog.length-border-distSqrt;
	    int jStop = doog[0].length-border-distSqrt;
		float[][] csv = null;
		if (m_debugMode) {
			csv = new float[doogBI.getWidth()-2*border][doogBI.getHeight()-2*border];
		}
		for (int i = iStart; i < iStop; i+=step) {
			for (int j = jStart; j < jStop; j+=step) {
				switch (orientNum) {
					case 0:
						//**********VERTICAL**********
						retVal.add(doog[i][j] - doog[i+distApart][j]); //difference of OFFSET
						if (m_debugMode) {
							csv[i-border][j-border] = doog[i][j] - doog[i+distApart][j];
						}
						break;
					case 1:
						//**********45 DEGREES**********
						retVal.add(doog[i][j] - doog[i+distSqrt][j+distSqrt]); //difference of OFFSET
						if (m_debugMode) {
							csv[i-border][j-border] = doog[i][j] - doog[i+distSqrt][j+distSqrt];
						}
						break;
					case 2:
						//**********HORIZONTAL**********
						retVal.add(doog[i][j] - doog[i][j+distApart]); //difference of OFFSET
						if (m_debugMode) {
							csv[i-border][j-border] = doog[i][j] - doog[i][j+distApart];
						}
						break;
					case 3:
						//**********135 DEGREES**********
						retVal.add(doog[i][j] - doog[i+distSqrt][j-distSqrt]); //difference of OFFSET
						if (m_debugMode) {
							csv[i-border][j-border] = doog[i][j] - doog[i+distSqrt][j-distSqrt];
						}
						break;
				}
			}
		}
//		if (m_debugMode) {
//			Persistence.SaveCSV(csv, Paths.get("E:", "Desktop", "stashme_doog", "orient" + orientNum + "_dist" + distApart + "_boofcv_diff_" + frameNum + ".csv"));
//			common.utils.Mat2.Sum(csv, 128);
//			FileIO.SaveImage(ImageUtils.Grayscale2BufferedImage(csv, 255), Paths.get("E:", "Desktop", "stashme_doog", "chan" + chanNum + "_orient" + orientNum + "_dist" + distApart + "_boofcv_diff_" + frameNum + ".png"));
//			csv = common.utils.Mat.Normalize0To255(csv);
//			UI.RenderImage(ImageUtils.Grayscale2BufferedImage(common.utils.Mat.Double2Unsigned(csv), 255));
//			UI.RenderImage(doogBI);
//		}
		return retVal;
	}
	
	
	private LinkedList<Float> JavaCVDOOGPar (IplImage luminanceImg, IplImage redvsgreenImg, IplImage yellowvsblueImg, int orientNum) {
		LinkedList<Float> currOrientation = new LinkedList<Float>();
		for (int distApart : m_distsApart) {
			currOrientation.addAll(JavaCVDOOG(luminanceImg, orientNum, 0, distApart));
			if (m_colorMode == ColorMode.COLOR_OPPONENT) {
				currOrientation.addAll(JavaCVDOOG(redvsgreenImg, orientNum, 1, distApart));
				currOrientation.addAll(JavaCVDOOG(yellowvsblueImg, orientNum, 2, distApart));
			}
		}
		return currOrientation;
	}
	private LinkedList<Float> JavaCVDOOG (IplImage img, int orientNum, int chanNum, int distApart) {
		Mat input = new Mat(img, true);
		
		LinkedList<Float> retVal = new LinkedList<Float>();
		
		CvMat gaussMat = m_gaussSetJavaCV.get(orientNum).get((distApart-1) / 4);
	    Mat kernel = new Mat(gaussMat);
	    
	    //filter the image
	    filter2D(input, input, -1, kernel, new opencv_core.Point(-1, -1), 0, org.bytedeco.javacpp.opencv_imgproc.BORDER_CONSTANT);
//	    cvSetImageROI(input, cvRect(10, 15, 150, 250));
//	    Rect roi = new Rect(10, 20, 100, 50);
//	    Mat image_roi = input.adjustROI(-10, -10, -10, -10); //top, bottom, left, right
//		cvCopy(orig, tmp, NULL);
//		cvResetImageROI(orig);
//	    cvSetImageROI(input, cvRect(0, 250, 350, 350));
//		IplImage tmp = cvCreateImage(cvGetSize(input), orig->depth, orig->nChannels);
//		cvCopy(orig, tmp, NULL);
//		cvResetImageROI(orig);
//	    Mat image_roi = input.adjustROI(-10, -10, -10, -10); //top, bottom, left, right
//		cvCopy(orig, tmp, NULL);
//	    IplImage output = new IplImage(input);
//	    cvSetImageROI(output, cvRect(30, 30, 150, 150));
//	    IplImage img2 = cvCreateImage(cvGetSize(output), output.depth(), output.nChannels());
//	    cvCopy(output, img2, null);
	    
	    int border = (int)Math.ceil((double)gaussMat.width()/2.0);
	    
//	    int subWidth = img.width()-2*border;
//	    int subHeight = img.height()-2*border;
//	    BufferedImage croppedImg = new BufferedImage(subWidth, subHeight, BufferedImage.TYPE_BYTE_GRAY);
//	    Graphics2D g2d = croppedImg.createGraphics();
//	    g2d.drawImage(input.getBufferedImage(), -border, -border, null);
//	    g2d.dispose();
//	    BufferedImage doogBI = croppedImg;
//	    byte[][] doogByte = ImageUtils.BufferedImage2Grayscale(doogBI);
//	    int[][] doogInt = common.utils.Mat.Unsigned2Int(doogByte);
//		cvSetImageROI(input, r);//After setting ROI (Region-Of-Interest) all processing will only be done on the ROI
//		IplImage cropped = cvCreateImage(cvGetSize(input), input.depth(), input.nChannels());
//		cvCopy(input, croppedImg); //Copy original image (only ROI) to the cropped image
	    
//	    CvRect r = new CvRect();
//		r.x(border);
//		r.y(border);
//		r.width(subWidth);
//		r.height(subHeight);
//	    CvMat croppedImg = new CvMat();
//	    cvGetSubRect(input.asCvMat(), croppedImg, r);
//	    int[][] doogInt = common.utils.Mat.Unsigned2Int(ImageUtils.BufferedImage2Grayscale(croppedImg.getBufferedImage()));
	    
//	    IplImage temp = input.asIplImage();
//	    float[][] doog = new float[temp.width()][temp.height()];
//	    ByteBuffer buffer = temp.asByteBuffer();
//	    BytePointer buffer = temp.arrayData();
//		int y;
//		for (int x = 0; x < temp.height(); x++) {
//			for (y = 0; y < temp.width(); y++) {
//				doog[y][x] = (float)buffer.get((x*temp.width()+y * 2));
//				doog[y][x] = (float)buffer.get((x*temp.width()+y * 2) +1);
//			}
//		}
	    float[][] doog = common.math.Mat.Unsigned2Float(ImageUtils.BufferedImage2Grayscale(input.getBufferedImage()));
	    Mat2.Div(doog, 255.0f); //the end result should be between -1 and +1 (not -255 and +255)
	    
	    int step = distApart/2;
	    int distSqrt = (int)(distApart*Math.sqrt(2));
	    int iStart = border;
	    int jStart = border+distSqrt; //add distSqrt for 135 degree case
	    int iStop = doog.length-border-distSqrt;
	    int jStop = doog[0].length-border-distSqrt;
	    
		float[][] csv = null;
		if (m_debugMode) {
			csv = new float[doog.length-2*border][doog[0].length-2*border];
		}
		for (int i = iStart; i < iStop; i+=step) {
			for (int j = jStart; j < jStop; j+=step) {
				switch (orientNum) {
					case 0:
						//**********VERTICAL**********
						retVal.add(doog[i][j] - doog[i+distApart][j]); //difference of OFFSET
						if (m_debugMode) {
							csv[i-border][j-border] = doog[i][j] - doog[i+distApart][j];
						}
						break;
					case 1:
						//**********45 DEGREES**********
						retVal.add(doog[i][j] - doog[i+distSqrt][j+distSqrt]); //difference of OFFSET
						if (m_debugMode) {
							csv[i-border][j-border] = doog[i][j] - doog[i+distSqrt][j+distSqrt];
						}
						break;
					case 2:
						//**********HORIZONTAL**********
						retVal.add(doog[i][j] - doog[i][j+distApart]); //difference of OFFSET
						if (m_debugMode) {
							csv[i-border][j-border] = doog[i][j] - doog[i][j+distApart];
						}
						break;
					case 3:
						//**********135 DEGREES**********
						retVal.add(doog[i][j] - doog[i+distSqrt][j-distSqrt]); //difference of OFFSET
						if (m_debugMode) {
							csv[i-border][j-border] = doog[i][j] - doog[i+distSqrt][j-distSqrt];
						}
						break;
				}
			}
		}
		
		if (m_debugMode) {
//			Persistence.SaveCSV(csv, Paths.get("E:", "Desktop", "stashme_doog", "orient" + orientNum + "_dist" + distApart + "_javacv_diff_" + frameNum + ".csv"));
			common.math.Mat2.Sum(csv, 128);
			FileIO.SaveImage(ImageUtils.Grayscale2BufferedImage(common.math.Mat.Float2Unsigned(csv), 255), Paths.get("E:", "Desktop", "stashme_doog", "chan" + chanNum + "_orient" + orientNum + "_dist" + distApart + "_javacv_diff_" + frameNum + ".png").toFile());
//			csv = common.utils.Mat.Normalize0To255(csv);
//			UI.RenderImage(ImageUtils.Grayscale2BufferedImage(csv, 255));
//			UI.RenderImage(ImageUtils.Grayscale2BufferedImage(common.utils.Mat.Normalize0To255(doogInt), 255));
//			UI.RenderImage(croppedImg.getBufferedImage());
		}
		return retVal;
	}
	
	
	//just for unit testing
	private void RenderDOOG () {
		for (int distApart : m_distsApart) {
			int sigma = (distApart-1) / 4;
			//**********VERTICAL**********
			Gaussian gauss = m_gaussSet.get(0).get(sigma);
			int gaussWidth = gauss.getWidth();
			int gaussHeight = gauss.getHeight();
			float maxKernelVal = Vec.MaxVal(gauss.getKernelData());
			float minKernelVal = Vec.MinVal(gauss.getKernelData());
			double[][] kernelMatrix = new double[gaussWidth][gaussHeight];
			for (int i = 0; i < gaussWidth; i++) {
				for (int j = 0; j < gaussHeight; j++) {
					kernelMatrix[i][j] = (gauss.getKernelData()[i*gaussHeight + j]-minKernelVal) * 255.0f / (maxKernelVal-minKernelVal);
				}
			}
			double[][] diff = new double[gaussWidth+100][gaussHeight + 100];
			for (int i = 0; i < gaussWidth; i++) {
				for (int j = 0; j < gaussHeight; j++) {
					diff[i+50-gaussWidth/2][j+50-gaussHeight/2] += kernelMatrix[i][j];
					diff[(int)(i+distApart)+50-gaussWidth/2][(int)(j)+50-gaussHeight/2] += kernelMatrix[i][j];
				}
			}
			UI.RenderImage(ImageUtils.Grayscale2BufferedImage(common.math.Mat.Double2Int(diff), 255));
			//**********45 DEGREES**********
			gauss = m_gaussSet.get(1).get(sigma);
			gaussWidth = gauss.getWidth();
			gaussHeight = gauss.getHeight();
			maxKernelVal = Vec.MaxVal(gauss.getKernelData());
			minKernelVal = Vec.MinVal(gauss.getKernelData());
			kernelMatrix = new double[gaussWidth][gaussHeight];
			for (int i = 0; i < gaussWidth; i++) {
				for (int j = 0; j < gaussHeight; j++) {
					kernelMatrix[i][j] = (gauss.getKernelData()[i*gaussHeight + j]-minKernelVal) * 255.0f / (maxKernelVal-minKernelVal);
				}
			}
			diff = new double[gaussWidth+100][gaussHeight+100];
			for (int i = 0; i < gaussWidth; i++) {
				for (int j = 0; j < gaussHeight; j++) {
					diff[i+50-gaussWidth/2][j+50-gaussHeight/2] += kernelMatrix[i][j];
					diff[(int)(i+distApart/Math.sqrt(2))+50-gaussWidth/2][(int)(j+distApart/Math.sqrt(2))+50-gaussHeight/2] += kernelMatrix[i][j];
				}
			}
			UI.RenderImage(ImageUtils.Grayscale2BufferedImage(common.math.Mat.Double2Int(diff), 255));
			//**********HORIZONTAL**********
			gauss = m_gaussSet.get(2).get(sigma);
			gaussWidth = gauss.getWidth();
			gaussHeight = gauss.getHeight();
			maxKernelVal = Vec.MaxVal(gauss.getKernelData());
			minKernelVal = Vec.MinVal(gauss.getKernelData());
			kernelMatrix = new double[gaussWidth][gaussHeight];
			for (int i = 0; i < gaussWidth; i++) {
				for (int j = 0; j < gaussHeight; j++) {
					kernelMatrix[i][j] = (gauss.getKernelData()[i*gaussHeight + j]-minKernelVal) * 255.0f / (maxKernelVal-minKernelVal);
				}
			}
			diff = new double[gaussWidth+100][gaussHeight + 100];
			for (int i = 0; i < gaussWidth; i++) {
				for (int j = 0; j < gaussHeight; j++) {
					diff[i+50-gaussWidth/2][j+50-gaussHeight/2] += kernelMatrix[i][j];
					diff[(int)(i)+50-gaussWidth/2][(int)(j+distApart)+50-gaussHeight/2] += kernelMatrix[i][j];
				}
			}
			UI.RenderImage(ImageUtils.Grayscale2BufferedImage(common.math.Mat.Double2Int(diff), 255));
			//**********135 DEGREES**********
			gauss = m_gaussSet.get(3).get(sigma);
			gaussWidth = gauss.getWidth();
			gaussHeight = gauss.getHeight();
			maxKernelVal = Vec.MaxVal(gauss.getKernelData());
			minKernelVal = Vec.MinVal(gauss.getKernelData());
			kernelMatrix = new double[gaussWidth][gaussHeight];
			for (int i = 0; i < gaussWidth; i++) {
				for (int j = 0; j < gaussHeight; j++) {
					kernelMatrix[i][j] = (gauss.getKernelData()[i*gaussHeight + j]-minKernelVal) * 255.0f / (maxKernelVal-minKernelVal);
				}
			}
			diff = new double[gaussWidth+100][gaussHeight + 100];
			for (int i = 0; i < gaussWidth; i++) {
				for (int j = 0; j < gaussHeight; j++) {
					diff[i+50-gaussWidth/2][j+50-gaussHeight/2] += kernelMatrix[i][j];
					diff[(int)((double)i+distApart/Math.sqrt(2))+50-gaussWidth/2][(int)((double)j-distApart/Math.sqrt(2))+50-gaussHeight/2] += kernelMatrix[i][j];
				}
			}
			UI.RenderImage(ImageUtils.Grayscale2BufferedImage(common.math.Mat.Double2Int(diff), 255));
		}
	}
}

package tracker;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.swing.JFrame;

import org.math.plot.Plot2DPanel;

import boofcv.alg.filter.binary.BinaryImageOps;
import boofcv.alg.filter.binary.Contour;
import boofcv.alg.filter.binary.ThresholdImageOps;
import boofcv.alg.filter.derivative.GImageDerivativeOps;
import boofcv.alg.misc.ImageStatistics;
import boofcv.alg.shapes.FitData;
import boofcv.alg.shapes.ShapeFittingOps;
import boofcv.gui.feature.VisualizeFeatures;
import boofcv.gui.feature.VisualizeShapes;
import boofcv.gui.image.ImagePanel;
import boofcv.gui.image.ShowImages;
import boofcv.io.MediaManager;
import boofcv.io.UtilIO;
import boofcv.io.image.ConvertBufferedImage;
import boofcv.io.image.SimpleImageSequence;
import boofcv.io.wrapper.DefaultMediaManager;
import common.FileIO;
import common.bagofwords.VocabKMeans;
import common.math.Distance;
import common.math.Mat;
import common.math.Vec;
import common.utils.ColorUtils;
import common.utils.ImageUtils;
import common.utils.ColorUtils.ColorMap;
import common.video.VideoFrameWriter;
import common.video.VideoRetina;
import georegression.struct.shapes.EllipseRotated_F64;
import georegression.struct.shapes.Rectangle2D_I32;
import boofcv.struct.ConnectRule;
import boofcv.struct.image.GrayF32;
import boofcv.struct.image.GrayU8;
import boofcv.struct.image.ImageGray;
import boofcv.struct.image.ImageType;

@SuppressWarnings("rawtypes")
public class MotionCluster<T extends ImageGray> {
	
	Class<T> imageType;
	ImagePanel gui = new ImagePanel();
	int clustersize;
	int streaksize;
	ArrayList<ClusterFrame> renderInfo;
	
	Map<Long, FeaturePosition[]> featMap;
	double[][] rawData;
	double[][] vectors;
	double[][] clusters;
	
	double[][] streakdata;
	
	public MotionCluster(Class<T> imageType, ArrayList<ClusterFrame> renderInfo, int clustersize, int streaksize) {
		this.imageType = imageType;
		this.renderInfo = renderInfo;
		this.clustersize = clustersize;
		this.streaksize = streaksize;
	}
	
	/**
	 * Transfers frame-by-frame information about features into feature by feature information
	 * Key: Feature ID
	 * Value: Array of FeaturePositions, where position i carries information at frame i; 
	 */
	public void featInfo(boolean motionLabeled) {
		
		featMap = new HashMap<Long, FeaturePosition[]>();
		
		int numFrames = renderInfo.size();
		for(int i = 0; i < numFrames; i++) {
			for(ClusterFeature c: renderInfo.get(i).features) {
				if(!featMap.containsKey(c.id)) {					
					//create key-value pair
					FeaturePosition[] apps = new FeaturePosition[numFrames];
					if(motionLabeled) {
						apps[i] = new FeaturePosition(c.clusterno, c.x, c.y);
					}
					else {
						apps[i] = new FeaturePosition(c.id, c.x, c.y);						
					}

					featMap.put(c.id, apps);
					
				}
				else {	
					
					//update FeaturePosition Array
					FeaturePosition[] apps = featMap.get(c.id);
					
					if(motionLabeled) {
						apps[i] = new FeaturePosition(c.clusterno, c.x, c.y);
					}
					else {
						apps[i] = new FeaturePosition(c.id, c.x, c.y);						
					}
					
				}
			}
		}
		
		//only adds features if they move around enough, defined by standard deviation of x and y pos
		// "enough" set based on pacman values
		Map<Long, FeaturePosition[]> featMap2 = new HashMap<Long, FeaturePosition[]>();

		for(Long l : featMap.keySet()) {
			
			//put into array of doubles to get standard deviation
			
			ArrayList<Double> xpos = new ArrayList<Double>();
			ArrayList<Double> ypos = new ArrayList<Double>();
			
			FeaturePosition[] apps = featMap.get(l);
			for(int i = 0; i < apps.length; i++) {
				if(apps[i] != null) {
					xpos.add(apps[i].x);
					ypos.add(apps[i].y);
				}
			}

			double xd[] = new double[xpos.size()];
			double yd[] = new double[ypos.size()];

			for(int i = 0; i < xpos.size(); i++) {
				xd[i] = (double) xpos.get(i);
				yd[i] = (double) ypos.get(i);
			}
			
			double xdev = Vec.StdDev(xd);
			double ydev = Vec.StdDev(yd);
			
			//add if standard deviation > 10 for x or y
			if(xdev > 10 || ydev > 10) {
				featMap2.put(l, featMap.get(l));
			}
			
		}
		
		featMap = featMap2;
	
	}
	
	/**
	 * Generates "split-up" features based on features seen in frames
	 * Split-up is done by turns or stop ends for streaks whose length > threshold
	 * Output is CSV file (named using destBase)
	 */
	public void relativeMotion(String destBase) {
		
		featInfo(true);
		
		int numFrames = renderInfo.size();
		
		System.out.println("Features: " + featMap.keySet().size() + ", Frames: " + numFrames);
		
		ArrayList<double[]>	relMoves = new ArrayList<double[]>(); //movement from first position
		ArrayList<Integer> streaks = new ArrayList<Integer>(); //streak length for each streak
		ArrayList<Long> ids = new ArrayList<Long>(); //feature ID for streak (used later for rendering)
		ArrayList<Integer> startframe = new ArrayList<Integer>(); //start frame of streak (used for rendering)
		ArrayList<Integer> endframe = new ArrayList<Integer>(); //end frame of streak (used for rendering)
		
		int maxj = 0;
		
		for(Long l : featMap.keySet()) {
			FeaturePosition[] posInfo = featMap.get(l);	
			for(int i = 0; i < posInfo.length; i++) {
				//i is frame number
				
				//potential movement streak detected
				if(posInfo[i] != null && posInfo[i].id == 1) {
					FeaturePosition c = posInfo[i];
					int start = i;
					i++;
					
					int j = 0; //used to count streak length
					double[] movement = new double[numFrames*2];
					while(i < posInfo.length && posInfo[i] != null && posInfo[i].id == 1) {
						movement[j] = posInfo[i].x - c.x;
						movement[numFrames+j] = posInfo[i].y - c.y;
						
						i++; //increment to avoid doubly-counting streaks
						j++;
					}
					
					if(j >= streaksize) {
						relMoves.add(movement);
						streaks.add(j);
						ids.add(l);
						startframe.add(start);
						endframe.add(i-1);
					}
					if(j > maxj) maxj = j;
				}
			}
			
		}

		//set to 10 to crop length of appearance
		maxj = streaksize;
		
		//compress array to save space, crop streaks > maxj
		double relgrid[][] = new double[relMoves.size()][(maxj*2)+3];
		for(int i = 0; i < relMoves.size(); i++) {
			double frame[] = relMoves.get(i);
			relgrid[i][0] = (double) ids.get(i);
			relgrid[i][1] = startframe.get(i);
			relgrid[i][2] = endframe.get(i);
			for(int j = 0; j < maxj; j++) {
				relgrid[i][j+3] = frame[j];
				relgrid[i][j+maxj+3] = frame[j+numFrames];
			}
		}

		System.out.println("Maximum streak: " + maxj);
		rawData = relgrid;
		
		if(destBase != null) {
			FileIO.SaveCSV(relgrid, new File(destBase + "_rel.csv"));
			
			int streakct[] = new int[streaks.size()];
			for(int i = 0; i < streaks.size(); i++) streakct[i] = streaks.get(i);
			
			FileIO.SaveCSV(streakct, new File(destBase + "_streaks.csv"));
		}
		
	}
	
public void featureWindows() {
		
		featInfo(true);
		
		int numFrames = renderInfo.size();
		
		System.out.println("Features: " + featMap.keySet().size() + ", Frames: " + numFrames);

		ArrayList<Long> ids = new ArrayList<Long>(); //feature ID for streak (used later for rendering)
		ArrayList<Integer> startframe = new ArrayList<Integer>(); //start frame of streak (used for rendering)
		ArrayList<Integer> endframe = new ArrayList<Integer>(); //end frame of streak (used for rendering)
		
		int maxj = 0;
		
		for(Long l : featMap.keySet()) {
			FeaturePosition[] posInfo = featMap.get(l);	
			for(int i = 0; i < posInfo.length; i++) {
				//i is frame number
				
				//potential movement streak detected
				if(posInfo[i] != null && (posInfo[i].id == 1 || posInfo[i].id == 2)) {
					FeaturePosition c = posInfo[i];
					int start = i;
					i++;
					
					int j = 0; //used to count streak length
					while(i < posInfo.length && posInfo[i] != null && (posInfo[i].id == 1 || posInfo[i].id == 2)) {
						
						i++; //increment to avoid doubly-counting streaks
						j++;
					}
					
					if(j >= streaksize) {
						ids.add(l);
						startframe.add(start);
						endframe.add(i-1);
					}
					if(j > maxj) maxj = j;
				}
			}
			
		}

		//set to 10 to crop length of appearance
		maxj = streaksize;
		
		//compress array to save space, crop streaks > maxj
		double relgrid[][] = new double[ids.size()][3];
		for(int i = 0; i < ids.size(); i++) {
			relgrid[i][0] = (double) ids.get(i);
			relgrid[i][1] = startframe.get(i);
			relgrid[i][2] = endframe.get(i);
		}

		streakdata = relgrid;
		
	}
	
	public void relativeMotionWithTurns(String destBase) {
		
		featInfo(true);
		int numFrames = renderInfo.size();
		
		System.out.println("Features: " + featMap.keySet().size() + ", Frames: " + numFrames);
		
		ArrayList<double[]>	relMoves = new ArrayList<double[]>();
		ArrayList<Integer> streaks = new ArrayList<Integer>();
		ArrayList<Long> ids = new ArrayList<Long>();
		ArrayList<Integer> startframe = new ArrayList<Integer>();
		ArrayList<Integer> endframe = new ArrayList<Integer>();
		
		int maxj = 0;
		int turns = 0, stopend = 0;
		
		for(Long l : featMap.keySet()) {
			FeaturePosition[] posInfo = featMap.get(l);	
			for(int i = 0; i < posInfo.length; i++) {
				if(posInfo[i] != null && (posInfo[i].id == 2)) { //|| posInfo[i].id == 3)) {
					
					int flag = 0;
					
					if(i < 5 || posInfo[i-5] == null) continue;
					FeaturePosition c = posInfo[i-5];
					
					double[] movement = new double[numFrames*2];
					
					int j;
					for(j = i-4; j <= i; j++) {
						if(posInfo[j] == null) {
							flag = 1;
							break;
						}
						movement[j-i+4] = posInfo[j].x - c.x;
						movement[j-i+4+numFrames] = posInfo[j].y - c.y;
					}
//					
//					while(posInfo[j] != null && posInfo[j].id == 2) {
////						System.out.println("pass");
//						j++;
//					}
//					if(posInfo[j] == null) continue;
//					
					int val = 5;
					for(; j <= i+5; j++) {
						if(posInfo[j] == null) {
							flag = 1;
							break;
						}
						movement[val] = posInfo[j].x - c.x;
						movement[val+numFrames] = posInfo[j].y - c.y;
						val++;
					}
		
					if(flag == 0) {
						relMoves.add(movement);
						streaks.add(10);
						ids.add(l);
						startframe.add(i-5);
						endframe.add(i+5);
						turns++;
					}
					
					while(i+1 < posInfo.length && posInfo[i+1] != null && posInfo[i+1].id == 2) {
						i++;
					}
//					System.out.println("turn");
				}
				if(posInfo[i] != null && (posInfo[i].id == 3)) { //|| posInfo[i].id == 3)) {
					stopend++;
				}
			}
			for(int i = 0; i < posInfo.length; i++) {
				if(posInfo[i] != null && posInfo[i].id == 1) {
					FeaturePosition c = posInfo[i];
					int start = i;
					i++;
					
					int j = 0;
					double[] movement = new double[numFrames*2];
					while(posInfo[i] != null && posInfo[i].id == 1) {
						movement[j] = posInfo[i].x - c.x;
						movement[numFrames+j] = posInfo[i].y - c.y;
						
						i++;
						j++;
					}
					
					if(j >= streaksize) {
						relMoves.add(movement);
						streaks.add(j);
						ids.add(l);
						startframe.add(start);
						endframe.add(i-1);
					}
					if(j > maxj) maxj = j;
				}
			}
			
		}

		maxj = streaksize;
		
		double relgrid[][] = new double[relMoves.size()][(maxj*2)+3];
		for(int i = 0; i < relMoves.size(); i++) {
			double frame[] = relMoves.get(i);
			relgrid[i][0] = (double) ids.get(i);
			relgrid[i][1] = startframe.get(i);
			relgrid[i][2] = endframe.get(i);
			for(int j = 0; j < maxj; j++) {
				relgrid[i][j+3] = frame[j];
				relgrid[i][j+maxj+3] = frame[j+numFrames];
			}
		}
		
		System.out.println("New Features: " + relMoves.size());
		System.out.println("Turns: " + turns);
		System.out.println("Stopends: " + stopend);
		System.out.println("Maximum streak: " + maxj);
		
		if(destBase != null) {
			FileIO.SaveCSV(relgrid, new File(destBase + "_rel.csv"));
			
			int streakct[] = new int[streaks.size()];
			for(int i = 0; i < streaks.size(); i++) streakct[i] = streaks.get(i);
			
			FileIO.SaveCSV(streakct, new File(destBase + "_streaks.csv"));
		}
		
	}

	public void relativeVelocityWithTurns(String destBase) {
		
		featInfo(true);
		int numFrames = renderInfo.size();
		
		System.out.println("Features: " + featMap.keySet().size() + ", Frames: " + numFrames);
		
		ArrayList<double[]>	relMoves = new ArrayList<double[]>();
		ArrayList<Integer> streaks = new ArrayList<Integer>();
		ArrayList<Long> ids = new ArrayList<Long>();
		ArrayList<Integer> startframe = new ArrayList<Integer>();
		ArrayList<Integer> endframe = new ArrayList<Integer>();
		
		int maxj = 0;
		int turns = 0, stopend = 0;
		
		for(Long l : featMap.keySet()) {
			FeaturePosition[] posInfo = featMap.get(l);	
			for(int i = 0; i < posInfo.length; i++) {
				if(posInfo[i] != null && (posInfo[i].id == 2)) { //|| posInfo[i].id == 3)) {
					
					int flag = 0;
					
					if(i < 5 || posInfo[i-5] == null) continue;
					FeaturePosition c = posInfo[i-5];
					
					double[] movement = new double[numFrames*2];
					
					int j;
					for(j = i-4; j <= i; j++) {
						if(posInfo[j] == null) {
							flag = 1;
							break;
						}
						movement[j-i+4] = posInfo[j].x - c.x;
						movement[j-i+4+numFrames] = posInfo[j].y - c.y;
						
						c = posInfo[j];
					}
	
					int val = 5;
					for(; j <= i+5; j++) {
						if(posInfo[j] == null) {
							flag = 1;
							break;
						}
						movement[val] = posInfo[j].x - c.x;
						movement[val+numFrames] = posInfo[j].y - c.y;

						c = posInfo[j];
						val++;
					}
					
					
					
					if(flag == 0) {
						relMoves.add(movement);
						streaks.add(10);
						ids.add(l);
						startframe.add(i-5);
						endframe.add(i+5);
						turns++;

						while(i+1 < posInfo.length && posInfo[i+1] != null && posInfo[i+1].id == 2) {
							i++;
						}
			
					}

////					System.out.println("turn");
				}
			}
//			for(int i = 0; i < posInfo.length; i++) {
//				if(posInfo[i] != null && (posInfo[i].id == 3)) { //|| posInfo[i].id == 3)) {
//					
//					int flag = 0;
//					
//					if(i < 5 || posInfo[i-5] == null) continue;
//					FeaturePosition c = posInfo[i-5];
//					
//					double[] movement = new double[numFrames*2];
//					
//					int j;
//					for(j = i-4; j <= i; j++) {
//						if(posInfo[j] == null) {
//							flag = 1;
//							break;
//						}
//						movement[j-i+4] = posInfo[j].x - c.x;
//						movement[j-i+4+numFrames] = posInfo[j].y - c.y;
//						
//						c = posInfo[j];
//					}
//	
//					int val = 5;
//					for(; j <= i+5; j++) {
//						if(posInfo[j] == null) {
//							flag = 1;
//							break;
//						}
//						movement[val] = posInfo[j].x - c.x;
//						movement[val+numFrames] = posInfo[j].y - c.y;
//
//						c = posInfo[j];
//						val++;
//					}
//
//					if((Math.abs(movement[7]) < 1 && Math.abs(movement[7+numFrames]) < 1)) {// || (Math.abs(movement[3]) < 1 && Math.abs(movement[3+numFrames]) < 1)) {
//						flag = 1;
//					}
////
//					if((Math.abs(movement[6]) < 1 && Math.abs(movement[6+numFrames]) < 1)) {// || (Math.abs(movement[3]) < 1 && Math.abs(movement[3+numFrames]) < 1)) {
//						flag = 1;
//					}
//					
//					
////					if(Math.abs(movement[3]) > 1 || Math.abs(movement[3+numFrames]) > 1 || Math.abs(movement[4]) > 1 || Math.abs(movement[4+numFrames]) > 1 
////							|| Math.abs(movement[5]) > 1 || Math.abs(movement[5+numFrames]) > 1 || Math.abs(movement[1]) > 1 || Math.abs(movement[1+numFrames]) > 1 
////							|| Math.abs(movement[2]) > 1 || Math.abs(movement[2+numFrames]) > 1) {
////						flag = 1;
////					}
//					
////					if(Math.abs(movement[7]) < 1 || Math.abs(movement[7+numFrames]) < 1) flag = 1;
////					
//					if(flag == 0) {
//						relMoves.add(movement);
//						streaks.add(10);
//						ids.add(l);
//						startframe.add(i-5);
//						endframe.add(i+5);
//						stopend++;
//			
//						while(i+1 < posInfo.length && posInfo[i+1] != null && posInfo[i+1].id == 3) {
//							i++;
//						}
//						
//					}
//
//				}
//
//			}
			for(int i = 0; i < posInfo.length; i++) {
				if(posInfo[i] != null && posInfo[i].id == 1) {
					FeaturePosition c = posInfo[i];
					int start = i;
					i++;
					
					int j = 0;
					double[] movement = new double[numFrames*2];
					while(posInfo[i] != null && posInfo[i].id == 1) {
						movement[j] = posInfo[i].x - c.x;
						movement[numFrames+j] = posInfo[i].y - c.y;

						c = posInfo[i];
						i++;
						j++;
						
					}
					
					if(j > streaksize) {
						relMoves.add(movement);
						streaks.add(j);
						ids.add(l);
						startframe.add(start);
						endframe.add(i-1);
					}
					if(j > maxj) maxj = j;
				}
			}
			
		}

		maxj = streaksize;
		
		double relgrid[][] = new double[relMoves.size()][(maxj*2)+3];
		for(int i = 0; i < relMoves.size(); i++) {
			double frame[] = relMoves.get(i);
			relgrid[i][0] = (double) ids.get(i);
			relgrid[i][1] = startframe.get(i);
			relgrid[i][2] = endframe.get(i);
			for(int j = 0; j < maxj; j++) {
				//without smoothing
//				relgrid[i][j+3] = frame[j]; 
//				relgrid[i][j+maxj+3] = frame[j+numFrames];
//				
//				with smoothing
				if(j == 0) {
					relgrid[i][j+3] = 0.67*frame[j] + 0.33 * frame[j+1]; 
					relgrid[i][j+maxj+3] = 0.67*frame[j+numFrames] + 0.33 * frame[j+numFrames+1];
				}
				else if(j == maxj-1) {
					relgrid[i][j+3] = 0.67*frame[j] + 0.67 * frame[j-1];
					relgrid[i][j+maxj+3] = 0.67*frame[j+numFrames] + 0.33 * frame[j+numFrames-1];
				}
				else if(j == 1) {
					relgrid[i][j+3] = 0.22 * frame[j-1] + 0.45*frame[j] + 0.22 * frame[j+1] + 0.11 * frame[j+2];
					relgrid[i][j+maxj+3] = 0.22 * frame[j+numFrames-1] + 0.45*frame[j+numFrames] + 0.22 * frame[j+numFrames+1] + 0.11 * frame[j+numFrames+2];					
				}
				else if(j == maxj-2) {
					relgrid[i][j+3] = 0.22 * frame[j+1] + 0.45*frame[j] + 0.22 * frame[j-1] + 0.11 * frame[j-2];
					relgrid[i][j+maxj+3] = 0.22 * frame[j+numFrames+1] + 0.45*frame[j+numFrames] + 0.22 * frame[j+numFrames-1] + 0.11 * frame[j+numFrames-2];					
				}
				else {
					relgrid[i][j+3] = 0.1 * frame[j+2] + 0.2 * frame[j+1] + 0.4*frame[j] + 0.2 * frame[j-1] + 0.1 * frame[j-2];
					relgrid[i][j+maxj+3] =  0.1 * frame[j+numFrames+2] + 0.2 * frame[j+numFrames+1] + 0.4*frame[j+numFrames] + 0.2 * frame[j+numFrames-1] + 0.1 * frame[j+numFrames-2];					
				}
			}
		}

		System.out.println("New Features: " + relMoves.size());
		System.out.println("Turns: " + turns);
		System.out.println("Stopends: " + stopend);
		System.out.println("Maximum streak: " + maxj);
		
		rawData = relgrid;
		
		if(destBase != null) {
			FileIO.SaveCSV(relgrid, new File(destBase + "_rel.csv"));
			
			int streakct[] = new int[streaks.size()];
			for(int i = 0; i < streaks.size(); i++) streakct[i] = streaks.get(i);
			
			FileIO.SaveCSV(streakct, new File(destBase + "_streaks.csv"));
		}
		

	}
	
public void polar(String destBase) {
		
		featInfo(true);
		int numFrames = renderInfo.size();
		
		System.out.println("Features: " + featMap.keySet().size() + ", Frames: " + numFrames);
		
		ArrayList<double[]>	relMoves = new ArrayList<double[]>();
		ArrayList<Integer> streaks = new ArrayList<Integer>();
		ArrayList<Long> ids = new ArrayList<Long>();
		ArrayList<Integer> startframe = new ArrayList<Integer>();
		ArrayList<Integer> endframe = new ArrayList<Integer>();
		
		int maxj = 0;
		int turns = 0, stopend = 0;
		double maxvel = 0;
		
		for(Long l : featMap.keySet()) {
			FeaturePosition[] posInfo = featMap.get(l);	
			for(int i = 0; i < posInfo.length; i++) {
				if(posInfo[i] != null && posInfo[i].id == 1) {
					FeaturePosition c = posInfo[i];
					int start = i;
					i++;
					
					int j = 0;
					double[] movement = new double[numFrames*2];
					while(posInfo[i] != null && posInfo[i].id == 1) {
						movement[j] = Math.sqrt(Math.pow(posInfo[i].x - c.x, 2) + Math.pow(posInfo[i].y - c.y, 2));
						if(movement[j] > maxvel) maxvel = movement[j];
						movement[numFrames+j] = Math.toDegrees(Math.atan2(posInfo[i].y - c.y, posInfo[i].x - c.x))/360;

						c = posInfo[i];
						i++;
						j++;
						
					}
					
					if(j > streaksize) {
						relMoves.add(movement);
						streaks.add(j);
						ids.add(l);
						startframe.add(start);
						endframe.add(i-1);
					}
					if(j > maxj) maxj = j;
				}
			}
			
		}

		maxj = streaksize;
		
		double relgrid[][] = new double[relMoves.size()][(maxj*2)+3];
		for(int i = 0; i < relMoves.size(); i++) {
			double frame[] = relMoves.get(i);
			relgrid[i][0] = (double) ids.get(i);
			relgrid[i][1] = startframe.get(i);
			relgrid[i][2] = endframe.get(i);
			for(int j = 0; j < maxj; j++) {
				//without smoothing
//				relgrid[i][j+3] = frame[j]; 
				relgrid[i][j+maxj+3] = frame[j+numFrames];
//				
//				with smoothing
				if(j == 0) {
					relgrid[i][j+3] = (0.67*frame[j] + 0.33 * frame[j+1])/maxvel; 
//					relgrid[i][j+maxj+3] = 0.67*frame[j+numFrames] + 0.33 * frame[j+numFrames+1];
				}
				else if(j == maxj-1) {
					relgrid[i][j+3] = (0.67*frame[j] + 0.67 * frame[j-1])/maxvel;
//					relgrid[i][j+maxj+3] = 0.67*frame[j+numFrames] + 0.33 * frame[j+numFrames-1];
				}
				else if(j == 1) {
					relgrid[i][j+3] = (0.22 * frame[j-1] + 0.45*frame[j] + 0.22 * frame[j+1] + 0.11 * frame[j+2])/maxvel;
//					relgrid[i][j+maxj+3] = 0.22 * frame[j+numFrames-1] + 0.45*frame[j+numFrames] + 0.22 * frame[j+numFrames+1] + 0.11 * frame[j+numFrames+2];					
				}
				else if(j == maxj-2) {
					relgrid[i][j+3] = (0.22 * frame[j+1] + 0.45*frame[j] + 0.22 * frame[j-1] + 0.11 * frame[j-2])/maxvel;
//					relgrid[i][j+maxj+3] = 0.22 * frame[j+numFrames+1] + 0.45*frame[j+numFrames] + 0.22 * frame[j+numFrames-1] + 0.11 * frame[j+numFrames-2];					
				}
				else {
					relgrid[i][j+3] = (0.1 * frame[j+2] + 0.2 * frame[j+1] + 0.4*frame[j] + 0.2 * frame[j-1] + 0.1 * frame[j-2])/maxvel;
//					relgrid[i][j+maxj+3] =  0.1 * frame[j+numFrames+2] + 0.2 * frame[j+numFrames+1] + 0.4*frame[j+numFrames] + 0.2 * frame[j+numFrames-1] + 0.1 * frame[j+numFrames-2];					
				}
			}
		}

		System.out.println("New Features: " + relMoves.size());
		System.out.println("Turns: " + turns);
		System.out.println("Stopends: " + stopend);
		System.out.println("Maximum streak: " + maxj);
		
		rawData = relgrid;
		
		if(destBase != null) {
			FileIO.SaveCSV(relgrid, new File(destBase + "_rel.csv"));
			
			int streakct[] = new int[streaks.size()];
			for(int i = 0; i < streaks.size(); i++) streakct[i] = streaks.get(i);
			
			FileIO.SaveCSV(streakct, new File(destBase + "_streaks.csv"));
		}
		

	}

	public void polar2(String destBase) {
		
		featInfo(true);
		int numFrames = renderInfo.size();
		
		System.out.println("Features: " + featMap.keySet().size() + ", Frames: " + numFrames);
		
		ArrayList<double[]>	relMoves = new ArrayList<double[]>();
		ArrayList<Integer> streaks = new ArrayList<Integer>();
		ArrayList<Long> ids = new ArrayList<Long>();
		ArrayList<Integer> startframe = new ArrayList<Integer>();
		ArrayList<Integer> endframe = new ArrayList<Integer>();
		
		int maxj = 0;
		int turns = 0, stopend = 0;
		
		for(Long l : featMap.keySet()) {
			FeaturePosition[] posInfo = featMap.get(l);	
			for(int i = 0; i < posInfo.length; i++) {
				if(posInfo[i] != null && (posInfo[i].id == 1 || posInfo[i].id == 2)) {
					FeaturePosition c = posInfo[i];
					int current = i;
					current++;
					
					int j = 0;
					double[] movement = new double[numFrames*2];
					while(current < posInfo.length && j < streaksize && posInfo[current] != null && (posInfo[i].id == 1 || posInfo[i].id == 2)) {
						movement[j] = Math.sqrt(Math.pow(posInfo[current].x - c.x, 2) + Math.pow(posInfo[current].y - c.y, 2));
						movement[numFrames+j] = Math.toDegrees(Math.atan2(posInfo[current].y - c.y, posInfo[current].x - c.x));
	
						c = posInfo[current];
						current++;
						j++;
						
					}
					
					if(j >= streaksize) {
						relMoves.add(movement);
						streaks.add(j);
						ids.add(l);
						startframe.add(i);
						endframe.add(current-1);
					}
					if(j > maxj) maxj = j;
				}
			}
			
		}
		maxj = streaksize;
		
		double relgrid[][] = new double[relMoves.size()][(maxj*2)+3+2];
		
		double angleValues[] = new double[relMoves.size()*maxj];
		double velocityValues[] = new double[relMoves.size()*maxj];	
		double stdstdangle[] = new double[relMoves.size()];
		double stdstdvelocity[] = new double[relMoves.size()];
		
		for(int i = 0; i < relMoves.size(); i++) {
			double frame[] = relMoves.get(i);
			relgrid[i][0] = (double) ids.get(i);
			relgrid[i][1] = startframe.get(i);
			relgrid[i][2] = endframe.get(i);
			
			double stdAngleValues[] = new double[maxj];
			double stdVelocityValues[] = new double[maxj];
			
			for(int j = 0; j < maxj; j++) {
				//without smoothing
	//			relgrid[i][j+3] = frame[j]; 
				relgrid[i][j+maxj+3] = frame[j+numFrames];
	//			
	//			with smoothing
				if(j == 0) {
					relgrid[i][j+3] = (0.67*frame[j] + 0.33 * frame[j+1]); 
	//				relgrid[i][j+maxj+3] = 0.67*frame[j+numFrames] + 0.33 * frame[j+numFrames+1];
				}
				else if(j == maxj-1) {
					relgrid[i][j+3] = (0.67*frame[j] + 0.67 * frame[j-1]);
	//				relgrid[i][j+maxj+3] = 0.67*frame[j+numFrames] + 0.33 * frame[j+numFrames-1];
				}
				else if(j == 1) {
					relgrid[i][j+3] = (0.22 * frame[j-1] + 0.45*frame[j] + 0.22 * frame[j+1] + 0.11 * frame[j+2]);
	//				relgrid[i][j+maxj+3] = 0.22 * frame[j+numFrames-1] + 0.45*frame[j+numFrames] + 0.22 * frame[j+numFrames+1] + 0.11 * frame[j+numFrames+2];					
				}
				else if(j == maxj-2) {
					relgrid[i][j+3] = (0.22 * frame[j+1] + 0.45*frame[j] + 0.22 * frame[j-1] + 0.11 * frame[j-2]);
	//				relgrid[i][j+maxj+3] = 0.22 * frame[j+numFrames+1] + 0.45*frame[j+numFrames] + 0.22 * frame[j+numFrames-1] + 0.11 * frame[j+numFrames-2];					
				}
				else {
					relgrid[i][j+3] = (0.1 * frame[j+2] + 0.2 * frame[j+1] + 0.4*frame[j] + 0.2 * frame[j-1] + 0.1 * frame[j-2]);
	//				relgrid[i][j+maxj+3] =  0.1 * frame[j+numFrames+2] + 0.2 * frame[j+numFrames+1] + 0.4*frame[j+numFrames] + 0.2 * frame[j+numFrames-1] + 0.1 * frame[j+numFrames-2];					
				}
				
				angleValues[i*maxj + j] = relgrid[i][j+maxj+3];
				velocityValues[i*maxj + j] = relgrid[i][j+3];
				
				stdAngleValues[j] = relgrid[i][j+maxj+3];
				stdVelocityValues[j] = relgrid[i][j+3];
			}
			
				relgrid[i][2*maxj+3] = Vec.StdDev(stdVelocityValues);
				relgrid[i][2*maxj+4] = Vec.StdDev(stdAngleValues);
				
				stdstdangle[i] = relgrid[i][2*maxj+4];
				stdstdvelocity[i] = relgrid[i][2*maxj+3];
			
		}
		
		double sdVelocity = Vec.StdDev(velocityValues);
		double sdAngle = Vec.StdDev(angleValues);
		double s3a = Vec.StdDev(stdstdangle);
		double s3v = Vec.StdDev(stdstdvelocity);

		for(int i = 0; i < relMoves.size(); i++) {
			for(int j = 0; j < maxj; j++) {
				
				relgrid[i][j+3] /= sdVelocity;
				relgrid[i][j+maxj+3] /= sdAngle;
				relgrid[i][2*maxj+3] /= s3v;
				relgrid[i][2*maxj+4] /= s3a;
//				relgrid[i][2*maxj+3] = 0;
//				relgrid[i][2*maxj+4] = 0;				
			}
		}
		
		System.out.println("New Features: " + relMoves.size());
		System.out.println("Turns: " + turns);
		System.out.println("Stopends: " + stopend);
		System.out.println("Maximum streak: " + maxj);
		
		rawData = relgrid;
		
		if(destBase != null) {
			FileIO.SaveCSV(relgrid, new File(destBase + "_rel.csv"));
			
			int streakct[] = new int[streaks.size()];
			for(int i = 0; i < streaks.size(); i++) streakct[i] = streaks.get(i);
			
			FileIO.SaveCSV(streakct, new File(destBase + "_streaks.csv"));
		}
		
	
	}

	public void polarWithPos(String destBase) {
		
		featInfo(true);
		int numFrames = renderInfo.size();
		
		System.out.println("Features: " + featMap.keySet().size() + ", Frames: " + numFrames);
		
		ArrayList<double[]>	relMoves = new ArrayList<double[]>();
		ArrayList<Integer> streaks = new ArrayList<Integer>();
		ArrayList<Long> ids = new ArrayList<Long>();
		ArrayList<Integer> startframe = new ArrayList<Integer>();
		ArrayList<Integer> endframe = new ArrayList<Integer>();
		
		int maxj = 0;
		int turns = 0, stopend = 0;
		double maxvel = 0;
		int maxx = 0;
		int maxy = 0;
		
		for(Long l : featMap.keySet()) {
			FeaturePosition[] posInfo = featMap.get(l);	
			for(int i = 0; i < posInfo.length; i++) {
				if(posInfo[i] != null && posInfo[i].id == 1) {
					FeaturePosition c = posInfo[i];
					FeaturePosition beg = posInfo[i];
					int start = i;
					i++;
					
					int j = 0;
					double[] movement = new double[numFrames*4];
					while(posInfo[i] != null && posInfo[i].id == 1) {
						movement[j] = Math.sqrt(Math.pow(posInfo[i].x - c.x, 2) + Math.pow(posInfo[i].y - c.y, 2));
						if(movement[j] > maxvel) maxvel = movement[j];
						movement[numFrames+j] = Math.toDegrees(Math.atan2(posInfo[i].y - c.y, posInfo[i].x - c.x))%360/360;
						
						movement[2*numFrames+j] = posInfo[i].x - beg.x;
						if(movement[2*numFrames+j] > maxx) maxx = (int) movement[2*numFrames+j];
						movement[3*numFrames+j] = posInfo[i].y - beg.y;
						if(movement[3*numFrames+j] > maxy) maxy = (int) movement[3*numFrames+j];
						
						c = posInfo[i];
						i++;
						j++;
						
					}
					
					if(j > streaksize) {
						relMoves.add(movement);
						streaks.add(j);
						ids.add(l);
						startframe.add(start);
						endframe.add(i-1);
					}
					if(j > maxj) maxj = j;
				}
			}
			
		}
	
		maxj = streaksize;
		
		double relgrid[][] = new double[relMoves.size()][(maxj*4)+3];
		for(int i = 0; i < relMoves.size(); i++) {
			double frame[] = relMoves.get(i);
			relgrid[i][0] = (double) ids.get(i);
			relgrid[i][1] = startframe.get(i);
			relgrid[i][2] = endframe.get(i);
			for(int j = 0; j < maxj; j++) {
				//without smoothing
	//			relgrid[i][j+3] = frame[j]; 
				relgrid[i][j+maxj+3] = frame[j+numFrames];
				relgrid[i][j+2*maxj+3] = frame[j+2*numFrames]/maxx;
				relgrid[i][j+3*maxj+3] = frame[j+3*numFrames]/maxy;
	//			
	//			with smoothing
				if(j == 0) {
					relgrid[i][j+3] = (0.67*frame[j] + 0.33 * frame[j+1])/maxvel; 
	//				relgrid[i][j+maxj+3] = 0.67*frame[j+numFrames] + 0.33 * frame[j+numFrames+1];
				}
				else if(j == maxj-1) {
					relgrid[i][j+3] = (0.67*frame[j] + 0.67 * frame[j-1])/maxvel;
	//				relgrid[i][j+maxj+3] = 0.67*frame[j+numFrames] + 0.33 * frame[j+numFrames-1];
				}
				else if(j == 1) {
					relgrid[i][j+3] = (0.22 * frame[j-1] + 0.45*frame[j] + 0.22 * frame[j+1] + 0.11 * frame[j+2])/maxvel;
	//				relgrid[i][j+maxj+3] = 0.22 * frame[j+numFrames-1] + 0.45*frame[j+numFrames] + 0.22 * frame[j+numFrames+1] + 0.11 * frame[j+numFrames+2];					
				}
				else if(j == maxj-2) {
					relgrid[i][j+3] = (0.22 * frame[j+1] + 0.45*frame[j] + 0.22 * frame[j-1] + 0.11 * frame[j-2])/maxvel;
	//				relgrid[i][j+maxj+3] = 0.22 * frame[j+numFrames+1] + 0.45*frame[j+numFrames] + 0.22 * frame[j+numFrames-1] + 0.11 * frame[j+numFrames-2];					
				}
				else {
					relgrid[i][j+3] = (0.1 * frame[j+2] + 0.2 * frame[j+1] + 0.4*frame[j] + 0.2 * frame[j-1] + 0.1 * frame[j-2])/maxvel;
	//				relgrid[i][j+maxj+3] =  0.1 * frame[j+numFrames+2] + 0.2 * frame[j+numFrames+1] + 0.4*frame[j+numFrames] + 0.2 * frame[j+numFrames-1] + 0.1 * frame[j+numFrames-2];					
				}
			}
		}
	
		System.out.println("New Features: " + relMoves.size());
		System.out.println("Turns: " + turns);
		System.out.println("Stopends: " + stopend);
		System.out.println("Maximum streak: " + maxj);
		
		rawData = relgrid;
		
		if(destBase != null) {
			FileIO.SaveCSV(relgrid, new File(destBase + "_rel.csv"));
			
			int streakct[] = new int[streaks.size()];
			for(int i = 0; i < streaks.size(); i++) streakct[i] = streaks.get(i);
			
			FileIO.SaveCSV(streakct, new File(destBase + "_streaks.csv"));
		}
		
	
	}

	public void kMeansClusters(String destLoc) {
		
		vectors = new double[rawData.length][rawData[0].length-3];
		for(int i = 0; i < rawData.length; i++) {
			for(int j = 3; j < rawData[0].length; j++) {
				vectors[i][j-3] = rawData[i][j];
			}
		}
		VocabKMeans clustering = new VocabKMeans(clustersize, 50, Distance.Kernel.EUCLIDIAN, vectors[0].length);
		clustering.Generate(vectors);
		
		clusters = new double[rawData.length][4];
		for(int i = 0; i < clusters.length; i++) {
			clusters[i][0] = rawData[i][0];
			clusters[i][1] = rawData[i][1];
			clusters[i][2] = rawData[i][2]; 
			clusters[i][3] = clustering.Lookup(vectors[i]);
		}
		
		if(destLoc != null) FileIO.SaveCSV(clusters, new File(destLoc + "_clusters.csv"));

	}
	
	public void featMapClusters() {
		
		for(Long l : featMap.keySet()) {
			FeaturePosition[] pos = featMap.get(l);
			for(int i = 0; i < pos.length; i++) {
				if(pos[i] != null) pos[i] = new FeaturePosition(-1, pos[i].x, pos[i].y);
			}
		}
		
		for(int i = 0; i < clusters.length; i++) {
			FeaturePosition[] pos = featMap.get((long) clusters[i][0]);
			for(int j = (int) clusters[i][1]; j <= (int) clusters[i][2] ; j++) {
				pos[j] = new FeaturePosition((long) clusters[i][3], pos[j].x, pos[j].y);
			}
		}
	}
	
	public void featMapClusters2() {
		
		for(Long l : featMap.keySet()) {
			FeaturePosition[] pos = featMap.get(l);
			for(int i = 0; i < pos.length; i++) {
				if(pos[i] != null) pos[i] = new FeaturePosition(-1, pos[i].x, pos[i].y);
			}
		}
		
		for(int i = 0; i < clusters.length; i++) {
			FeaturePosition[] pos = featMap.get((long) clusters[i][0]);
			int j = (int) clusters[i][1] + 4;
			pos[j] = new FeaturePosition((long) clusters[i][3], pos[j].x, pos[j].y);
		}
	}

	public void plot() {

        // create your PlotPanel (you can use it as a JPanel)
        Plot2DPanel plot = new Plot2DPanel();
        // define the legend position
//        plot.addLegend("---");

        // add a line plot to the PlotPanel
        for(int i = 0; i < rawData.length; i++)
        plot.addLinePlot("my plot", vectors[i]);

        // put the PlotPanel in a JFrame like a JPanel
        JFrame frame = new JFrame("a plot panel");
        frame.setSize(600, 600);
        frame.setContentPane(plot);
        frame.setVisible(true);
	}
	
	public void kMeansPlot(String saveName) {
        
        Plot2DPanel[] plots = new Plot2DPanel[clustersize];
        int[] counts = new int[clustersize];
        for(int i = 0; i < clustersize; i++) plots[i] = new Plot2DPanel();

        for(int i = 0; i < rawData.length; i++) {
        	
        	double[] x21 = {21};
        	double[] x22 = {22};
        	
        	plots[(int) clusters[i][3]].addLinePlot(Double.toString(clusters[i][1]), Arrays.copyOfRange(vectors[i], 0, 20));
        	plots[(int) clusters[i][3]].addScatterPlot(Double.toString(clusters[i][1]), x21, Arrays.copyOfRange(vectors[i], 20, 21));
        	plots[(int) clusters[i][3]].addScatterPlot(Double.toString(clusters[i][1]), x22, Arrays.copyOfRange(vectors[i], 21, 22));
        	counts[(int) clusters[i][3]]++;
//        	plots[(int) clusters[i][3]].setFixedBounds(0, 0, 22);

        	System.out.println("plotted " + i + " of " + rawData.length);
        }

        for(int i = 0; i < clustersize; i++) {
            JFrame frame = new JFrame("K-Means plot (cluster " + i + ", " + ((double)counts[i] * 100/rawData.length) + "%)");
            int height = 600, width = 600;
            frame.setSize(height, width);
        	frame.setContentPane(plots[i]);
        	plots[i].setFixedBounds(0, 0, 22);
            frame.setVisible(true);

//            BufferedImage image = new BufferedImage(height, width, BufferedImage.TYPE_INT_RGB);
//            Graphics2D graphics2D = image.createGraphics();
//            frame.paint(graphics2D);
//            try {
//    			ImageIO.write(image,"png", new File("kMeans_" + saveName + "_k=" + clustersize + ".png"));
//    		} catch (IOException e) {
//    			e.printStackTrace();
//    		}
            
        }

	}
	
	public void kMeansHistogram() {
        Plot2DPanel plot = new Plot2DPanel();
        double[] counts = new double[clusters.length];
        
        for(int i = 0; i < clusters.length; i++) {
        	counts[i] = clusters[i][3];
        	System.out.print(clusters[i][3] + " ");
        }
        
        plot.addHistogramPlot("Appearances of k-values", counts, clustersize);
        JFrame frame = new JFrame("Histogram");
        frame.setSize(600, 600);
        frame.setContentPane(plot);
        frame.setVisible(true);	
	}
	
	@SuppressWarnings("unchecked")
	public SimpleImageSequence processVideo(String vidPath) {

		Class imageType = GrayF32.class;
		MediaManager media = DefaultMediaManager.INSTANCE;
		SimpleImageSequence sequence = media.openVideo(UtilIO.pathExample(vidPath), ImageType.single(imageType)); 
		sequence.setLoop(false);
		
		return sequence;
	}

	public void replay(String vidLoc, String outLoc) {
		
		SimpleImageSequence<T> sequence = processVideo(vidLoc);
		// Figure out how large the GUI window should be
		T frame = sequence.next();
		
		gui.setPreferredSize(new Dimension(frame.getWidth(),frame.getHeight()));
		VideoFrameWriter rec = new VideoFrameWriter(new File(outLoc), frame.getWidth(), frame.getHeight(), 10);
		rec.OpenFile();

		ShowImages.showWindow(gui,"Replay after Recluster...", true);
		int frames = 0;
		while(sequence.hasNext()) {
			frame = sequence.next();
			ClusterFrame frameInfo = renderInfo.get(frames);

			BufferedImage orig = sequence.getGuiImage();
			Graphics2D g2 = orig.createGraphics();
			
			for(ClusterFeature f: frameInfo.features) {
				
				FeaturePosition[] pos = featMap.get(f.id);
				if(pos == null || pos[frames] == null || pos[frames].id < 0) continue;
				double clusterno = (double) pos[frames].id;
//				System.out.println(clusterno);
				int rgb[] = ColorUtils.ColorMapRGB(ColorMap.JET, clusterno/clustersize);
				Color featColor = new Color(rgb[0], rgb[1], rgb[2]);	
				VisualizeFeatures.drawPoint(g2, (int) f.x, (int) f.y, featColor);
				g2.setColor(Color.WHITE);
				g2.drawString(Integer.toString((int) clusterno), (int) f.x-5, (int) f.y+5);
			}
			
			g2.drawString("Frame: " + frames, frame.getWidth()- 600, frame.getHeight()-50);
			
			gui.setBufferedImage(orig);	
			gui.repaint();
			rec.ProcessFrame(orig);
			frames++;
		}
		
		rec.Close();
	}
	
	/**
	 * Processes every video in a folder
	 */	
	@SuppressWarnings("unchecked")
	public void batch(String folder) {
		
		File dir = new File(folder);
		File[] listOfFiles = dir.listFiles();
		
		ArrayList<String> fq = new ArrayList<String>();
		for (File file : listOfFiles) {
		    if (file.isFile() && (file.getName().toLowerCase().endsWith("_rend.zip"))) {
		    	fq.add(file.getAbsolutePath());
		    }
		}
		
		ArrayList<ClusterFrame> master = new ArrayList<ClusterFrame>();
		for(int i = 0; i < fq.size(); i++) {
			String currentFilePath = fq.get(i);
//			System.out.println("PROCESSING VIDEO " + (i+1) + " of " + fq.size() + ", " + currentFilePath);
			ArrayList<ClusterFrame> temp = (ArrayList<ClusterFrame>) FileIO.LoadObject(new File(currentFilePath));
			master.addAll((ArrayList<ClusterFrame>) FileIO.LoadObject(new File(currentFilePath)));
		}		
		
		//concatenate clusterframes
		//run together
		//open videos, run them in order, save to a video writer
		
//		for(int i = 0; i < fq.size(); i++) {
//			String currentFilePath = fq.get(i);
//	    	System.out.println("PROCESSING VIDEO " + (i+1) + " of " + fq.size() + ", " + currentFilePath);
//			process(currentFilePath);
//		}
		
//		genFeatCluster(frames/20, "cluster_new");
		
//		setFrames(0);
//		openVid();
//				
//		for(int i = 0; i < fq.size(); i++) {
//			updateRender(fq.get(i));
//		}
//		
//		closeVid();
		
		return;
	}
	
	@SuppressWarnings({ "unchecked", "unused" })
	public void createWindows(String vidLoc, String destbase, int minLength) {
		//get 
		final Comparator<double[]> arrayComparator = new Comparator<double[]>() {
	        @Override
	        public int compare(double[] o1, double[] o2) {
	            if(o1[1] < o2[1]) return -1;
	            else if(o1[1] > o2[1]) return 1;
	            else return 0;
	        }
	    };
		Arrays.sort(streakdata, arrayComparator);
				
		VideoFrameWriter[] outSmall = new VideoFrameWriter[streakdata.length];
		VideoFrameWriter[] outBig = new VideoFrameWriter[streakdata.length];
		for(int i = 0; i < streakdata.length; i++) {
			if(streakdata[i][2] - streakdata[i][1] >= minLength) {
				outSmall[i] = new VideoFrameWriter(new File(destbase + "_f" + streakdata[i][0] + "_s" + streakdata[i][1] + "_e" + streakdata[i][2] + "_small.mp4"), 32, 32, 5);
				outBig[i] = new VideoFrameWriter(new File(destbase + "_f" + streakdata[i][0] + "_s" + streakdata[i][1] + "_e" + streakdata[i][2] + "_big.mp4"), 204, 204, 5);
			}
			else {
				outSmall[i] = null;
				outBig[i] = null;
			}
		}
		
		SimpleImageSequence<T> sequence = processVideo(vidLoc);

		T frame;
		int frames = 0;
		while(sequence.hasNext()) {
			frame = sequence.next();
			BufferedImage newFrame = sequence.getGuiImage();
			
			for(int i = 0; i < streakdata.length; i++) {
				if(outSmall[i] != null && frames == streakdata[i][1]) {
					outSmall[i].OpenFile();
					outBig[i].OpenFile();
				}
				if(outSmall[i] != null && frames >= streakdata[i][1] && frames <= streakdata[i][2]) {
					FeaturePosition[] pos = featMap.get((long) streakdata[i][0]);
//					System.out.println(pos[frames].x + " " + pos[frames].y);
					BufferedImage small = new BufferedImage(19, 19, newFrame.getType());
					Graphics2D g = small.createGraphics();
					int upleftx = (int) pos[frames].x - 9;
					int uplefty = (int) pos[frames].y - 9;
					
					int subx = upleftx;
					int suby = uplefty;
					int w = 19;
					int h = 19;
					int startx = 0;
					int starty = 0;
					
					if(upleftx < 0) {
						subx = 0;
						w = upleftx+19;
						startx = -upleftx;
					}
					if(uplefty < 0) {
						suby = 0;
						h = uplefty+19;
						starty = -uplefty;
					}
					if(upleftx+19 > newFrame.getWidth()) {
						w = upleftx+19 - newFrame.getWidth();
					}
					if(uplefty+19 > newFrame.getHeight()) {
						h = uplefty+19 - newFrame.getHeight();
					}
					
					BufferedImage window = newFrame.getSubimage(subx, suby, w, h);
					g.drawImage(window, startx, starty, null);
					
					outSmall[i].ProcessFrame(small);
					
					BufferedImage big = new BufferedImage(99, 99, newFrame.getType());
					Graphics2D g2 = big.createGraphics();
					
					upleftx = (int) pos[frames].x - 49;
					uplefty = (int) pos[frames].y - 49;
					
					subx = upleftx;
					suby = uplefty;
					w = 99;
					h = 99;
					startx = 0;
					starty = 0;
					
					if(upleftx < 0) {
						subx = 0;
						w = upleftx+99;
						startx = -upleftx;
					}
					if(uplefty < 0) {
						suby = 0;
						h = uplefty+99;
						starty = -uplefty;
					}
					if(upleftx+99 > newFrame.getWidth()) {
						w = upleftx+99 - newFrame.getWidth();
					}
					if(uplefty+99 > newFrame.getHeight()) {
						h = uplefty+99 - newFrame.getHeight();
					}
					
					BufferedImage window2 = newFrame.getSubimage(subx, suby, w, h);
					g2.drawImage(window2, startx, starty, null);
					g2.setColor(Color.RED);
					g2.drawRect(40, 40, 19, 19);
					
					outBig[i].ProcessFrame(big);
				}
				if(outSmall[i] != null && frames == streakdata[i][2]) {
					outSmall[i].Close();
					outBig[i].Close();
				}
			}
			System.out.println("just processed frame " + frames);
			frames++;
		}
		
	}
	
	@SuppressWarnings("unchecked")
	public static void main(String[] args) {
//		ArrayList<ClusterFrame> render = (ArrayList<ClusterFrame>) FileIO.LoadObject(new File("/home/grangerlab/Desktop/inter/intersection_rend.zip"));
//		MotionCluster mc = new MotionCluster(GrayF32.class, render, 12, 10);
////		mc.relativeMotion(null);
//		mc.polar2(null);
//		mc.kMeansClusters(null);
//		mc.featMapClusters2();
//		mc.kMeansPlot("intersect");
////		mc.kMeansHistogram();
////		mc.replay("/home/grangerlab/Desktop/inter/intersection.m4v", "intersection_javaPolarWindow.mp4");
//		
		
		ArrayList<ClusterFrame> render = (ArrayList<ClusterFrame>) FileIO.LoadObject(new File("/home/grangerlab/Desktop/inter/intersection_rend.zip"));
		MotionCluster mc = new MotionCluster(GrayF32.class, render, 12, 10);
		mc.featureWindows();
		mc.createWindows("/home/grangerlab/Desktop/inter/intersection.m4v", "pinhole/inter", 25);
		
//		ArrayList<ClusterFrame> pacmanrender = (ArrayList<ClusterFrame>) FileIO.LoadObject(new File("/home/grangerlab/Desktop/pacman2/Pacman_blown_rend.zip"));
//		MotionCluster pac = new MotionCluster(GrayF32.class, pacmanrender, 10, 10);
//		pac.relativeVelocityWithTurns(null);
//		pac.kMeansClusters(null);
//		pac.featMapClusters();
//		pac.kMeansPlot("pacman");
//		pac.replay("/home/grangerlab/Desktop/pacman2/Pacman_blown.mp4", "pacman_javaBIG.mp4");
	}
}

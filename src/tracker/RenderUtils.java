package tracker;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import boofcv.alg.filter.derivative.GImageDerivativeOps;
import boofcv.gui.feature.VisualizeFeatures;
import boofcv.gui.image.ImagePanel;
import boofcv.gui.image.ShowImages;
import boofcv.io.MediaManager;
import boofcv.io.UtilIO;
import boofcv.io.image.SimpleImageSequence;
import boofcv.io.wrapper.DefaultMediaManager;
import common.FileIO;
import common.math.Vec;
import common.utils.ColorUtils;
import common.utils.ColorUtils.ColorMap;
import common.video.VideoFrameWriter;
import boofcv.struct.image.GrayF32;
import boofcv.struct.image.ImageGray;
import boofcv.struct.image.ImageType;

@SuppressWarnings("rawtypes")
public class RenderUtils<T extends ImageGray> {
	
	Class<T> imageType;
	ImagePanel gui = new ImagePanel();
	
	public RenderUtils(Class<T> imageType) {
		this.imageType = imageType;
	}
	
	/**
	 * Transfers frame-by-frame information about features into feature by feature information
	 * Key: Feature ID
	 * Value: Array of FeaturePositions, where position i carries information at frame i; 
	 */
	public static Map<Long, FeaturePosition[]> featInfo(Map<Long, Integer> featapps, ArrayList<ClusterFrame> renderInfo) {
		
		Map<Long, FeaturePosition[]> featMap = new HashMap<Long, FeaturePosition[]>();
		
		int numFrames = renderInfo.size();
		for(int i = 0; i < numFrames; i++) {
			for(ClusterFeature c: renderInfo.get(i).features) {
				if(!featMap.containsKey(c.id)) {
					
					//create key-value pair
					FeaturePosition[] apps = new FeaturePosition[numFrames];
					apps[i] = new FeaturePosition(c.id, c.x, c.y);
					
					//only register if appears given number of times, currently disabled at 0
					if(featapps.get(c.id) > 0) featMap.put(c.id, apps);
				}
				else {
					
					//update FeaturePosition Array
					FeaturePosition[] apps = featMap.get(c.id);
					apps[i] = new FeaturePosition(c.id, c.x, c.y);
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
		
		return featMap2;
	
	}
	
	/**
	 * Similar to featInfo() except carries information about type of movement
	 * Utilizes clusterno attribute of FeaturePosition
	 * 1- normal movement, 2- turn, 3- stop end, 4- rest
	 * Key: Feature ID
	 * Value: Array of FeaturePositions, where position i carries information at frame i; 
	 */
	
	public static Map<Long, FeaturePosition[]> featInfoMotion(ArrayList<ClusterFrame> renderInfo) {
		
		Map<Long, FeaturePosition[]> featMap = new HashMap<Long, FeaturePosition[]>();
		
		int numFrames = renderInfo.size();
		for(int i = 0; i < numFrames; i++) {
			for(ClusterFeature c: renderInfo.get(i).features) {
				if(!featMap.containsKey(c.id)) {
					FeaturePosition[] apps = new FeaturePosition[numFrames];
					apps[i] = new FeaturePosition(c.clusterno, c.x, c.y);
					featMap.put(c.id, apps);
				}
				else {
					FeaturePosition[] apps = featMap.get(c.id);
					apps[i] = new FeaturePosition(c.clusterno, c.x, c.y);
				}
			}
		}

		return featMap;
	
	}
	
	/**
	 * Generates "split-up" features based on features seen in frames
	 * Split-up is done by turns or stop ends for streaks whose length > threshold
	 * Output is CSV file (named using destBase)
	 */
	public static void relativeMotion(ArrayList<ClusterFrame> renderInfo, String destBase, int threshold) {
		
		Map<Long, FeaturePosition[]> featMap = featInfoMotion(renderInfo);
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
					
					if(j > threshold) {
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
		maxj = 10;
		
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
		FileIO.SaveCSV(relgrid, new File(destBase + "_rel.csv"));
		
		//update streak information
		
		int streakct[] = new int[streaks.size()];
		for(int i = 0; i < streaks.size(); i++) {
			streakct[i] = streaks.get(i);
		}
		
		FileIO.SaveCSV(streakct, new File(destBase + "_streaks.csv"));
	}
	
	
	public static void relativeMotionWithTurns(ArrayList<ClusterFrame> renderInfo, String destBase, int threshold) {
		
		Map<Long, FeaturePosition[]> featMap = featInfoMotion(renderInfo);
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
					
					if(j > threshold) {
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

		maxj = 10;
		
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
		
//		double relgrid[][] = new double[relMoves.size()][numFrames*2];
//		for(int i = 0; i < relMoves.size(); i++) {
//			relgrid[i] = relMoves.get(i);
//		}
		System.out.println("New Features: " + relMoves.size());
		System.out.println("Turns: " + turns);
		System.out.println("Stopends: " + stopend);
		System.out.println("Maximum streak: " + maxj);
		FileIO.SaveCSV(relgrid, new File(destBase + "_rel.csv"));
		
		int streakct[] = new int[streaks.size()];
		for(int i = 0; i < streaks.size(); i++) streakct[i] = streaks.get(i);
		
		FileIO.SaveCSV(streakct, new File(destBase + "_streaks.csv"));
	}

	public static void relativeVelocityWithTurns(ArrayList<ClusterFrame> renderInfo, String destBase, int threshold) {
		
		Map<Long, FeaturePosition[]> featMap = featInfoMotion(renderInfo);
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
					
					if(j > threshold) {
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

		maxj = 10;
		
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
		
//		double relgrid[][] = new double[relMoves.size()][numFrames*2];
//		for(int i = 0; i < relMoves.size(); i++) {
//			relgrid[i] = relMoves.get(i);
//		}
		System.out.println("New Features: " + relMoves.size());
		System.out.println("Turns: " + turns);
		System.out.println("Stopends: " + stopend);
		System.out.println("Maximum streak: " + maxj);
		FileIO.SaveCSV(relgrid, new File(destBase + "_rel.csv"));
		
		int streakct[] = new int[streaks.size()];
		for(int i = 0; i < streaks.size(); i++) streakct[i] = streaks.get(i);
		
		FileIO.SaveCSV(streakct, new File(destBase + "_streaks.csv"));
	}
	
	
	public static void relativeXPos(Map<Long, Integer> featapps, ArrayList<ClusterFrame> renderInfo, String destBase) {
		Map<Long, FeaturePosition[]> featMap = featInfo(featapps, renderInfo);
		Map<Long, Double> firstXPos = new HashMap<Long, Double>();
		Map<Long, Double> firstYPos = new HashMap<Long, Double>();

		double relgrid[][] = new double[2*renderInfo.size()+1][featMap.keySet().size()];
		double absgrid[][] = new double[2*renderInfo.size()+1][featMap.keySet().size()];
		double appgrid[][] = new double[2*renderInfo.size()+1][featMap.keySet().size()];
		
		System.out.println("Feature count: " + featMap.keySet().size());
		System.out.println("frame count: " + renderInfo.size());
		
		int j = 0;
		for(Long l : featMap.keySet()) {
			FeaturePosition[] posInfo = featMap.get(l);
			relgrid[0][j] = (double) l;
			absgrid[0][j] = (double) l;
			appgrid[0][j] = (double) l;

			for(int i = 0; i < posInfo.length; i++) {
				FeaturePosition currPos = posInfo[i];
				if(currPos == null) continue;
				
				if(!firstXPos.containsKey(currPos.id)) {
					firstXPos.put(currPos.id, currPos.x);
				}
				relgrid[i+1][j] = currPos.x - firstXPos.get(currPos.id);
				absgrid[i+1][j] = currPos.x;
				appgrid[i+1][j] = 1;

				if(!firstYPos.containsKey(currPos.id)) {
					firstYPos.put(currPos.id, currPos.y);
				}
				relgrid[renderInfo.size()+i+1][j] = currPos.y - firstYPos.get(currPos.id);
				absgrid[renderInfo.size()+i+1][j] = currPos.y;
				appgrid[renderInfo.size()+i+1][j] = 1;
			
			}
			j++;
		}
		
		FileIO.SaveCSV(relgrid, new File(destBase + "_rel.csv"));
		FileIO.SaveCSV(absgrid, new File(destBase + "_abs.csv"));
		FileIO.SaveCSV(appgrid, new File(destBase + "_app.csv"));
		
	}

	public static void diff(ClusterFrame first, ClusterFrame second) {
		for(ClusterFeature c: first.features) {
			int match = 0;
			for(ClusterFeature d: second.features) {
				if(c.id == d.id) {
					match = 1;
					System.out.println("feature " + c.id + " moved from (" + c.x + "," + c.y + ") to (" + d.x + "," + d.y + ")");
					break;
				}
			}
			if(match == 0) {
				System.out.println("feature " + c.id + " at (" + c.x + "," + c.y + ") went away.");
			}	
		}
		for(ClusterFeature d: first.features) {
			int match = 0;
			for(ClusterFeature c: second.features) {
				if(c.id == d.id) {
					match = 1;
					break;
				}
			}
			if(match == 0) {
				System.out.println("feature " + d.id + " at (" + d.x + "," + d.y + ") appeared.");
			}	
		}		
	}
	
	public static void totalDiff(ArrayList<ClusterFrame> renderInfo) {
		for(int i = 0; i < renderInfo.size()-1; i++) {
			diff(renderInfo.get(i), renderInfo.get(i+1));
		}
	}
	
	public static Map<Double, Double> getClusters(String csvLoc) {
		
		double rawCluster[][] = FileIO.LoadCSV(new File(csvLoc));
		Map<Double, Double> clusters = new HashMap<Double, Double>();
		for(int i = 0; i < rawCluster.length; i++) {
			clusters.put(rawCluster[i][0], rawCluster[i][1]);
		}
		
		return clusters;
	}
	
	@SuppressWarnings("unchecked")
	public SimpleImageSequence processVideo(String vidPath) {

		Class imageType = GrayF32.class;
		MediaManager media = DefaultMediaManager.INSTANCE;
		SimpleImageSequence sequence = media.openVideo(UtilIO.pathExample(vidPath), ImageType.single(imageType)); 
		sequence.setLoop(false);
		
		return sequence;
	}
	
	@SuppressWarnings("unchecked")
	public void replay(ArrayList<ClusterFrame> renderInfo, String csvLoc, String vidLoc, String outLoc) {
		
		Map<Double, Double> clusters = getClusters(csvLoc);
		SimpleImageSequence<T> sequence = processVideo(vidLoc);
		// Figure out how large the GUI window should be
		T frame = sequence.next();
		
		gui.setPreferredSize(new Dimension(frame.getWidth(),frame.getHeight()));
		VideoFrameWriter rec = new VideoFrameWriter(new File(outLoc), frame.getWidth(), frame.getHeight(), 4);
		rec.OpenFile();

		ShowImages.showWindow(gui,"Replay after Recluster...", true);
		int frames = 0;
		while(sequence.hasNext()) {
			frame = sequence.next();
			ClusterFrame frameInfo = renderInfo.get(frames);

			BufferedImage orig = sequence.getGuiImage();
			Graphics2D g2 = orig.createGraphics();
			
			for(ClusterFeature f: frameInfo.features) {
				Double clusterno = clusters.get((double) f.id);
				int red = (int)((255/30)*(clusterno))%255;
				int green = (int)(((255/30)*(100-clusterno)))%255;
				int blue = (int)((128+(255/30)*clusterno)%255);
				Color featColor = new Color(red, green, blue);
				VisualizeFeatures.drawPoint(g2, (int) f.x, (int) f.y, featColor);
				g2.setColor(Color.WHITE);
				g2.drawString((clusterno).toString(), (int) f.x-5, (int) f.y+5);
			}
			
			gui.setBufferedImage(orig);	
			gui.repaint();
			rec.ProcessFrame(orig);
			frames++;
		}
		
		rec.Close();
	}
	
	@SuppressWarnings("unchecked")
	public void diffPlay(String absLoc, String vidLoc, String outLoc, int showbg) {
		
		double rawCluster[][] = FileIO.LoadCSV(new File(absLoc));
		int half = (rawCluster.length-1)/2;
		SimpleImageSequence<T> sequence = processVideo(vidLoc);
		// Figure out how large the GUI window should be
		T frame = sequence.next();
		
		gui.setPreferredSize(new Dimension(frame.getWidth(),frame.getHeight()));
		VideoFrameWriter rec = new VideoFrameWriter(new File(outLoc), frame.getWidth(), frame.getHeight(), 4);
		rec.OpenFile();

		ShowImages.showWindow(gui,"Heatmap", true);
		frame = sequence.next();
		int frames = 1;
		while(sequence.hasNext() && frames+half+1 < rawCluster.length) {
			frame = sequence.next();
			
			BufferedImage orig;
			
			if(showbg == 0) {
				BufferedImage orig1 = sequence.getGuiImage();
				orig = new BufferedImage(orig1.getWidth(), orig1.getHeight(), BufferedImage.TYPE_3BYTE_BGR);				
			}
			else {
				orig = sequence.getGuiImage();
			}
			
			Graphics2D g2 = orig.createGraphics();
			
			for(int i = 0; i < rawCluster[0].length; i++) {
				
				double prevX = rawCluster[frames][i];
				double currX = rawCluster[frames+1][i];
				double prevY = rawCluster[frames+half][i];
				double currY = rawCluster[frames+half+1][i];
				
				if(prevX == 0 || prevY == 0 || currX == 0 || currY == 0) continue;
				
				double euclidean = Math.sqrt(Math.pow(prevX-currX, 2) + Math.pow(prevY-currY, 2));
				
//				if(euclidean < 1) continue;
				
				double sigmoid = 2*((1 / (1 + Math.exp(-euclidean/10))) - 0.5);
				
				int red = (int) (255 * sigmoid);
				int blue = (int) (255 * (1 - sigmoid));
				int green = 0;
				
				Color featColor = new Color(red, green, blue);
				VisualizeFeatures.drawPoint(g2, (int) currX, (int) currY, featColor);
				g2.setColor(Color.WHITE);
				if(euclidean > 1) g2.drawString(new DecimalFormat("##.##").format(euclidean), (int) currX-5, (int) currY+5);
			}
			g2.setColor(Color.WHITE);
			g2.setFont(new Font("TimesRoman", Font.BOLD, 20));			
			g2.drawString("FRAME: " + frames, orig.getWidth()- 200, orig.getHeight()-50);
			
			gui.setBufferedImage(orig);	
			gui.repaint();
			rec.ProcessFrame(orig);
			frames++;
		}
				
		rec.Close();		
	}

	@SuppressWarnings("unchecked")
	public void diffPlayCluster(String absLoc, String vidLoc, String outLoc, int showbg, String csvLoc) {
		
		Map<Double, Double> clusters = getClusters(csvLoc);
		double rawCluster[][] = FileIO.LoadCSV(new File(absLoc));
		int half = (rawCluster.length-1)/2;
		SimpleImageSequence<T> sequence = processVideo(vidLoc);
		// Figure out how large the GUI window should be
		T frame = sequence.next();
		
		gui.setPreferredSize(new Dimension(frame.getWidth(),frame.getHeight()));
		VideoFrameWriter rec = new VideoFrameWriter(new File(outLoc), frame.getWidth(), frame.getHeight(), 4);
		rec.OpenFile();

		ShowImages.showWindow(gui,"Heatmap", true);
		frame = sequence.next();
		int frames = 1;
		while(sequence.hasNext() && frames+half+1 < rawCluster.length) {
			frame = sequence.next();
			
			BufferedImage orig;
			
			if(showbg == 0) {
				BufferedImage orig1 = sequence.getGuiImage();
				orig = new BufferedImage(orig1.getWidth(), orig1.getHeight(), BufferedImage.TYPE_3BYTE_BGR);				
			}
			else {
				orig = sequence.getGuiImage();
			}
			
			Graphics2D g2 = orig.createGraphics();
			
			for(int i = 0; i < rawCluster[0].length; i++) {
				
				double prevX = rawCluster[frames][i];
				double currX = rawCluster[frames+1][i];
				double prevY = rawCluster[frames+half][i];
				double currY = rawCluster[frames+half+1][i];
				
				if(prevX == 0 || prevY == 0 || currX == 0 || currY == 0) continue;
				
				double euclidean = Math.sqrt(Math.pow(prevX-currX, 2) + Math.pow(prevY-currY, 2));
				
				if(euclidean < 1) continue;
				
				double sigmoid = 2*((1 / (1 + Math.exp(-euclidean/10))) - 0.5);
				
				int red = (int) (255 * sigmoid);
				int blue = (int) (255 * (1 - sigmoid));
				int green = 0;
				
				Color featColor = new Color(red, green, blue);
				VisualizeFeatures.drawPoint(g2, (int) currX, (int) currY, featColor);
				g2.setColor(Color.WHITE);
				if(euclidean > 1) g2.drawString(clusters.get(rawCluster[0][i]).toString(), (int) currX-5, (int) currY+5);
			}
			
			gui.setBufferedImage(orig);	
			gui.repaint();
			rec.ProcessFrame(orig);
			frames++;
		}
				
		rec.Close();		
	}

	public void archivePlaySimple(ClipArchive renderInfo, ArrayList<ClusterFrame> clusters, String vidLoc, String outLoc) {
		
		Map<Long, FeaturePosition[]> featInfo = featInfo(renderInfo.featapps, clusters);
		
//		Map<Double, Double> clusters = getClusters(csvLoc);
		SimpleImageSequence<T> sequence = processVideo(vidLoc);
		// Figure out how large the GUI window should be
		T frame = sequence.next();
		
		gui.setPreferredSize(new Dimension(frame.getWidth(),frame.getHeight()));
		VideoFrameWriter rec = new VideoFrameWriter(new File(outLoc), frame.getWidth(), frame.getHeight(), 4);
		rec.OpenFile();

		ShowImages.showWindow(gui,"Replay after Recluster...", true);
		int frames = 0;
		while(sequence.hasNext()) {
			frame = sequence.next();
//			BufferedImage orig = new BufferedImage(sequence.getNextWidth(), sequence.getNextHeight(), BufferedImage.TYPE_3BYTE_BGR);
			BufferedImage orig = sequence.getGuiImage();
			Graphics2D g2 = orig.createGraphics();

			SimpleFrame frameInfo = renderInfo.localFrames.get(frames);
			
			int black = 0;
			for(FeaturePosition f: frameInfo.features) {
				
				Color featColor;
				Double clusterno = new Double(0);
				
				if(featInfo.containsKey(f.id)) {
					clusterno = (double) f.id;
					int rgb[] = ColorUtils.ColorMapRGB(ColorMap.JET, (((double) clusterno)%49)/49);
					featColor = new Color(rgb[0], rgb[1], rgb[2]);					
				}
				else {
					continue;
				}

				VisualizeFeatures.drawPoint(g2, (int) f.x, (int) f.y, featColor);
				g2.setColor(Color.WHITE);
				g2.drawString(Integer.toString(clusterno.intValue()), (int) f.x-5, (int) f.y+5);
			}

			//draw frame number for convenient reference
			g2.setColor(Color.WHITE);
			g2.setFont(new Font("TimesRoman", Font.BOLD, 20));
			g2.drawString("Active Features: " + (frameInfo.features.size()-black) + " (" + frameInfo.features.size() + " total)", orig.getWidth()- 600, orig.getHeight()-50);
			
			gui.setBufferedImage(orig);	
			gui.repaint();
			rec.ProcessFrame(orig);
			frames++;
		}
		
		rec.Close();
	}
	
	
	@SuppressWarnings("unchecked")
	public void archivePlay(ClipArchive renderInfo, String csvLoc, String vidLoc, String outLoc, int numClusters) {
		
		for(Long l :renderInfo.featapps.keySet()) {
			System.out.println(l + " " + renderInfo.featapps.get(l));
		}
		
		Map<Double, Double> clusters = getClusters(csvLoc);
		SimpleImageSequence<T> sequence = processVideo(vidLoc);
		// Figure out how large the GUI window should be
		T frame = sequence.next();
		
		gui.setPreferredSize(new Dimension(frame.getWidth(),frame.getHeight()));
		VideoFrameWriter rec = new VideoFrameWriter(new File(outLoc), frame.getWidth(), frame.getHeight(), 4);
		rec.OpenFile();

		ShowImages.showWindow(gui,"Replay after Recluster...", true);
		int frames = 0;
		while(sequence.hasNext()) {
			frame = sequence.next();
//			BufferedImage orig = new BufferedImage(sequence.getNextWidth(), sequence.getNextHeight(), BufferedImage.TYPE_3BYTE_BGR);
			BufferedImage orig = sequence.getGuiImage();
			Graphics2D g2 = orig.createGraphics();

			SimpleFrame frameInfo = renderInfo.localFrames.get(frames);
			
			int black = 0;
			for(FeaturePosition f: frameInfo.features) {
				
				Color featColor;
				Double clusterno = new Double(0);
				
				if(clusters.containsKey((double) f.id)) {
					clusterno = clusters.get((double) f.id);
					int rgb[] = ColorUtils.ColorMapRGB(ColorMap.JET, clusterno/numClusters);
					featColor = new Color(rgb[0], rgb[1], rgb[2]);					
				}
				else {
					featColor = Color.BLACK;
					black++;
					continue;
				}

				VisualizeFeatures.drawPoint(g2, (int) f.x, (int) f.y, featColor);
				g2.setColor(Color.WHITE);
				if(clusters.containsKey((double) f.id)) g2.drawString(Integer.toString(clusterno.intValue()), (int) f.x-5, (int) f.y+5);
			}

			//draw frame number for convenient reference
			g2.setColor(Color.WHITE);
			g2.setFont(new Font("TimesRoman", Font.BOLD, 20));
			g2.drawString("Active Features: " + (frameInfo.features.size()-black) + " (" + frameInfo.features.size() + " total)", orig.getWidth()- 600, orig.getHeight()-50);
			
			gui.setBufferedImage(orig);	
			gui.repaint();
			rec.ProcessFrame(orig);
			frames++;
		}
		
		rec.Close();
	}
	
	public void archivePlayMotion(ClipArchive renderInfo, String csvLoc, String vidLoc, String outLoc, int numClusters) {
		
		SimpleImageSequence<T> sequence = processVideo(vidLoc);
		// Figure out how large the GUI window should be
		T frame = sequence.next();
		
		gui.setPreferredSize(new Dimension(frame.getWidth(),frame.getHeight()));
		VideoFrameWriter rec = new VideoFrameWriter(new File(outLoc), frame.getWidth(), frame.getHeight(), 15);
		rec.OpenFile();

		ShowImages.showWindow(gui,"Replay after Recluster...", true);
		int frames = 0;
		while(sequence.hasNext()) {
			frame = sequence.next();
//			BufferedImage orig = new BufferedImage(sequence.getNextWidth(), sequence.getNextHeight(), BufferedImage.TYPE_3BYTE_BGR);
			BufferedImage orig = sequence.getGuiImage();
			Graphics2D g2 = orig.createGraphics();

			SimpleFrame frameInfo = renderInfo.localFrames.get(frames);
			
			int black = 0;
			for(FeaturePosition f: frameInfo.features) {
				
				Color featColor;

				int clusterno = -1;
				double rawCluster[][] = FileIO.LoadCSV(new File(csvLoc));
				for(int i = 0; i < rawCluster.length; i++) {
					if((int) f.id == (int) rawCluster[i][0] && frames >= rawCluster[i][1]-1 && frames <= rawCluster[i][2]) {
						clusterno = (int) rawCluster[i][3];
						break;
					}
				}
				
				if(clusterno > 0) {
					int rgb[] = ColorUtils.ColorMapRGB(ColorMap.JET, clusterno/numClusters);
					featColor = new Color(rgb[0], rgb[1], rgb[2]);					
				}
				else {
					featColor = Color.BLACK;
					black++;
					continue;
				}

				VisualizeFeatures.drawPoint(g2, (int) f.x, (int) f.y, featColor);
				g2.setColor(Color.WHITE);
				if(clusterno > 0) g2.drawString(Integer.toString(clusterno), (int) f.x-5, (int) f.y+5);
			}

			//draw frame number for convenient reference
			g2.setColor(Color.WHITE);
			g2.setFont(new Font("TimesRoman", Font.BOLD, 20));
			g2.drawString("Active Features: " + (frameInfo.features.size()-black) + " (" + frameInfo.features.size() + " total)", orig.getWidth()- 600, orig.getHeight()-50);
			
			gui.setBufferedImage(orig);	
			gui.repaint();
			rec.ProcessFrame(orig);
			frames++;
		}
		
		rec.Close();
	}
	
	
	@SuppressWarnings("unchecked")
	public static void main(String[] args) {
		
//		
//		ArrayList<ClusterFrame> renderInfo = (ArrayList<ClusterFrame>) FileIO.LoadObject(new File("/home/grangerlab/Desktop/pacman2/Pacman_blown_rend.zip"));
//		ClipArchive arc = (ClipArchive) FileIO.LoadObject(new File("/home/grangerlab/Desktop/pacman2/Pacman_blown_arc.zip"));
//		relativeMotion(renderInfo, "pacman_split", 10);
//		relativeVelocityWithTurns(renderInfo, "pacman_vturns", 10);
		RenderUtils util = new RenderUtils(GrayF32.class);
//		util.archivePlayMotion(arc, "/home/grangerlab/Desktop/pacman2/Pacman_turns_cluster.csv", "/home/grangerlab/Desktop/pacman2/Pacman_blown.mp4", "replay_pacman.mp4", 20);
		
//		util.diffPlay("pacman_abs.csv", "/home/grangerlab/Desktop/pacman/Pacman_Java_cropped.mp4", "diff_pacman.mp4",1);
//		
		
//		ArrayList<ClusterFrame> renderInfo = (ArrayList<ClusterFrame>) FileIO.LoadObject(new File("/home/grangerlab/Desktop/pob/news51_rend.zip"));
////		ClipArchive arc = (ClipArchive) FileIO.LoadObject(new File("/home/grangerlab/Desktop/pob/news51_arc.zip"));
//		RenderUtils util = new RenderUtils(GrayF32.class);
//		util.diffPlayCluster("pob_abs.csv", "/home/grangerlab/Desktop/pob/news51.avi", "diff_pob.mp4", 0, "/home/grangerlab/Desktop/pob/news51_cluster.csv");
//		
		
		
//		util.archivePlay(arc, "/home/grangerlab/Desktop/pob/news51_cluster.csv", "/home/grangerlab/Desktop/pob/news51.avi", "replay_pob.mp4");
//		
//		relativeXPos(renderInfo, "pob");
		
//		Position[] a = new Position[10];
//		for(int i = 0; i < 10; i++) {
//			System.out.println(a[i]);
//		}
		
//		ArrayList<ClusterFrame> render = (ArrayList<ClusterFrame>) FileIO.LoadObject(new File("/home/grangerlab/Desktop/highway/small_rend.zip"));
//		ClipArchive arc = (ClipArchive) FileIO.LoadObject(new File("/home/grangerlab/Desktop/highway/small_arc.zip"));
//		relativeMotion(render, "highway", 10);
//		util.archivePlayMotion(arc, "/home/grangerlab/Desktop/highway/highway_cluster.csv", "/home/grangerlab/Desktop/highway/small.avi", "replay_highway.mp4", 2);

		ArrayList<ClusterFrame> render = (ArrayList<ClusterFrame>) FileIO.LoadObject(new File("/home/grangerlab/Desktop/inter/intersection_rend.zip"));
		ClipArchive arc = (ClipArchive) FileIO.LoadObject(new File("/home/grangerlab/Desktop/inter/intersection_arc.zip"));
		relativeVelocityWithTurns(render, "intersect", 10);
//		util.archivePlaySimple(arc, render, "/home/grangerlab/Desktop/inter/intersection.m4v", "intersect_replay.mp4");
		util.archivePlayMotion(arc, "/home/grangerlab/Desktop/inter/intersect_cluster.csv", "/home/grangerlab/Desktop/inter/intersection.m4v", "intersect_replay_motion.mp4", 12);
	}
}

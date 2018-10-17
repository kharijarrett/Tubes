package tracker;

import java.awt.image.BufferedImage;
import java.io.Serializable;
import java.util.ArrayList;

import common.math.Vec;
import georegression.struct.point.Point2D_I32;

public class LocalGrid implements Serializable {
	int id;
	int x;
	int y;
	
	double histogram[];
	
	public LocalGrid(int id, int x, int y) {
		this.id = id;
		this.x = x;
		this.y = y;
		this.histogram = new double[9];
	}
	
	public static ArrayList<LocalGrid> computeContexts(ArrayList<Point2D_I32> shapePoints, int size, int offset, int xdim, int ydim, boolean ignoreZero) {
		
		ArrayList<LocalGrid> contexts = new ArrayList<LocalGrid>();
		
		//fill histograms
		for(int h = 3*size/2 + 1; h + 3*size/2 + 1 < xdim; h += offset) {
			for(int i = 3*size/2 + 1; i + 3*size/2 + 1 < ydim; i += offset) {
				
				LocalGrid con = new LocalGrid(contexts.size(), h, i);
				
				for(int j = 0; j < shapePoints.size(); j++) {
					Point2D_I32 p2 = shapePoints.get(j);
					
					//grid arrangement
					//[0][1][2]
					//[3][4][5]
					//[6][7][8]
					
					int l = 9;
					int relx = p2.x - h;
					int rely = p2.y - i;
					
					if(relx > size/2 && relx <= 3*size/2) {
						if(rely > size/2 && rely <= 3*size/2) l = 2;
						if(rely <= size/2 && rely >= -size/2) l = 5;
						if(rely < -size/2 && rely >= -3*size/2) l = 8;
					}
					else if(relx <= size/2 && relx >= -size/2) {
						if(rely > size/2 && rely <= 3*size/2) l = 1;
						if(rely <= size/2 && rely >= -size/2) l = 4;
						if(rely < -size/2 && rely >= -3*size/2) l = 7;
					}
					else if(relx < -size/2 && relx >= -3*size/2) {
						if(rely > size/2 && rely <= 3*size/2) l = 0;
						if(rely <= size/2 && rely >= -size/2) l = 3;
						if(rely < -size/2 && rely >= -3*size/2) l = 6;
					}
					if(l == 9) continue;
					con.histogram[l]++;
//					con.histogram[l] = 1;
//					con.histogram[l] = con.histogram[l] + ((2 - con.histogram[l])/2);
				}
				
				if(ignoreZero == true) {
					int sum = 0;
					for(int j = 0; j < con.histogram.length; j++) {
						sum += con.histogram[j];
					}
					
					for(int j = 0; j < con.histogram.length; j++) {
						con.histogram[j] = con.histogram[j]/sum;
					}
					
					if(sum == 0) continue;
				}
				
				contexts.add(con);
			
			}
		}
		
		return contexts;
	}
	
public static ArrayList<LocalGrid> computeContexts(BufferedImage image, int size, int offset, int xdim, int ydim, boolean ignoreZero) {
		
		ArrayList<LocalGrid> contexts = new ArrayList<LocalGrid>();
		
		//fill histograms
		for(int h = 3*size/2 + 1; h + 3*size/2 + 1 < xdim; h += offset) {
			for(int i = 3*size/2 + 1; i + 3*size/2 + 1 < ydim; i += offset) {
				
				LocalGrid con = new LocalGrid(contexts.size(), h, i);
				
				for(int j = h - 3*size/2 - 1; j < image.getWidth() && j < h + 3*size/2 + 1; j++) {
					for(int k = i - 3*size/2 - 1; k < image.getHeight() && k < i + 3*size/2 + 1; k++) {
						
						int rgb = image.getRGB(j, k); //always returns TYPE_INT_ARGB
						int alpha = (rgb >> 24) & 0xFF;
						int red =   (rgb >> 16) & 0xFF;
						int green = (rgb >>  8) & 0xFF;
						int blue =  (rgb      ) & 0xFF;				
						
						if(red == 0 && green == 0 && blue == 0) continue;
						
						//grid arrangement
						//[0][1][2]
						//[3][4][5]
						//[6][7][8]
						
						int l = 9;
						int relx = j - h;
						int rely = k - i;
						
						if(relx > size/2 && relx <= 3*size/2) {
							if(rely > size/2 && rely <= 3*size/2) l = 2;
							if(rely <= size/2 && rely >= -size/2) l = 5;
							if(rely < -size/2 && rely >= -3*size/2) l = 8;
						}
						else if(relx <= size/2 && relx >= -size/2) {
							if(rely > size/2 && rely <= 3*size/2) l = 1;
							if(rely <= size/2 && rely >= -size/2) l = 4;
							if(rely < -size/2 && rely >= -3*size/2) l = 7;
						}
						else if(relx < -size/2 && relx >= -3*size/2) {
							if(rely > size/2 && rely <= 3*size/2) l = 0;
							if(rely <= size/2 && rely >= -size/2) l = 3;
							if(rely < -size/2 && rely >= -3*size/2) l = 6;
						}
						if(l == 9) continue;
						con.histogram[l]++;
						
					}

				}
				
				if(ignoreZero == true) {
					int sum = 0;
					for(int j = 0; j < con.histogram.length; j++) {
						sum += con.histogram[j];
					}
					
					if(sum == 0) continue;
					
					for(int j = 0; j < con.histogram.length; j++) {
						con.histogram[j] = con.histogram[j]/sum;
					}
					
				}
				
				contexts.add(con);
			
			}
		}
		
		return contexts;
	}
	
	public static ArrayList<LocalGrid> computeAllContexts(ArrayList<ArrayList<Point2D_I32>> shapePoints, int radius, int offset, int xdim, int ydim, boolean ignoreZero) {
		
		ArrayList<LocalGrid> allContexts = new ArrayList<LocalGrid>();
		for(int i = 0; i < shapePoints.size(); i++) {
			ArrayList<LocalGrid> context = computeContexts(shapePoints.get(i), radius, offset, xdim, ydim, ignoreZero);
			allContexts.addAll(context);
		}
		
		return allContexts;
		
	}
}
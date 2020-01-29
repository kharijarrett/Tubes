package tracker;

import java.io.Serializable;
import java.util.ArrayList;

import common.math.Vec;
import georegression.struct.point.Point2D_I32;

public class LocalContext implements Serializable {
	int id;
	int x;
	int y;
	
	static int nlogbins = 1;
	static int npolarbins = 12;
	
	double histogram[];
	
	public LocalContext(int id, int x, int y) {
		this.id = id;
		this.x = x;
		this.y = y;
		this.histogram = new double[npolarbins];
	}
	
	public LocalContext(int id, int x, int y, int npolarbins) {
		this.id = id;
		this.x = x;
		this.y = y;
		this.npolarbins = npolarbins;
		this.histogram = new double[npolarbins];
	}
	
	public static ArrayList<LocalContext> computeContexts(ArrayList<Point2D_I32> shapePoints, int radius, int offset, int xdim, int ydim, boolean ignoreZero) {
		
		ArrayList<LocalContext> contexts = new ArrayList<LocalContext>();
		
		//set outer to 2*mean, get bin limits
		double outer = radius;
		double logouter = Math.log10(outer);
		double binedges[] = new double[nlogbins];
		
		for(int i = 0; i < nlogbins; i++) {
			binedges[i] = Math.pow(10, ((i+1)/nlogbins)*logouter);
		}
		
		//fill histograms
		for(int h = radius; h+radius < xdim; h += offset) {
			for(int i = radius; i+radius < ydim; i += offset) {
				
				LocalContext con = new LocalContext(contexts.size(), h, i);
				
				for(int j = 0; j < shapePoints.size(); j++) {
					Point2D_I32 p2 = shapePoints.get(j);
					
					double dist = Math.sqrt(Math.pow(h-p2.x, 2) + Math.pow(i-p2.y, 2));
					if(dist > radius) continue;
					
					double degree = Math.toDegrees(Math.atan2(p2.y - i, p2.x - h));
					if(degree < 0) degree += 360;
					int l = (int) (degree/360 * npolarbins);
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
	
	public static ArrayList<LocalContext> computeAllContexts(ArrayList<ArrayList<Point2D_I32>> shapePoints, int radius, int offset, int xdim, int ydim, boolean ignoreZero) {
		
		ArrayList<LocalContext> allContexts = new ArrayList<LocalContext>();
		for(int i = 0; i < shapePoints.size(); i++) {
			ArrayList<LocalContext> context = computeContexts(shapePoints.get(i), radius, offset, xdim, ydim, ignoreZero);
			allContexts.addAll(context);
		}
		
		return allContexts;
		
	}
	
//	public static double chisquared(int[] a, int[] b) {
//		
//		double score = 0;
//		for(int i = 0; i < a.length; i++) {
//			if(a[i] + b[i] == 0) {
//				score += 0;
//			}
//			else {
//				score += (a[i] - b[i])*(a[i] - b[i])/(a[i] + b[i]);					
//			}
//			
//		}
//		
//		score *= 0.5;
//		
//		return score;
//	}
//	
//	public static double[][] costmatrix(ArrayList<LocalContext> c1, ArrayList<LocalContext> c2) {
//		
//		double costmatrix[][] = new double[c1.size()][c2.size()];
//		for(int i = 0; i < c1.size(); i++) {
//			for(int j = 0; j < c2.size(); j++)
//				costmatrix[i][j] = chisquared(c1.get(i).histogram, c2.get(j).histogram);
//		}
//		
//		return costmatrix;
//	}
	
}
package tracker;

import java.util.ArrayList;

import common.math.Vec;
import georegression.struct.point.Point2D_I32;

public class ShapeContext {
	int id;
	int x;
	int y;
	
	static int nlogbins = 5;
	static int npolarbins = 12;
	
	int histogram[][];
	
	public ShapeContext(int id, int x, int y) {
		this.id = id;
		this.x = x;
		this.y = y;
		this.histogram = new int[nlogbins][npolarbins];
	}
	
	public ShapeContext(int id, int x, int y, int nlogbins, int npolarbins) {
		this.id = id;
		this.x = x;
		this.y = y;
		this.nlogbins = nlogbins;
		this.npolarbins = npolarbins;
		this.histogram = new int[nlogbins][npolarbins];
	}
	
	public static ArrayList<ShapeContext> computeContexts(ArrayList<Point2D_I32> shapePoints) {
		
		ArrayList<ShapeContext> contexts = new ArrayList<ShapeContext>();
		
		//calculate distance matrix
		int s = shapePoints.size();
		double distances[][] = new double[s][s];
		for(int i = 0; i < s; i++) {
			for(int j = i+1; j < s; j++) {
				Point2D_I32 p1 = shapePoints.get(i);
				Point2D_I32 p2 = shapePoints.get(j);
				distances[i][j] = Math.sqrt(Math.pow(p1.x-p2.x, 2) + Math.pow(p1.y-p2.y, 2));
				distances[j][i] = distances[i][j];
			}
		}
		
		//calculate mean distance
		int ndists = (s * (s-1))/2;
		double dists[] = new double[ndists];
		int c = 0;
		
		for(int i = 0; i < s; i++) {
			for(int j = i+1; j < s; j++) {
				dists[c] = distances[i][j];
				c++;
			}
		}
		
		double meandist = Vec.Mean(dists);
		
		
		//set outer to 2*mean, get bin limits
		double outer = 2*meandist;
		double logouter = Math.log10(outer);
		double binedges[] = new double[nlogbins];
		
		for(int i = 0; i < nlogbins; i++) {
			binedges[i] = Math.pow(10, ((i+1)/nlogbins)*logouter);
		}
		
		//fill histograms
		for(int i = 0; i < s; i++) {
			Point2D_I32 p1 = shapePoints.get(i);
			ShapeContext con = new ShapeContext(contexts.size(), p1.x, p1.y);
			
			for(int j = 0; j < s; j++) {
				if(j == i) continue;
				Point2D_I32 p2 = shapePoints.get(j);
				
				double d = distances[i][j];
				int k = 0;
				while(k < nlogbins && d > binedges[k]) k++;
				if(k >= nlogbins) continue;
				
				double degree = Math.toDegrees(Math.atan2(p2.y - p1.y, p2.x - p1.x));
				if(degree < 0) degree += 360;
				int l = (int) (degree/360 * npolarbins);
				con.histogram[k][l]++;

			}
			
			contexts.add(con);
		}
		
		return contexts;
	}
	
	public static double chisquared(int[][] a, int[][] b) {
		
		double score = 0;
		for(int i = 0; i < a.length; i++) {
			for(int j = 0; j < a[i].length; j++) {
				if(a[i][j] + b[i][j] == 0) {
					score += 0;
				}
				else {
					score += (a[i][j] - b[i][j])*(a[i][j] - b[i][j])/(a[i][j] + b[i][j]);					
				}
			}
		}
		
		score *= 0.5;
		
		return score;
	}
	
	public static double[][] costmatrix(ArrayList<ShapeContext> c1, ArrayList<ShapeContext> c2) {
		
		double costmatrix[][] = new double[c1.size()][c2.size()];
		for(int i = 0; i < c1.size(); i++) {
			for(int j = 0; j < c2.size(); j++)
				costmatrix[i][j] = chisquared(c1.get(i).histogram, c2.get(j).histogram);
		}
		
		return costmatrix;
	}
	
}

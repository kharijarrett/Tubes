package tracker;

import java.awt.Color;
import java.io.Serializable;
import java.util.ArrayList;

import javax.swing.JFrame;

import org.math.plot.Plot3DPanel;

import common.math.Vec;
import common.utils.ColorUtils;
import common.utils.ColorUtils.ColorMap;
import georegression.struct.point.Point2D_I32;
import javafx.geometry.Point3D;

public class ShapeContext3D implements Serializable {

	private static final long serialVersionUID = 1L;
	int id;
	int x;
	int y;
	int t;
	
	static int nlogbins = 5;
	static int npolarbins = 12;
	static int nazimuthbins = 6;
	
	int histogram[][][];
	
	public ShapeContext3D(int id, int x, int y, int t) {
		this.id = id;
		this.x = x;
		this.y = y;
		this.t = t;
		this.histogram = new int[nlogbins][npolarbins][nazimuthbins];
	}
	
	public ShapeContext3D(int id, int x, int y, int t, int nlogbins, int npolarbins, int nazimuthbins) {
		this.id = id;
		this.x = x;
		this.y = y;
		this.t = t;
		this.nlogbins = nlogbins;
		this.npolarbins = npolarbins;
		this.nazimuthbins = nazimuthbins;
		this.histogram = new int[nlogbins][npolarbins][nazimuthbins];
	}
	
	public String toString() {
		String output = "";
		output += "x: " + this.x;
		output += " y: " + this.y;
		output += " t: " + this.t;
		output += "\n";
		
		int sum = 0;
		for(int k = 0; k < nazimuthbins; k++) {
			for(int i = 0; i < nlogbins; i++) {
				for(int j = 0; j < npolarbins; j++) {
					output += "[" + this.histogram[i][j][k] + "]";
					sum += this.histogram[i][j][k];
				}
				output += "\n";
			}
			output += "\n\n";
		}
		
		output += "used: " + sum;
		return output;
	}
	
	public int[] vector() {
		int vec[] = new int[nazimuthbins*nlogbins*npolarbins];
		int ct = 0;
		for(int k = 0; k < nazimuthbins; k++) {
			for(int i = 0; i < nlogbins; i++) {
				for(int j = 0; j < npolarbins; j++) {
					vec[ct] = histogram[i][j][k];
					ct++;
				}
			}		
		}
		return vec;
	}
	
	public static ArrayList<Point3D> computeTimePoints(ArrayList<ArrayList<Point2D_I32>> shapeseries, double timescale) {
		
		ArrayList<Point3D> timepoints = new ArrayList<Point3D>();
		
		for(int i = 0; i < shapeseries.size(); i++) {
			for(Point2D_I32 p : shapeseries.get(i)) {
				timepoints.add(new Point3D(p.x, p.y, i*timescale));
			}
		}
		
		return timepoints;
	}
	
	public static ArrayList<ShapeContext3D> computeContexts(ArrayList<Point3D> timepoints) {
			
		ArrayList<ShapeContext3D> contexts = new ArrayList<ShapeContext3D>();		
		
		//calculate distance matrix
		int s = timepoints.size();
		double sum = 0;
		for(int i = 0; i < s; i++) {
			for(int j = i+1; j < s; j++) {
				Point3D p1 = timepoints.get(i);
				Point3D p2 = timepoints.get(j);					
				sum += Math.sqrt((p1.getX()-p2.getX())*(p1.getX()-p2.getX()) + 
						(p1.getY()-p2.getY())*(p1.getY()-p2.getY()) + (p1.getZ()-p2.getZ())*(p1.getZ()-p2.getZ()));
			}
		}		
		
		//calculate mean distance
		int ndists = (s * (s-1))/2;
		double meandist = sum/ndists;

		//set outer to 2*mean, get bin limits
		double outer = 2*meandist;
		double logouter = Math.log10(outer);
		double binedges[] = new double[nlogbins];
		
//		System.out.println("OUTER: " + outer);
		for(int i = 0; i < nlogbins; i++) {
			binedges[i] = Math.pow(10, ((double)(i+1)/nlogbins)*logouter);
//			System.out.println("Bin " + i + " " + binedges[i]);
		}
		
		//fill histograms
		for(int i = 0; i < s; i++) {
			Point3D p1 = timepoints.get(i);
//			System.out.println("Processing pt " + i);
			ShapeContext3D con = new ShapeContext3D(contexts.size(), (int) p1.getX(), (int)  p1.getY(), (int)  p1.getZ());
			
			for(int j = 0; j < s; j++) {
				if(j == i) continue;
				Point3D p2 = timepoints.get(j);
				
				double d = Math.sqrt((p1.getX()-p2.getX()*(p1.getX()-p2.getX()) + 
						(p1.getY()-p2.getY())*(p1.getY()-p2.getY()) + (p1.getZ()-p2.getZ())*(p1.getZ()-p2.getZ())));
				int k = 0;
				while(k < nlogbins && d > binedges[k]) k++;
				if(k >= nlogbins) continue;
				
				double degree = Math.toDegrees(Math.atan2(p2.getY() - p1.getY(), p2.getX() - p1.getX()));
				if(degree < 0) degree += 360;
				int l = (int) (degree/360 * npolarbins);
				
//				double azimuth = Math.toDegrees(Math.acos((p2.getZ()-p1.getZ())/d));
				
				double r = Math.sqrt((p1.getX()-p2.getX())*(p1.getX()-p2.getX()) + (p1.getY()-p2.getY())*(p1.getY()-p2.getY()));
				double azimuth = Math.toDegrees(Math.atan(r/(p2.getZ()-p1.getZ())));
				if(p2.getZ()-p1.getZ() == 0) azimuth = 0;
				if(Double.isNaN(azimuth)) {
					System.out.println("nan...");
					System.out.println(r);
					System.out.println(p2.getZ());
					System.out.println(p1.getZ());
					System.out.println(r/(p2.getZ()-p1.getZ()));
					System.out.println("/endnan");
					continue;
				}
//				System.out.println(azimuth);
//				azimuth = -azimuth + 90;
				
				
				int m = 1;
				while(Math.toDegrees(Math.asin((double) 2*m/nazimuthbins)) < Math.abs(azimuth)) m++;
//				System.out.println(azimuth);
				if(azimuth >= 0) m += nazimuthbins/2 - 1;
				else /*if azimuth < 0 */ m = nazimuthbins/2 - m;
				
				con.histogram[k][l][m]++;
				
			}
			
			contexts.add(con);
		}
		
		return contexts;
	}

	public static ArrayList<ShapeContext3D> computeContextSlices(ArrayList<Point3D> timepoints) {
		
		ArrayList<ShapeContext3D> contexts = new ArrayList<ShapeContext3D>();		
		
		//calculate distance matrix
		int s = timepoints.size();
		double sum = 0;
		int ndists = 0;
		for(int i = 0; i < s; i++) {
			for(int j = i+1; j < s; j++) {
				
				Point3D p1 = timepoints.get(i);
				Point3D p2 = timepoints.get(j);					
				
				if(Math.abs(p1.getZ() - p2.getZ()) > 10) continue;
				
				sum += Math.sqrt((p1.getX()-p2.getX())*(p1.getX()-p2.getX()) + 
						(p1.getY()-p2.getY())*(p1.getY()-p2.getY()) + (p1.getZ()-p2.getZ())*(p1.getZ()-p2.getZ()));
				
				ndists++;
			}
		}		
		
		//calculate mean distance
		double meandist = sum/ndists;

		//set outer to 2*mean, get bin limits
		double outer = 2*meandist;
		double logouter = Math.log10(outer);
		double binedges[] = new double[nlogbins];
		
//		System.out.println("OUTER: " + outer);
		for(int i = 0; i < nlogbins; i++) {
			binedges[i] = Math.pow(10, ((double)(i+1)/nlogbins)*logouter);
//			System.out.println("Bin " + i + " " + binedges[i]);
		}
		
		//fill histograms
		for(int i = 0; i < s; i++) {
			Point3D p1 = timepoints.get(i);
//			System.out.println("Processing pt " + i);
			ShapeContext3D con = new ShapeContext3D(contexts.size(), (int) p1.getX(), (int)  p1.getY(), (int)  p1.getZ());
			
			for(int j = 0; j < s; j++) {
				if(j == i) continue;
				Point3D p2 = timepoints.get(j);

				if(Math.abs(p1.getZ() - p2.getZ()) > 10) continue;
				
				double d = Math.sqrt((p1.getX()-p2.getX()*(p1.getX()-p2.getX()) + 
						(p1.getY()-p2.getY())*(p1.getY()-p2.getY()) + (p1.getZ()-p2.getZ())*(p1.getZ()-p2.getZ())));
				int k = 0;
				while(k < nlogbins && d > binedges[k]) k++;
				if(k >= nlogbins) continue;
				
				double degree = Math.toDegrees(Math.atan2(p2.getY() - p1.getY(), p2.getX() - p1.getX()));
				if(degree < 0) degree += 360;
				int l = (int) (degree/360 * npolarbins);
				
//				double azimuth = Math.toDegrees(Math.acos((p2.getZ()-p1.getZ())/d));
				
				double r = Math.sqrt((p1.getX()-p2.getX())*(p1.getX()-p2.getX()) + (p1.getY()-p2.getY())*(p1.getY()-p2.getY()));
				double azimuth = Math.toDegrees(Math.atan(r/(p2.getZ()-p1.getZ())));
				if(p2.getZ()-p1.getZ() == 0) azimuth = 0;
				if(Double.isNaN(azimuth)) {
					System.out.println("nan...");
					System.out.println(r);
					System.out.println(p2.getZ());
					System.out.println(p1.getZ());
					System.out.println(r/(p2.getZ()-p1.getZ()));
					System.out.println("/endnan");
					continue;
				}
//				System.out.println(azimuth);
//				azimuth = -azimuth + 90;
				
				
				int m = 1;
				while(Math.toDegrees(Math.asin((double) 2*m/nazimuthbins)) < Math.abs(azimuth)) m++;
//				System.out.println(azimuth);
				if(azimuth >= 0) m += nazimuthbins/2 - 1;
				else /*if azimuth < 0 */ m = nazimuthbins/2 - m;
				
				con.histogram[k][l][m]++;
				
			}
			
			contexts.add(con);
		}
		
		return contexts;
	}
	
	public static void computeOneContext(ArrayList<Point3D> timepoints, int index) {
		
		Plot3DPanel plot = new Plot3DPanel();
		
		//calculate distance matrix
		int s = timepoints.size();
		double sum = 0;
		for(int i = 0; i < s; i++) {
			for(int j = i+1; j < s; j++) {
				Point3D p1 = timepoints.get(i);
				Point3D p2 = timepoints.get(j);					
				sum += Math.sqrt((p1.getX()-p2.getX())*(p1.getX()-p2.getX()) + 
						(p1.getY()-p2.getY())*(p1.getY()-p2.getY()) + (p1.getZ()-p2.getZ())*(p1.getZ()-p2.getZ()));
			}
		}		
		
		//calculate mean distance
		int ndists = (s * (s-1))/2;
		double meandist = sum/ndists;

		//set outer to 2*mean, get bin limits
		double outer = 2*meandist;
		double logouter = Math.log10(outer);
		double binedges[] = new double[nlogbins];
		
		System.out.println("OUTER: " + outer);
		for(int i = 0; i < nlogbins; i++) {
			binedges[i] = Math.pow(10, ((double)(i+1)/nlogbins)*logouter);
			System.out.println("Bin " + i + " " + binedges[i]);
		}
		
		//fill histograms
		int i = index;
		Point3D p1 = timepoints.get(i);
		System.out.println("Processing pt " + i);
		
		double X[] = new double[1];
		double Y[] = new double[1];
		double T[] = new double[1];
		
		X[0] = p1.getX();
		Y[0] = p1.getY();
		T[0] = p1.getZ();
		
		plot.addScatterPlot("ShapeContext3D", Color.BLACK, X, Y, T);
		
		ShapeContext3D con = new ShapeContext3D(0, (int) p1.getX(), (int)  p1.getY(), (int)  p1.getZ());
		
		for(int j = 0; j < s; j++) {
			if(j == i) continue;
			Point3D p2 = timepoints.get(j);
			
			double d = Math.sqrt((p1.getX()-p2.getX()*(p1.getX()-p2.getX()) + 
					(p1.getY()-p2.getY())*(p1.getY()-p2.getY()) + (p1.getZ()-p2.getZ())*(p1.getZ()-p2.getZ())));
			int k = 0;
			while(k < nlogbins && d > binedges[k]) k++;
			if(k >= nlogbins) continue;
			
			double degree = Math.toDegrees(Math.atan2(p2.getY() - p1.getY(), p2.getX() - p1.getX()));
			if(degree < 0) degree += 360;
			int l = (int) (degree/360 * npolarbins);
			
//			double azimuth = Math.toDegrees(Math.acos((p2.getZ()-p1.getZ())/d));
			
			double r = Math.sqrt((p1.getX()-p2.getX())*(p1.getX()-p2.getX()) + (p1.getY()-p2.getY())*(p1.getY()-p2.getY()));
			double azimuth = Math.toDegrees(Math.atan(r/(p2.getZ()-p1.getZ())));
			if(p2.getZ()-p1.getZ() == 0) azimuth = 0;
			if(Double.isNaN(azimuth)) {
				System.out.println("nan...");
				System.out.println(r);
				System.out.println(p2.getZ());
				System.out.println(p1.getZ());
				System.out.println(r/(p2.getZ()-p1.getZ()));
				System.out.println("/endnan");
				continue;
			}
//			System.out.println(azimuth);
//			azimuth = -azimuth + 90;
			
			
			int m = 1;
			while(Math.toDegrees(Math.asin((double) 2*m/nazimuthbins)) < Math.abs(azimuth)) m++;
//			System.out.println(azimuth);
			if(azimuth >= 0) m += nazimuthbins/2 - 1;
			else /*if azimuth < 0 */ m = nazimuthbins/2 - m;
			
			con.histogram[k][l][m]++;

			X[0] = p2.getX();
			Y[0] = p2.getY();
			T[0] = p2.getZ();
			
			int rgb[] = ColorUtils.ColorMapRGB(ColorMap.JET, (double) ((7*k+41)*(29*l+17)*(71*m+23) % 360) /360);
			Color color = new Color(rgb[0], rgb[1], rgb[2]);
			
			plot.addScatterPlot("ShapeContext3D", color, X, Y, T);		
			
		}
		
		JFrame frame = new JFrame("a plot panel");
		frame.setSize(1200, 1200);
		frame.setContentPane(plot);
		frame.setVisible(true);
		
		System.out.println(con);
		
	}
	
	public static double chisquared(int[][][] a, int[][][] b) {
		
		double score = 0;
		for(int i = 0; i < a.length; i++) {
			for(int j = 0; j < a[i].length; j++) {
				for(int k = 0; k < a[i][j].length; k++) {
					if(a[i][j][k] + b[i][j][k] == 0) {
						score += 0;
					}
					else {
						score += (a[i][j][k] - b[i][j][k])*(a[i][j][k] - b[i][j][k])/(a[i][j][k] + b[i][j][k]);					
					}					
				}

			}
		}
		
		score *= 0.5;
		
		return score;
	}
	
	public static double[][] costmatrix(ArrayList<ShapeContext3D> c1, ArrayList<ShapeContext3D> c2) {
		
		double costmatrix[][] = new double[c1.size()][c2.size()];
		for(int i = 0; i < c1.size(); i++) {
			for(int j = 0; j < c2.size(); j++)
				costmatrix[i][j] = chisquared(c1.get(i).histogram, c2.get(j).histogram);
		}
		
		return costmatrix;
	}
	
}

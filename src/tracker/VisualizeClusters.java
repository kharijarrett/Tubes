package tracker;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;

import com.sun.javafx.iio.ImageStorage.ImageType;

import common.FileIO;
import common.math.Vec;

public class VisualizeClusters {

	public static void displayClusters(String dest, String csv) {
		double clusters[][] = FileIO.LoadCSV(new File(csv));
		
		for(int i = 0; i < clusters.length; i++) {
			
			BufferedImage output = new BufferedImage(50, 50, BufferedImage.TYPE_INT_RGB);
			Graphics2D g2 = output.createGraphics();
			
//			for(int j = 0; j < clusters[j].length; j++) {
				for(int k = 0; k < 5; k++) {
					for(int l = 0; l < 5; l++) {
						double val = clusters[i][5*k+l];
						val = val/2 + 0.5;
//						val = (1 / ( 1 + (Math.pow(Math.E,(-1*(15*val+1)/2)))));
						Color h = new Color((int) (val*255), (int) (val*255), (int) (val*255));
						g2.setColor(h);
						g2.fillRect(10*k, 10*l, 10, 10);
					}
				}
//			}
			FileIO.SaveImage(output, new File(dest + i + ".png"));
		}
		
	}
	
	public static void layerTwoClusters(String dest, String csv, String layerone) {
		double clusters[][] = FileIO.LoadCSV(new File(csv));
		double layone[][] = FileIO.LoadCSV(new File(layerone));
		
		for(int i = 0; i < clusters.length; i++) {
			
			BufferedImage output = new BufferedImage(90, 90, BufferedImage.TYPE_INT_RGB);
			Graphics2D g2 = output.createGraphics();
			
			double factor[][] =  {  {1,1./2.,1./3.,1./4.,1./5.,1./4.,1./3.,1./2.,1.},
								    {1./2.,1./3.,1./4.,1./5.,1./6.,1./5.,1./4.,1./3.,1./2.},
								    {1./3.,1./4.,1./5.,1./6.,1./7.,1./6.,1./5.,1./4.,1./3.},
								    {1./4.,1./5.,1./6.,1./7.,1./8.,1./7.,1./6.,1./5.,1./4.},
								    {1./5.,1./6.,1./7.,1./8.,1./9.,1./8.,1./7.,1./6.,1./5.},
								    {1./4.,1./5.,1./6.,1./7.,1./8.,1./7.,1./6.,1./5.,1./4.},
								    {1./3.,1./4.,1./5.,1./6.,1./7.,1./6.,1./5.,1./4.,1./3.},
								    {1./2.,1./3.,1./4.,1./5.,1./6.,1./5.,1./4.,1./3.,1./2.},
								    {1.,1./2.,1./3.,1./4.,1./5.,1./4,1./3.,1./2.,1.} };
			double out[][] = new double[9][9];
			
			
			
//			double vecs[][] = new double[40][25];
			for(int k = 0; k < 5; k++) {
				for(int l = 0; l < 5; l++) {
					double sumsquares = 0;
					double vec[] = new double[25];
//					for(int j = 0; j < 40; j++) {
//						sumsquares += Math.pow(clusters[i][25*j + 5*k + l],2);
//					}
//					for(int j = 0; j < 40; j++) {
						for(int m = 0; m < 40; m++) {
							
							vec = Vec.Add(vec, Vec.Mul(layone[m], Math.pow(clusters[i][(5*k+l)*40 + m],7)));
//							vec[5*k + l] += Math.pow(clusters[i][25*j + 5*k + l],1) * layone[m][5*k+l];
//							vecs[i][5*k+l] += Math.pow(clusters[i][25*j + 5*k + l],5) * layone[m][5*k+l];
						}
//					}
					
					for(int j = 0; j < 5; j++) {
						for(int m = 0; m < 5; m++) {
							out[k+j][l+m] += vec[5*j + m] * factor[k+j][l+m];
						}
					}
				}
			}
			
			double maxval = 0;
			for(int k = 0; k < 9; k++) {
				for(int l = 0; l < 9; l++) {
					if(Math.abs(out[k][l]) > maxval) maxval = Math.abs(out[k][l]);
				}
			}
			
			for(int k = 0; k < 9; k++) {
				for(int l = 0; l < 9; l++) {
					double val = (out[k][l]/maxval + 1) / 2;
					System.out.println(val + " " + k + " " + l);
					Color h = new Color((int) (val*255), (int) (val*255), (int) (val*255));
					g2.setColor(h);
					g2.fillRect(10*k, 10*l, 10, 10);
				}
			}

			FileIO.SaveImage(output, new File(dest + i + ".png"));
		}
		
	}
	
	public static void main(String[] args) {
		
//		displayClusters("interclusters/doog", "/home/grangerlab/workspace/chris/clusters_intersection_l1.csv");
		layerTwoClusters("layer2/l", "/home/grangerlab/workspace/chris/layertwopacman.csv", "doog_fullbb.csv");
	}
	
}

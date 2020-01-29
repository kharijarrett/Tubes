package tracker;

import java.io.File;

import common.FileIO;

public class HeatShape {
	
	WindowWorker window;
	double grid[][];
	
	public HeatShape(WindowWorker window, int gridsize) {
		this.window = window;
		grid = new double[gridsize][gridsize];
	}
	
	public HeatShape(String filepath, int gridsize) {
		this.window = (WindowWorker) FileIO.LoadObject(new File(filepath));
		grid = new double[gridsize][gridsize];		
	}
	
	public void printWindow() {
		for(WindowInfo w: window.windows) {
			System.out.println(w.rect);
			System.out.println(w.feats.size());
		}
	}
	
	public void fillGrid() {
		for(WindowInfo w: window.windows) {
			for(FeaturePosition p: w.feats) {
				int x = (int) (((p.x - w.rect.getX()) / w.rect.getWidth()) * grid.length);
				int y = (int) (((p.y - w.rect.getY()) / w.rect.getHeight()) * grid.length);
				grid[x][y]++;
				if(x >= 1) grid[x-1][y] += 0.25;
				if(x < grid.length-1) grid[x+1][y] += 0.25;
				if(y >= 1) grid[x][y-1] += 0.25;
				if(y < grid.length-1) grid[x][y+1] += 0.25;
				if(x >= 1 && y >= 1) grid[x-1][y-1] += 0.25;
				if(x < grid.length-1 && y < grid.length-1) grid[x+1][y+1] += 0.25;
				if(x < grid.length-1 && y >= 1) grid[x+1][y-1] += 0.25;
				if(x >= 1 && y < grid.length-1) grid[x-1][y+1] += 0.25;
			}
		}	
	}
	
	public void printGrid(String csvDest) {
		for(int i = 0; i < grid.length; i++) {
			for(int j = 0; j < grid.length; j++) {
				System.out.print(grid[i][j]);
				if(j != grid.length-1) System.out.print(" ");
			}
			System.out.print("\n");
		}
		FileIO.SaveCSV(grid, new File(csvDest));
	}
	
	public void printDist() {
		for(int i = 0; i < window.windows.size(); i++) {
			int pts = window.windows.get(i).feats.size();
			double dist[][] = new double[pts][pts];
			
			System.out.print("(" + i + ", " + pts + "x" + pts + ")\n");
			
			for(int j = 0; j < pts; j++) {
				
				System.out.print("(" + window.windows.get(i).feats.get(j).id + ")");
				if(j != pts-1) System.out.print("\t");
				
				for(int k = j+1; k < pts; k++) {
					FeaturePosition a = window.windows.get(i).feats.get(j);
					FeaturePosition b = window.windows.get(i).feats.get(k);
					
					dist[j][k] = Math.sqrt(Math.pow((a.x - b.x), 2) + Math.pow((a.y - b.y), 2));
					dist[k][j] = dist[j][k];
				}
			}
			
			System.out.println();
			
			for(int j = 0; j < pts; j++) {
				
//				System.out.print("(" + window.windows.get(i).feats.get(j).id + ")\t");
				
				for(int k = 0; k < pts; k++) {
					System.out.print(Math.round(dist[j][k]));
					if(k != pts-1) System.out.print("\t");
				}
				System.out.print("\n");
			}
			
			System.out.println("------------------");
			
//			if(i == ) break;
		}
	}
	
	public static void main(String[] args) {
		HeatShape h = new HeatShape("/home/grangerlab/workspace/chris/box/pac_f269_l312.zip", 10);
//		h.printWindow();
		h.printDist();
//		h.fillGrid();
//		h.printGrid("test3.csv");
//		System.out.println(h.window.windows.size());
	}
	
}

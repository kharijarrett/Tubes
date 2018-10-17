package tracker;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;

import common.FileIO;
import common.UI;
import common.bagofwords.VocabKMeans;
import common.math.Distance;

public class CSVMerge {
	
	@SuppressWarnings("unchecked")
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
			    if (file.isFile() && (file.getName().toLowerCase().endsWith("_arc.zip"))) {
			    	fq.add(file.getAbsolutePath());
			    }
			}
			
			for(int i = 0; i < fq.size(); i++) {
				String currentFilePath = fq.get(i);
		    	System.out.println("processing video-archive " + (i+1) + " of " + fq.size() + ", " + currentFilePath);
		    	
		    	ArrayList<ShapeContext3D> contexts = (ArrayList<ShapeContext3D>) FileIO.LoadObject(new File(currentFilePath));
		    	for(int j = 0; j < contexts.size(); j++) {
		    		int[] currRow = contexts.get(j).vector();
		    		
		    		boolean firstPrint = true;
					for (int currElement : currRow) {
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
	
	public static void vectorize(String loadfile) {
		UI.tic();
		double[][] vectors = FileIO.LoadCSV(new File(loadfile));
		System.out.println(UI.toc());
		int k = 30;
		
		VocabKMeans clustering = new VocabKMeans(k, 50, Distance.Kernel.EUCLIDIAN, vectors[0].length);
//		double outs[][] = clustering.GenerateAndReturn(vectors);
		clustering.Generate(vectors);
//		FileIO.SaveCSV(outs, new File(k + "means.csv"));
		
		ArrayList<double[]> cts = new ArrayList<double[]>();
		
		double hist[] = new double[k];
		for(int i = 0; i < vectors.length; i++) {
			int c = (int) clustering.Lookup(vectors[i]);
			hist[c]++;
//			System.out.print(c + " ");
//			if(i%70 == 69) System.out.println();
			if(i%70 == 69) {
				cts.add(hist.clone());
				hist = new double[k];
			}
		}
//		System.out.println();
//		for(int i = 0; i < cts.size(); i++) {
//			int curr[] = cts.get(i);
//			for(int j = 0; j < curr.length; j++) {
//				System.out.print(curr[j] + " ");
//			}
//			System.out.println();
//		}
		
		double histvectors[][] = new double[cts.size()][cts.get(0).length];
		for(int i = 0; i < cts.size(); i++) {
			histvectors[i] = (double[]) cts.get(i);
		}
		
//		for(int i = 0; i < k; i++) System.out.print(hist[i] + " ");
		VocabKMeans clusterhist = new VocabKMeans(k, 50, Distance.Kernel.EUCLIDIAN, histvectors[0].length);
		clusterhist.Generate(histvectors);
		
		for(int i = 0; i < histvectors.length; i++) {
			int c = (int) clustering.Lookup(histvectors[i]);
			System.out.print(c + " ");
		}
		
	}
	
	public static void main(String[] args) {
		writeCSV("/home/grangerlab/workspace/chris/pac_all", new File("total.csv"));
//		vectorize("totalmillion.csv");
	}
	
}

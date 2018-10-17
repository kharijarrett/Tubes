package tracker;

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;

public class BoxProcessor {
	
	public static ArrayList<Rectangle2D> mergeBoxes(ArrayList<Rectangle2D> boxes) {
		
		Rectangle2D revised[] = new Rectangle2D[boxes.size()];
		for(int i = 0; i < boxes.size(); i++) {
			revised[i] = boxes.get(i);
		}
		
		for(int i = 0; i < boxes.size(); i++) {
			for(int j = i+1; j < boxes.size(); j++) {
				
//				System.out.println("-----");
//				System.out.println(boxes.get(i));
//				System.out.println(boxes.get(j));
//				System.out.println(boxes.get(i).getX() + " " + boxes.get(i).getWidth());
//				System.out.println(boxes.get(i).getY() + " " + boxes.get(i).getHeight());
//				System.out.println(boxes.get(j).getX() + " " + boxes.get(j).getWidth());
//				System.out.println(boxes.get(j).getY() + " " + boxes.get(j).getHeight());
				if(boxes.get(i).contains(boxes.get(j))) {
					revised[j] = null;
//					continue;
				}
				if(boxes.get(j).contains(boxes.get(i))) {
					revised[i] = null;
//					continue;
				}
//				
//				if(boxes.get(i).getX() > boxes.get(j).getX() && boxes.get(i).getX() + boxes.get(i).getWidth() < boxes.get(j).getX() + boxes.get(j).getWidth()
//						&& boxes.get(i).getY() > boxes.get(j).getY() && boxes.get(i).getY() + boxes.get(i).getHeight() < boxes.get(j).getY() + boxes.get(j).getHeight()) {
//					revised[i] = null;
//					System.out.println("ha ^^");
//					continue;
//				}
//				
//				if(boxes.get(i).getX() < boxes.get(j).getX() && boxes.get(i).getX() + boxes.get(i).getWidth() > boxes.get(j).getX() + boxes.get(j).getWidth()
//						&& boxes.get(i).getY() < boxes.get(j).getY() && boxes.get(i).getY() + boxes.get(i).getHeight() > boxes.get(j).getY() + boxes.get(j).getHeight()) {
//					revised[j] = null;
//					System.out.println("ha ^^");
//					continue;
//				}
////				
//				if(boxes.get(j).getMinX() > boxes.get(i).getMinX() && boxes.get(j).getMaxX() < boxes.get(i).getMaxX()
//						&& boxes.get(j).getMinY() > boxes.get(i).getMinY() && boxes.get(j).getMaxY() < boxes.get(i).getMaxY()) {
//					revised[j] = null;
//					continue;
//				}
				
//				System.out.println("all good");

			}
		}
		
		ArrayList<Rectangle2D> revisedlist = new ArrayList<Rectangle2D>();
		for(int i = 0; i < boxes.size(); i++) {
			if(revised[i] != null) revisedlist.add(revised[i]);
//			else System.out.println("not adding one");
		}
		return revisedlist;
	}
	
}

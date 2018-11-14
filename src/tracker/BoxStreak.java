package tracker;

import java.util.ArrayList;
import java.awt.Polygon;
import java.awt.geom.Rectangle2D;

public class BoxStreak {
	
	ArrayList<ArrayList<BoxFrame>> streaks;
	
	public BoxStreak() {
		streaks = new ArrayList<ArrayList<BoxFrame>>();
	}
	
	public ArrayList<BoxFrame> getBoxFrames(int frame, ArrayList<Rectangle2D> boxset, ArrayList<Polygon> polyset, ArrayList<FeaturePosition> feats) {
		
		ArrayList<BoxFrame> boxes = new ArrayList<BoxFrame>();
		for(int i = 0; i < boxset.size(); i++) {
			boxes.add(new BoxFrame(boxset.get(i), polyset.get(i), frame));
		}

		for(BoxFrame b : boxes) {
			b.setIncluded(feats);
		}
		
		return boxes;
	}
	
	public int featureOverlap(BoxFrame a, BoxFrame b) {
		int over = 0;
		for(FeaturePosition l : a.feats) {
			for(FeaturePosition m : b.feats) {
				if(l.id == m.id) {
					over++;
					continue;
				}
			}
		}
		return over;
	}
	
	public boolean overlap(BoxFrame a, BoxFrame b) {
//		System.out.println(a.frame + " " + b.frame);
		if(!a.bound.intersects(b.bound) && featureOverlap(a, b) == 0) return false;
		if(a.frame < b.frame - 10) return false;
//		if(featureOverlap(a, b) == 0) return false;
		if(a.bound.getHeight() > 1.4 * b.bound.getHeight()) return false;
		if(b.bound.getHeight() > 1.4 * a.bound.getHeight()) return false;
		if(a.bound.getWidth() > 1.4 * b.bound.getWidth()) return false;
		if(b.bound.getWidth() > 1.4 * a.bound.getWidth()) return false;
		
		if(a.frame == b.frame) return false;
		
		return true;
	}
	
	public ArrayList<Integer> associate(int frame, ArrayList<Rectangle2D> boxset, ArrayList<Polygon> polyset, ArrayList<FeaturePosition> feats) {
		
		ArrayList<Integer> assoc = new ArrayList<Integer>();
//		System.out.println("hi" + boxset.size());
		ArrayList<BoxFrame> boxes = getBoxFrames(frame, boxset, polyset, feats);
		int upto = streaks.size();
//		for(int i = 0; i < upto; i++) {
//			if(streaks.get(i).size() < 10 && streaks.get(i).get(streaks.get(i).size()-1).frame < frame-10) {
//				streaks.remove(i);
//				i--;
//				upto--;
//			}
//
//		}
		
		for(BoxFrame b : boxes) {
			if(streaks.isEmpty()) {
				ArrayList<BoxFrame> newStreak = new ArrayList<BoxFrame>();
				newStreak.add(b);
				streaks.add(newStreak);
				assoc.add(-1);
			}
			else {
				upto = streaks.size();
				int flag = 0;
				for(int i = 0; i < upto; i++) {
//					System.out.println(i);
					
//					if(frame == 199 && i == 17) {
//						System.out.println("Upto " + upto);
//						System.out.println(b);
//						System.out.println(streaks.get(i).get(streaks.get(i).size()-1));
//						System.out.println(overlap(streaks.get(i).get(streaks.get(i).size()-1), b));
//						System.out.println(streaks.get(i).size() >= 2 && (overlap(streaks.get(i).get(streaks.get(i).size()-2), b)));
//					}
					
					if(overlap(streaks.get(i).get(streaks.get(i).size()-1), b)) {
//						|| (streaks.get(i).size() >= 2 && (overlap(streaks.get(i).get(streaks.get(i).size()-2), b)) ) ) {
						streaks.get(i).add(b);
						flag = 1;
						assoc.add(i);
						break;
					}
				}
				if(flag == 0) {
					ArrayList<BoxFrame> newStreak = new ArrayList<BoxFrame>();
					newStreak.add(b);
					streaks.add(newStreak);
					assoc.add(-1);
				}
			}
		}
//		System.out.println("hi2" + boxset.size());
		
		return assoc;
		
	}
	
	public String toString() {
		String output = "";
		for(ArrayList<BoxFrame> a : streaks) {
			output += a.size() + ": ";
			for(BoxFrame b : a) {
				output += b.frame + " ";
			}
			output += "\n";
		}
		return output;
	}
} 

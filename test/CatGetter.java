package test;


/**
 * <p>
 * Quick utilities used in the program to name some files appropriately
 * </p>
 *
 * @author Chris Kymn
 */
public class CatGetter {

	public static String extract(String path) {
		int i = path.length()-1;
		while(i >= 0 && path.charAt(i) != '/') {
			i--;
		}
		if(i < 0) {
			System.err.println("Error: not absolute file path");
			return null;
		}
		else {
			i++;
			int j = i;
			while(j < path.length() && !Character.isDigit(path.charAt(j)) && path.charAt(j) != '.') j++;
			return path.substring(i, j);
		}
	}
	
	public static String stem(String path) {
		int i = path.length()-1;
		while(i >= 0 && path.charAt(i) != '/') {
			i--;
		}
		if(i < 0) {
			System.err.println("Error: not absolute file path");
			return null;
		}
		else {
			i++;
			int j = i;
			while(j < path.length() && path.charAt(j) != '.') j++;
			return path.substring(0, j) + "_arc";
		}		
	}
	
	public static String render(String path) {
		int i = path.length()-1;
		while(i >= 0 && path.charAt(i) != '/') {
			i--;
		}
		if(i < 0) {
			System.err.println("Error: not absolute file path");
			return null;
		}
		else {
			i++;
			int j = i;
			while(j < path.length() && path.charAt(j) != '.') j++;
			return path.substring(0, j) + "_rend";
		}		
	}
	
	public static String constructArc(String path) {
		int i = path.length()-1;
		while(i >= 0 && path.charAt(i) != '/') {
			i--;
		}
		if(i < 0) {
			System.err.println("Error: not absolute file path");
			return null;
		}
		else {
			i++;
			int j = i;
			while(j < path.length() && path.charAt(j) != '.') j++;
			return path.substring(0, j) + "_arc.zip";
		}	
	}
	
	public static String process(String path) {
		int i = path.length()-1;
		while(i >= 0 && path.charAt(i) != '/') {
			i--;
		}
		if(i < 0) {
			System.err.println("Error: not absolute file path");
			return null;
		}
		else {
			i++;
			int j = i;
			while(j < path.length() && path.charAt(j) != '.') j++;
			return path.substring(i, j) + "_pro.mp4";
		}	
	}
	
	public static void main(String[] args) {
		System.out.println("HIIIIIIIIIIIIIIIIIIII ");
		System.out.print(extract("hydrant29.avi"));
	}
}

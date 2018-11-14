package tracker;

/**
 * <p>
 * Quick string manipulation utilities used in the program to name some files appropriately
 * </p>
 *
 * @author Chris Kymn
 */
public class CatGetter {
	
	public static int stemstart(String path) {
		int i = path.length()-1;
		
		while(i >= 0 && (path.charAt(i) != '/') && (path.charAt(i) != '\\' ) ) {
			i--;
		}
		if(i < 0) {
			System.err.println("Error: not absolute file path");
			return -1;
		}
		else {
			return i+1;
		}
	}
	
	/**
	 * takes a file path and returns the end without an extension or numbers at the end of the name
	 * ex. "/home/Desktop/hydrant52.avi" returns "hydrant"
	 */
	public static String extract(String path) {
		int i = stemstart(path);
		int j = i;
		while(j < path.length() && !Character.isDigit(path.charAt(j)) && path.charAt(j) != '.') j++;
		return path.substring(i, j);
	}
	/**
	 * Helper method for other functions
	 * Returns the file path minus any extensions
	 */	
	public static int stemend(String path) {
		int i = stemstart(path);
		int j = i;
		while(j < path.length() && path.charAt(j) != '.') j++;
		return j;		
	}
	
	/**
	 * Used for file name in archiving tracker information
	 * takes a file path and appends _arc
	 * ex. "/home/Desktop/hydrant52.avi" returns "/home/Desktop/hydrant52_arc"
	 */
	public static String stem(String path) {
		return path.substring(0, stemend(path)) + "_arc";
	}
	
	/**
	 * Used for file name in archiving tracker information
	 * takes a file path and appends _arc
	 * ex. "/home/Desktop/hydrant52.avi" returns "/home/Desktop/hydrant52_samp"
	 */
	public static String samp(String path) {
		return path.substring(0, stemend(path)) + "_samp";
	}
	
	/**
	 * Used for file name in creating split up output video
	 * takes a file path and appends _arc
	 * ex. "/home/Desktop/hydrant52.avi" returns "/home/Desktop/hydrant"
	 */
	public static String stemOnly(String path) {
		return path.substring(stemstart(path), stemend(path));
	}	
	
	/**
	 * Used for saving render output
	 * takes a file path and appends _rend
	 * ex. "/home/Desktop/hydrant52.avi" returns "/home/Desktop/hydrant52_rend"
	 */
	public static String render(String path) {
		return path.substring(0, stemend(path)) + "_rend";
	}
	
	/**
	 * Used for finding a file name in archiving tracker information
	 * takes a file path and appends _arc.zip
	 * ex. "/home/Desktop/hydrant52.avi" returns "/home/Desktop/hydrant52_arc"
	 */
	public static String constructArc(String path) {
		System.out.println(stemstart(path));
		return path.substring(0, stemend(path)) + "_arc.zip";
	}

	/**
	 * Used for finding a file name in archiving tracker information
	 * takes a file path and appends _arc.zip
	 * ex. "/home/Desktop/hydrant52.avi" returns "hydrant52_pro.mp4"
	 */
	public static String process(String path) {
		return path.substring(stemstart(path), stemend(path)) + "_pro.mp4";
	}

}

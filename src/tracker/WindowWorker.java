package tracker;

import java.io.Serializable;
import java.util.ArrayList;

public class WindowWorker implements Serializable {
	
	ArrayList<WindowInfo> windows;
	
	public WindowWorker() {
		windows = new ArrayList<WindowInfo>();
	}
}

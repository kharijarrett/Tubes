package tracker;

import static org.bytedeco.javacpp.opencv_core.*;
import static org.bytedeco.javacpp.opencv_imgproc.*;
import static org.bytedeco.javacpp.opencv_video.*;
import static org.bytedeco.javacpp.opencv_highgui.*;
import static org.bytedeco.javacpp.opencv_contrib.*;

import java.awt.image.*;


/**
 * Performs some low level processing of an input image
 *
 * An implementation of:
 * Benoit A., Caplier A., Durette B., Herault, J., "USING HUMAN VISUAL SYSTEM MODELING FOR BIO-INSPIRED LOW LEVEL IMAGE PROCESSING", Elsevier, Computer Vision and Image Understanding 114 (2010), pp. 758-773, DOI: http://dx.doi.org/10.1016/j.cviu.2010.01.011
 *
 * @author Eli Bowen
 * @since May 15, 2015
 * 
 * @author Khari Jarrett (just stole from Eli mostly)
 *
 */


import org.bytedeco.javacpp.opencv_contrib.Retina;
import org.bytedeco.javacpp.opencv_core.IplImage;
import org.bytedeco.javacpp.opencv_core.Mat;
import org.bytedeco.javacpp.opencv_core.Size;

import common.video.VideoProcessorBase;
import common.video.VideoRetina;

public class VariableRetina extends VideoProcessorBase {
	
	private final boolean m_colorMode;
	private Retina m_retina = null;
	private BufferedImage m_magnoBI = null;
	private BufferedImage m_parvoBI = null;

	public VariableRetina(int videoWidth, int videoHeight, boolean colorMode) {
		super(videoWidth, videoHeight);
		m_colorMode = colorMode;
		m_retina = new Retina(new Size(width, height), colorMode);
		
		m_retina.setupOPLandIPLParvoChannel(true, true, 0.7f, 0.5f, 0.53f, 0f, 1f, 10f, 0.7f);
		m_retina.setupIPLMagnoChannel(true, 0f, 0f, 7f, 1.2f, 0.95f, 0f, 7f);
		// TODO Auto-generated constructor stub
	}
	
	public void ProcessFrame (BufferedImage currFrame) {
		if (currFrame.getWidth() != width) {
			throw new IllegalArgumentException("VideoRetina.ProcessFrame(): passed in an image with width [" + currFrame.getWidth() + "] when a width of [" + width + "] was required!");
		}
		if (currFrame.getHeight() != height) {
			throw new IllegalArgumentException("VideoRetina.ProcessFrame(): passed in an image with height [" + currFrame.getHeight() + "] when a height of [" + height + "] was required!");
		}
		IplImage javaCVImg = IplImage.createFrom(currFrame);
		m_retina.run(new Mat(javaCVImg));
		Mat outputMagno = new Mat(javaCVImg);
		m_retina.getMagno(outputMagno);
		Mat outputParvo = new Mat(javaCVImg);
		m_retina.getParvo(outputParvo);
		
		m_magnoBI = outputMagno.getBufferedImage();
		m_parvoBI = outputParvo.getBufferedImage();
		super.FrameComplete();
	}
	
	public BufferedImage getMagno () {
		return m_magnoBI;
	}
	
	
	public BufferedImage getParvo () {
		return m_parvoBI;
	}
	
	
	public boolean getIsColor () {
		return m_colorMode;
	}
	
	

}

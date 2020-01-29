package test;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;

import boofcv.io.MediaManager;
import boofcv.io.UtilIO;
import boofcv.io.image.SimpleImageSequence;
import boofcv.io.wrapper.DefaultMediaManager;
import boofcv.struct.image.GrayF32;
import boofcv.struct.image.ImageGray;
import boofcv.struct.image.ImageType;
import common.math.Mat;
import common.utils.DataUtils;
import common.utils.ImageUtils;
import common.video.VideoFrameWriter;
import common.video.VideoRetina;

@SuppressWarnings("rawtypes")
public class RGBModel<T extends ImageGray> {
	
	Class<T> imageType;

	public RGBModel(Class<T> imageType) {
		this.imageType = imageType;
	}
	
	/**
     * Converts the R, G, B image to a black vs white, red vs green,
yellow vs blue image
     *
     * @param image    width x height x RGB
     * @return 3 x width x height
     */
    @SuppressWarnings("unused")
	private static ArrayList<float[][]> RawOpponent (byte[][][] image) {
        int width = image.length;
        int height = image[0].length;
        ArrayList<float[][]> retVal = new ArrayList<float[][]>(3);
        retVal.add(new float[width][height]);
        retVal.add(new float[width][height]);
        retVal.add(new float[width][height]);
        double luminance, white, red, green, blue, yellow;
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                red = (double)DataUtils.Unsigned2Int(image[i][j][0]);
                green = (double)DataUtils.Unsigned2Int(image[i][j][1]);
                blue = (double)DataUtils.Unsigned2Int(image[i][j][2]);
                
                //bytes are in the range (-128,127)
                //red = (image[i][j][0] + 128.0) / 255.0;
                //green = (image[i][j][1] + 128.0) / 255.0;
                //blue = (image[i][j][2] + 128.0) / 255.0;
                
                luminance = 0.3 * red + 0.59 * green + 0.11 * blue; 
                //Y = 0.3R + 0.59G + 0.11B //http://gimp-savvy.com/BOOK/index.html?node54.html
                white = Math.max(Math.max(red, green), blue);
                if (white == 0.0) {
                    yellow = 1.0; //handle NaN
                } else {
                    yellow = (white - blue) / white; //Y = (255-B-K) /(255-K)
                }
                retVal.get(0)[i][j] = (float)luminance; //whitevsblack
                retVal.get(1)[i][j] = (float)((red - green + 255.0) /2.0); //redvsgreen
                retVal.get(2)[i][j] = (float)((yellow - blue + 255.0) / 2.0); //yellowvsblue
            }
        }
        return retVal;
    }
	
	public SimpleImageSequence processVideo(String vidPath) {

		Class imageType = GrayF32.class;
		MediaManager media = DefaultMediaManager.INSTANCE;
		SimpleImageSequence sequence = media.openVideo(UtilIO.pathExample(vidPath), ImageType.single(imageType)); 
		sequence.setLoop(false);

		return sequence;
	}
	
	public static BufferedImage maxContrast(BufferedImage input) {
		byte[][][] image = ImageUtils.BufferedImage2Color(input);
		ArrayList<float[][]> retVal = RawOpponent(image);
		
		byte grayscale[][] = ImageUtils.BufferedImage2Grayscale(input);
		int gray[][] = Mat.Unsigned2Int(grayscale);
		for(int i = 0; i < gray.length; i++) {
			for(int j = 0; j < gray[i].length; j++) {
				gray[i][j] = (int) Float.max(Float.max(Float.max(Float.max(retVal.get(0)[i][j], retVal.get(1)[i][j]), retVal.get(2)[i][j]), 255-retVal.get(2)[i][j]), 255-retVal.get(1)[i][j]);
			}
		}
		
		grayscale = Mat.Int2Unsigned(gray);
		
		return ImageUtils.Grayscale2BufferedImage(grayscale, 255);
	}
	
	@SuppressWarnings({ "unchecked", "unused" })
	public void process(String stemLoc, String vidLoc) {
		
		SimpleImageSequence<T> sequence = processVideo(vidLoc);

		int height = sequence.getNextHeight();
		int width = sequence.getNextWidth(); 
		
		VideoFrameWriter magno = new VideoFrameWriter(new File(stemLoc + "_magno.mp4"), width, height, 30);
		VideoFrameWriter parvo = new VideoFrameWriter(new File(stemLoc + "_parvo.mp4"), width, height, 30);
		
		magno.OpenFile();
		parvo.OpenFile();
		
		VideoRetina retina = new VideoRetina(width, height, true);
		
		T frame;
		int frames = 0;
		while(sequence.hasNext()) {
			frame = sequence.next();
			BufferedImage currFrame = sequence.getGuiImage();
			
			currFrame = maxContrast(currFrame);
//			
//			byte[][][] image = ImageUtils.BufferedImage2Color(currFrame);
//			ArrayList<float[][]> retVal = RawOpponent(image);
//			
//			byte grayscale[][] = ImageUtils.BufferedImage2Grayscale(currFrame);
//			int gray[][] = Mat.Unsigned2Int(grayscale);
//			for(int i = 0; i < gray.length; i++) {
//				for(int j = 0; j < gray[i].length; j++) {
//					gray[i][j] = (int) Float.max(Float.max(Float.max(Float.max(retVal.get(0)[i][j], retVal.get(1)[i][j]), retVal.get(2)[i][j]), 255-retVal.get(2)[i][j]), 255-retVal.get(1)[i][j]);
//				}
//			}
//			
//			grayscale = Mat.Int2Unsigned(gray);
//			
//			currFrame = ImageUtils.Grayscale2BufferedImage(grayscale, 255);
			
//			BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);  
//			Graphics g = image.getGraphics();  
//			g.drawImage(currFrame, 0, 0, null);  
//			g.dispose();
//			retina.ProcessFrame(image);
			
			retina.ProcessFrame(currFrame);
			magno.ProcessFrame(retina.getMagno());
			parvo.ProcessFrame(retina.getParvo());
			System.out.println("Finished processing frame " + frames);
			frames++;
		}
		
		magno.Close();
		parvo.Close();
		
	}
	
	@SuppressWarnings("unchecked")
	public static void main(String[] args) {
		RGBModel r = new RGBModel(GrayF32.class);
		r.process("pac_r", "/home/grangerlab/Desktop/highway/small.avi");
		
	}
}

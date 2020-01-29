package tracker;

import java.awt.image.BufferedImage;
import java.util.ArrayList;

import common.math.Mat;
import common.utils.DataUtils;
import common.utils.ImageUtils;

public class RGBModel {

	/**
     * Converts the R, G, B image to a black vs white, red vs green,
yellow vs blue image
     *
     * @param image    width x height x RGB
     * @return 3 x width x height
     */
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
    /**
     * 
     * @param input - a BufferedImage to process
     * @return a grayscale BufferedImage with the max {luminance, red, green, blue, yellow} value
     */
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

}

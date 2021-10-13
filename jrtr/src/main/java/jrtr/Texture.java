package jrtr;

import java.io.IOException;
import java.awt.image.BufferedImage;

/**
 * Declares the functionality to manage textures.
 */
public interface Texture {

	public void load(String fileName) throws IOException;
}

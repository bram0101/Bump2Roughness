/*
BSD 3-Clause License

Copyright (c) 2020, Bram Stout
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

3. Neither the name of the copyright holder nor the names of its
   contributors may be used to endorse or promote products derived from
   this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/


package nl.bramstout.bump2roughness;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

import nl.bramstout.bump2roughness.ImageBuffer.RGB;
import nl.bramstout.bump2roughness.Threading.Task;

public class ImageContainer {

	int width;
	int height;
	int mipmapLevels;
	ImageBuffer[] buffers; // One buffer for each mip map level
	boolean read = false;
	File imgFile = null;

	public ImageContainer(int width, int height) {
		this.width = width;
		this.height = height;
		mipmapLevels = 1;
		buffers = new ImageBuffer[1];
		buffers[0] = new ImageBuffer(width, height);
	}

	public ImageContainer(int width, int height, int mipmapLevels) {
		this.width = width;
		this.height = height;
		this.mipmapLevels = mipmapLevels;
		buffers = new ImageBuffer[mipmapLevels];

		for (int i = 0; i < mipmapLevels; ++i) {
			int scaleFactor = (int) Math.pow(2.0, (double) i);
			buffers[i] = new ImageBuffer(width / scaleFactor, height / scaleFactor);
		}
	}

	public ImageContainer(File imgFile) throws IOException {
		this.imgFile = imgFile;
		
		BufferedImage img = ImageIO.read(imgFile);
		width = img.getWidth();
		height = img.getHeight();

		// We want every image to be a square power of 2 image.
		// So, calculate the closest power of 2 resolution.
		
		int res = Math.max(width, height);

		double resLog = Math.floor(Math.log10(res) / Math.log10(2.0));
		double outRes = (double) Math.pow(2.0, resLog);
		
		width = (int) outRes;
		height = (int) outRes;
		mipmapLevels = (int) resLog;
		
		//int smallestSize = Math.min(width, height);
		//mipmapLevels = (int) Math.floor(Math.log10(smallestSize) / Math.log10(2.0));

		buffers = new ImageBuffer[mipmapLevels];
	}

	private RGB sampleBufferedImage(BufferedImage img, int i, int j) {
		i = i % img.getWidth();
		if(i < 0)
			i += img.getWidth();
		j = j % img.getHeight();
		if(j < 0)
			j += img.getHeight();
		
		Color col = new Color(img.getRGB(i, j));
		RGB rgb = new RGB(((float) col.getRed()) / 255.0f, ((float) col.getGreen()) / 255.0f, ((float) col.getBlue()) / 255.0f);
		
		if(img.getType() == BufferedImage.TYPE_BYTE_GRAY || img.getType() == BufferedImage.TYPE_USHORT_GRAY) {
			// Single channel greyscale images apparently get a gamma applied on them, so undo it.
			
			rgb.r = (float) Math.pow(rgb.r, 2.2);
			rgb.g = (float) Math.pow(rgb.g, 2.2);
			rgb.b = (float) Math.pow(rgb.b, 2.2);
		}
		
		return rgb;
	}
	
	private float lerp(float a, float b, double t) {
		return (float) (a * (1.0 - t) + b * t);
	}
	
	private RGB lerp(RGB a, RGB b, double t) {
		return new RGB(lerp(a.r, b.r, t), lerp(a.g, b.g, t), lerp(a.b, b.b, t));
	}
	
	private RGB bilinear(BufferedImage img, double u, double v) {
		double i = u * ((double) img.getWidth());
		double j = v * ((double) img.getHeight());
		
		double i0 = Math.floor(i);
		double i1 = Math.ceil(i);
		double j0 = Math.floor(j);
		double j1 = Math.ceil(j);
		
		double it = i - i0;
		double jt = j - j0;
		
		RGB c00 = sampleBufferedImage(img, (int) i0, (int) j0);
		RGB c10 = sampleBufferedImage(img, (int) i1, (int) j0);
		RGB c01 = sampleBufferedImage(img, (int) i0, (int) j1);
		RGB c11 = sampleBufferedImage(img, (int) i1, (int) j1);
		
		return lerp(lerp(c00, c10, it), lerp(c01, c11, it), jt);
	}
	
	public void read() {
		if (read) return;

		try {

			System.out.println("Reading file: " + imgFile);

			BufferedImage img = ImageIO.read(imgFile);

			System.out.println("Resolution: " + width + "x" + height + "  MipMap Levels: " + mipmapLevels);

			buffers[0] = new ImageBuffer(width, height);
			
			if(img.getWidth() != width || img.getHeight() != height) {
				// The resolution doesn't match, so we use bilinear interpolation to fill in the pixels.
				for (int i = 0; i < width; ++i) {
					for (int j = 0; j < height; ++j) {
						double u = ((double) i) / ((double) width);
						double v = ((double) j) / ((double) height);
						buffers[0].setPixel(i, j, bilinear(img, u, v));
					}
				}
			}else {
				for (int i = 0; i < width; ++i) {
					for (int j = 0; j < height; ++j) {
						buffers[0].setPixel(i, j, sampleBufferedImage(img, i, j));
					}
				}
			}

			for (int i = 1; i < mipmapLevels; ++i) {
				System.out.println("Generating MipMap level " + i);

				int scaleFactor = (int) Math.pow(2.0, (double) i);
				buffers[i] = new ImageBuffer(width / scaleFactor, height / scaleFactor);
				for (int j = 0; j < buffers[i].width; ++j) {
					for (int k = 0; k < buffers[i].height; ++k) {
						RGB c00 = buffers[i - 1].getPixel(j * 2, k * 2);
						RGB c10 = buffers[i - 1].getPixel(j * 2 + 1, k * 2);
						RGB c01 = buffers[i - 1].getPixel(j * 2, k * 2 + 1);
						RGB c11 = buffers[i - 1].getPixel(j * 2 + 1, k * 2 + 1);
						RGB c = new RGB((c00.r + c10.r + c01.r + c11.r) / 4.0f, (c00.g + c10.g + c01.g + c11.g) / 4.0f, (c00.b + c10.b + c01.b + c11.b) / 4.0f);
						buffers[i].setPixel(j, k, c);
					}
				}
			}
			
			read = true;
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}
	
	public void free() {
		if(read) {
			for(int i = 0; i < buffers.length; ++i) {
				buffers[i] = null;
				// Java's garbage collection will see this and actually
				// free the memory.
			}
			read = false;
			
			// Tell the JVM that it can free memory if it wants to.
			System.gc();
		}
	}

	public String[] write(File filename) throws IOException {
		System.out.println("Saving to file " + filename.toString());

		if (mipmapLevels == 1) {
			BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
			for (int i = 0; i < width; ++i) {
				for (int j = 0; j < height; ++j) {
					RGB rgb = buffers[0].getPixel(i, j);
					Color col = new Color(rgb.r, rgb.g, rgb.b);
					img.setRGB(i, j, col.getRGB());
				}
			}
			String extension = "";

			int i = filename.getName().lastIndexOf('.');
			if (i > 0) {
				extension = filename.getName().substring(i + 1);
			}
			ImageIO.write(img, extension, filename);
			
			return new String[] { filename.getPath() };
		} else {
			String extension = "";

			int index = filename.getName().lastIndexOf('.');
			if (index > 0) {
				extension = filename.getName().substring(index + 1);
			}

			final String ext = extension;
			
			String path = filename.getAbsolutePath();
			final String basepath = path.substring(0, path.length() - extension.length() - 1);

			/*for (int level = 0; level < mipmapLevels; ++level) {
				BufferedImage img = new BufferedImage(buffers[level].width, buffers[level].height, BufferedImage.TYPE_INT_ARGB);
				for (int i = 0; i < buffers[level].width; ++i) {
					for (int j = 0; j < buffers[level].height; ++j) {
						RGB rgb = buffers[level].getPixel(i, j);
						Color col = new Color(rgb.r, rgb.g, rgb.b);
						img.setRGB(i, j, col.getRGB());
					}
				}
				String path = basepath + "_" + level + "." + extension;

				System.out.println("Mip Map File: " + path);

				ImageIO.write(img, extension, new File(path));
			}*/
			Threading.runParallel(mipmapLevels, new Task() {

				@Override
				public void run(int level) {
					BufferedImage img = new BufferedImage(buffers[level].width, buffers[level].height, BufferedImage.TYPE_INT_ARGB);
					for (int i = 0; i < buffers[level].width; ++i) {
						for (int j = 0; j < buffers[level].height; ++j) {
							RGB rgb = buffers[level].getPixel(i, j);
							Color col = new Color(rgb.r, rgb.g, rgb.b);
							img.setRGB(i, j, col.getRGB());
						}
					}
					String path = basepath + "_" + level + "." + ext;

					System.out.println("Mip Map File: " + path);

					try {
						ImageIO.write(img, ext, new File(path));
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				
			});
			
			String[] filenames = new String[mipmapLevels];
			for(int level = 0; level < mipmapLevels; ++level) {
				filenames[level] = basepath + "_" + level + "." + extension;
			}
			
			return filenames;
		}
	}

}

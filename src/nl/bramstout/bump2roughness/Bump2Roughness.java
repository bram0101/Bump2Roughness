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

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;

import nl.bramstout.bump2roughness.ImageBuffer.RGB;
import nl.bramstout.bump2roughness.Threading.Task;

public class Bump2Roughness {

	public static interface ProgressCallback {

		/**
		 * Called every so often with the current progress and status. This is used for when you want to give the user a dialog with a progress bar.
		 * 
		 * @param progress
		 * @param status
		 */
		public void onProgress(double progress, String status);

		/**
		 * Add the value to the current progress
		 * 
		 * @param progress
		 */
		public void addProgress(double additionalProgress);

	}

	public static enum MAPTYPE {
		DISPLACEMENT, BUMP, NORMAL
	}

	public static enum RENDERER {
		ARNOLD, RENDERMAN
	}

	public static enum OUTPUTMODE {
		TEXTURE, INDIVIDUAL_LEVELS
	}

	public static class Settings {
		// The base roughness image to add this to
		ImageContainer roughnessImg = null;
		// If roughnessImg is null, then it uses this float value as the base.
		float roughnessValue = 0.0f;

		// Each of the input image to calculate the roughness for
		ArrayList<ImageContainer> imgs = new ArrayList<ImageContainer>();
		// The normalisation factor for each image
		ArrayList<Float> normalisationFactors = new ArrayList<Float>();
		// Whether or not the image is a bump/displacement map or a normal map
		ArrayList<MAPTYPE> mapType = new ArrayList<MAPTYPE>();

		// The size in scene units of the UV space
		float unitSize = 1.0f;
		// The renderer for which to create a roughness texture
		RENDERER renderer = RENDERER.ARNOLD;

		// The path of the texture file to create
		String outputPath = "";
		// What should this algorithm output
		OUTPUTMODE outputMode = OUTPUTMODE.TEXTURE;

		String maketxPath = "";

		ProgressCallback callback;
	}

	// A mip mapped image to store the output in
	ImageContainer outputImg;

	Settings settings;

	public Bump2Roughness(Settings settings) throws Exception {
		this.settings = settings;

		// Figure out the main resolution of our roughness texture.
		// If we have a base roughness image, then use that.
		// Otherwise, get the resolution from the first given image.
		int imgWidth = 0;
		int imgHeight = 0;
		if (settings.roughnessImg != null) {
			imgWidth = settings.roughnessImg.width;
			imgHeight = settings.roughnessImg.height;
		} else {
			imgWidth = settings.imgs.get(0).width;
			imgHeight = settings.imgs.get(0).height;
		}

		// Make sure that every texture is the same resolution.
		for (int i = 0; i < settings.imgs.size(); ++i) {
			if (settings.imgs.get(i).width != imgWidth || settings.imgs.get(i).height != imgHeight) { throw new Exception(
					"Img " + i + " is not the same resolution as either the roughness image or img 0!"); }
		}

		outputImg = new ImageContainer((int) imgWidth, (int) imgHeight, settings.imgs.get(0).mipmapLevels);
	}

	/**
	 * Returns the slope at coordinates (x, y) for the given image at index with the given mipmap level.
	 * 
	 * This function returns the slope for the x and y separately. This is because we want the difference between slopes and slopes can also be negative.
	 * However, to combine both slopes into one slope you'd do sqrt(x*x + y*y), which always gives you a positive slope. This generally makes it less accurate,
	 * so we have to return the two slopes separately.
	 * 
	 * @param x
	 * @param y
	 * @param index
	 * @param level
	 * @return
	 */
	public float[] getSlope(int x, int y, int index, int level) {
		if (settings.mapType.get(index) == MAPTYPE.NORMAL) {
			// For normal maps, we simply calculate the slope of the vector compared to up (0,0,1)
			// and there is no need to create a derivative.

			// Get the xyz values and remap them back to the correct values.
			RGB xyz = settings.imgs.get(index).buffers[level].getPixel(x, y);
			xyz.r = xyz.r * 2.0f - 1.0f;
			xyz.g = xyz.g * 2.0f - 1.0f;
			xyz.b = xyz.b * 2.0f - 1.0f;

			// Get the slopes.
			float slopeX = xyz.r / xyz.b;
			float slopeY = xyz.g / xyz.b;

			// Add in the strength values given by the user.
			slopeX *= settings.normalisationFactors.get(index);
			slopeY *= settings.normalisationFactors.get(index);

			if (settings.renderer == RENDERER.ARNOLD) {
				// From my testing with Arnold, normal maps have a maximum slope of 1.0.
				// Most likely this is to make things look nicer, but it does mean we
				// have to clamp the slope here.
				// This isn't the case with Renderman
				slopeX = Math.max(-1.0f, Math.min(1.0f, slopeX));
				slopeY = Math.max(-1.0f, Math.min(1.0f, slopeY));
			}

			return new float[] { slopeX, slopeY };
		} else {
			// Bump of Displacement maps:

			float duv;
			// We assume that if an image does not have a square aspect ratio, through UV mapping
			// the texels will still end up square which means du = dv and only one value is needed.
			// Du and Dv is the size of a single texel in world coordinates.
			duv = settings.unitSize / ((float) settings.imgs.get(index).buffers[level].width);

			// Get Dx and Dy
			float dx = settings.imgs.get(index).buffers[level].getPixel(x + 1, y).r - settings.imgs.get(index).buffers[level].getPixel(x, y).r;
			float dy = settings.imgs.get(index).buffers[level].getPixel(x, y + 1).r - settings.imgs.get(index).buffers[level].getPixel(x, y).r;

			// Multiply by the strength of the bump or displacement map
			dx *= settings.normalisationFactors.get(index);
			dy *= settings.normalisationFactors.get(index);

			// Calculate the slope
			float slopeX = dx / duv;
			float slopeY = dy / duv;

			if (settings.mapType.get(index) == MAPTYPE.BUMP) {
				if (settings.renderer == RENDERER.RENDERMAN) {
					// Compared to Arnold, Renderman seems to make the
					// bump maps roughly 20 times less strong.
					slopeX /= 20.0f;
					slopeY /= 20.0f;
				} else {
					// From my testing with Arnold, bump maps have a maximum slope of 1.0.
					// Most likely this is to make things look nicer, but it does mean we
					// have to clamp the slope here.
					slopeX = Math.max(-1.0f, Math.min(1.0f, slopeX));
					slopeY = Math.max(-1.0f, Math.min(1.0f, slopeY));
				}
			}

			return new float[] { slopeX, slopeY };
		}
	}

	public void calculateRoughnessForImageForLevel(int index, int level) {
		System.out.println("Calculating img " + index + " level " + level);

		if (level == 0) return; // The roughness we calculate is for the lost detail in the maps.
								// At level 0 we haven't lost any details
		int width = outputImg.buffers[level].width;
		int height = outputImg.buffers[level].height;

		int scaleFactor = (int) Math.pow(2.0, (double) level);

		for (int i = 0; i < width; ++i) {
			for (int j = 0; j < height; ++j) {
				// Get the slope at the current mip map level
				float[] meanSlope = getSlope(i, j, index, level);

				float deviation = 0.0f;
				float count = 0.0f;
				float[] sampleSlope = null;
				// For each texel that is in the current mip mapped texel, we calculate the difference between
				// its slope and meanSlope. Those differences are then averages using RMS (root mean squared).
				// Basically, RMS = sqrt(a*a + b*b + c*c + d*d + ...)
				for (int ii = i * scaleFactor; ii < (i + 1) * scaleFactor; ++ii) {
					for (int jj = j * scaleFactor; jj < (j + 1) * scaleFactor; ++jj) {
						// Get the slope
						sampleSlope = getSlope(ii, jj, index, 0);
						// Calculate the difference between this slope of meanSlope
						sampleSlope[0] = sampleSlope[0] - meanSlope[0];
						sampleSlope[1] = sampleSlope[1] - meanSlope[1];
						// Square it and add it to deviation
						deviation += sampleSlope[0] * sampleSlope[0] + sampleSlope[1] * sampleSlope[1];
						// Also increase count which we use to average the deviations.
						count += 1.0f;
					}
				}

				// Normalise the averaging using count
				deviation /= count;
				// The roughness parameter in shaders are 2x the deviation, so multiply by 2.0
				deviation *= 2.0f;

				// In reality deviation should be square rooted right here. However, when adding multiple roughness values
				// together, you do it like so roughnessNew = sqrt(roughnessA*roughnessA + roughnessB+roughnessB)
				// So, it's easier to hold on with the square root and keep everything as a squared value, so that we can
				// simply add them together and only at the end get the square root of it.

				// Add the deviation to the buffer and store the new value.
				RGB val = outputImg.buffers[level].getPixel(i, j);
				val.r += deviation;
				val.g += deviation;
				val.b += deviation;

				outputImg.buffers[level].setPixel(i, j, val);
			}
		}
	}

	public void calculateRoughnessForImage(int index) {
		settings.callback.onProgress((((float) index) / ((float) settings.imgs.size())) * 0.6 + 0.05, "Reading img " + index);

		// Read the current image into memory.
		settings.imgs.get(index).read();

		settings.callback.onProgress((((float) index) / ((float) settings.imgs.size())) * 0.6 + 0.05, "Calculating roughness for img " + index);

		// for (int i = 0; i < imgs.get(index).mipmapLevels; ++i) {
		// calculateRoughnessForImageForLevel(index, i);
		// }

		// Calculate the roughness for each mip map level.
		Threading.runParallel(settings.imgs.get(index).mipmapLevels, new Task() {

			@Override
			public void run(int i) {
				calculateRoughnessForImageForLevel(index, i);

				float progressLevels = 1.0f / ((float) (settings.imgs.get(index).mipmapLevels * settings.imgs.size()));
				settings.callback.addProgress(progressLevels * 0.6);
			}

		});
		// Free the memory used for this image.
		// Doing a read-free thing here, means that we don't need as much memory
		// as when we read all of the textures into memory at the beginning.
		settings.imgs.get(index).free();
	}

	/**
	 * Put the base roughness value or texture into the output image.
	 */
	public void fillOutputWithBaseRoughness() {
		if (settings.roughnessImg == null) {
			// It's a single value, so just go through each mip map level's buffer and set it to the roughness value.
			// We square it, since you need to square roughness values first before you can add them together.
			for (int i = 0; i < outputImg.mipmapLevels; ++i) {
				Arrays.fill(outputImg.buffers[i].data, settings.roughnessValue * settings.roughnessValue);
			}
		} else {
			settings.roughnessImg.read();
			
			// We have a roughness image to use as a base, so just copy the values over.
			for (int level = 0; level < outputImg.mipmapLevels; ++level) {
				for (int i = 0; i < outputImg.buffers[level].data.length; ++i) {
					float val = settings.roughnessImg.buffers[level].data[i];
					val = val * val;
					outputImg.buffers[level].data[i] = val;
				}
			}
			
			settings.roughnessImg.free();
		}
	}

	/**
	 * Goes through the values and square root it. Additionally, clip it to (0.0, 1.0)
	 * 
	 * @param level
	 */
	public void normaliseOutput(int level) {
		for (int i = 0; i < outputImg.buffers[level].data.length; ++i) {
			float val = outputImg.buffers[level].data[i];
			val = (float) Math.sqrt(val);
			val = Math.min(Math.max(val, 0.0f), 1.0f);
			outputImg.buffers[level].data[i] = val;
		}
	}

	public void maketxConstantColorFix() {
		// If we use a solid roughness value as the base instead of a texture,
		// then maketx is going to optimise the image and just put in a constant
		// colour which means that this whole 'putting the roughness from displacement
		// maps in the mip map of a texture' wouldn't work.
		//
		// To fix this, we simply need to make the highest level of the not a constant colour.
		// So, we mix it with the roughness from level 1.

		// If we have an image as the base roughness, there is no need to do this.
		if (settings.roughnessImg != null) return;

		for (int i = 0; i < outputImg.buffers[0].width; ++i) {
			for (int j = 0; j < outputImg.buffers[0].height; ++j) {
				float val = outputImg.buffers[0].getPixel(i, j).r;
				float l1Val = outputImg.buffers[1].getPixel(i / 2, j / 2).r;
				l1Val = (float) Math.pow(l1Val, 0.333); // Gamma the l1Val to bring up the small changes in bump
				// Mix between the two with the original value having a 97.5% weight
				float nVal = val * 0.975f + l1Val * 0.025f;
				outputImg.buffers[0].setPixel(i, j, new RGB(nVal, nVal, nVal));
			}
		}
	}

	public void calculateRoughness() {
		settings.callback.onProgress(0.0, "Loading base roughness");
		fillOutputWithBaseRoughness();

		settings.callback.onProgress(0.05, "Calculating roughnesses");
		for (int i = 0; i < settings.imgs.size(); ++i) {
			calculateRoughnessForImage(i);
		}

		settings.callback.onProgress(0.65, "Cleaning up roughness textures");
		for (int i = 0; i < outputImg.mipmapLevels; ++i)
			normaliseOutput(i);

		maketxConstantColorFix();
	}

	public void writeOutput() throws Exception {
		settings.callback.onProgress(0.70, "Writing texture");

		// Writes out each mip map level to it's own png file.
		String[] filenames = outputImg.write(new File(settings.outputPath + ".png"));

		if (settings.outputMode == OUTPUTMODE.TEXTURE) {
			settings.callback.onProgress(0.90, "Generating TX/TEX file");

			// If we want to create a tx file, then generate the appropriate command and call
			// either maketx or txmake. After that we can delete the individual mip map level images
			// writen out by outputImg.write()

			System.out.println("Creating tx file");

			String command = "";
			if (new File(settings.maketxPath).getName().contains("txmake")) {
				// We are talking about the Renderman txmake and not the Arnold/OIIO maketx.

				command = settings.maketxPath + " -verbose -mode periodic -byte";

				command += " -usermipmap ";

				for (int level = 0; level < outputImg.mipmapLevels; ++level) {
					command += " " + filenames[level];
				}

				command += " " + settings.outputPath;
			} else {
				// We are talking about the Arnold/OIIO maketx
				// TODO: For some reason, Arnold's maketx doesn't work? It crashes for me. So, try to fix that.

				command = settings.maketxPath + " -v -wrap periodic";
				if (settings.renderer == RENDERER.ARNOLD) command += " --oiio";
				if (settings.renderer == RENDERER.RENDERMAN) command += " --prman";

				for (int level = 1; level < outputImg.mipmapLevels; ++level) {
					command += " --mipimage " + filenames[level];
				}

				command += " " + filenames[0]; // Add in the first level as the base image

				command += " -o " + settings.outputPath;
			}

			System.out.println(command);

			// Run the command and also print out anything it gets from the process.
			Process process = Runtime.getRuntime().exec(command);
			int exitCode = process.waitFor();
			new BufferedReader(new InputStreamReader(process.getInputStream())).lines().forEach(System.out::println);
			new BufferedReader(new InputStreamReader(process.getErrorStream())).lines().forEach(System.out::println);
			if (exitCode != 0) System.out.println("ERROR: maketx exitted with code " + exitCode);

			// Delete the temporary files.
			for (String fname : filenames) {
				new File(fname).delete();
			}
			
			if(exitCode != 0)
				throw new Exception("maketx/txmake exitted with code " + exitCode);
		}

		System.out.println("Done writing output");

		settings.callback.onProgress(1.0, "Done writing output");
	}

}

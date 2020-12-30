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

import java.util.Arrays;

public class ImageBuffer {
	
	int width;
	int height;
	float[] data;
	
	public ImageBuffer(int width, int height) {
		this.width = width;
		this.height = height;
		this.data = new float[width * height * 3];
		Arrays.fill(data, 0.0f);
	}
	
	public ImageBuffer(int width, int height, float[] data) {
		this.width = width;
		this.height = height;
		this.data = data;
	}
	
	public RGB getPixel(int x, int y) {
		x = x >= 0 ? x % width : x % width + width;
		y = y >= 0 ? y % height : y % height + height;
		
		int index = (y * width + x) * 3;
		
		return new RGB(data[index], data[index + 1], data[index + 2]);
	}
	
	public void setPixel(int x, int y, RGB value) {
		x = x >= 0 ? x % width : x % width + width;
		y = y >= 0 ? y % height : y % height + height;
		
		int index = (y * width + x) * 3;
		
		data[index] = value.r;
		data[index + 1] = value.g;
		data[index + 2] = value.b;
	}
	
	public static class RGB{
		public float r;
		public float g;
		public float b;
		
		public RGB() {
			r = g = b = 0.0f;
		}
		
		public RGB(float v) {
			r = g = b = v;
		}
		
		public RGB(float r, float g, float b) {
			this.r = r;
			this.g = g;
			this.b = b;
		}
		
		public RGB(RGB v) {
			this.r = v.r;
			this.g = v.g;
			this.b = v.b;
		}
	}
	
}

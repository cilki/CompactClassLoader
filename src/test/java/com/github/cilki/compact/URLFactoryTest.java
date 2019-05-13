/******************************************************************************
 *                                                                            *
 *  Copyright 2019 Tyler Cook (https://github.com/cilki)                      *
 *                                                                            *
 *  Licensed under the Apache License, Version 2.0 (the "License");           *
 *  you may not use this file except in compliance with the License.          *
 *  You may obtain a copy of the License at                                   *
 *                                                                            *
 *      http://www.apache.org/licenses/LICENSE-2.0                            *
 *                                                                            *
 *  Unless required by applicable law or agreed to in writing, software       *
 *  distributed under the License is distributed on an "AS IS" BASIS,         *
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  *
 *  See the License for the specific language governing permissions and       *
 *  limitations under the License.                                            *
 *                                                                            *
 *****************************************************************************/
package com.github.cilki.compact;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Paths;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class URLFactoryTest {

	static final byte[] library_1 = new byte[] { 80, 75, 3, 4, 20, 0, 8, 8, 8, 0, 86, 2, -127, 78, 0, 0, 0, 0, 0, 0, 0,
			0, 0, 0, 0, 0, 20, 0, 4, 0, 77, 69, 84, 65, 45, 73, 78, 70, 47, 77, 65, 78, 73, 70, 69, 83, 84, 46, 77, 70,
			-2, -54, 0, 0, -13, 77, -52, -53, 76, 75, 45, 46, -47, 13, 75, 45, 42, -50, -52, -49, -77, 82, 48, -44, 51,
			-32, -27, -30, -27, 2, 0, 80, 75, 7, 8, -78, 127, 2, -18, 27, 0, 0, 0, 25, 0, 0, 0, 80, 75, 3, 4, 20, 0, 8,
			8, 8, 0, 54, 2, -127, 78, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 29, 0, 0, 0, 99, 111, 109, 47, 101, 120, 97,
			109, 112, 108, 101, 47, 67, 108, 97, 115, 115, 49, 50, 56, 51, 57, 52, 46, 99, 108, 97, 115, 115, 117, 80,
			77, 75, -61, 64, 20, -100, 77, -46, -92, 77, -85, -115, -75, 32, 30, -59, 75, -21, -63, -48, 38, -110, -106,
			-118, -105, -126, -89, -96, -121, 74, -17, -101, -72, -44, 45, -101, 68, -110, -76, -8, -77, -12, 36, 120,
			-16, 7, -8, -93, -60, -105, 88, 80, 4, 23, -10, -51, -5, -104, 121, 59, -20, -57, -25, -37, 59, -128, 0,
			125, 11, 26, -61, 81, -100, 37, -82, 120, -30, -55, -93, 18, -18, 92, -15, -94, 24, -115, 39, -34, -44, -73,
			96, 48, 56, 107, -66, -27, -82, -30, -23, -54, -67, -115, -42, 34, 46, 25, -52, 75, -103, -54, -14, -118,
			65, 31, 12, -105, 12, -58, 60, -69, 23, 54, 116, -76, 58, 104, -64, 100, -24, -122, 50, 21, 55, -101, 36,
			18, -7, 29, -113, -108, 96, -24, -123, 89, -52, -43, -110, -25, -78, -86, 119, 77, -93, 124, -112, 5, -61,
			113, -8, -49, -13, 51, -94, -112, 74, 49, -12, 7, -61, -16, -57, -58, -94, -52, 101, -70, -102, 53, 113,
			-64, -48, 30, 77, -57, -34, -60, 15, 46, 60, 63, 96, -80, 23, -39, 38, -113, -59, -75, -84, -42, 59, -65,
			86, -99, 87, 106, -100, -128, 44, -93, 58, 26, 101, 100, -107, -94, 69, -107, 75, -56, 8, 27, 103, -81, 104,
			-66, -44, 99, -101, -94, 89, 55, 117, -76, 41, 118, -66, 9, -124, 123, -124, 45, -20, -93, -69, 19, -97,
			-42, 19, -30, -11, -100, -25, 63, 82, -77, -106, 106, 116, -23, 7, -22, -20, -16, 11, 80, 75, 7, 8, 16, 75,
			41, -44, 11, 1, 0, 0, 122, 1, 0, 0, 80, 75, 1, 2, 20, 0, 20, 0, 8, 8, 8, 0, 86, 2, -127, 78, -78, 127, 2,
			-18, 27, 0, 0, 0, 25, 0, 0, 0, 20, 0, 4, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 77, 69, 84, 65, 45,
			73, 78, 70, 47, 77, 65, 78, 73, 70, 69, 83, 84, 46, 77, 70, -2, -54, 0, 0, 80, 75, 1, 2, 20, 0, 20, 0, 8, 8,
			8, 0, 54, 2, -127, 78, 16, 75, 41, -44, 11, 1, 0, 0, 122, 1, 0, 0, 29, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
			0, 97, 0, 0, 0, 99, 111, 109, 47, 101, 120, 97, 109, 112, 108, 101, 47, 67, 108, 97, 115, 115, 49, 50, 56,
			51, 57, 52, 46, 99, 108, 97, 115, 115, 80, 75, 5, 6, 0, 0, 0, 0, 2, 0, 2, 0, -111, 0, 0, 0, -73, 1, 0, 0, 0,
			0 };

	static final byte[] library_3 = new byte[] { 80, 75, 3, 4, 10, 0, 0, 0, 0, 0, 117, 3, -127, 78, 72, -3, 24, 112, 11,
			0, 0, 0, 11, 0, 0, 0, 12, 0, 28, 0, 114, 101, 115, 111, 117, 114, 99, 101, 46, 116, 120, 116, 85, 84, 9, 0,
			3, 78, -95, -95, 92, 78, -95, -95, 92, 117, 120, 11, 0, 1, 4, -24, 3, 0, 0, 4, -24, 3, 0, 0, 56, 50, 51, 55,
			54, 52, 51, 55, 55, 53, 52, 80, 75, 1, 2, 30, 3, 10, 0, 0, 0, 0, 0, 117, 3, -127, 78, 72, -3, 24, 112, 11,
			0, 0, 0, 11, 0, 0, 0, 12, 0, 24, 0, 0, 0, 0, 0, 1, 0, 0, 0, -92, -127, 0, 0, 0, 0, 114, 101, 115, 111, 117,
			114, 99, 101, 46, 116, 120, 116, 85, 84, 5, 0, 3, 78, -95, -95, 92, 117, 120, 11, 0, 1, 4, -24, 3, 0, 0, 4,
			-24, 3, 0, 0, 80, 75, 5, 6, 0, 0, 0, 0, 1, 0, 1, 0, 82, 0, 0, 0, 81, 0, 0, 0, 0, 0 };

	@Test
	@DisplayName("Build a nested URL with 1 layer")
	void toNested_1() throws IOException {

		try (var in = URLFactory
				.toNested(Paths.get("src/test/resources/nested_1.jar").toAbsolutePath() + "!/library_1.jar")
				.openStream()) {

			assertArrayEquals(library_1, in.readAllBytes());
		}
	}

	@Test
	@DisplayName("Build a nested URL with 2 layers")
	void toNested_2() throws IOException {
		try (var in = URLFactory.toNested(
				Paths.get("src/test/resources/nested_2.jar").toAbsolutePath() + "!/nested_1.jar!/1/library_3.jar")
				.openStream()) {

			assertArrayEquals(library_3, in.readAllBytes());
		}
	}

	@Test
	@DisplayName("Build a nested URL with 3 layers")
	void toNested_3() throws IOException {
		try (var in = URLFactory.toNested(Paths.get("src/test/resources/nested_3.jar").toAbsolutePath()
				+ "!/nested_2.jar!/nested_1.jar!/1/library_3.jar").openStream()) {

			assertArrayEquals(library_3, in.readAllBytes());
		}
	}

	@Test
	@DisplayName("Add an entry to a nested URL by position")
	void buildNested_1() throws IOException {
		URL nested = URLFactory.toNested(
				Paths.get("src/test/resources/nested_3.jar").toAbsolutePath() + "!/nested_2.jar!/nested_1.jar");

		try (var in = URLFactory.buildNested(nested, "1/library_3.jar", 2).openStream()) {

			assertArrayEquals(library_3, in.readAllBytes());
		}
	}

	@Test
	@DisplayName("Add an entry to a nested URL by value")
	void buildNested_2() throws IOException {
		URL nested = URLFactory.toNested(
				Paths.get("src/test/resources/nested_3.jar").toAbsolutePath() + "!/nested_2.jar!/nested_1.jar");

		try (var in = URLFactory.buildNested(nested, "1/library_3.jar", library_3).openStream()) {

			assertArrayEquals(library_3, in.readAllBytes());
		}
	}
}

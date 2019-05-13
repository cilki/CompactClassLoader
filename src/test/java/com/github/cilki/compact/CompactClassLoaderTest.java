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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Paths;
import java.util.Scanner;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CompactClassLoaderTest {

	@Test
	@DisplayName("Load classes from nested jars")
	void loadClass_1() throws Exception {
		var loader = new CompactClassLoader(null);
		loader.add(Paths.get("src/test/resources/nested_1.jar").toAbsolutePath().toUri().toURL(), true);

		// Nested jar location: /library_1.jar
		Class<?> c = loader.loadClass("com.example.Class128394");
		assertEquals("com.example.Class128394", c.getName());
		assertEquals("19238475347", c.getMethod("call").invoke(null));

		// Nested jar location: /1/2/3/library_2.jar
		Class<?> d = loader.loadClass("Class904232");
		assertEquals("Class904232", d.getName());
		assertEquals("94859273822", d.getMethod("call").invoke(null));
	}

	@Test
	@DisplayName("Load classes from deeply nested jars")
	void loadClass_2() throws Exception {
		var loader = new CompactClassLoader(null);
		loader.add(Paths.get("src/test/resources/nested_2.jar").toAbsolutePath().toUri().toURL(), true);

		// Nested jar location: /nested_1.jar!/library_1.jar
		Class<?> c = loader.loadClass("com.example.Class128394");
		assertEquals("com.example.Class128394", c.getName());
		assertEquals("19238475347", c.getMethod("call").invoke(null));

		// Nested jar location: /nested_1.jar!/1/2/3/library_2.jar
		Class<?> d = loader.loadClass("Class904232");
		assertEquals("Class904232", d.getName());
		assertEquals("94859273822", d.getMethod("call").invoke(null));
	}

	@Test
	@DisplayName("Load classes from nested jar only")
	void loadClass_3() throws Exception {
		var loader = new CompactClassLoader(null);
		loader.add(Paths.get("src/test/resources/nested_1.jar!/1/2/3/library_2.jar").toAbsolutePath().toUri().toURL(),
				false);

		// Nested jar location: /nested_1.jar!/library_1.jar
		assertThrows(ClassNotFoundException.class, () -> loader.loadClass("com.example.Class128394"));

		// Nested jar location: /nested_1.jar!/1/2/3/library_2.jar
		Class<?> d = loader.loadClass("Class904232");
		assertEquals("Class904232", d.getName());
		assertEquals("94859273822", d.getMethod("call").invoke(null));
	}

	@Test
	@DisplayName("Load classes from standard library")
	void loadClass_4() throws Exception {
		var loader = new CompactClassLoader(null);
		loader.add(Paths.get("src/test/resources/nested_1.jar!/1/2/3/library_2.jar").toAbsolutePath().toUri().toURL(),
				false);

		// Load random JRE class
		Class<?> d = loader.loadClass("java.time.chrono.JapaneseDate");
		assertEquals("java.time.chrono.JapaneseDate", d.getName());
	}

	@Test
	@DisplayName("Load classes that have included dependencies")
	void loadClass_5() throws Exception {
		var loader = new CompactClassLoader(null);
		loader.add(Paths.get("src/test/resources/testapp.jar").toAbsolutePath().toUri().toURL(), true);

		// Load main
		Class<?> d = loader.loadClass("testapp.Main");
		assertEquals("testapp.Main", d.getName());
		d.getMethod("main").invoke(null);
	}

	@Test
	@DisplayName("Load resources from nested jars")
	void getResourceAsStream_1() throws Exception {
		var loader = new CompactClassLoader(null);
		loader.add(Paths.get("src/test/resources/nested_1.jar").toAbsolutePath().toUri().toURL());

		// Nested jar location: /1/library_3.jar
		try (Scanner in = new Scanner(loader.getResourceAsStream("resource.txt"))) {
			assertEquals(in.nextLine(), "82376437754");
		}

		// Nested jar location: /library_1.jar
		assertNotNull(loader.getResource("library_1.jar"));
	}
}

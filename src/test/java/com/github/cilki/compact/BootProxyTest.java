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

import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BootProxyTest {

	@Test
	@DisplayName("Boot nested application")
	void main_1(@TempDir Path temp) throws Exception {
		// Build a runnable jar
		try (FileSystem zip = FileSystems.newFileSystem(URI.create("jar:file:" + temp.resolve("app.jar")),
				Map.of("create", "true"))) {

			// Copy CompactClassLoader classes
			for (String c : List.of("BootProxy", "CompactClassLoader", "NodeClassLoader", "URLFactory$1$1",
					"URLFactory$1", "URLFactory$2$1", "URLFactory$2", "URLFactory$3", "URLFactory$3$1", "URLFactory")) {
				String resourceName = "com/github/cilki/compact/" + c + ".class";
				Files.createDirectories(zip.getPath(resourceName).getParent());
				Files.copy(BootProxyTest.class.getClassLoader().getResourceAsStream(resourceName),
						zip.getPath(resourceName));
			}

			// Write manifest
			Files.createDirectories(zip.getPath("META-INF"));
			Files.writeString(zip.getPath("META-INF/MANIFEST.MF"),
					"Manifest-Version: 1.0\nMain-Class: com.github.cilki.compact.BootProxy\nBoot-Class: testapp.Main\n\n");

			// Copy test application
			try (FileSystem app = FileSystems.newFileSystem(
					URI.create("jar:file:" + Paths.get("src/test/resources/testapp.jar").toAbsolutePath()),
					Collections.emptyMap())) {
				Files.copy(app.getPath("lib1.jar"), zip.getPath("lib1.jar"));
				Files.copy(app.getPath("lib2.jar"), zip.getPath("lib2.jar"));

				Files.createDirectory(zip.getPath("testapp"));
				Files.copy(app.getPath("testapp/Main.class"), zip.getPath("testapp/Main.class"));
			}
		}

		// Execute
		Process process = new ProcessBuilder("java", "-jar", temp.resolve("app.jar").toString()).start();
		assertEquals(0, process.waitFor());
	}
}

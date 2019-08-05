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

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * A utility class for building nested {@link URL}s.
 * 
 * @author cilki
 */
final class URLFactory {

	/**
	 * A reusable {@link URLStreamHandler} that opens an {@link InputStream} on a
	 * nested {@link URL}.
	 */
	private static final URLStreamHandler nestedJarHandler = new URLStreamHandler() {

		@Override
		protected URLConnection openConnection(URL url) throws IOException {
			return new URLConnection(url) {

				private InputStream in;

				@Override
				public void connect() throws IOException {
					// Split into the components of the nested URL
					String[] levels = url.toString().split("!/");

					String file = levels[0];
					if (!file.startsWith("jar:file:"))
						throw new MalformedURLException("Unexpected URL protocol: " + url.toString());

					// Remove jar protocol
					file = file.substring(4);

					ZipInputStream jar = new ZipInputStream(new URL(file).openStream());

					for (int i = 1; i < levels.length; i++) {
						if (i != 1)
							jar = new ZipInputStream(jar);

						// Find the entry that equals levels[i]
						ZipEntry entry;
						while ((entry = jar.getNextEntry()) != null && !entry.getName().equals(levels[i]))
							;
						if (entry == null) {
							jar.close();
							throw new FileNotFoundException("Entry not found: " + levels[i]);
						}
					}

					in = jar;
				}

				@Override
				public InputStream getInputStream() throws IOException {
					if (in == null)
						connect();
					return in;
				}
			};
		}
	};

	/**
	 * Convert the given {@link URL} into an identical-path {@link URL} capable of
	 * reading from the component.
	 * 
	 * @param url An input {@link URL}
	 * @return A {@link URL} capable of opening an {@link InputStream} on the
	 *         component
	 * @throws MalformedURLException
	 */
	public static URL toNested(URL url) throws MalformedURLException {
		if (url.getPath().contains("!/"))
			return new URL(null, ensureProtocol(url.toString()), nestedJarHandler);
		return url;
	}

	/**
	 * Convert the given {@link String} url into an identical-path {@link URL}
	 * capable of reading from the component.
	 * 
	 * @param url An input url
	 * @return A {@link URL} capable of opening an {@link InputStream} on the
	 *         component
	 * @throws MalformedURLException
	 */
	public static URL toNested(String url) throws MalformedURLException {
		if (url.contains("!/"))
			return new URL(null, ensureProtocol(url), nestedJarHandler);
		return new URL(url);
	}

	/**
	 * Build a nested {@link URL} for the given base URL and the given entry.
	 * 
	 * @param base     The base {@link URL}
	 * @param entry    The entry within the base {@link URL}
	 * @param position The entry's position in the base {@link URL}
	 * @return A {@link URL} capable of opening an {@link InputStream} on the
	 *         component
	 * @throws MalformedURLException
	 */
	public static URL buildNested(URL base, String entry, int position) throws MalformedURLException {
		Objects.requireNonNull(base);
		Objects.requireNonNull(entry);
		if (position < 0)
			throw new IllegalArgumentException("Invalid entry position: " + position);

		return new URL(null, ensureProtocol(base + "!/" + entry), new URLStreamHandler() {

			@Override
			protected URLConnection openConnection(URL u) throws IOException {
				return new URLConnection(u) {

					private InputStream in;

					@Override
					public void connect() throws IOException {
						ZipInputStream jar = new ZipInputStream(base.openStream());

						// Go back to the entry
						for (int i = 0; i <= position; i++)
							jar.getNextEntry();

						in = jar;
					}

					@Override
					public InputStream getInputStream() throws IOException {
						if (in == null)
							connect();
						return in;
					}
				};
			}
		});
	}

	/**
	 * Build a nested {@link URL} for the given base URL and the given entry.
	 * 
	 * @param base  The base {@link URL}
	 * @param entry The entry within the base {@link URL}
	 * @param bytes The entry's data
	 * @return A {@link URL} capable of opening an {@link InputStream} on the
	 *         component
	 * @throws MalformedURLException
	 */
	public static URL buildNested(URL base, String entry, byte[] bytes) throws MalformedURLException {
		Objects.requireNonNull(base);
		Objects.requireNonNull(entry);
		Objects.requireNonNull(bytes);

		return new URL(null, ensureProtocol(base + "!/" + entry), new URLStreamHandler() {

			@Override
			protected URLConnection openConnection(URL u) throws IOException {
				return new URLConnection(u) {

					@Override
					public void connect() throws IOException {
					}

					@Override
					public InputStream getInputStream() throws IOException {
						return new ByteArrayInputStream(bytes);
					}
				};
			}
		});
	}

	/**
	 * Verify that the given URL begins with "jar:file:".
	 * 
	 * @param url The input URL
	 * @return A URL whose protocol is "jar:file:"
	 */
	private static String ensureProtocol(String url) {
		if (url.startsWith("jar:file:"))
			return url;
		if (url.startsWith("file:"))
			return "jar:" + url;

		return "jar:file:" + url;
	}

	private URLFactory() {
	}
}

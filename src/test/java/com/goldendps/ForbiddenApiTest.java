package com.goldendps;

import static org.junit.Assert.assertTrue;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.Test;

/** The plugin code uses none of the APIs the Plugin Hub review rejects. */
public class ForbiddenApiTest
{
	private static final List<String> FORBIDDEN = Arrays.asList(
		"import java.io.File;", "java.nio.file.Files", "java.nio.file.Paths", "RUNELITE_DIR",
		"Filepath.Unchecked", "new Gson(", "new OkHttpClient", "Thread.sleep", "java.lang.reflect",
		"Runtime.getRuntime", "ProcessBuilder", "java.awt.Desktop", "LinkBrowser", "menuAction(");

	@Test
	public void noForbiddenApis() throws IOException
	{
		List<String> found = new ArrayList<>();
		try (Stream<Path> files = Files.walk(Paths.get("src/main/java")))
		{
			for (Path file : (Iterable<Path>) files.filter(f -> f.toString().endsWith(".java"))::iterator)
			{
				String code = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
				for (String api : FORBIDDEN)
				{
					if (code.contains(api))
					{
						found.add(file.getFileName() + ": " + api);
					}
				}
			}
		}
		assertTrue("not allowed on the Plugin Hub: " + found, found.isEmpty());
	}
}

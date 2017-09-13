/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MigrateContent {
    // UUID structure 8-4-4-4-12
    private static final String UUID_REGEX = "[a-z0-9]{8}-[a-z0-9]{4}-[a-z0-9]{4}-[a-z0-9]{4}-[a-z0-9]{12}";

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("You must supply a path that contains content to modify");
            System.exit(0);
        }

        final Path path = Paths.get(args[0]);
        if (!Files.isDirectory(path)) {
            System.err.println("'" + path.toAbsolutePath().toString() + "' is not a valid path");
            System.exit(0);
        }

        try (final Stream<Path> stream = Files.walk(path)) {
            final List<Path> list = stream
                    .filter(p -> {
                        final String fileName = p.getFileName().toString();
                        return fileName.matches("([^.]*\\.){2,}xml") &&
                                fileName.matches(".*\\.[A-Z][a-z]+\\.xml$");
                    })
                    .collect(Collectors.toList());
            list.forEach(MigrateContent::process);
        } catch (final IOException e) {
            System.err.println(e.getMessage());
        }
    }

    private static void process(final Path path) {
        try {
            System.out.println("Processing: " + path.toAbsolutePath().toString());

            final String fileName = path.getFileName().toString();
            final String fileStem = fileName.substring(0, fileName.lastIndexOf("."));
            final String type = fileStem.substring(fileStem.lastIndexOf(".") + 1);

            // Get path elements
            final List<String> pathElements = new ArrayList<>();
            Path parent = path.getParent();
            while (parent != null && parent.getFileName() != null && !parent.getFileName().toString().equalsIgnoreCase("stroomContent")) {
                pathElements.add(0, parent.getFileName().toString());
                parent = parent.getParent();
            }

            final String xml = new String(Files.readAllBytes(path), Charset.forName("UTF-8"));
            final String uuid = getValue(xml, "uuid");
            final String name = getValue(xml, "name");

            final Properties properties = new Properties();
            properties.setProperty("uuid", uuid);
            properties.setProperty("type", type);
            properties.setProperty("name", name);
            properties.setProperty("path", String.join("/", pathElements));

            final String newFileStem = createFilePrefix(name, type, uuid);
            final OutputStream outputStream = Files.newOutputStream(path.getParent().resolve(newFileStem + ".node"));
            PropertiesSerialiser.write(properties, outputStream);

            // Rename related files.
            try (final Stream<Path> stream = Files.list(path.getParent())) {
                stream
                        .filter(p -> p.getFileName().toString().startsWith(fileStem + ".") &&
                                !p.getFileName().toString().startsWith(newFileStem))
                        .forEach(p -> {
                            try {
                                final String extension = p.getFileName().toString().substring(fileStem.length());
                                final String newFileName = newFileStem + extension;
                                Files.move(p, p.getParent().resolve(newFileName));
                            } catch (final IOException e) {
                                System.err.println(e.getMessage());
                            }
                        });
            } catch (final IOException e) {
                System.err.println(e.getMessage());
            }

        } catch (final IOException e) {
            System.err.println(e.getMessage());
        }
    }

    private static String getValue(final String xml, final String tag) {
        final int start = xml.indexOf("<" + tag + ">");
        final int end = xml.indexOf("</" + tag + ">");
        if (start != -1 && end != -1) {
            return xml.substring(start + 6, end);
        }
        return null;
    }

    private static String createFilePrefix(final String name, final String type, final String uuid) {
        return toSafeFileName(name, 100) + "." + type + "." + uuid;
    }

    private static String toSafeFileName(final String string, final int maxLength) {
        String safe = string.replaceAll("[^A-Za-z0-9]", "_");
        if (safe.length() > maxLength) {
            safe = safe.substring(0, maxLength);
        }
        return safe;
    }
}
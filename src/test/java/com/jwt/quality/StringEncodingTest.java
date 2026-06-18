package com.jwt.quality;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class StringEncodingTest {
    private static final Pattern MOJIBAKE_MARKERS = mojibakeMarkers();

    @Test
    void mainJavaTemplatesAndReadmeDoNotContainKnownMojibakeMarkers() throws IOException {
        List<Path> files;
        try (Stream<Path> paths = Stream.concat(
                Files.walk(Path.of("src/main/java")),
                Files.walk(Path.of("src/main/resources/templates"))
        )) {
            files = Stream.concat(paths, Stream.of(Path.of("README.md")))
                    .filter(Files::isRegularFile)
                    .toList();
        }

        List<String> offenders = files.stream()
                .filter(this::containsMojibakeMarker)
                .map(Path::toString)
                .toList();

        assertThat(offenders).isEmpty();
    }

    private boolean containsMojibakeMarker(Path path) {
        try {
            return MOJIBAKE_MARKERS.matcher(Files.readString(path, StandardCharsets.UTF_8)).find();
        } catch (IOException e) {
            throw new IllegalStateException("파일을 읽을 수 없습니다: " + path, e);
        }
    }

    private static Pattern mojibakeMarkers() {
        int[] codePoints = {0x6FE1, 0x6E72, 0x5A9B, 0x936E, 0x8ADB, 0xF9E1, 0x5AC4, 0x6028, 0x6D39, 0x5BC3};
        StringBuilder pattern = new StringBuilder("[");
        for (int codePoint : codePoints) {
            pattern.appendCodePoint(codePoint);
        }
        pattern.append(']');
        return Pattern.compile(pattern.toString());
    }
}

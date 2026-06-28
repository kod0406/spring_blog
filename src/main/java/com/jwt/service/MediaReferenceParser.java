package com.jwt.service;

import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class MediaReferenceParser {
    private static final Pattern MEDIA_PATH = Pattern.compile("(?<![A-Za-z0-9_-])/media/(\\d+)(?!\\d)");

    public Set<Long> referencedMediaIds(String markdown) {
        Set<Long> mediaIds = new HashSet<>();
        if (markdown == null || markdown.isBlank()) {
            return mediaIds;
        }
        Matcher matcher = MEDIA_PATH.matcher(markdown);
        while (matcher.find()) {
            mediaIds.add(Long.parseLong(matcher.group(1)));
        }
        return mediaIds;
    }
}

package com.jwt.service;

import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class MarkdownService {
    private static final Pattern VIDEO_TOKEN = Pattern.compile("!\\[video]\\((/media/\\d+)\\)");
    private static final Pattern MEDIA_SOURCE = Pattern.compile("^/media/\\d+$");

    private final Parser parser = Parser.builder().build();
    private final HtmlRenderer renderer = HtmlRenderer.builder().build();

    public String render(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }

        String withVideoPlaceholders = replaceVideoTokens(markdown);
        String html = renderer.render(parser.parse(withVideoPlaceholders));
        Safelist safelist = Safelist.relaxed()
                .addTags("video", "source")
                .addAttributes("video", "controls", "preload")
                .addAttributes("source", "src", "type")
                .addProtocols("source", "src", "http", "https")
                .addProtocols("a", "href", "http", "https", "mailto")
                .addProtocols("img", "src", "http", "https")
                .preserveRelativeLinks(true);

        String cleaned = Jsoup.clean(html, safelist);
        Document document = Jsoup.parseBodyFragment(cleaned);
        document.outputSettings().prettyPrint(false);
        for (Element mediaElement : document.select("img, source")) {
            if (!MEDIA_SOURCE.matcher(mediaElement.attr("src")).matches()) {
                mediaElement.remove();
            }
        }
        return document.body().html();
    }

    private String replaceVideoTokens(String markdown) {
        Matcher matcher = VIDEO_TOKEN.matcher(markdown);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String url = matcher.group(1);
            String replacement = "<video controls preload=\"metadata\"><source src=\"" + url + "\"></video>";
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}

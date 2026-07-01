package com.jwt.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownMediaRenderingTest {
    private final MarkdownService markdownService = new MarkdownService();

    @Test
    void localImageAndVideoMediaPathsArePreserved() {
        String rendered = markdownService.render("![image](/media/10)\n\n![video](/media/20)");

        assertThat(rendered).contains("src=\"/media/10\"");
        assertThat(rendered).contains("<video controls");
        assertThat(rendered).contains("src=\"/media/20\"");
    }

    @Test
    void externalAndDataMediaSourcesAreRemoved() {
        String rendered = markdownService.render("![external](https://example.com/image.png)\n\n![data](data:image/png;base64,AAAA)");

        assertThat(rendered).doesNotContain("<img");
        assertThat(rendered).doesNotContain("example.com");
        assertThat(rendered).doesNotContain("data:image");
    }
}

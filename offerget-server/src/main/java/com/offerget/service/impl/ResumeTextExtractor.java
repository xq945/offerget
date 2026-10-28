package com.offerget.service.impl;

import java.io.IOException;
import java.io.InputStream;

import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.springframework.stereotype.Component;

/**
 * 基于 Tika 提取 PDF / DOCX / TXT 简历文本，统一清洗空白并限制长度。
 */
@Component
public class ResumeTextExtractor {

    private static final int MAX_CHARS = 20000;

    private final Tika tika = new Tika();

    public String extract(InputStream inputStream) throws IOException, TikaException {
        String text = tika.parseToString(inputStream);
        if (text == null) {
            return "";
        }
        String normalized = text.replace(' ', ' ')
                .replace("\r\n", "\n")
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
        return normalized.length() > MAX_CHARS ? normalized.substring(0, MAX_CHARS) : normalized;
    }

}

package com.offerget.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.annotation.PostConstruct;

/**
 * 简历文件等上传资源的静态映射：/uploads/** → 本地存储目录。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${app.file.storage-root}")
    private String storageRoot;

    private Path root;

    @PostConstruct
    public void init() throws IOException {
        this.root = Paths.get(storageRoot).toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = root.toUri().toString();
        if (!location.endsWith("/")) {
            location += "/";
        }
        registry.addResourceHandler("/uploads/**").addResourceLocations(location);
    }

    public Path getRoot() {
        return root;
    }

}

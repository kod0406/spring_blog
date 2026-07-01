package com.jwt.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "oci.object-storage")
public class OciObjectStorageProperties {
    private String authMode = "instance-principal";
    private String configFile;
    private String profile = "DEFAULT";
    private String region;
    private String imageRegion;
    private String videoRegion;
    private String namespace;
    private String imageNamespace;
    private String videoNamespace;
    private String imageBucket;
    private String videoBucket;
}

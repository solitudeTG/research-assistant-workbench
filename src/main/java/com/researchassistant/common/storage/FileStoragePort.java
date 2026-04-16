package com.researchassistant.common.storage;

import java.nio.file.Path;
import org.springframework.web.multipart.MultipartFile;

public interface FileStoragePort {

    String save(MultipartFile file);

    Path resolve(String storagePath);
}

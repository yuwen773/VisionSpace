package com.yuwen.visionspace.agent.service;

import com.yuwen.visionspace.manager.storage.FileUploadService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.Resource;

@Slf4j
@Service
public class AgentImageUploadService {

    @Resource
    private FileUploadService fileUploadService;

    public String uploadImage(MultipartFile file) {
        String dateStr = java.time.LocalDate.now().toString();
        String pathPrefix = "/agent-chat/" + dateStr + "/";
        return fileUploadService.uploadImageFile(file, pathPrefix);
    }

    public void deleteImage(String imageUrl) {
        fileUploadService.deleteFileByUrl(imageUrl, "agent-chat");
    }
}

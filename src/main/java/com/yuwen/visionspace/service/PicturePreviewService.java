package com.yuwen.visionspace.service;

public interface PicturePreviewService {

    void generateAndUpdatePreview(Long pictureId, String originalStoragePath);
}

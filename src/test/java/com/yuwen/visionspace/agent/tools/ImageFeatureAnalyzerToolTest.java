package com.yuwen.visionspace.agent.tools;

import com.yuwen.visionspace.agent.model.ImageAnalysisResult;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("local")
class ImageFeatureAnalyzerToolTest {

    @Resource
    private ImageFeatureAnalyzerTool imageFeatureAnalyzerTool;

    @Test
    void analyzeImageFeatures() {
        ImageAnalysisResult result = imageFeatureAnalyzerTool.analyzeImageFeatures(
                "https://pic.cnblogs.com/avatar/3476096/20260324125711.png");
        System.out.println(result);
        assertNotNull(result);
        assertNotNull(result.getDescription());
    }
}

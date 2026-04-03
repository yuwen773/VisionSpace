package com.yuwen.visionspace.agent.tools;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.RuntimeUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.system.SystemUtil;
import com.yuwen.visionspace.agent.model.ImageCategoryEnum;
import com.yuwen.visionspace.agent.model.ImageResource;
import com.yuwen.visionspace.exception.BusinessException;
import com.yuwen.visionspace.exception.ErrorCode;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.dromara.x.file.storage.core.FileInfo;
import org.dromara.x.file.storage.core.FileStorageService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class MermaidDiagramTool {

    private static final String MERMAID_PATH_PREFIX = "mermaid/";

    @Resource
    private FileStorageService fileStorageService;

    @Tool(name = "generateMermaidDiagram", description = "将 Mermaid 代码转换为 SVG 架构图图片并上传。适用于需要展示系统架构、技术栈关系、模块依赖、流程图等结构化图示的场景。返回上传后的图片 URL。")
    public List<ImageResource> generateMermaidDiagram(@ToolParam(description = "Mermaid 图表代码，支持 flowchart、sequenceDiagram、graph 等语法，例如：graph TD; A-->B; B-->C") String mermaidCode,
                                                      @ToolParam(description = "架构图的一句简短描述，用于图片 alt 文本和搜索，例如：'微服务架构拓扑图'、'用户认证流程'") String description) {
        if (StrUtil.isBlank(mermaidCode)) {
            return new ArrayList<>();
        }
        try {
            // 转换为SVG图片
            File diagramFile = convertMermaidToSvg(mermaidCode);
            // 上传到存储
            String filename = RandomUtil.randomString(5) + "_" + diagramFile.getName();
            // 上传到存储
            FileInfo fileInfo = fileStorageService.of(diagramFile)
                    .setPath(MERMAID_PATH_PREFIX)
                    .setSaveFilename(filename)
                    .upload();
            // 清理临时文件
            FileUtil.del(diagramFile);
            if (fileInfo != null && StrUtil.isNotBlank(fileInfo.getUrl())) {
                return Collections.singletonList(ImageResource.builder()
                        .category(ImageCategoryEnum.ARCHITECTURE)
                        .description(description)
                        .url(fileInfo.getUrl())
                        .build());
            }
        } catch (Exception e) {
            log.error("生成架构图失败: {}", e.getMessage(), e);
        }
        return new ArrayList<>();
    }

    /**
     * 将Mermaid代码转换为SVG图片
     */
    private File convertMermaidToSvg(String mermaidCode) {
        // 创建临时输入文件
        File tempInputFile = FileUtil.createTempFile("mermaid_input_", ".mmd", true);
        FileUtil.writeUtf8String(mermaidCode, tempInputFile);
        // 创建临时输出文件
        File tempOutputFile = FileUtil.createTempFile("mermaid_output_", ".svg", true);
        // 根据操作系统选择命令
        String command = SystemUtil.getOsInfo().isWindows() ? "mmdc.cmd" : "mmdc";
        // 构建命令
        String cmdLine = String.format("%s -i %s -o %s -b transparent",
                command,
                tempInputFile.getAbsolutePath(),
                tempOutputFile.getAbsolutePath()
        );
        // 执行命令
        RuntimeUtil.execForStr(cmdLine);
        // 检查输出文件
        if (!tempOutputFile.exists() || tempOutputFile.length() == 0) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "Mermaid CLI 执行失败");
        }
        // 清理输入文件，保留输出文件供上传使用
        FileUtil.del(tempInputFile);
        return tempOutputFile;
    }
}

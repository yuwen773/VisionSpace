package com.yuwen.visionspace.acceptance;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * 隔离验收的嵌入模型替身。
 *
 * <p>隔离环境没有在线 embedding 服务，也不该为了验证「向量库装得上」去调真实接口。
 * 这里给出确定性的向量：站内检索的装配、健康信号与降级路径都不依赖向量的具体数值。
 */
@TestConfiguration(proxyBeanMethods = false)
public class StubEmbeddingConfiguration {

    @Bean
    public EmbeddingModel acceptanceEmbeddingModel() {
        return new StubEmbeddingModel();
    }

    /**
     * 固定维度的确定性向量：同样的文本永远得到同样的向量。
     */
    static final class StubEmbeddingModel implements EmbeddingModel {

        private static final int DIMENSIONS = 8;

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            List<Embedding> embeddings = request.getInstructions().stream()
                    .map(text -> new Embedding(vector(text), 0))
                    .toList();
            return new EmbeddingResponse(embeddings);
        }

        @Override
        public float[] embed(Document document) {
            return vector(document.getText());
        }

        private float[] vector(String text) {
            float[] vector = new float[DIMENSIONS];
            if (text == null) {
                return vector;
            }
            for (int i = 0; i < text.length(); i++) {
                vector[i % DIMENSIONS] += text.charAt(i);
            }
            return vector;
        }
    }
}

package org.example.aicommon.config;

import org.example.aicommon.PromptManage;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 情绪分析客户端，web 和 diary-service 共用同一个 bean 定义。
 *
 * 只放"两个服务完全一样"的 bean。web 的 open-ai（心理咨询对话）和 chatMemory
 * 依赖会话上下文，属于 web 自己的东西，留在 web 的 ChatClientConfig 里。
 *
 * 生效前提：使用方启动类的 scanBasePackages 要包含 org.example.aicommon。
 */
@Configuration
public class AiClientConfig {

    @Bean("emotion-analyzer")
    public ChatClient emotionAnalyzerClient(OpenAiChatModel openAiChatModel){
        return ChatClient.builder(openAiChatModel)
                .defaultSystem(PromptManage.EMOTION_ANALYSIS_SYSTEM_PROMPT)
                .build();
    }
}

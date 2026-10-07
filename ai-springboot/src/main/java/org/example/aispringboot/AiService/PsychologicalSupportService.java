package org.example.aispringboot.AiService;

import lombok.extern.slf4j.Slf4j;
import org.example.aispringboot.DTO.command.ConsultationSectionCreateDTO;
import org.example.aispringboot.DTO.response.ConsultationMessageResponseDTO;
import org.example.aispringboot.entity.ConsultationSession;
import org.example.aispringboot.exception.BusinessException;
import org.example.aispringboot.mapper.ConsultationSessionMapper;
import org.example.aispringboot.service.ConsultationMessageService;
import org.example.aispringboot.service.ConsultationSessionsService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AI 对话专用服务。
 * 注意：会话的【查询】不在这里 —— 那是 AB端/C端共用的，在 KnowledgeConsultationService。
 */
@Slf4j
@Service
public class PsychologicalSupportService {
    @Autowired
    @Qualifier("open-ai")
    private ChatClient chatClient;
    @Autowired
    private ConsultationSessionsService consultationSessionsService;
    @Autowired
    private ConsultationMessageService consultationMessageService;
    @Autowired
    private ConsultationSessionMapper consultationSessionMapper;
    @Autowired
    private EmotionAnalysisService emotionAnalysisService;

    public StructOutPut.StreamChatSession startSession(Long userId, ConsultationSectionCreateDTO createDTO){
        ConsultationSession consultationSession=consultationSessionsService.createSession(userId,createDTO);
        consultationMessageService.saveUserMessage(consultationSession.getId(),createDTO.getInitialMessage(),null);

        String sessionId="session_"+consultationSession.getId();
        return new StructOutPut.StreamChatSession(
                sessionId,
                userId,
                createDTO.getInitialMessage(),
                System.currentTimeMillis(),
                System.currentTimeMillis()+86400000L,//24小时超时时间
                1,
                "ACTIVE"
        );
    }

    public Flux<String> streamPsychologicalChat(Long userId,String sessionId,String userMessage){
        log.info("AI收到流式对话请求：userId={},sessionId={},消息长度={}",
                userId,sessionId,userMessage==null?0:userMessage.length());
        //创建响应流
        return Flux.create(sink->{
            AtomicBoolean firstToken=new AtomicBoolean(true);
            //sink.next()：发布数据
            //sink.complete()：完成流，告诉前端已完成
            //sink.error(exception)：发布错误
            Long dbSessionId=extractSessionId(sessionId);
            if(dbSessionId==null){
                sink.error(new BusinessException("会话Id格式错误"));
                return;
            }
            ConsultationSession s=consultationSessionMapper.selectById(dbSessionId);
            //防止越权，拿别人的session
            if(s==null || !userId.equals(s.getUserId())){
                sink.error(new BusinessException("会话不存在或无权访问"));
                return;
            }
            boolean isInitialMessage=false;//是否为初始消息
            //判断是否为初始消息，避免重复请求
            Integer messageCount=consultationMessageService.getMessageCountBySessionId(dbSessionId);
            if(messageCount>0){
                //直接发布
                ConsultationMessageResponseDTO lastMessage =
                        consultationMessageService.getLastMessageBySessionId(dbSessionId);
                if(lastMessage!=null && lastMessage.getSenderType()!=null
                        && lastMessage.getSenderType()==1 && userMessage.equals(lastMessage.getContent())){
                    //发送者必须是用户的类型，不然AI检测到AI发的，无限递归的发了
                    isInitialMessage=true;
                }
            }
            if(!isInitialMessage){
                //该保存用户消息到数据库
                consultationMessageService.saveUserMessage(dbSessionId,userMessage,null);
            }

            //流式对话——基于ChatClient
            //生成对话记忆管理
            String conversationId="conversation_"+sessionId;
            //存储AI完整响应
            StringBuilder fullResponse=new StringBuilder();

            log.info("AI开始调用大模型（流式）：conversationId={},历史消息数={}",
                    conversationId,messageCount);
            //用chatClient发送消息到OpenAI
            long startTime=System.currentTimeMillis();
            chatClient.prompt()
                    .user(userMessage)
                    .advisors(advisorSpec ->
                            advisorSpec.param(ChatMemory.CONVERSATION_ID,conversationId))
                    .stream()
                    .content()
                    .doOnNext(fragment->{
                        if(firstToken.compareAndSet(true,false)){
                            log.info("AI收到首个相应片段，首字耗时={}ms",System.currentTimeMillis()-startTime);
                        }
                        fullResponse.append(fragment);
                        sink.next(fragment);
                    })
                    .doOnComplete(()->{
                        String completeRes=fullResponse.toString();
                        log.info("【AI】大模型流式响应结束：conversationId={},共{}字,耗时={}ms",
                                conversationId,completeRes.length(),System.currentTimeMillis()-startTime);
                        //消息存储到表中
                        consultationMessageService.saveAiMessage(dbSessionId,completeRes,"openai");
                        sink.complete();
                        emotionAnalysisService.analyzeAsync(dbSessionId);
                    })
                    .doOnError(error->{
                        log.error("流式对话失败",error);
                        sink.error(error);
                    })
                    .subscribe(
                            ignored->{},
                            error->{}
                    );//订阅、启动流
        });

    }

    //session_123 → 123。格式不对返回 null
    private Long extractSessionId(String sessionId){
        if(sessionId==null || !sessionId.startsWith("session_")){
            return null;
        }
        try{
            return Long.parseLong(sessionId.substring("session_".length()));
        }catch (NumberFormatException e){
            return null;
        }
    }
}

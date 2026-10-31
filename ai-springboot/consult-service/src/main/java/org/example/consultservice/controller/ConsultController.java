package org.example.consultservice.controller;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.example.aicommon.dto.SessionEmotionResponseDTO;
import org.example.common.BusinessException;
import org.example.common.Result;
import org.example.common.ResultCode;
import org.example.consultservice.AiService.StructOutPut;
import org.example.consultservice.DTO.command.ConsultationPageQueryDTO;
import org.example.consultservice.DTO.command.ConsultationSectionCreateDTO;
import org.example.consultservice.DTO.command.ConsultationStreamDTO;
import org.example.consultservice.DTO.response.ConsultationMessageResponseDTO;
import org.example.consultservice.DTO.response.ConsultationSessionResponseDTO;
import org.example.consultservice.service.KnowledgeConsultationService;
import org.example.consultservice.service.PsychologicalSupportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.example.common.util.IdentityUtil.*;

@Slf4j
@RestController
@RequestMapping("/api/psychological-chat")
public class ConsultController {
    @Autowired
    private PsychologicalSupportService psychologicalSupportService;
    @Autowired
    private KnowledgeConsultationService consultationService;

    //-------------------------------------会话列表详情

    @GetMapping("/sessions")
    public Result<IPage<ConsultationSessionResponseDTO>> getSessionList(
            @Valid ConsultationPageQueryDTO query,
            @RequestHeader(value=HEADER_USER_ID,required = false)Long userId,
            @RequestHeader(value=HEADER_USER_ROLE,required=false)Integer roleType,
            @RequestHeader(value=HEADER_USER_NAME,required = false)String username){
        log.debug("Id={}分页查询会话列表，currentPage={}，size={}",userId,query.getCurrentPage(),query.getSize());
        return Result.success(consultationService.getConsultationPage(query,roleType,userId));
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public Result<List<ConsultationMessageResponseDTO>> getSessionDetail(
            @PathVariable Long sessionId,
            @RequestHeader(value=HEADER_USER_ID,required = false)Long userId,
            @RequestHeader(value=HEADER_USER_ROLE,required=false)Integer roleType){
        log.debug("Id={}查询心理会话消息,sessionId={}",userId,sessionId);
        return Result.success(consultationService.getConsultationDetail(sessionId,roleType,userId));
    }

    //-------------------------------------C端专用

    @PostMapping("/session/start")
    public Result<StructOutPut.StreamChatSession> startSession(@Valid @RequestBody ConsultationSectionCreateDTO createDTO,
                                                               @RequestHeader(value=HEADER_USER_ID,required = false)Long userId){
        StructOutPut.StreamChatSession session=psychologicalSupportService.startSession(userId,createDTO);
        return Result.success(session);
    }

    @PostMapping(value="/stream",produces = MediaType.TEXT_EVENT_STREAM_VALUE)//produces定义返回类型
    public Flux<ServerSentEvent<String>> streamChat(@Valid @RequestBody ConsultationStreamDTO streamDTO,
                                                    @RequestHeader(value=HEADER_USER_ID)Long userId){
        if(userId==null){
            return Flux.just(ServerSentEvent.<String>builder()
                    .event("error")
                    .data(JSONUtil.toJsonStr(Result.error(ResultCode.UNAUTHORIZED.getCode(),ResultCode.UNAUTHORIZED.getMsg(),"用户未登录")))
                    .build());
        }

        return psychologicalSupportService.streamPsychologicalChat(userId,streamDTO.getSessionId(),streamDTO.getUserMessage())
                .map(fragment->{
                    return ServerSentEvent.<String>builder()
                            .event("message")
                            .data(JSONUtil.toJsonStr(
                                    Result.success(Map.of("content",fragment,"type","normal")))
                            )
                            .build();
                })
                .onErrorResume(error->{
                    if(error instanceof BusinessException e){
                        log.warn("[业务] 流式对话被拒绝：{}",e.getMessage());
                        return Flux.just(ServerSentEvent.<String>builder()
                                .event("error")
                                .data(JSONUtil.toJsonStr(Result.error(e.getCode(),e.getMessage(),null)))
                                .build());
                    }
                    log.error("流式对话失败",error);
                    return Flux.just(ServerSentEvent.<String>builder()
                            .event("error")
                            .data(JSONUtil.toJsonStr(Result.error(ResultCode.SYSTEM_ERROR.getCode(),"AI服务暂不可用",null)))
                            .build());
                })
                .concatWith(Flux.just(ServerSentEvent.<String>builder()
                        .event("done")
                        .data("{}")
                        .build()
                ))
                .delayElements(Duration.ofMillis(10));
    }

    @DeleteMapping("/sessions/{id}")
    public Result<Void> deleteSession(@PathVariable("id") Long sessionId,
                                      @RequestHeader(value=HEADER_USER_ID,required = false)Long userId,
                                      @RequestHeader(value=HEADER_USER_ROLE,required=false)Integer roleType){
        log.debug("Id={}删除会话sessionId={}",userId,sessionId);
        consultationService.deleteSession(sessionId,roleType,userId);
        return Result.success();
    }

    @GetMapping("/session/{sessionId}/emotion")
    public Result<SessionEmotionResponseDTO> getSessionEmotion(
            @PathVariable String sessionId,
            @RequestHeader(value=HEADER_USER_ID,required = false)Long userId,
            @RequestHeader(value=HEADER_USER_ROLE,required=false)Integer roleType){
        log.debug("Id={}查询会话情绪,sessionId={}",userId,sessionId);
        return Result.success(consultationService.getSessionEmotion(parseSessionId(sessionId),roleType,userId));
    }

    //前端传的是 "session_123"，转成库里用的 123
    private Long parseSessionId(String sessionId){
        if(sessionId==null || !sessionId.startsWith("session_")){
            throw new BusinessException("会话Id格式错误");
        }
        try{
            return Long.parseLong(sessionId.substring("session_".length()));
        }catch(NumberFormatException e){
            throw new BusinessException("会话Id格式错误");
        }
    }
}

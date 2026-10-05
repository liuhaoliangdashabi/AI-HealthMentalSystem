package org.example.aispringboot.controller;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.example.aispringboot.AiService.PsychologicalSupportService;
import org.example.aispringboot.AiService.StructOutPut;
import org.example.aispringboot.DTO.command.ConsultationPageQueryDTO;
import org.example.aispringboot.DTO.command.ConsultationSectionCreateDTO;
import org.example.aispringboot.DTO.command.ConsultationStreamDTO;
import org.example.aispringboot.DTO.response.ConsultationMessageResponseDTO;
import org.example.aispringboot.DTO.response.ConsultationSessionResponseDTO;
import org.example.aispringboot.DTO.response.SessionEmotionResponseDTO;
import org.example.aispringboot.DTO.response.UserLoginResponseDTO;
import org.example.aispringboot.common.Result;
import org.example.aispringboot.common.ResultCode;
import org.example.aispringboot.exception.BusinessException;
import org.example.aispringboot.service.KnowledgeConsultationService;
import org.example.aispringboot.util.JwtTokenUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/psychological-chat")
public class PsycholigicalChatController {
    @Autowired
    private PsychologicalSupportService psychologicalSupportService;
    @Autowired
    private KnowledgeConsultationService consultationService;
    @Autowired
    private JwtTokenUtil jwtTokenUtil;

    //-------------------------------------会话列表详情

    @GetMapping("/sessions")
    public Result<IPage<ConsultationSessionResponseDTO>> getSessionList(
            @Valid ConsultationPageQueryDTO query,
            @AuthenticationPrincipal UserLoginResponseDTO.UserDetailResponseDTO user){
        log.debug("Id={}分页查询会话列表，currentPage={}，size={}",user.getId(),query.getCurrentPage(),query.getSize());
        return Result.success(consultationService.getConsultationPage(query,user));
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public Result<List<ConsultationMessageResponseDTO>> getSessionDetail(
            @PathVariable Long sessionId,
            @AuthenticationPrincipal UserLoginResponseDTO.UserDetailResponseDTO user){
        log.debug("Id={}查询心理会话消息,sessionId={}",user.getId(),sessionId);
        return Result.success(consultationService.getConsultationDetail(sessionId,user));
    }

    //-------------------------------------C端专用

    @PostMapping("/session/start")
    public Result<StructOutPut.StreamChatSession> startSession(@Valid @RequestBody ConsultationSectionCreateDTO createDTO){
        Long userId= jwtTokenUtil.getCurrentUserId();
        StructOutPut.StreamChatSession session=psychologicalSupportService.startSession(userId,createDTO);
        return Result.success(session);
    }

    @PostMapping(value="/stream",produces = MediaType.TEXT_EVENT_STREAM_VALUE)//produces定义返回类型
    public Flux<ServerSentEvent<String>> streamChat(@Valid @RequestBody ConsultationStreamDTO streamDTO){
        Long userId= jwtTokenUtil.getCurrentUserId();

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
                .delayElements(Duration.ofMillis(50));
    }

    @DeleteMapping("/sessions/{id}")
    public Result<Void> deleteSession(@PathVariable("id") Long sessionId,
                                      @AuthenticationPrincipal UserLoginResponseDTO.UserDetailResponseDTO user){
        log.debug("Id={}删除会话sessionId={}",user.getId(),sessionId);
        consultationService.deleteSession(sessionId,user);
        return Result.success();
    }

    @GetMapping("/session/{sessionId}/emotion")
    public Result<SessionEmotionResponseDTO> getSessionEmotion(
            @PathVariable String sessionId,
            @AuthenticationPrincipal UserLoginResponseDTO.UserDetailResponseDTO user){
        log.debug("Id={}查询会话情绪,sessionId={}",user.getId(),sessionId);
        return Result.success(consultationService.getSessionEmotion(parseSessionId(sessionId),user));
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

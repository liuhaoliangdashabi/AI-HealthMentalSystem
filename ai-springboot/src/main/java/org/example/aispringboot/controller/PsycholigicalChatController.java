package org.example.aispringboot.controller;

import cn.hutool.json.JSONUtil;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.example.aispringboot.AiService.PsychologicalSupportService;
import org.example.aispringboot.AiService.StructOutPut;
import org.example.aispringboot.DTO.command.ConsultationSectionCreateDTO;
import org.example.aispringboot.DTO.command.ConsultationStreamDTO;
import org.example.aispringboot.DTO.response.SessionEmotionResponseDTO;
import org.example.aispringboot.common.Result;
import org.example.aispringboot.common.ResultCode;
import org.example.aispringboot.exception.BusinessException;
import org.example.aispringboot.util.JwtTokenUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.Map;
@Slf4j
@RestController
@RequestMapping("/api/psychological-chat")
public class PsycholigicalChatController {
    @Autowired
    private PsychologicalSupportService psychologicalSupportService;
    @Autowired
    private JwtTokenUtil jwtTokenUtil;

//    @GetMapping("/sessions")
//    public Result<> aa(){
//
//        return Result.success();
//    }
//
//    @GetMapping("/sessions/{sessionId}")
//    public Result<> bb(@PathVariable Long sessionId){
//
//        return Result.success();
//    }

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
    public Result<Void> deleteSession(@PathVariable("id") Long sessionId){
        log.debug("删除会话sessionId={}",sessionId);
        return Result.success();
    }

    @GetMapping("/session/{sessionId}/emotion")
    public Result<SessionEmotionResponseDTO> getSessionEmotion(@PathVariable String sessionId){
        log.debug("查询会话情绪,sessionId={}",sessionId);
        return Result.success(null);
    }
}

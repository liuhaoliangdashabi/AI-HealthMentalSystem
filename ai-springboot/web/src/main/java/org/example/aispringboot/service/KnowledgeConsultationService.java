package org.example.aispringboot.service;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.example.aispringboot.DTO.command.ConsultationPageQueryDTO;
import org.example.aispringboot.DTO.response.ConsultationMessageResponseDTO;
import org.example.aispringboot.DTO.response.ConsultationSessionResponseDTO;
import org.example.aispringboot.DTO.response.SessionEmotionResponseDTO;
import org.example.aispringboot.DTO.response.UserLoginResponseDTO;
import org.example.aispringboot.entity.ConsultationMessage;
import org.example.aispringboot.entity.ConsultationSession;
import org.example.aispringboot.entity.User;
import org.example.aispringboot.enumClass.UserType;
import org.example.common.BusinessException;
import org.example.aispringboot.mapper.ConsultationMessageMapper;
import org.example.aispringboot.mapper.ConsultationSessionMapper;
import org.example.aispringboot.mapper.UserMapper;
import org.example.aispringboot.service.convert.ConsultationConvert;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class KnowledgeConsultationService {
    @Autowired
    private ConsultationSessionMapper sessionMapper;
    @Autowired
    private ConsultationMessageMapper messageMapper;
    @Autowired
    private UserMapper userMapper;

    /** 会话列表，AB 端和 C 端共用。管理员看全部，普通用户只看自己的。 */
    public IPage<ConsultationSessionResponseDTO> getConsultationPage(ConsultationPageQueryDTO query,
                                                                     UserLoginResponseDTO.UserDetailResponseDTO user) {
        boolean isAdmin = UserType.ADMIN.getCode().equals(user.getUserType());
        Long scopeUserId = isAdmin ? null : user.getId();   // null 表示不限制

        Page<ConsultationSession> page = new Page<>(query.getCurrentPage(), query.getSize());
        sessionMapper.selectPage(page, new LambdaQueryWrapper<ConsultationSession>()
                .eq(scopeUserId != null, ConsultationSession::getUserId, scopeUserId)
                .orderByDesc(ConsultationSession::getStartedAt));
        List<ConsultationSession> sessions = page.getRecords();
        if (sessions.isEmpty()) {
            return page.convert(s -> null);
        }

        Set<Long> userIds = sessions.stream()
                .map(ConsultationSession::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> nicknameMap = userIds.isEmpty() ? Map.of() :
                userMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(User::getId, User::getDisplayName));
        userIds.removeAll(nicknameMap.keySet());
        if(!userIds.isEmpty())log.warn("这些用户id不存在，可能是脏数据:{}",userIds);

        Set<Long> sessionIds = sessions.stream()
                .map(ConsultationSession::getId).collect(Collectors.toSet());
        List<ConsultationMessage> messages = messageMapper.selectList(
                new LambdaQueryWrapper<ConsultationMessage>()
                        .in(ConsultationMessage::getSessionId, sessionIds)
                        .orderByAsc(ConsultationMessage::getCreatedAt)
        );

        Map<Long, Integer> countMap = new HashMap<>();
        Map<Long, ConsultationMessage> lastMap = new HashMap<>();
        for (ConsultationMessage m : messages) {
            countMap.merge(m.getSessionId(), 1, Integer::sum);
            lastMap.put(m.getSessionId(), m);
        }

        return page.convert(s -> {
               ConsultationMessage last = lastMap.get(s.getId());
               return ConsultationConvert.sessionToResponse(
                       s, nicknameMap.get(s.getUserId()),
                       countMap.getOrDefault(s.getId(), 0),
                       last == null ? null : last.getContent(),
                       last == null ? null : last.getCreatedAt());
                }
        );

    }

    /** 会话消息列表，AB 端和 C 端共用，权限在这里判。 */
    public List<ConsultationMessageResponseDTO> getConsultationDetail(Long sessionId,
                                                                      UserLoginResponseDTO.UserDetailResponseDTO user) {
        ConsultationSession session=sessionMapper.selectById(sessionId);
        if(session==null){
            log.warn("{}会话不存在",sessionId);
            throw new BusinessException("会话不存在");
        }
        checkAccess(session,user);

        LambdaQueryWrapper<ConsultationMessage> qw=new LambdaQueryWrapper<>();
        qw.eq(ConsultationMessage::getSessionId,sessionId)
                .orderByAsc(ConsultationMessage::getCreatedAt)
                .orderByAsc(ConsultationMessage::getId);

        List<ConsultationMessage> messages=messageMapper.selectList(qw);
        return messages.stream().map(ConsultationConvert::messageToResponse).toList();
    }

    /** 删除会话，会话和消息两张表，必须在一个事务里，先删子表再删主表。 */
    @Transactional
    public void deleteSession(Long sessionId, UserLoginResponseDTO.UserDetailResponseDTO user) {
        ConsultationSession session=sessionMapper.selectById(sessionId);
        if(session==null){
            log.warn("要删除的会话不存在，sessionId={}",sessionId);
            throw new BusinessException("会话不存在");
        }
        checkAccess(session,user);

        int deleted=messageMapper.delete(new LambdaQueryWrapper<ConsultationMessage>()
                .eq(ConsultationMessage::getSessionId,sessionId));
        sessionMapper.deleteById(sessionId);
        log.info("用户{}删除会话{}，连带删除{}条消息",user.getId(),sessionId,deleted);
    }

    /** 会话最后的情绪分析结果。库里 last_emotion_analysis 是一段 JSON，直接反序列化。 */
    public SessionEmotionResponseDTO getSessionEmotion(Long sessionId,
                                                       UserLoginResponseDTO.UserDetailResponseDTO user) {
        ConsultationSession session=sessionMapper.selectById(sessionId);
        if(session==null){
            log.warn("{}会话不存在",sessionId);
            throw new BusinessException("会话不存在");
        }
        checkAccess(session,user);

        String json=session.getLastEmotionAnalysis();
        if(json==null || json.isBlank()){
            log.debug("会话{}还没有情绪分析结果",sessionId);
            //analyzed=false 是给前端轮询看的：它能明确区分「还没算」和「算出来是中性」
            SessionEmotionResponseDTO empty=normalize(SessionEmotionResponseDTO.builder().build());
            empty.setAnalyzed(false);
            return empty;
        }
        try{
            SessionEmotionResponseDTO dto=normalize(JSONUtil.toBean(json, SessionEmotionResponseDTO.class));
            dto.setAnalyzed(true);
            return dto;
        }catch(Exception e){
            log.warn("会话{}的情绪分析JSON解析失败，原内容={}",sessionId,json,e);
            throw new BusinessException("情绪分析数据格式异常");
        }
    }

    /** 补全 null 字段。前端有 improvementSuggestions.length 这类写法，字段为 null 会抛 TypeError 导致整页停止更新。 */
    private SessionEmotionResponseDTO normalize(SessionEmotionResponseDTO dto){
        if(dto.getImprovementSuggestions()==null) dto.setImprovementSuggestions(List.of());
        if(dto.getEmotionScore()==null) dto.setEmotionScore(50);
        if(dto.getRiskLevel()==null) dto.setRiskLevel(0);
        if(dto.getIsNegative()==null) dto.setIsNegative(false);
        return dto;
    }

    //越权校验：管理员放行，普通用户只能碰自己的会话
    private void checkAccess(ConsultationSession session, UserLoginResponseDTO.UserDetailResponseDTO user){
        boolean isAdmin=UserType.ADMIN.getCode().equals(user.getUserType());
        if(!isAdmin && !session.getUserId().equals(user.getId())){
            log.warn("userId={},userType={}不是该次会话的参与者，无权访问该会话",user.getId(),user.getUserType());
            throw new BusinessException("用户无权访问该会话");
        }
    }
}

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
import org.example.aispringboot.exception.BusinessException;
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

    /**
     * 会话列表 —— AB端和C端【共用同一个接口】
     * 差别只有可见范围：管理员看全部，普通用户只看自己的
     */
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

    /**
     * 会话消息列表 —— 同样AB端和C端共用一个接口，权限在这里判
     */
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

    /**
     * 删除会话 —— 两张表（会话 + 消息），所以必须在一个事务里
     * 结构和"删除知识分类"完全一样：先处理子表，再删主表
     */
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

    /**
     * 会话最后的情绪分析结果
     * 库里 last_emotion_analysis 存的就是一段 JSON，直接反序列化成 DTO
     */
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
            return normalize(SessionEmotionResponseDTO.builder().build());
        }
        try{
            return normalize(JSONUtil.toBean(json, SessionEmotionResponseDTO.class));
        }catch(Exception e){
            log.warn("会话{}的情绪分析JSON解析失败，原内容={}",sessionId,json,e);
            throw new BusinessException("情绪分析数据格式异常");
        }
    }

    /**
     * 补全 null 字段。
     * 前端模板里有 `currentEmotion.improvementSuggestions.length>0` 这种写法，
     * 一旦这个字段是 null，Vue 的渲染函数就抛 TypeError，【整个会话页会停止更新】——
     * 表现出来就是"点会话没反应"、"删了不掉"。所以这里保证集合类字段永远不是 null。
     */
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

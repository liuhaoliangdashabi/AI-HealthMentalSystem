package org.example.aispringboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.example.aispringboot.DTO.command.ConsultationPageQueryDTO;
import org.example.aispringboot.DTO.response.ConsultationMessageResponseDTO;
import org.example.aispringboot.DTO.response.ConsultationSessionResponseDTO;
import org.example.aispringboot.DTO.response.UserLoginResponseDTO;
import org.example.aispringboot.entity.ConsultationMessage;
import org.example.aispringboot.entity.ConsultationSession;
import org.example.aispringboot.entity.User;
import org.example.aispringboot.enumClass.UserType;
import org.example.aispringboot.exception.BusinessException;
import org.example.aispringboot.mapper.ConsultationMessageMapper;
import org.example.aispringboot.mapper.ConsultationSessionMapper;
import org.example.aispringboot.mapper.UserMapper;
import org.example.aispringboot.service.convert.KnowledgeConvert;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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

    public IPage<ConsultationSessionResponseDTO> getConsultationPage(@Valid ConsultationPageQueryDTO query) {
        Page<ConsultationSession> page = new Page<>(query.getCurrentPage(), query.getSize());

        sessionMapper.selectPage(page, new LambdaQueryWrapper<ConsultationSession>().
                orderByDesc(ConsultationSession::getStartedAt));
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
               return KnowledgeConvert.ConsultationSessionToResponse(
                       s, nicknameMap.get(s.getUserId()),
                       countMap.getOrDefault(s.getId(), 0),
                       last == null ? null : last.getContent(),
                       last == null ? null : last.getCreatedAt());
                }
        );

    }

    public List<ConsultationMessageResponseDTO> getConsultationDetail(Long sessionId,
                                                                      UserLoginResponseDTO.UserDetailResponseDTO user) {
        ConsultationSession session=sessionMapper.selectById(sessionId);
        if(session==null){
            log.warn("{}会话不存在",sessionId);
            throw new BusinessException("会话不存在");
        }
        boolean isAdmin= UserType.ADMIN.getCode().equals(user.getUserType());
        if(!isAdmin && !session.getUserId().equals(user.getId())){
            log.warn("userId={},userType={}不是该次会话的参与者，无权访问该会话",user.getId(),user.getUserType());
            throw new BusinessException("用户无权访问该会话");
        }

        LambdaQueryWrapper<ConsultationMessage> qw=new LambdaQueryWrapper<>();
        qw.eq(ConsultationMessage::getSessionId,sessionId)
                .orderByAsc(ConsultationMessage::getCreatedAt)
                .orderByAsc(ConsultationMessage::getId);

        List<ConsultationMessage> messages=messageMapper.selectList(qw);
        return messages.stream().map(KnowledgeConvert::messageToResponse).toList();
    }
}

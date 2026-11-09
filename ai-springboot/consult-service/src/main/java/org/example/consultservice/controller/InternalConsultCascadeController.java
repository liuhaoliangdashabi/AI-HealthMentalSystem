package org.example.consultservice.controller;

import lombok.extern.slf4j.Slf4j;
import org.example.consultservice.service.KnowledgeConsultationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.parameters.P;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/internal/consult")
public class InternalConsultCascadeController {
    @Autowired
    private KnowledgeConsultationService consultationService;

    @DeleteMapping("/by-user/{userId}/{targetId}")
    public int deleteById(@PathVariable("userId")Long userId,
                          @PathVariable("targetId")Long targetId){
        log.info("user-serviceId={}请求级联删除Id={}的会话",userId,targetId);
        return consultationService.deleteByUserId(targetId);
    }
}

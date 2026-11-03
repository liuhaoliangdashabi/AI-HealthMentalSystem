package org.example.consultservice.controller;

import lombok.extern.slf4j.Slf4j;
import org.example.consultservice.DTO.response.ConsultStatDTO;
import org.example.consultservice.entity.ConsultationMessage;
import org.example.consultservice.service.ConsultStatService;
import org.example.consultservice.service.ConsultationMessageService;
import org.example.consultservice.service.ConsultationSessionsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/internal/consult/stats")
public class InternalConsultController {
    @Autowired
    private ConsultStatService statService;

    @GetMapping("/overview")
    public ConsultStatDTO overview(@RequestParam(defaultValue = "30")int days){
        log.debug("Analytics部分发出请求，查询Consult部分数据");
        return statService.overview(days);
    }
}

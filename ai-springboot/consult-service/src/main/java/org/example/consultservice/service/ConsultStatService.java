package org.example.consultservice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.consultservice.DTO.response.ConsultDailyRow;
import org.example.consultservice.DTO.response.ConsultStatDTO;
import org.example.consultservice.entity.ConsultationSession;
import org.example.consultservice.mapper.ConsultationSessionMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class ConsultStatService {
    @Autowired
    private ConsultationSessionMapper sessionMapper;
    public ConsultStatDTO overview(int days) {
        LocalDate today=LocalDate.now();
        LocalDate since=today.minusDays(Math.max(1,days)-1L);

        ConsultStatDTO dto=new ConsultStatDTO();
        dto.setTotal(sessionMapper.selectCount(null));
        dto.setTodayNew(sessionMapper.selectCount(
                new LambdaQueryWrapper<ConsultationSession>()
                        .ge(ConsultationSession::getStartedAt,today.atStartOfDay())
                        .lt(ConsultationSession::getStartedAt,today.plusDays(1).atStartOfDay())));
        dto.setAvgDurationMinutes(sessionMapper.avgDurationMinutes());

        for(ConsultDailyRow row:sessionMapper.dailySince(since)){
            ConsultStatDTO.Daily d=new ConsultStatDTO.Daily();
            d.setCount(row.getCnt());
            d.setUserCount(row.getUserCount());
            dto.getDaily().put(row.getStatDate().toString(),d);
        }

        return dto;
    }
}

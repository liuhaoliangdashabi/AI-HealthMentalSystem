package org.example.consultservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.consultservice.entity.ConsultationSession;

@Mapper
public interface ConsultationSessionMapper extends BaseMapper<ConsultationSession> {
}

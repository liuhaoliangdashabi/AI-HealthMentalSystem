package org.example.consultservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.consultservice.entity.ConsultationMessage;

@Mapper
public interface ConsultationMessageMapper extends BaseMapper<ConsultationMessage> {
}

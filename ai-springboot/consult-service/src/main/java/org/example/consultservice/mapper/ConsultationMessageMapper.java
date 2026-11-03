package org.example.consultservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.example.consultservice.entity.ConsultationMessage;
import org.springframework.stereotype.Service;

@Mapper
public interface ConsultationMessageMapper extends BaseMapper<ConsultationMessage> {

}

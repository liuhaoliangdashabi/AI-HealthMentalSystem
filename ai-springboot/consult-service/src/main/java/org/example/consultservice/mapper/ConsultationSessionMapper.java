package org.example.consultservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.example.consultservice.DTO.response.ConsultDailyRow;
import org.example.consultservice.entity.ConsultationSession;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface ConsultationSessionMapper extends BaseMapper<ConsultationSession> {
    @Select("select round(avg(timestampdiff(minute,s.started_at,m.last_at)),1) "+
            " from mental_health_consult.consultation_session s "+
            "join(select session_id,max(created_at) last_at "+
                "from mental_health_consult.consultation_message group by session_id) m on m.session_id=s.id")
    Double avgDurationMinutes();

    @Select("select date(started_at) as statDate,count(*) as cnt,count(distinct user_id) as userCount "+
            "from mental_health_consult.consultation_session where started_at>=#{since} group by date(started_at)")
    List<ConsultDailyRow> dailySince(@Param("since")LocalDate since);
}

package org.example.aispringboot.DTO.response;

import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class ConsultStatDTO {
    private long total;
    private long todayNew;
    private Double avgDurationMinutes;
    private Map<String,Daily> daily=new HashMap<>();

    @Data
    public static class Daily{
        private long count;
        private Double avgMoodScore;
        private long userCount;
    }

    public static ConsultStatDTO empty() {
        return new ConsultStatDTO();
    }
}

package org.example.consultservice.DTO.response;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class ConsultStatDTO {
    private long total;
    private long today;
    private Double avgDurationMinutes;
    private Map<String,Daily> daily=new HashMap<>();

    @Data
    public static class Daily{
        private long count;
        private Double avgMoodScore;
    }

    public static ConsultStatDTO empty() {
        return new ConsultStatDTO();
    }
}

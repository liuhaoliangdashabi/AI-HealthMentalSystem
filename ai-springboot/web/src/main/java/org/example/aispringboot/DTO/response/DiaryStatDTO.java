package org.example.aispringboot.DTO.response;

import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class DiaryStatDTO {
    private long total;
    private long todayNew;
    private Double avgMoodScore;
    private Map<String,Daily> daily;

    @Data
    public static class Daily{
        private long count;
        private Double avgMoodScore;
        private List<Long> userIds=new ArrayList<>();
    }

    public static DiaryStatDTO empty() {
        return new DiaryStatDTO();
    }
}

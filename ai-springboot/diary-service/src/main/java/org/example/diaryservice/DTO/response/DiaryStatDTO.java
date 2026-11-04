package org.example.diaryservice.DTO.response;

import lombok.Data;
import java.util.HashMap;
import java.util.Map;

@Data
public class DiaryStatDTO {
    private long total;
    private long todayNew;
    private Double avgMoodScore;
    private Map<String,Daily> daily=new HashMap<>();

    @Data
    public static class Daily{
        private long count;
        private Double avgMoodScore;
        private long userCount;
    }

    public static DiaryStatDTO empty() {
        return new DiaryStatDTO();
    }
}

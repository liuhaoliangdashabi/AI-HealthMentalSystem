package org.example.aispringboot.DTO.response;

import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

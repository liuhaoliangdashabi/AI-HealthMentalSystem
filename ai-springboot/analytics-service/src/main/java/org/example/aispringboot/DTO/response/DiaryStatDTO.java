package org.example.aispringboot.DTO.response;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
public class DiaryStatDTO {
    private long total;
    private long todayNew;
    private Double avgMoodScore;
    private Map<String,Daily> daily=new HashMap<>();

    private boolean degraded=false;

    @Data
    public static class Daily{
        private long count;
        private Double avgMoodScore;
        private long userCount;
    }

    public DiaryStatDTO(boolean degraded) {
        this.degraded=degraded;
    }

    public static DiaryStatDTO empty() {
        return new DiaryStatDTO(true);
    }
}

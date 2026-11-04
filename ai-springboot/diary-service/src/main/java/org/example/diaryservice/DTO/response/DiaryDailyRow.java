package org.example.diaryservice.DTO.response;

import lombok.Data;

import java.time.LocalDate;

@Data
public class DiaryDailyRow {
    private LocalDate diaryDate;
    private Long cnt;
    private Double avgMood;
    private Long userCount;
}


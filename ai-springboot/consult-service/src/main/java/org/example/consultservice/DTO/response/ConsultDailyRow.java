package org.example.consultservice.DTO.response;

import lombok.Data;

import java.time.LocalDate;

@Data
public class ConsultDailyRow {
    private LocalDate statDate;
    private Long cnt;
    private Long userCount;
}

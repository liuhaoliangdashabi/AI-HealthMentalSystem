package org.example.aispringboot.DTO.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UvTrendItem {
    private String date;
    private Long activeUsers;
}
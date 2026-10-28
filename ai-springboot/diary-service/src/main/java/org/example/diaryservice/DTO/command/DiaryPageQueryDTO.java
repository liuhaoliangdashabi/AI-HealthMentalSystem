package org.example.diaryservice.DTO.command;

import lombok.Data;

@Data
public class DiaryPageQueryDTO {
    private Integer current=1;
    private Integer size=5;
    private Long userId;
    private Integer minMoodScore;
    private Integer maxMoodScore;
    private String dominantEmotion;

    public void setCurrent(Integer current) {
        if(current!=null)this.current = current;
    }

    public void setSize(Integer size) {
        if(size!=null)this.size = Math.min(size,50);
    }
}

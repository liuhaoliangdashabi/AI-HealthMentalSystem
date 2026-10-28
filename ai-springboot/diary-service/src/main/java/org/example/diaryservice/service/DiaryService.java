package org.example.diaryservice.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.example.diaryservice.DTO.command.DiaryPageQueryDTO;
import org.example.diaryservice.DTO.response.DiaryResponseDTO;
import org.springframework.stereotype.Service;

@Service
public class DiaryService {
    public IPage<DiaryResponseDTO> getEmotionalPage(DiaryPageQueryDTO queryDTO, Long userId, boolean b) {
        return null;
    }

    public void deleteEmotionDiary(Long diaryId, Long userId, boolean b) {
    }
}

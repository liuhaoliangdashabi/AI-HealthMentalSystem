package org.example.aispringboot.DTO.command;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;

@Data
public class FileCommandDTO {
    MultipartFile file;
    String businessType;
    String businessId;
    String businessField;
}

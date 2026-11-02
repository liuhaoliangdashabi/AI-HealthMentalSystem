package org.example.fileservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.example.fileservice.entity.SysFileInfo;

@Mapper
public interface FileMapper extends BaseMapper<SysFileInfo> {
}

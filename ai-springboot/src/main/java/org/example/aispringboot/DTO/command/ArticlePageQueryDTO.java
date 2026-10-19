package org.example.aispringboot.DTO.command;

import lombok.Data;

@Data
public class ArticlePageQueryDTO {
    private Integer currentPage=1;
    private Integer size=5;
    private String title;
    private Long categoryId;
    private Integer status;
    private String sortField;
    private String sortDirection;

    public void setCurrentPage(Integer currentPage) {
        //页码传 0 或负数没意义，保持默认 1
        if(currentPage!=null && currentPage>0)this.currentPage = currentPage;
    }

    public void setSize(Integer size) {
        //必须封顶：不封的话 ?size=100000 一次就能把整张表拉进内存
        if(size!=null)this.size = Math.min(size, 50);
    }
}

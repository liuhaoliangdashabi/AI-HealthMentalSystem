package org.example.aispringboot.DTO.command;

import lombok.Data;

@Data
public class ConsultationPageQueryDTO {
    private Integer currentPage=1;
    private Integer size=5;

    public void setCurrentPage(Integer currentPage) {
        if(currentPage!=null)this.currentPage = currentPage;
    }

    public void setSize(Integer size) {
        if(size!=null)this.size = size;
    }

    //AB端前端传 currentPage / size，C端前端传 pageNum / pageSize —— 同一个接口要都接住。
    //Spring 绑定看的是 setter 不是字段，所以这两个没有对应字段的 setter 照样能被绑定。
    public void setPageNum(Integer pageNum) {
        if(pageNum!=null)this.currentPage = pageNum;
    }

    public void setPageSize(Integer pageSize) {
        if(pageSize!=null)this.size = pageSize;
    }
}

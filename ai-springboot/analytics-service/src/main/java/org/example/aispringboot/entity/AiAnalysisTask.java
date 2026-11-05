package org.example.aispringboot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.aispringboot.enumClass.TaskPriority;
import org.example.aispringboot.enumClass.TaskStatus;
import org.example.aispringboot.enumClass.TaskType;

import java.time.Duration;
import java.time.LocalDateTime;

@Data
@TableName("ai_analysis_task")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiAnalysisTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    //Long 类型要用 @NotNull
    @NotNull(message = "日记不能为空")
    @TableField("diary_id")
    private Long diaryId;

    @NotNull(message = "用户不能为空")
    @TableField("user_id")
    private Long userId;

    //PENDING / PROCESSING / COMPLETED / FAILED——见 TaskStatus
    @NotBlank(message = "任务状态不能为空")
    @Pattern(regexp = "PENDING|PROCESSING|COMPLETED|FAILED", message = "任务状态只有四种取值")
    private String status;

    //AUTO / MANUAL / ADMIN / BATCH——见 TaskType
    @NotBlank(message = "任务类型不能为空")
    @TableField("task_type")
    @Pattern(regexp = "AUTO|MANUAL|ADMIN|BATCH", message = "任务类型只有四种取值")
    private String taskType;

    //1:低 2:正常 3:高 4:紧急——见 TaskPriority
    @NotNull(message = "优先级不能为空")
    @Min(value = 1, message = "优先级只有 1~4")
    @Max(value = 4, message = "优先级只有 1~4")
    private Integer priority;

    @NotNull(message = "重试次数不能为空")
    @TableField("retry_count")
    @Min(value = 0, message = "重试次数不能是负数")
    private Integer retryCount;

    @NotNull(message = "最大重试次数不能为空")
    @TableField("max_retry_count")
    @Min(value = 0, message = "最大重试次数不能是负数")
    private Integer maxRetryCount;

    @TableField("error_message")
    private String errorMessage;

    @TableField("started_at")
    private LocalDateTime startedAt;

    @TableField("completed_at")
    private LocalDateTime completedAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    //==================== 封装方法 ====================

    public boolean isPending() {
        return TaskStatus.PENDING.getCode().equals(this.status);
    }

    public boolean isProcessing() {
        return TaskStatus.PROCESSING.getCode().equals(this.status);
    }

    public boolean isCompleted() {
        return TaskStatus.COMPLETED.getCode().equals(this.status);
    }

    public boolean isFailed() {
        return TaskStatus.FAILED.getCode().equals(this.status);
    }

    //是否为终态——跑完了，调度器不用再捞
    public boolean isFinished() {
        return isCompleted() || isFailed();
    }

    //能不能重试——必须是失败状态，且次数还没到上限
    public boolean canRetry() {
        if (!isFailed()) {
            return false;
        }
        if (retryCount == null || maxRetryCount == null) {
            return false;
        }
        return retryCount < maxRetryCount;
    }

    //状态的中文名
    public String getStatusDisplayName() {
        try {
            return TaskStatus.fromCode(this.status).getDescription();
        } catch (IllegalArgumentException e) {
            return "未知";
        }
    }

    //任务类型的中文名
    public String getTaskTypeDisplayName() {
        try {
            return TaskType.fromCode(this.taskType).getDescription();
        } catch (IllegalArgumentException e) {
            return "未知";
        }
    }

    //优先级的中文名
    public String getPriorityDisplayName() {
        try {
            return TaskPriority.fromCode(this.priority).getDescription();
        } catch (IllegalArgumentException e) {
            return "未知";
        }
    }

    //处理耗时（毫秒）——没开始或没结束返回 null
    public Long getDurationMillis() {
        if (startedAt == null || completedAt == null) {
            return null;
        }
        return Duration.between(startedAt, completedAt).toMillis();
    }
}

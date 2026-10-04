package com.ri.artificial.domain.po;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * @author Ri
 * @date 2026-10-01 11:19
 */
@Data
@Accessors(chain = true)
@TableName("sys_knowledge")
public class SysKnowledge {
    // 雪花 ID 为 64 位，必须用 Long 承接；用 Integer 会被截断（含符号位），既不唯一也可能为负
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 归属用户id（区分不同用户的知识库数据） */
    private Long userId;
    private String vectorId;
    private String fileName;
    private String fileUrl;
    private Long fileSize;
    private Integer chunkCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

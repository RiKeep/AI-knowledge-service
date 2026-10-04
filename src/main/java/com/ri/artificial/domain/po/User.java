package com.ri.artificial.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @author Ri
 * @date 2026-10-01 19:47
 */
@Data
@Accessors(chain = true)
@TableName("sys_user")
public class User {
    // 雪花 ID 为 64 位，必须用 Long 承接；用 Integer 会被截断（含符号位），既不唯一也可能为负
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    private String username;
    private String password;
}

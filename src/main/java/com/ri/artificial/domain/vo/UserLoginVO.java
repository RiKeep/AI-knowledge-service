package com.ri.artificial.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * @author Ri
 * @date 2026-10-01 20:22
 */
@Data
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
public class UserLoginVO {
    private Long userId;
    private Long expire;
    private String token;
}

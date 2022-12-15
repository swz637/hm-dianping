package com.hmdp.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * @author 637
 */
@Data
public class UserDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long id;
    private String nickName;
    private String icon;
    private Integer followCount;
    private Integer fansCount;
}

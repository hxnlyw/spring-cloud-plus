package org.gourd.hu.rbac.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.gourd.hu.base.common.entity.BaseEntity;

/**
 * @author gourd
 * @date 2019-04-02 17:26:16
 * Created by CodeGen .
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("rbac_user_role")
public class RbacUserRole extends BaseEntity {

    /**
     * 用户id
     */
    @TableField("user_id")
    private  Long userId;

    /**
     * 角色id
     */
    @TableField("role_id")
    private  Long roleId;


}
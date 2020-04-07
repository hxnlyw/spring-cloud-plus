package org.gourd.hu.rbac.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import org.gourd.hu.base.common.exception.BusinessException;
import org.gourd.hu.cache.utils.RedisUtil;
import org.gourd.hu.core.constant.MessageConstant;
import org.gourd.hu.rbac.service.AuthService;
import org.gourd.hu.rbac.auth.jwt.JwtClaim;
import org.gourd.hu.rbac.auth.jwt.JwtToken;
import org.gourd.hu.rbac.auth.jwt.JwtUtil;
import org.gourd.hu.rbac.constant.JwtConstant;
import org.gourd.hu.rbac.dao.RbacPermissionDao;
import org.gourd.hu.rbac.dao.RbacRoleDao;
import org.gourd.hu.rbac.dao.RbacUserDao;
import org.gourd.hu.rbac.dto.RbacUserRegisterDTO;
import org.gourd.hu.rbac.entity.RbacPermission;
import org.gourd.hu.rbac.entity.RbacRole;
import org.gourd.hu.rbac.entity.RbacUser;
import org.gourd.hu.rbac.entity.SysTenant;
import org.gourd.hu.rbac.service.RbacUserService;
import org.gourd.hu.rbac.service.SysTenantService;
import org.gourd.hu.rbac.utils.ShiroKitUtil;
import org.gourd.hu.rbac.vo.UserVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author: gourd
 * createAt: 2018/9/17
 */
@Service
@Slf4j
@Transactional(rollbackFor = Exception.class)
public class AuthServiceImpl implements AuthService {

    @Autowired
    private RbacUserService rbacUserService;
    @Autowired
    private SysTenantService sysTenantService;
    @Autowired
    private RbacUserDao rbacUserDao;
    @Autowired
    private RbacPermissionDao rbacPermissionDao;
    @Autowired
    private RbacRoleDao rbacRoleDao;


    @Override
    public UserVO register(RbacUserRegisterDTO rbacUserDTO) {
        return rbacUserService.register(rbacUserDTO);
    }

    @Override
    @DS("slave")
    public JwtToken login(String account, String password) {
        String[] accountItems = StringUtils.split(account, "@");
        // 账号元素
        String accountItem = accountItems[0];
        // 承租人元素（number或code）
        String tenantItem = accountItems[1];
        SysTenant tenant = sysTenantService.checkGetTenant(tenantItem);
        // 从数据库中取出用户信息
        RbacUser user = rbacUserDao.getByAccountAndTenantId(accountItem,tenant.getId());
        // 判断用户是否存在
        if(user == null) {
            throw new BusinessException(MessageConstant.DATA_NOT_FOUND);
        }
        if(!user.getPassword().equals(ShiroKitUtil.md5(password,user.getAccount()))){
            throw new BusinessException(MessageConstant.ACCOUNT_PWD_ERROR);
        }
        // 添加权限
        List<RbacRole> rbacRoleList = rbacRoleDao.findByUserId(user.getId());
        // 角色
        Set<String> roleCodes = rbacRoleList.stream().map(e -> e.getCode()).collect(Collectors.toSet());
        String[] roleCodeArray = new String[roleCodes.size()];
        roleCodes.toArray(roleCodeArray);
        // 权限
        String[] permissionArray = new String[4];
        roleCodes.toArray(roleCodeArray);
        if(CollectionUtils.isNotEmpty(roleCodes)){
            List<Long> roleIds = rbacRoleList.stream().map(e -> e.getId()).collect(Collectors.toList());
            // 得到用户角色的所有角色所有的权限
            List<RbacPermission> permissionList = rbacPermissionDao.findByRoleIds(roleIds);
            Set<String> permissionCodes = permissionList.stream().map(e -> e.getCode()).collect(Collectors.toSet());
            permissionArray =  new String[permissionCodes.size()];
            permissionCodes.toArray(permissionArray);
        }

        Long currentTimeMillis = System.currentTimeMillis();
        JwtUtil.setRefresh(user.getId().toString(),currentTimeMillis);
        // 生成token
        JwtClaim jwtClaim = new JwtClaim();
        jwtClaim.setUserName(user.getName());
        jwtClaim.setAccount(user.getAccount());
        jwtClaim.setRoles(roleCodeArray);
        jwtClaim.setPermissions(permissionArray);
        jwtClaim.setCurrentTimeMillis(currentTimeMillis);
        jwtClaim.setTenantId(user.getTenantId());
        String accessToken = JwtUtil.generateToken(user.getId().toString(), jwtClaim);
        JwtToken jwtUser = new JwtToken(accessToken,user.getId(),user.getName());

        return jwtUser;
    }

    @Override
    public void logout(String token) {
        Subject subject = SecurityUtils.getSubject();
        subject.logout();
        String userId = JwtUtil.getSubject(token);
        RedisUtil.del(JwtConstant.PREFIX_SHIRO_REFRESH_TOKEN + userId);
        RedisUtil.del(JwtConstant.PREFIX_SHIRO_ACCESS_TOKEN + userId);
    }


}

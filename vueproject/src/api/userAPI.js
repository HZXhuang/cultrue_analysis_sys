import {req} from "../utils/request";

// 根据关键词查询用户信息
export const getUserInfoByPage = (params)=>{
  return req("get", "/user/byPage", params, null)
};
// 更新用户信息
export const updateUserInfo = params=>{
  return req("put", "/user/update", null, params)
};

// 根据用户id删除指定的用户
export const deleteUser = params=>{
  return req("delete", "/user/delete/" + params, null, null)
};

// 根据用户id重置指定用户的密码为123456
export const resetUserPwd = params=>{
  return req("put", "/user/resetPwd", {userId: params}, null)
};

// 用户修改密码的接口，需要原来的密码，参数放在请求数据体中 参数userId和password
export const updateUserPassword = params=>{
  return req("put", "/user/pass", null, params)
};

// 用户登录的接口
export const userLogin = params=>{
  return req("post", "/user/login", null, params)
};

// 用户注册的接口
export const userRegister = params=>{
  return req("post", "/user/register", null, params)
};

// 导出全部用户信息为Excel文件的接口
export const exportUserInfo=  params=>{
  return req("get", "/user/export", null, null)
};

// 导出用于导入用户信息的Excel模板的接口
export const exportUserInfoTemplate = params=>{
  return req("get", "/user/importTemplate", null, null)
};
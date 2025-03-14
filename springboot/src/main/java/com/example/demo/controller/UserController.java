package com.example.demo.controller;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.poi.excel.ExcelUtil;
import cn.hutool.poi.excel.ExcelWriter;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.demo.common.Result;
import com.example.demo.entity.*;
import com.example.demo.entity.UserRecord;
import com.example.demo.entity.model.LoginUser;
import com.example.demo.entity.vo.ResponseUserVO;
import com.example.demo.enums.PwdEnum;
import com.example.demo.enums.ResponseStatusEnum;
import com.example.demo.mapper.CoreUserMapper;
import com.example.demo.mapper.UserMapper;
import com.example.demo.mapper.UserRecordMapper;
import com.example.demo.service.DataAnalysisService;
import com.example.demo.utils.JwtUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.util.*;

@RestController
@RequestMapping("/api/user")
@Api(tags = "用户控制器")
public class UserController extends BaseController {

    @Resource
    UserMapper userMapper;

    @Autowired
    CoreUserMapper coreUserMapper;

    @Autowired
    private BCryptPasswordEncoder bCryptPasswordEncoder; //注入bcrypt加密

    @Autowired
    private UserRecordMapper userRecordMapper;

    @Autowired
    private DataAnalysisService dataAnalysisService;

    @Autowired
    UserDetailsService userDetailsService;

    @Autowired
    private JwtUtil jwtUtil;

    private int currentCnt = 0; // 记录所有用户的浏览作品次数

    private static final int cntLimit = 10; // 浏览次数限制，超过这个值就更新用户的推荐作品，运行推荐算法

    @PostMapping("/login")
    @ApiOperation(value = "用户登录")
    public Result login(@RequestBody User userParam) {
        User res = userMapper.selectByUsername(userParam.getUsername());
        /*QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", userParam.getUsername());
        queryWrapper.eq("password", userPwd.getPassword());
        User res = userMapper.selectOne(queryWrapper);*/


        if (res == null) {
            return Result.error("-1", "用户不存在！");
        }
        // 判断密码是否正确
        if (!bCryptPasswordEncoder.matches(userParam.getPassword(), res.getPassword())) {
            return Result.error("-1", "密码错误！");
        }

        // 生成token
//        String token = JwtUtil.genToken(res);
//        res.setToken(token);
        return Result.success(res);
    }

    @PostMapping("/register")
    @ApiOperation(value = "用户注册")
    public Result register(@RequestBody User user) {
        User res = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, user.getUsername()));
        if (res != null) {
            return Result.error("-1", "该用户名已存在");
        }
        String password = user.getPassword();
        if (password == null || "".equals(password.trim())) { // 用户输入的密码为空或者空字符串，设置为默认的密码
            user.setPassword("123456");
        }
        String name = user.getName();
        if (name == null || "".equals(name.trim())) name = "用户" + IdWorker.getId();
        User userInfo = User.builder()
                .username(user.getUsername())
                .password(bCryptPasswordEncoder.encode(user.getPassword())) // 加密
                .name(name)
                .workUnit(user.getWorkUnit())
                .email(user.getEmail())
                .phone(user.getPhone())
                .build();

        userMapper.insert(userInfo);

        return Result.success();
    }

    // 更新用户的信息  根据id
    @PutMapping("/update")
    @ApiOperation(value = "修改用户信息")
    public Result update(@RequestBody User user) {
        /*if (user.getPassword() == null) {
            user.setPassword(bCryptPasswordEncoder.encode(PwdEnum.PASSWORD.getPassword()));
        }*/
        userMapper.updateById(user);
        return Result.success();
    }

    // 删除用户
    @DeleteMapping("/delete/{ids}")
    @ApiOperation(value = "根据ID删除用户")
    @ApiImplicitParam(name = "ids", value = "用户ID数组")
    public Result delete(@PathVariable Integer[] ids) {
        int res = userMapper.deleteBatchIds(Arrays.asList(ids));
        if (res > 0) {
            return Result.success();
        }
        return Result.error("-1", "删除失败！");
    }

    // 更改用户密码
    @PutMapping("/pass")
    @ApiOperation(value = "更改用户密码")
    public Result pass(@RequestBody Map<String, Object> map) {
        // 从上下文中取出Authentication认证对象
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        LoginUser loginUser = (LoginUser) authentication.getPrincipal(); // 获取用户对象
        String password = loginUser.getPassword(); // 获取用户的密码
        if (!JwtUtil.matchPassword(map.get("oldPwd").toString(), password)) {
            return Result.error("-1", "修改密码失败，旧密码错误！");
        }
        if (JwtUtil.matchPassword(map.get("newPwd").toString(), password)) {
            return Result.error("-1", "新密码不能与旧密码相同！");
        }
        map.put("newPwd", bCryptPasswordEncoder.encode(map.get("newPwd").toString()));
        map.put("id", loginUser.getUser().getId());
        int res = userMapper.updatePass(map);
        if (res > 0) {
            // 设置新的密码
            loginUser.getUser().setPassword(map.get("newPwd").toString());
            // 刷新redis缓存中的用户信息 密码
            jwtUtil.setLoginUser(loginUser);
            return Result.success();
        } else {
            return Result.error(ResponseStatusEnum.ERROR);
        }

    }

    // 重置用户的密码
    @PutMapping("/resetPwd")
    @ApiOperation(value = "重置用户密码")
    public Result resetPwd(@RequestParam Integer userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.error("-1", "未找到用户");
        }
        // 重置密码
        user.setPassword(bCryptPasswordEncoder.encode(PwdEnum.PASSWORD.getPassword()));
        userMapper.updateById(user);
        return Result.success();
    }

    // 获取当前登录的用户个人信息
    @GetMapping("/profile")
    @ApiOperation(value = "查询当前登录的用户个人信息")
    public Result getProfile() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        LoginUser loginUser = (LoginUser) authentication.getPrincipal(); // 获取用户对象
        if (loginUser == null) {
            return Result.error("-1", "用户不存在");
        }
        // 重新从数据库中查询
        LoginUser newLoginUser = (LoginUser) userDetailsService.loadUserByUsername(loginUser.getUsername());
        ResponseUserVO responseUserVO = new ResponseUserVO(newLoginUser);
        return Result.success(responseUserVO);
    }

    @GetMapping("/id/{id}")
    @ApiOperation(value = "根据ID查询用户信息")
    public Result getById(@PathVariable Long id) {
        return Result.success(userMapper.selectById(id));
    }

    @GetMapping("/all")
    @ApiOperation(value = "查询所有用户信息")
    public Result findAll() {
        return Result.success(userMapper.selectList(null));
    }

    /**
     * 统计数据
     *
     * @return
     */
    @GetMapping("/countWorkUnit")
    @ApiOperation(value = "统计用户部门分布信息")
    public Result count() {
        return Result.success(userMapper.countWorkUnit());
    }

    /**
     * 用户分页列表查询
     *
     * @param pageNum 页号
     * @param pageSize 页大小
     * @param searchUsername 搜索关键词  用户名
     * @param searchName 搜索关键词  用户昵称
     * @return 结果
     */
    @GetMapping("/byPage")
    @ApiOperation(value = "分页模糊查询用户信息")
    @PreAuthorize("hasRole('ROLE_admin')")
    public Result findPage(@RequestParam(defaultValue = "1") Integer pageNum,
                              @RequestParam(defaultValue = "10") Integer pageSize,
                              @RequestParam(defaultValue = "") String searchUsername,
                              @RequestParam(defaultValue = "") String searchName) {

        Page<User> userPage = userMapper.findPage(new Page<>(pageNum, pageSize), searchUsername, searchName);

        return Result.success(userPage);
    }

    /**
     * Excel导出
     *
     * @param response 响应对象
     * @throws IOException
     */
    @RequestMapping(value = "/export", method = {RequestMethod.GET, RequestMethod.POST})
    @ApiOperation(value = "导出所有用户信息")
    public void export(HttpServletResponse response) throws IOException {

        List<Map<String, Object>> list = CollUtil.newArrayList();

        List<User> all = userMapper.selectList(null);
        for (User user : all) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("用户名", user.getUsername());
            row.put("昵称", user.getName());
            row.put("工作单位", user.getWorkUnit());
            row.put("邮箱", user.getEmail());
            row.put("电话号码", user.getPhone());
            list.add(row);
        }

        // 2. 写excel
        ExcelWriter writer = ExcelUtil.getWriter(true);
        writer.write(list, true);

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet;charset=utf-8");
        String fileName = URLEncoder.encode("用户信息", "UTF-8");
        response.setHeader("Content-Disposition", "attachment;filename=" + fileName + ".xlsx");

        ServletOutputStream out = response.getOutputStream();
        writer.flush(out, true);
        writer.close();
        IoUtil.close(System.out);
    }

    /**
     * Excel导出  模板
     *
     * @param response 响应对象
     * @throws IOException IO异常
     */
    @RequestMapping(value = "/importTemplate", method = {RequestMethod.GET, RequestMethod.POST})
    @ApiOperation(value = "导出用户信息导入模板")
    public void importTemplate(HttpServletResponse response) throws IOException {

        List<Map<String, Object>> list = CollUtil.newArrayList();

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("用户名", "");
        row.put("昵称", "");
        row.put("工作单位", "");
        row.put("邮箱", "");
        row.put("电话号码", "");
        list.add(row);

        // 2. 写excel
        ExcelWriter writer = ExcelUtil.getWriter(true);
        writer.write(list, true);

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet;charset=utf-8");
        String fileName = URLEncoder.encode("用户信息导入模板", "UTF-8");
        response.setHeader("Content-Disposition", "attachment;filename=" + fileName + ".xlsx");

        ServletOutputStream out = response.getOutputStream();
        writer.flush(out, true);
        writer.close();
        IoUtil.close(System.out);
    }

    /**
     * Excel导入
     *
     * @param file Excel
     * @return
     * @throws IOException
     */
    @PostMapping("/import")
    @ApiOperation(value = "导入用户信息")
    public Result upload(MultipartFile file) throws IOException {
        InputStream inputStream = file.getInputStream();
        List<List<Object>> lists = ExcelUtil.getReader(inputStream).read(1);
        List<User> saveList = new ArrayList<>();
        for (List<Object> row : lists) {
            User user = new User();
            user.setUsername(row.get(0).toString());
            user.setName(row.get(1).toString());
            user.setWorkUnit(row.get(2).toString());
            user.setEmail(row.get(3).toString());
            user.setPhone(row.get(4).toString());
            // 设置成默认的密码
            user.setPassword(bCryptPasswordEncoder.encode(PwdEnum.PASSWORD.getPassword()));
            saveList.add(user);
        }
        for (User user : saveList) {
            userMapper.insert(user);
        }
        return Result.success();
    }

    @GetMapping(value = "/selectChanged")
    @ApiOperation(value = "记录用户的浏览操作")
    public Result recordUserSelect(@RequestParam Integer userId,@RequestParam Integer workId){
//        System.out.println("成功收到");
        if(userId==null)
            return Result.error("用户未登录","-1");
        this.currentCnt++;
        // 超过界线，更新推荐列表
        if (this.currentCnt > cntLimit) {
            this.currentCnt = 0;
            dataAnalysisService.updateRecommendation();
        }
        userRecordMapper.addUserRecord(new UserRecord(null,userId,workId,new Date()));
        int count=userRecordMapper.selectUserRecentRecord(userId,workId);
        if(count==0){
            userRecordMapper.addUserRecentRecord(new UserRecentRecord(null,userId,workId,1, new Date()));
        }
        else{
//            System.out.println("更新用户最近访问");
            userRecordMapper.updateUserRecentRecord(userId,workId,new Date());
        }
//        System.out.println(count);
        return Result.success();
    }

    @GetMapping(value = "/selectAllRecordByUserId")
    @ApiOperation(value = "查询用户的作品浏览记录")
    public Result findAllRecordByUserId(@RequestParam Integer userId,
                                        @RequestParam(required = false, defaultValue = "1") Integer pageNum,
                                        @RequestParam(required = false, defaultValue = "10") Integer pageSize){
//        System.out.println("成功收到");
        if(userId==null)
            return Result.error("用户未登录","-1");
//        System.out.println(userRecordMapper.selectRecordByUserIdPaging(new Page<>(pageNum,pageSize),userId));
        return Result.success(userRecordMapper.selectRecentRecordByUserIdPaging(new Page<>(pageNum,pageSize),userId));
    }

    @ApiOperation(value = "查询文化作品的核心用户")
    @GetMapping(value = "/getCoreUserByWorkId")
    public Result getCoreUserByUserId(@RequestParam(required = true) Integer workId,
                                      @RequestParam(required = false, defaultValue = "1") Integer pageNum,
                                      @RequestParam(required = false, defaultValue = "5") Integer pageSize){
        IPage<CoreUser> result= coreUserMapper.selectCoreUserByPage(new Page<CoreUser>(pageNum, pageSize),workId);
        Map<String,Object> map = new HashMap<>();
        map.put("workId", workId);
        map.put("coreUserDetails", result);
        return Result.success(map);
    }


}

package com.example.demo.controller;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.poi.excel.ExcelUtil;
import cn.hutool.poi.excel.ExcelWriter;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.demo.common.Result;
import com.example.demo.entity.MonitorWork;
import com.example.demo.entity.RawComment;
import com.example.demo.entity.vo.CountryCommentNum;
import com.example.demo.entity.vo.MonthCommentNum;
import com.example.demo.mapper.MonitorWorkMapper;
import com.example.demo.mapper.RawCommentMapper;
import com.example.demo.service.CommentQueryService;
import com.example.demo.utils.FanyiV3Util;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

// 监测文化作品信息
@RequestMapping("/api/comment")
@RestController
@Api(tags = "作品评论控制器")
public class RawCommentController {
    @Autowired
    private RawCommentMapper rawCommentMapper;

    @Autowired
    private MonitorWorkMapper monitorWorkMapper;

    @Autowired
    private CommentQueryService commentQueryService;

    // 查询所有的评论信息
    @GetMapping("/all")
    @ApiOperation(value = "查询所有评论信息")
    public Result findAll() {
        List<RawComment> rawComments = rawCommentMapper.selectList(null);
        return Result.success(rawComments);
    }

    // 根据评论id查询评论信息
    @GetMapping("/id/{id}")
    @ApiOperation(value = "根据评论ID查询评论信息")
    public Result findById(@PathVariable Long id) throws IOException {
        RawComment rawComment = rawCommentMapper.selectById(id);
        if(rawComment.getOppositeTranslated() == null || rawComment.getOppositeTranslated().isEmpty()){
            String trResult = null;
            if(Objects.equals(rawComment.getLanguage(), "汉语")){
                trResult = FanyiV3Util.getTranslationResult(rawComment.getContent() ,"auto", "en");
            }else if(Objects.equals(rawComment.getLanguage(), "英语")) {
                trResult = FanyiV3Util.getTranslationResult(rawComment.getContent() ,"auto", "zh-CHS");
            }
            rawComment.setOppositeTranslated(trResult);
            LambdaQueryWrapper<RawComment> lambdaQueryWrapper = new LambdaQueryWrapper<>();
            lambdaQueryWrapper.eq(RawComment::getId, rawComment.getId());
            rawCommentMapper.update(rawComment, lambdaQueryWrapper);
        }
        if (rawComment != null) {
            rawComment.setMonitorWork(monitorWorkMapper.selectById(rawComment.getWorkId()));
        }
        return Result.success(rawComment);
    }

    // 查询所有的国家列表
    @GetMapping("/countries")
    @ApiOperation(value = "查询所有的国家列表")
    public Result findAllCountry(@RequestParam(defaultValue = "", required = false) Integer workId) {
        List<String> list = rawCommentMapper.selectAllCountry(workId);
        return Result.success(list);
    }

    // 查询所有的平台列表
    @GetMapping("/platforms")
    @ApiOperation(value = "查询所有的平台列表")
    public Result findAllPlatform() {
        List<String> list = rawCommentMapper.selectAllPlatform();
        return Result.success(list);
    }

    // 带搜索关键词的分页查询评论信息
    @GetMapping("/byPage")
    @ApiOperation(value = "分页查询所有评论信息")
    public Result findPage(@RequestParam(required = false, defaultValue = "-1") Integer workId,
                           @RequestParam(required = false, defaultValue = "") String searchContent,
                           @RequestParam(required = false, defaultValue = "") String searchCountry,
                           @RequestParam(required = false, defaultValue = "") String searchPlatform,
                           @RequestParam(required = false, defaultValue = "") String searchTime,
                           @RequestParam(required = false, defaultValue = "1") Integer pageNum,
                           @RequestParam(required = false, defaultValue = "10") Integer pageSize) throws ParseException {
        LambdaQueryWrapper<RawComment> queryWrapper = Wrappers.<RawComment>lambdaQuery()
                .like(RawComment::getContent, searchContent)
                .like(RawComment::getCountry, searchCountry)
                .like(RawComment::getPlatform, searchPlatform)
                .orderByDesc(RawComment::getLikes);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        if (StringUtils.isNotBlank(searchTime)) {
            queryWrapper.eq(RawComment::getPostTime, sdf.parse(searchTime));
        }
        if (workId != -1) queryWrapper.eq(RawComment::getWorkId, workId);
        return Result.success(rawCommentMapper.selectPage(new Page<>(pageNum, pageSize), queryWrapper));
    }

    // 关联分页模糊查询
    @GetMapping("/byPage2")
    @ApiOperation(value = "分页关联查询所有评论信息")
    public Result byPage2(@RequestParam(required = false, defaultValue = "") String searchWorkName,
                          @RequestParam(required = false, defaultValue = "") String searchContent,
                          @RequestParam(required = false, defaultValue = "") String searchCountry,
                          @RequestParam(required = false, defaultValue = "") String searchPlatform,
                          @RequestParam(required = false, defaultValue = "") String searchTime,
                          @RequestParam(required = false, defaultValue = "1") Integer pageNum,
                          @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
        Page<RawComment> res = rawCommentMapper.selectPage2(new Page<>(pageNum, pageSize),
                searchWorkName, searchContent,
                searchCountry, searchPlatform, searchTime);
        return Result.success(res);
    }

    // 获取热点评论
    @GetMapping("/getHotComment")
    @ApiOperation(value = "查询热点评论")
    public Result getHotComment(@RequestParam(required = false, defaultValue = "") String searchWorkName,
                                @RequestParam(required = false, defaultValue = "") String searchContent,
                                @RequestParam(required = false, defaultValue = "") String searchCountry,
                                @RequestParam(required = false, defaultValue = "") String searchPlatform,
                                @RequestParam(required = false, defaultValue = "") String searchTime,
                                @RequestParam(required = false, defaultValue = "1") Integer pageNum,
                                @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
        Page<RawComment> res = rawCommentMapper.selectHotComments(new Page<>(pageNum, pageSize),
                searchWorkName, searchContent,
                searchCountry, searchPlatform, searchTime);
        return Result.success(res);
    }

    // 根据id删除指定评论
    @DeleteMapping("/delete/{ids}")
    @ApiOperation(value = "根据ID删除指定评论")
    @ApiImplicitParam(name = "ids", value = "评论ID数组")
    public Result deleteById(@PathVariable Long[] ids) {
        int res = rawCommentMapper.deleteBatchIds(Arrays.asList(ids));
        if (res > 0) {
            return Result.success();
        }
        return Result.error("-1", "该评论已经被删除了");
    }

    // 新增评论信息
    @PostMapping("/add")
    @ApiOperation(value = "新增评论信息")
    public Result add(@RequestBody RawComment rawComment) {
        int res = rawCommentMapper.insert(rawComment);
        if (res > 0) {
            return Result.success();
        }
        return Result.error("-1", "插入评论失败");
    }

    // 修改评论信息
    @PutMapping("/update")
    @ApiOperation(value = "修改评论信息")
    public Result update(@RequestBody RawComment rawComment) {
        int res = rawCommentMapper.updateById(rawComment);
        if (res > 0) {
            return Result.success();
        }
        return Result.error("-1", "修改评论失败");
    }

    /**
     * Excel导出
     *
     * @param response 响应对象
     * @throws IOException
     */
    @RequestMapping(value = "/export", method = {RequestMethod.GET, RequestMethod.POST})
    @ApiOperation(value = "导出所有评论信息")
    public void export(@RequestParam(required = false, defaultValue = "") String searchWorkName,
                       @RequestParam(required = false, defaultValue = "") String searchContent,
                       @RequestParam(required = false, defaultValue = "") String searchCountry,
                       @RequestParam(required = false, defaultValue = "") String searchPlatform,
                       @RequestParam(required = false, defaultValue = "") String searchTime,
                       HttpServletResponse response) throws IOException {
        List<Map<String, Object>> list = CollUtil.newArrayList();

        LambdaQueryWrapper<RawComment> queryWrapper2 = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(searchWorkName)) {
            LambdaQueryWrapper<MonitorWork> queryWrapper1 = new LambdaQueryWrapper<>();
            queryWrapper1.like(MonitorWork::getName, searchWorkName);
            List<Integer> workIds = monitorWorkMapper.selectList(queryWrapper1).stream().map(MonitorWork::getId)
                    .collect(Collectors.toList());

            queryWrapper2.in(RawComment::getWorkId, workIds);
        }
        if (StringUtils.isNotBlank(searchCountry)) {
            queryWrapper2.eq(RawComment::getCountry, searchCountry);
        }
        if (StringUtils.isNotBlank(searchPlatform)) {
            queryWrapper2.eq(RawComment::getPlatform, searchPlatform);
        }
        if (StringUtils.isNotBlank(searchTime)) {
            queryWrapper2.eq(RawComment::getPostTime, searchTime);
        }

        queryWrapper2.like(RawComment::getContent, searchContent);

        List<RawComment> all = rawCommentMapper.selectList(queryWrapper2);

        for (RawComment rawComment : all) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("评论ID", rawComment.getId());
            row.put("所属作品ID", rawComment.getWorkId());
            row.put("评论内容", rawComment.getContent());
            row.put("翻译成中文后的评论内容", rawComment.getTranslated());
            row.put("原始评论的语言", rawComment.getLanguage());
            row.put("评论点赞数", rawComment.getLikes());
            row.put("评论的情感倾向", rawComment.getSentiment());
            row.put("评论所属国家", rawComment.getCountry());
            row.put("评论所属平台", rawComment.getPlatform());
            row.put("评论发布的时间", rawComment.getPostTime());
            row.put("评论的相反翻译", rawComment.getOppositeTranslated());
            list.add(row);
        }

        // 2. 写excel
        ExcelWriter writer = ExcelUtil.getWriter(true);
        writer.write(list, true);

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet;charset=utf-8");
        String fileName = URLEncoder.encode("作品相关评论信息", "UTF-8");
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
    @ApiOperation(value = "导出评论信息导入模板")
    public void importTemplate(HttpServletResponse response) throws IOException {

        List<Map<String, Object>> list = CollUtil.newArrayList();

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("评论内容", "");
        row.put("评论的作品ID", "");
        row.put("评论点赞数", "");
        row.put("评论的情感倾向", "");
        row.put("评论所属国家", "");
        row.put("评论所属平台", "");
        row.put("评论发布的时间", "");
        list.add(row);

        // 2. 写excel
        ExcelWriter writer = ExcelUtil.getWriter(true);
        writer.write(list, true);

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet;charset=utf-8");
        String fileName = URLEncoder.encode("作品相关评论信息导入模板", "UTF-8");
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
    @ApiOperation(value = "导入评论信息")
    public Result upload(MultipartFile file) throws IOException, ParseException {
        InputStream inputStream = file.getInputStream();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        // 获取excel表中的每一行数据
        List<List<Object>> lists = ExcelUtil.getReader(inputStream).read(1);
        List<RawComment> saveList = new ArrayList<>();
        for (List<Object> row : lists) { // 将数据保存到列表中
            RawComment rawComment = new RawComment();
            rawComment.setContent(row.get(0).toString());
            rawComment.setLikes(Integer.valueOf(row.get(1).toString()));
            rawComment.setWorkId(Integer.valueOf(row.get(2).toString()));
            rawComment.setSentiment(row.get(3).toString());
            rawComment.setCountry(row.get(4).toString());
            rawComment.setPlatform(row.get(5).toString());
            rawComment.setPostTime(sdf.parse(row.get(6).toString()));
            saveList.add(rawComment);
        }
        for (RawComment rawComment : saveList) {
            rawCommentMapper.insert(rawComment); // 插入数据
        }
        return Result.success();
    }

    // 按国家分类查询作品评论数量
    @GetMapping("/getCommentNum")
    @ApiOperation(value = "按国家分类查询所有作品评论数量")
    public Result findCountryCommentsNumByWorkId(@RequestParam(required = false) Integer workId) {
        List<CountryCommentNum> commentList = rawCommentMapper.getCommentNumByCountryAndWorkId(workId);
        return Result.success(commentList);
    }

    @GetMapping("/getCommentNumByWorkIdAndCountry")
    @ApiOperation(value = "查询某个作品在某个国家的评论数量")
    public Result getCommentNumByWorkIdAndCountry(@RequestParam(required = true) Integer workId, @RequestParam(required = true) String country) {
        LambdaQueryWrapper<RawComment> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(RawComment::getWorkId,workId).eq(RawComment::getCountry,country);
        int count = rawCommentMapper.selectCount(lambdaQueryWrapper);
        return Result.success(count);
    }

    @GetMapping("/getCommentNumBySubCategory")
    @ApiOperation(value = "分页查询不同子类型作品的评论量")
    public Result getCommentNumBySubCategory(@RequestParam(required = false, defaultValue = "") String subCategory,
                                             @RequestParam(required = false, defaultValue = "1") Integer pageNum,
                                             @RequestParam(required = false, defaultValue = "10") Integer pageSize) {

        return Result.success(commentQueryService.querySubCategoryCommentNum(subCategory, pageNum, pageSize));
    }

    @GetMapping("/getAllCommentNumBySubCategory")
    @ApiOperation(value = "查询所有不同子类型作品的评论量")
    public Result getAllCommentNumBySubCategory(@RequestParam(required = false, defaultValue = "") String subCategory) {

        return Result.success(commentQueryService.querySubCategoryCommentNum(subCategory));
    }

    @GetMapping("/getCommentNumByLanguage")
    @ApiOperation(value = "分页查询不同语言评论量")
    public Result getCommentNumByLanguage(@RequestParam(required = false, defaultValue = "") String language,
                                          @RequestParam(required = false, defaultValue = "1") Integer pageNum,
                                          @RequestParam(required = false, defaultValue = "10") Integer pageSize) {

        return Result.success(commentQueryService.queryLanguageCommentNum(language, pageNum, pageSize));
    }

    @GetMapping("/getCommentNumByPlatform")
    @ApiOperation(value = "分页查询不同平台的评论量")
    public Result getCommentNumByPlatform(@RequestParam(required = false, defaultValue = "") String platform,
                                          @RequestParam(required = false, defaultValue = "1") Integer pageNum,
                                          @RequestParam(required = false, defaultValue = "10") Integer pageSize) {

        return Result.success(commentQueryService.queryPlatformCommentNum(platform, pageNum, pageSize));
    }

    @GetMapping("/getHighCommentByPlatform")
    @ApiOperation(value = "根据作品id查询不同平台的高影响力评论")
    public Result getHighCommentByPlatform(@RequestParam(required = true, defaultValue = "") Integer workId,
                                          @RequestParam(required = false, defaultValue = "5") Integer size) {
        List<String> platforms = rawCommentMapper.selectPlatformByWorkId(workId);
        Map<String,Object> map = new HashMap<>();
        for(String platform : platforms){
            LambdaQueryWrapper<RawComment> lambdaQueryWrapper = new LambdaQueryWrapper<>();
            lambdaQueryWrapper.eq(RawComment::getWorkId,workId).eq(RawComment::getPlatform,platform)
                    .orderByDesc(RawComment::getLikes).last("limit "+size);
            List<RawComment> highCommentList = rawCommentMapper.selectList(lambdaQueryWrapper);
            map.put(platform,highCommentList);
        }
        return Result.success(map);
    }

    @GetMapping("/getCommentNumByWorkIdAndYear")
    @ApiOperation(value = "查询近年的评论数量变化")
    public Result getCommentNumByWorkIdAndYear(@RequestParam(required = false, defaultValue = "") Integer workId,
                                           @RequestParam(required = false, defaultValue = "1") Integer year,
                                               @RequestParam(required = true) String country ) {
        List<MonthCommentNum> list = new ArrayList<>();
        for(int i=year*12-1;i>=0;i--){
            MonthCommentNum monthCommentNum = rawCommentMapper.getCommentNumByWorkIdAndYear(workId,i,country);
            if(monthCommentNum==null){
                Date date = new Date();
                Calendar calendar = Calendar.getInstance();
                calendar.setTime(date);
                calendar.add(Calendar.MONTH, -1*i);
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM");
                String time=sdf.format(calendar.getTime());
                monthCommentNum = new MonthCommentNum(0,time);
            }
            list.add(monthCommentNum);
        }
        return Result.success(list);
    }

    @GetMapping("/getPlatformByWorkIdAndCountry")
    @ApiOperation(value = "根据作品id和国家查询在该国家对该作品发布过评论的所有平台")
    public Result getPlatformByWorkIdAndCountry(@RequestParam(required = false, defaultValue = "") Integer workId,
                                               @RequestParam(required = true) String country ) {
        List<String> platformList = rawCommentMapper.getPlatformByWorkIdAndCountry(workId,country);
        return Result.success(platformList);
    }

    @GetMapping("/getPolarityComment")
    @ApiOperation(value = "根据平台作品id和平台和国家查询极性评论")
    public Result getPolarityComment(@RequestParam(required = false, defaultValue = "") Integer workId,
                                     @RequestParam(required = true) String country,
                                     @RequestParam(required = true) String platform ,
                                     @RequestParam(required = false, defaultValue = "5") Integer size) {
        Map<String,Object> map = new HashMap<>();
        LambdaQueryWrapper<RawComment> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        if(workId !=null){
            lambdaQueryWrapper.eq(RawComment::getWorkId,workId);
        }
        lambdaQueryWrapper.eq(RawComment::getPlatform,platform).eq(RawComment::getSentiment, "积极")
                .eq(RawComment::getCountry, country)
                .orderByDesc(RawComment::getLikes).last("limit "+size);
        List<RawComment> positiveCommentList = rawCommentMapper.selectList(lambdaQueryWrapper);
        map.put("positive",positiveCommentList);

        LambdaQueryWrapper<RawComment> lambdaQueryWrapper1 = new LambdaQueryWrapper<>();
        if(workId !=null){
            lambdaQueryWrapper1.eq(RawComment::getWorkId,workId);
        }
        lambdaQueryWrapper1.eq(RawComment::getPlatform,platform).eq(RawComment::getSentiment, "消极")
                .eq(RawComment::getCountry, country)
                .orderByDesc(RawComment::getLikes).last("limit "+size);
        List<RawComment> negativeCommentList = rawCommentMapper.selectList(lambdaQueryWrapper1);
        map.put("negative",negativeCommentList);
        return Result.success(map);
    }

    @ApiOperation(value = "根据作品id查询总体信息")
    @GetMapping("/getCommentOverallMessage")
    public Result getCommentOverallMessage(@RequestParam(required = true) Integer workId){
        LambdaQueryWrapper<RawComment> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(RawComment::getWorkId, workId);
        int totalCommentNum = rawCommentMapper.selectCount(lambdaQueryWrapper);

        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
        String todayDate = formatter.format(calendar.getTime());
        lambdaQueryWrapper.eq(RawComment::getPostTime, todayDate);
        int todayCommentNum = rawCommentMapper.selectCount(lambdaQueryWrapper);

        QueryWrapper<RawComment> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("workId",workId);
        queryWrapper.select("DISTINCT platform");
        int platformNum = rawCommentMapper.selectCount(queryWrapper);

        QueryWrapper<RawComment> queryWrapper1 = new QueryWrapper<>();
        queryWrapper1.eq("workId",workId);
        queryWrapper1.select("DISTINCT country");
        int countryNum = rawCommentMapper.selectCount(queryWrapper1);

        QueryWrapper<RawComment> queryWrapper2 = new QueryWrapper<>();
        queryWrapper2.eq("workId",workId);
        queryWrapper2.select("DISTINCT language");
        int languageNum = rawCommentMapper.selectCount(queryWrapper2);

        lambdaQueryWrapper.clear();
        lambdaQueryWrapper.eq(RawComment::getWorkId, workId).orderByDesc(RawComment::getPostTime).last("limit 1");
        RawComment farComment = rawCommentMapper.selectOne(lambdaQueryWrapper);
        lambdaQueryWrapper.clear();
        lambdaQueryWrapper.eq(RawComment::getWorkId, workId).orderByAsc(RawComment::getPostTime).last("limit 1");
        RawComment nearComment = rawCommentMapper.selectOne(lambdaQueryWrapper);
        long milliseconds1 = nearComment.getPostTime().getTime();
        long milliseconds2 = farComment.getPostTime().getTime();
        long diff = milliseconds2 - milliseconds1;
        long daysBetween = diff / (24 * 60 * 60 * 1000);
        String firstCommentDate = formatter.format(milliseconds1);
        String lastCommentDate = formatter.format(milliseconds2);


        Map<String,Object> messageMap = new HashMap<>();
        messageMap.put("allNum",totalCommentNum);
        messageMap.put("todayNum",todayCommentNum);
        messageMap.put("platformNum",platformNum);
        messageMap.put("countryNum",countryNum);
        messageMap.put("languageNum",languageNum);
        messageMap.put("firstCommentDate",firstCommentDate);
        messageMap.put("lastCommentDate",lastCommentDate);
        messageMap.put("daysBetween",daysBetween);

        return Result.success(messageMap);
    }

}


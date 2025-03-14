package com.example.demo.controller;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.poi.excel.ExcelUtil;
import cn.hutool.poi.excel.ExcelWriter;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.demo.common.Result;
import com.example.demo.entity.SentimentAnalysis;
import com.example.demo.entity.dto.SentimentDto;
import com.example.demo.mapper.SentimentAnalysisMapper;
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
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

// 监测作品的细腻情感分析
@RequestMapping("/api/sentiment")
@RestController
@Api(tags = "情感分析控制器")
public class SentimentAnalysisController {
    @Autowired
    private SentimentAnalysisMapper sentimentAnalysisMapper;

    // 查询所有的情感分析结果
    @GetMapping("/all")
    @ApiOperation(value = "查询所有情感分析结果")
    public Result findAll() {
        List<SentimentAnalysis> sentimentAnalyses = sentimentAnalysisMapper.findAll();
        return Result.success(sentimentAnalyses);
    }

    // 根据情感分析id查询情感分析结果
    @GetMapping("/id/{id}")
    @ApiOperation(value = "根据ID查询情感分析结果")
    public Result findById(@PathVariable Long id) {
        SentimentAnalysis sentimentAnalysis = sentimentAnalysisMapper.selectById(id);
        return Result.success(sentimentAnalysis);
    }

    // 分页 模糊查询
    @GetMapping("/byPage")
    @ApiOperation(value = "分页模糊查询情感分析结果")
    public Result findPage(@RequestParam(required = false, defaultValue = "") String searchWorkName,
                              @RequestParam(required = false, defaultValue = "") String searchCountry,
                              @RequestParam(required = false, defaultValue = "") String searchPlatform,
                              @RequestParam(required = false, defaultValue = "") String searchTime,
                              @RequestParam(required = false, defaultValue = "1") Integer pageNum,
                              @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
        Page<SentimentAnalysis> mapperPage = sentimentAnalysisMapper
                .findPage(new Page<>(pageNum, pageSize),
                        searchWorkName,
                        searchCountry,
                        searchPlatform,
                        searchTime);
        return Result.success(mapperPage);
    }
    // 精确多条件兼容查询
    @GetMapping("/query-list")
    @ApiOperation(value = "多条件精确查询情感分析结果")
    public Result findAllQuery(@RequestParam Integer searchWorkId,
                                  @RequestParam(required = false, defaultValue = "") String searchCountry,
                                  @RequestParam(required = false, defaultValue = "") String searchPlatform,
                                  @RequestParam(required = false, defaultValue = "") String searchTime) {
        QueryWrapper<SentimentAnalysis> query = new QueryWrapper<>();
        if (searchWorkId != null) {
            query.eq("workId", searchWorkId);
        }
        if (StringUtils.isNotBlank(searchCountry)) {
            query.eq("country", searchCountry);
        }
        if (StringUtils.isNotBlank(searchPlatform)) {
            query.eq("platform", searchPlatform);
        }
        if (StringUtils.isNotBlank(searchTime)) {
            query.eq("time", searchTime);
        }

        return Result.success(sentimentAnalysisMapper.selectList(query));
    }


    // 根据作品ID和日期查询各个国家的情感分析结果
    @GetMapping("/getWorldSentiment")
    @ApiOperation(value = "查询各个国家的情感分析结果")
    public Result getWorldSentiment(@RequestParam Integer searchWorkId,
                                       @RequestParam(defaultValue = "", required = false) String searchTime) {
        List<SentimentAnalysis> res = sentimentAnalysisMapper.findByWorkIdAndTime(searchWorkId, searchTime);
        return Result.success(res);
    }


    // 统计不同情感倾向的评论数
    @GetMapping("/countDaily")
    @ApiOperation(value = "统计不同情感倾向的评论数")
    public Result countDaily(@RequestParam Integer workId,
                                @RequestParam(required = false, defaultValue = "") String country,
                                @RequestParam(required = false, defaultValue = "") String postTime
                                ) {
        QueryWrapper<SentimentAnalysis> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("workId", workId);
        if (country.length() > 0) {
            queryWrapper.eq("country", country);
        }
        if (postTime.length() > 0) {
            queryWrapper.eq("time", postTime);
        }
        List<SentimentAnalysis> sentimentAnalyses
                = sentimentAnalysisMapper.selectList(queryWrapper);
        SentimentDto sentimentDto = new SentimentDto();
        for (SentimentAnalysis sentimentAnalysis: sentimentAnalyses) {
            sentimentDto.happyAdd(sentimentAnalysis.getHappy());
            sentimentDto.amazedAdd(sentimentAnalysis.getAmazed());
            sentimentDto.neutralityAdd(sentimentAnalysis.getNeutrality());
            sentimentDto.sadAdd(sentimentAnalysis.getSad());
            sentimentDto.angryAdd(sentimentAnalysis.getAngry());
            sentimentDto.fearAdd(sentimentAnalysis.getFear());
        }
        return Result.success(sentimentDto);
    }

    // 按月统计不同情感倾向的评论数
    @GetMapping("/countMonthly")
    @ApiOperation(value = "统计不同情感倾向的评论数")
    public Result countMonthly(@RequestParam Integer workId,
                             @RequestParam(required = false, defaultValue = "") String country,
                             @RequestParam(required = false, defaultValue = "") String selectMonth
    ) {
        SentimentDto sentimentDto = sentimentAnalysisMapper.selectSentimentByMonth(workId, country, selectMonth);
        if (sentimentDto == null) {
            sentimentDto = new SentimentDto();
        }
        return Result.success(sentimentDto);
    }

    // 根据id删除指定情感分析结果
    @DeleteMapping("/delete/{ids}")
    @ApiOperation(value = "根据ID删除指定情感分析结果")
    @ApiImplicitParam(name = "ids", value = "情感分析ID数组")
    public Result deleteById(@PathVariable Long[] ids) {
        int res = sentimentAnalysisMapper.deleteBatchIds(Arrays.asList(ids));
        if (res > 0) {
            return Result.success();
        }
        return Result.error("-1", "该情感分析结果已经被删除了");
    }

    // 新增情感分析结果
    @PostMapping("/add")
    @ApiOperation(value = "新增情感分析结果")
    public Result add(@RequestBody SentimentAnalysis sentimentAnalysis) {
        int res = sentimentAnalysisMapper.insert(sentimentAnalysis);
        if (res > 0) {
            return Result.success();
        }
        return Result.error("-1", "插入情感分析结果失败");
    }

    // 修改情感分析结果
    @PutMapping("/update")
    @ApiOperation(value = "修改情感分析结果")
    public Result update(@RequestBody SentimentAnalysis sentimentAnalysis) {
        int res = sentimentAnalysisMapper.updateById(sentimentAnalysis);
        if (res > 0) {
            return Result.success();
        }
        return Result.error("-1", "修改情感分析结果失败");
    }

    /**
     * Excel导出
     *
     * @param response 响应对象
     * @throws IOException
     */
    @RequestMapping(value = "/export", method = {RequestMethod.GET, RequestMethod.POST})
    @ApiOperation(value = "导出所有情感分析结果")
    public void export(HttpServletResponse response) throws IOException {

        List<Map<String, Object>> list = CollUtil.newArrayList();

        List<SentimentAnalysis> all = sentimentAnalysisMapper.findAll();
        for (SentimentAnalysis sentimentAnalysis : all) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("情感分析ID", sentimentAnalysis.getId());
            row.put("所属作品ID", sentimentAnalysis.getWorkId());
            row.put("所属作品名", sentimentAnalysis.getMonitorWork().getName());
            row.put("所属作品类型", sentimentAnalysis.getMonitorWork().getCategory());
            row.put("所属国家", sentimentAnalysis.getCountry());
            row.put("所分析评论的发布时间", sentimentAnalysis.getTime());
            row.put("所属平台", sentimentAnalysis.getPlatform());
            row.put("开心的评论数", sentimentAnalysis.getHappy());
            row.put("惊讶的评论数", sentimentAnalysis.getAmazed());
            row.put("中立的评论数", sentimentAnalysis.getNeutrality());
            row.put("伤心的评论数", sentimentAnalysis.getSad());
            row.put("愤怒的评论数", sentimentAnalysis.getAngry());
            row.put("恐惧的评论数", sentimentAnalysis.getFear());
            list.add(row);
        }

        // 2. 写excel
        ExcelWriter writer = ExcelUtil.getWriter(true);
        writer.write(list, true);

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet;charset=utf-8");
        String fileName = URLEncoder.encode("细腻情感分析信息", "UTF-8");
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
    @ApiOperation(value = "导出情感分析结果导入模板")
    public void importTemplate(HttpServletResponse response) throws IOException {

        List<Map<String, Object>> list = CollUtil.newArrayList();

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("所属作品ID", "");
        row.put("所属国家", "");
        row.put("所分析评论的发布时间", "");
        row.put("所属平台", "");
        row.put("开心的评论数", "");
        row.put("惊讶的评论数", "");
        row.put("中立的评论数", "");
        row.put("厌恶的评论数", "");
        row.put("愤怒的评论数", "");
        row.put("恐惧的评论数", "");
        list.add(row);

        // 2. 写excel
        ExcelWriter writer = ExcelUtil.getWriter(true);
        writer.write(list, true);

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet;charset=utf-8");
        String fileName = URLEncoder.encode("细腻情感分析信息导入模板", "UTF-8");
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
    @ApiOperation(value = "导入情感分析结果")
    public Result upload(MultipartFile file) throws IOException, ParseException {
        InputStream inputStream = file.getInputStream();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        // 获取excel表中的每一行数据
        List<List<Object>> lists = ExcelUtil.getReader(inputStream).read(1);
        List<SentimentAnalysis> saveList = new ArrayList<>();
        for (List<Object> row : lists) { // 将数据保存到列表中
            SentimentAnalysis sentimentAnalysis = new SentimentAnalysis();
            sentimentAnalysis.setWorkId(Integer.valueOf(row.get(0).toString()));
            sentimentAnalysis.setCountry(row.get(1).toString());
            sentimentAnalysis.setTime(sdf.parse(row.get(2).toString()));
            sentimentAnalysis.setPlatform(row.get(3).toString());
            sentimentAnalysis.setHappy(Integer.valueOf(row.get(4).toString()));
            sentimentAnalysis.setAmazed(Integer.valueOf(row.get(5).toString()));
            sentimentAnalysis.setNeutrality(Integer.valueOf(row.get(6).toString()));
            sentimentAnalysis.setSad(Integer.valueOf(row.get(7).toString()));
            sentimentAnalysis.setAngry(Integer.valueOf(row.get(8).toString()));
            sentimentAnalysis.setFear(Integer.valueOf(row.get(9).toString()));
            saveList.add(sentimentAnalysis);
        }
        for (SentimentAnalysis sentimentAnalysis : saveList) {
            sentimentAnalysisMapper.insert(sentimentAnalysis); // 插入数据
        }
        return Result.success();
    }
}

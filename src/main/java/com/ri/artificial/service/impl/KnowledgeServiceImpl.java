package com.ri.artificial.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpStatus;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ri.artificial.domain.Result;
import com.ri.artificial.domain.po.SysKnowledge;
import com.ri.artificial.domain.query.KnowledgePageQuery;
import com.ri.artificial.domain.dto.SplitterForm;
import com.ri.artificial.domain.vo.preview.Chunk;
import com.ri.artificial.domain.vo.preview.PreviewResultVO;
import com.ri.artificial.domain.vo.preview.Stats;
import com.ri.artificial.exception.BadRequestException;
import com.ri.artificial.exception.SystemException;
import com.ri.artificial.mapper.SysKnowledgeMapper;
import com.ri.artificial.service.IKnowledgeService;
import com.ri.artificial.splitter.DocumentSplitter;
import com.ri.artificial.splitter.SplitterRegistry;
import com.ri.artificial.utils.OssClientUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author Ri
 * @date 2026-10-01 11:14
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeServiceImpl extends ServiceImpl<SysKnowledgeMapper, SysKnowledge> implements IKnowledgeService {

    /** 预览接口最多返回的分片数：防止大文件产生几千片撑爆响应 */
    private static final int PREVIEW_MAX_CHUNKS = 100;

    private final OssClientUtil ossClient;

    private final VectorStore vectorStore;

    private final SplitterRegistry splitterRegistry;

    @Override
    public Page<SysKnowledge> pageKnowledge(KnowledgePageQuery query) {
        Long userId = StpUtil.getLoginIdAsLong();
        return lambdaQuery().eq(ObjectUtil.isNotNull(userId), SysKnowledge::getUserId, userId)
                .like(StrUtil.isNotBlank(query.getFileName()), SysKnowledge::getFileName, query.getFileName())
                .page(query.toPage());
    }

    @Override
    @Transactional
    public Result<String> uploadFiles(List<MultipartFile> files, String splitterName, SplitterForm params) {
        Long userId = StpUtil.getLoginIdAsLong();
        // 上传到oss服务器，拿到所有文件url。
        List<String> fileUrls = ossClient.uploadFiles(files);
        // 逐个保存文件信息，分块数后续解析后回填
        for (int i = 0; i < files.size(); i++) {
            MultipartFile file = files.get(i);
            SysKnowledge knowledge = new SysKnowledge();
            // 拼接 Knowledge 对象
            knowledge.setUserId(userId)
                    .setFileName(file.getOriginalFilename())
                    .setFileUrl(fileUrls.get(i))
                    .setFileSize(file.getSize())
                    .setVectorId(UUID.randomUUID().toString());
            // 存入到数据库中
            save(knowledge);
            // 保存到 Milvus 中（同样不在事务管控内，失败需自行补偿删除 DB 记录）
            saveMilvus(file, knowledge, splitterName, params);
        }
        return Result.success();
    }

    /**
     * 分片预览（dry-run）。
     */
    @Override
    public PreviewResultVO previewChunks(MultipartFile file, String splitterName, SplitterForm params) {
        long startMs = System.currentTimeMillis();

        DocumentSplitter splitter = splitterRegistry.get(splitterName);
        List<Document> chunks;
        try {
            chunks = splitter.split(parseFile(file), params);
        } catch (Exception e) {
            throw new SystemException(file.getOriginalFilename() + " 文件解析或分片失败", e);
        }
        long costMs = System.currentTimeMillis() - startMs;

        // 统计信息按全量分片计算（截断展示不影响统计的真实性）
        List<Integer> lengths = chunks.stream().map(c -> c.getText().length()).toList();
        int total = lengths.size();
        int min = lengths.stream().min(Integer::compareTo).orElse(0);
        int max = lengths.stream().max(Integer::compareTo).orElse(0);
        int avg = total == 0 ? 0 : (int) Math.round(lengths.stream().mapToInt(Integer::intValue).average().orElse(0));

        // 明细最多返回 PREVIEW_MAX_CHUNKS 片，防止大文件撑爆响应
        List<Chunk> items = new ArrayList<>();
        for (int i = 0; i < chunks.size() && i < PREVIEW_MAX_CHUNKS; i++) {
            Document c = chunks.get(i);
            Map<String, Object> meta = c.getMetadata();
            // Fixed 策略会在元数据里带原文偏移，其他策略为空
            Long start = meta.get("start") instanceof Number n ? n.longValue() : null;
            Long end = meta.get("end") instanceof Number n ? n.longValue() : null;
            items.add(new Chunk(i, c.getText(), c.getText().length(), start, end));
        }

        return new PreviewResultVO(
                file.getOriginalFilename(),
                splitterName,
                items,
                new Stats(total, min, avg, max, costMs),
                total > PREVIEW_MAX_CHUNKS
        );
    }

    /** 向量解析保存到 milvus 中 */
    private void saveMilvus(MultipartFile file, SysKnowledge knowledge, String splitterName, SplitterForm params) {
        List<Document> chunks = Collections.emptyList();
        try {
            // 1. 按扩展名解析
            List<Document> documents = parseFile(file);
            // 2. 用指定策略分片
            DocumentSplitter splitter = splitterRegistry.get(splitterName);
            chunks = splitter.split(documents, params);

            // 3. 打元数据，用于检索时按用户/文件隔离
            chunks.forEach(chunk -> {
                chunk.getMetadata().put("document_id", knowledge.getVectorId());
                chunk.getMetadata().put("user_id", knowledge.getUserId());
                chunk.getMetadata().put("file_name", knowledge.getFileName());
                chunk.getMetadata().put("splitter", splitterName);
            });

            // 4. 写入向量库
            vectorStore.add(chunks);

            // 5. 回填分块数
            knowledge.setChunkCount(chunks.size());
            updateById(knowledge);
        } catch (Exception e) {
            // 补偿：删掉可能已写入的向量
            if (CollUtil.isNotEmpty(chunks)) {
                List<String> ids = chunks.stream().map(Document::getId).toList();
                try {
                    vectorStore.delete(ids);
                } catch (Exception ex) {
                    // 记日志即可
                    log.warn("补偿删除 Milvus 向量失败，需手动清理。docId={}, vectorIds={}",
                            knowledge.getVectorId(), ids, ex);
                }
            }
            throw new SystemException(file.getOriginalFilename() + "文件解析或向量化失败，已跳过索引", e);
        }
    }

    private List<Document> parseFile(MultipartFile file) {
        String filename = file.getOriginalFilename();
        // 解析后缀名
        String ext = filename == null ? "" :
                filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
        // 获取文件资源
        Resource resource = file.getResource();

        // 根据不同的文件格式使用不同的解析器
        if("pdf".equals(ext)){
            return new PagePdfDocumentReader(resource).read();
        } else if ("md".equals(ext) || "markdown".equals(ext)){
            MarkdownDocumentReaderConfig mdConfig = MarkdownDocumentReaderConfig.builder()
                    // 遇到 --- 分割线，切分
                    .withHorizontalRuleCreateDocument(true)
                    .build();
            return new MarkdownDocumentReader(resource, mdConfig).read();
        } else {
            return new TikaDocumentReader(resource).read();
        }
    }

    @Override
    @Transactional
    public Result<String> deleteKnowledge(Long userId, List<Long> ids) {
        // 查询对应用户的知识库
        List<SysKnowledge> list = lambdaQuery().in(SysKnowledge::getId, ids)
                        .eq(SysKnowledge::getUserId, userId).list();

        if(CollUtil.isEmpty(list)){
            throw new BadRequestException("文件不存在", HttpStatus.HTTP_BAD_REQUEST);
        }
        // 根据Id查询出对应的vectorId
        List<String> vectorIds = list.stream().map(SysKnowledge::getVectorId)
                .filter(StrUtil::isNotBlank)
                .toList();

        // 如果被删除列表Id为空的话就不进行删除了
        if(CollUtil.isNotEmpty(vectorIds)){
            try{
                vectorStore.delete(new FilterExpressionBuilder()
                        // 使用 .toArray() 否则会变成 [[]] 形式，直接报错
                        .in("document_id", vectorIds.toArray())
                        .build());
            } catch(Exception e) {
                throw new SystemException("删除 Milvus 向量失败, docIds: " + vectorIds, e);
            }
        }
        // 删除 OSS 中的文件
        List<String> fileUrls = list.stream().map(SysKnowledge::getFileUrl)
                .filter(StrUtil::isNotBlank).toList();
        if (CollUtil.isNotEmpty(fileUrls)) {
            ossClient.deleteFiles(fileUrls);
        }
        // 逻辑删除数据库记录
        removeByIds(ids);
        return Result.success();
    }

    @Override
    public List<String> listVectorIds(List<Long> docIds) {
        Long userId = StpUtil.getLoginIdAsLong();
        // 根据 docIds 拿到该用户的知识库文件
        List<SysKnowledge> list = lambdaQuery().in(SysKnowledge::getId, docIds)
                .eq(SysKnowledge::getUserId, userId)
                .list();

        // 如果没有，则直接返回空列表
        if(CollUtil.isEmpty(list)) {
            return CollUtil.empty(List.class);
        }
        // 如果有则把所有 vectorId 返回
        return list.stream().map(SysKnowledge::getVectorId)
                .filter(StrUtil::isNotBlank)
                .distinct().toList();
    }
}
